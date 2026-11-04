package com.wetune

import android.net.ConnectivityManager
import com.wetune.sources.JioSaavn
import com.wetune.sources.Local
import com.wetune.sources.Source
import com.wetune.sources.Subsonic
import com.wetune.youtube.YouTube
import java.util.concurrent.ConcurrentHashMap

/** Turns a track into a playable URL, with a short cache (YouTube URLs expire after ~6h). */
object Resolver {
    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()
    private const val TTL = 3 * 60 * 60 * 1000L

    /** YouTube id → matching JioSaavn ref, or "" when there's no good match. */
    private val saavnMatch = ConcurrentHashMap<String, String>()
    private val saavnInfo = ConcurrentHashMap<String, String>()

    fun source(name: String): Source = when (name) {
        "yt" -> YouTube
        "saavn" -> JioSaavn
        "subsonic" -> Subsonic
        else -> Local
    }

    fun highQuality(): Boolean {
        val cm = App.ctx.getSystemService(ConnectivityManager::class.java)
        return if (cm.isActiveNetworkMetered) Settings.mobileHigh else Settings.wifiHigh
    }

    /**
     * Blocking. With "Play songs from: JioSaavn" a YouTube song is played from JioSaavn's 320 kbps file when the same
     * recording is found there ([title]/[artist]/[durationMs] describe it); otherwise, and for everything else, from its own source.
     */
    fun streamUrl(source: String, ref: String, high: Boolean = highQuality(), title: String? = null, artist: String? = null, durationMs: Long = 0): String {
        val key = "$source:$ref:$high:${Settings.playFrom}"
        cache[key]?.let { (url, expires) -> if (expires > System.currentTimeMillis()) return url }
        if (source == "yt") UpgradeLog.add("resolve $ref playFrom=${Settings.playFrom} hints=${title != null} known=${saavnMatch[ref]?.let { if (it.isEmpty()) "no-match" else "match" } ?: "unknown"}")
        if (source == "yt" && Settings.playFrom == "saavn" && title != null) {
            // already matched: play the 320 kbps copy straight away
            if (saavnMatch.containsKey(ref)) saavnFor(ref, title, artist.orEmpty(), durationMs, high)?.let {
                AudioInfo.upgrading("yt:$ref", "upgraded")
                cache[key] = it to System.currentTimeMillis() + TTL
                return it
            }
            // not matched yet: start on YouTube now, look for the copy in the background, and upgrade if it turns up
            else matchInBackground(ref, title, artist.orEmpty(), durationMs, high)
        }
        val url = source(source).streamUrl(ref, high)
        cache[key] = url to System.currentTimeMillis() + TTL
        // the background search often finishes while YouTube is still answering; don't let the YouTube address win.
        // (saved first, then checked: a match landing after this check is followed by forget(), which clears it)
        if (source == "yt" && Settings.playFrom == "saavn" && title != null && saavnMatch[ref].orEmpty().isNotEmpty())
            saavnFor(ref, title, artist.orEmpty(), durationMs, high)?.let {
                UpgradeLog.add("copy for $ref arrived during resolve; using it")
                AudioInfo.upgrading("yt:$ref", "upgraded")
                cache[key] = it to System.currentTimeMillis() + TTL
                return it
            }
        cache[key] = url to System.currentTimeMillis() + TTL
        return url
    }

    /** Called with a YouTube id once its 320 kbps JioSaavn copy is found in the background (the player may switch to it). */
    var onUpgrade: ((ytRef: String) -> Unit)? = null
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val worker = java.util.concurrent.Executors.newCachedThreadPool()

    private fun matchInBackground(ref: String, title: String, artist: String, durationMs: Long, high: Boolean) {
        if (!pending.add(ref)) return
        worker.execute {
            try {
                UpgradeLog.add("searching JioSaavn for $ref: $title / $artist")
                val url = saavnFor(ref, title, artist, durationMs, high)
                UpgradeLog.add("result for $ref: ${if (url != null) "found" else "no match"}")
                if (url != null) onUpgrade?.invoke(ref)
            } catch (e: Throwable) {
                UpgradeLog.add("search failed for $ref: $e")
                AudioInfo.upgrading("yt:$ref", "none")
            } finally { pending.remove(ref) }
        }
    }

    /** Forgets the cached address of a song so the next load resolves it again (e.g. to pick up its JioSaavn copy). */
    fun forget(source: String, ref: String) {
        cache.keys.removeAll { it.startsWith("$source:$ref:") }
    }

    private fun saavnFor(ytRef: String, title: String, artist: String, durationMs: Long, high: Boolean): String? {
        val id = "yt:$ytRef"
        if (!saavnMatch.containsKey(ytRef)) AudioInfo.upgrading(id, "searching")
        val match = saavnMatch.getOrPut(ytRef) {
            runCatching {
                SourceMatch.queries(title, artist).asSequence().map { q ->
                    val found = JioSaavn.search(q).take(20)
                    found.firstOrNull { it.ref.let(JioSaavn::has320) && SourceMatch.same(title, artist, durationMs, it) }
                        .also { if (it == null) UpgradeLog.add("no match for \"$q\" (${durationMs / 1000}s); nearest: " + found.take(3).joinToString { c -> "${c.title} / ${c.artist} (${c.durationMs / 1000}s${if (JioSaavn.has320(c.ref)) "" else ", no 320"})" }) }
                }.firstOrNull { it != null }
                    ?.also { saavnInfo[ytRef] = listOf(it.title, it.artist, it.album).filter(String::isNotBlank).joinToString(" · ") }?.ref
            }.getOrNull().orEmpty()
        }
        saavnInfo[ytRef]?.takeIf { match.isNotEmpty() }?.let { AudioInfo.matched(id, it) }
        val url = match.takeIf { it.isNotEmpty() }?.let { runCatching { JioSaavn.streamUrl(it, high) }.getOrNull() }
        if (url == null) AudioInfo.upgrading(id, "none") // "upgraded" is set once the copy actually plays
        return url
    }

    fun invalidate(source: String, ref: String) {
        cache.keys.removeAll { it.startsWith("$source:$ref:") }
        // if the JioSaavn copy failed to play, fall back to YouTube for this song from now on
        if (source == "yt" && saavnMatch[ref].orEmpty().isNotEmpty()) { saavnMatch[ref] = ""; AudioInfo.upgrading("yt:$ref", "none") }
    }
}
