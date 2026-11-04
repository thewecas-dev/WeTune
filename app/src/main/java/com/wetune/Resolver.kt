package com.wetune

import android.net.ConnectivityManager
import com.wetune.sources.Addons
import com.wetune.sources.JioSaavn
import com.wetune.sources.Local
import com.wetune.sources.Source
import com.wetune.sources.Subsonic
import com.wetune.youtube.YouTube
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a track into a playable URL, with a short cache (YouTube URLs expire after ~6h).
 *
 * YouTube songs climb a quality ladder up to the ceiling set in Settings: YouTube's own stream plays at once, then
 * JioSaavn's 320 kbps copy, then a lossless copy from the user's add-ons (in their order), each only when the exact
 * same recording is found. Matches are searched in the background; the player switches when one turns up.
 */
object Resolver {
    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()
    private const val TTL = 3 * 60 * 60 * 1000L
    private const val LOSSLESS_TTL = 20 * 60 * 1000L // add-on file links are signed and expire

    /** YouTube id → matching JioSaavn ref, or "" when there's no good match. */
    private val saavnMatch = ConcurrentHashMap<String, String>()
    private val saavnInfo = ConcurrentHashMap<String, String>()
    /** YouTube id → matching add-on ref ("addonId|trackId"), or "" when no add-on has it. */
    private val losslessMatch = ConcurrentHashMap<String, String>()
    private val losslessInfo = ConcurrentHashMap<String, String>()
    /** Which copy each YouTube song last played from ("lossless" | "saavn"), so a failing copy can be dropped. */
    private val using = ConcurrentHashMap<String, String>()
    /** Byte rate of the copy each YouTube song resolved to last, for sizing a pre-fill (FLAC ≈ 70% of raw PCM). */
    private val byteRate = ConcurrentHashMap<String, Long>()
    fun bytesPerSec(ytRef: String): Long? = byteRate[ytRef]

    /** What the playing copy of YouTube song [ytRef] is, for the pipeline: "Lossless add-on · Qobuz […]", "JioSaavn · 320 kbps"; null = YouTube itself. */
    fun copyName(ytRef: String): String? = when (using[ytRef]) {
        "lossless" -> "Lossless add-on · " + (losslessMatch[ytRef]?.let(Addons::nameOf) ?: "unknown")
        "saavn" -> "JioSaavn · 320 kbps"
        else -> null
    }

    fun source(name: String): Source = when (name) {
        "yt" -> YouTube
        "saavn" -> JioSaavn
        "subsonic" -> Subsonic
        else -> Local
    }

    /** The quality ceiling for the current connection: 0 data saver, 1 high, 2 max (320 kbps), 3 lossless. */
    fun level(): Int {
        val cm = App.ctx.getSystemService(ConnectivityManager::class.java)
        return when (if (cm.isActiveNetworkMetered) Settings.maxMobile else Settings.maxWifi) { "lossless" -> 3; "max" -> 2; "high" -> 1; else -> 0 }
    }

    fun levelName(l: Int) = when (l) { 3 -> "lossless"; 2 -> "max (320 kbps)"; 1 -> "high"; else -> "data saver" }

    fun highQuality(): Boolean = level() >= 1

    private fun wantsMax() = level() >= 2 && Settings.saavnOn
    private fun wantsLossless() = level() >= 3 && Addons.enabled.isNotEmpty()

    /** Blocking. The playable address for a track: the best copy known within the ceiling, else its own source. */
    fun streamUrl(source: String, ref: String, high: Boolean = highQuality(), title: String? = null, artist: String? = null, durationMs: Long = 0): String {
        val key = "$source:$ref:$high:${wantsMax()}:${wantsLossless()}"
        cache[key]?.let { (url, expires) -> if (expires > System.currentTimeMillis()) return url }
        val climbs = source == "yt" && title != null
        if (source == "yt") UpgradeLog.add("resolve $ref level=${level()} hints=${title != null} 320=${state(saavnMatch, ref)} lossless=${state(losslessMatch, ref)}")
        if (climbs) {
            // already matched: play the best copy straight away
            bestKnown(ref, high)?.let { (url, ttl) -> cache[key] = url to System.currentTimeMillis() + ttl; return url }
            // otherwise start on YouTube now and look for better copies in the background
        }
        if (climbs) AudioInfo.event("yt:$ref", "Started on YouTube (${if (high) "high" else "data saver"})")
        if (climbs && needsSearch(ref)) matchInBackground(ref, title!!, artist.orEmpty(), durationMs, high)
        val url = source(source).streamUrl(ref, high)
        cache[key] = url to System.currentTimeMillis() + TTL
        // the background search often finishes while YouTube is still answering; don't let the YouTube address win.
        // (saved first, then checked: a match landing after this check is followed by forget(), which clears it)
        if (climbs) bestKnown(ref, high)?.let { (better, ttl) ->
            UpgradeLog.add("copy for $ref arrived during resolve; using it")
            cache[key] = better to System.currentTimeMillis() + ttl
            return better
        }
        return url
    }

    private fun state(m: Map<String, String>, ref: String) = m[ref]?.let { if (it.isEmpty()) "none" else "found" } ?: "unknown"

    private fun needsSearch(ref: String) = (wantsLossless() && !losslessMatch.containsKey(ref)) || (wantsMax() && !saavnMatch.containsKey(ref))

    /** The best already-matched copy allowed by the ceiling, with how long its address stays valid. */
    private fun bestKnown(ref: String, high: Boolean): Pair<String, Long>? {
        val id = "yt:$ref"
        if (wantsLossless()) losslessMatch[ref]?.takeIf { it.isNotEmpty() }?.let { m ->
            runCatching { Addons.stream(m) }.getOrNull()?.let { s ->
                using[ref] = "lossless"
                byteRate[ref] = s.bitDepth.coerceAtLeast(16).toLong() * s.sampleRate.coerceAtLeast(44_100) * 2 / 8 * 7 / 10
                AudioInfo.lossless(id, s.codec, s.bitDepth, s.sampleRate)
                AudioInfo.event(id, "Lossless copy from ${Addons.nameOf(m) ?: "an add-on"} (${listOfNotNull(s.codec, s.bitDepth.takeIf { it > 0 }?.let { "$it-bit" }, s.sampleRate.takeIf { it > 0 }?.let { "${AudioInfo.formatKhz(it)} kHz" }).joinToString(" · ")})")
                losslessInfo[ref]?.let { AudioInfo.matched(id, it) }
                AudioInfo.upgrading(id, "upgraded")
                return s.url to LOSSLESS_TTL
            }
        }
        if (wantsMax()) saavnMatch[ref]?.takeIf { it.isNotEmpty() }?.let { m ->
            runCatching { JioSaavn.streamUrl(m, high) }.getOrNull()?.let { url ->
                using[ref] = "saavn"
                byteRate[ref] = 40_000L
                AudioInfo.lossless(id, null, 0, 0)
                AudioInfo.event(id, "JioSaavn's 320 kbps copy")
                saavnInfo[ref]?.let { AudioInfo.matched(id, it) }
                AudioInfo.upgrading(id, if (wantsLossless() && !losslessMatch.containsKey(ref)) "searching" else "upgraded")
                return url to TTL
            }
        }
        return null
    }

    /** Called with a YouTube id once a better copy is found in the background (the player may switch to it). */
    var onUpgrade: ((ytRef: String) -> Unit)? = null
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val worker = java.util.concurrent.Executors.newCachedThreadPool()

    /** Looks up a song's better copies ahead of time (no audio is downloaded), so it starts at its best when it plays. */
    fun prepare(ref: String, title: String, artist: String, durationMs: Long) {
        if (needsSearch(ref)) matchInBackground(ref, title, artist, durationMs, highQuality())
    }

    /**
     * Searches JioSaavn and the add-ons at the same time and upgrades once, straight to the best copy found: it waits up
     * to 6 s for a lossless answer before settling for 320 kbps (add-on servers can be slow to wake); a lossless copy
     * that turns up later still upgrades the song (or its next play).
     */
    private fun matchInBackground(ref: String, title: String, artist: String, durationMs: Long, high: Boolean) {
        if (!pending.add(ref)) return
        AudioInfo.upgrading("yt:$ref", "searching")
        worker.execute {
            try {
                val lossless = if (wantsLossless() && !losslessMatch.containsKey(ref)) worker.submit<Boolean> {
                    UpgradeLog.add("searching add-ons for $ref: $title / $artist")
                    losslessFind(ref, title, artist, durationMs).also { UpgradeLog.add("add-ons for $ref: ${if (it) "found " + losslessMatch[ref] else "no match"}") }
                } else null
                val saavn = if (wantsMax() && !saavnMatch.containsKey(ref)) worker.submit<Boolean> {
                    UpgradeLog.add("searching JioSaavn for $ref: $title / $artist")
                    saavnFind(ref, title, artist, durationMs).also { UpgradeLog.add("JioSaavn for $ref: ${if (it) "found" else "no match"}") }
                } else null
                val losslessInTime = lossless?.let { runCatching { it.get(6, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull() }
                runCatching { saavn?.get() }
                if (lossless != null) AudioInfo.event("yt:$ref", when (losslessInTime) { true -> "Found lossless on ${losslessMatch[ref]?.let(Addons::nameOf)}"; false -> "No lossless copy on your add-ons"; null -> "Add-ons slow to answer; trying 320 kbps meanwhile" })
                if (saavn != null) AudioInfo.event("yt:$ref", if (saavnMatch[ref].orEmpty().isNotEmpty()) "Found JioSaavn 320 kbps" else "No JioSaavn 320 kbps copy")
                if (losslessMatch[ref].orEmpty().isNotEmpty() || saavnMatch[ref].orEmpty().isNotEmpty()) onUpgrade?.invoke(ref)
                if (lossless != null && losslessInTime == null && runCatching { lossless.get() }.getOrDefault(false)) {
                    UpgradeLog.add("lossless for $ref arrived late; upgrading again")
                    AudioInfo.event("yt:$ref", "Found lossless on ${losslessMatch[ref]?.let(Addons::nameOf)} (late)")
                    onUpgrade?.invoke(ref)
                }
                if (losslessMatch[ref].orEmpty().isEmpty() && saavnMatch[ref].orEmpty().isEmpty()) AudioInfo.upgrading("yt:$ref", "none")
            } catch (e: Throwable) {
                UpgradeLog.add("search failed for $ref: $e")
                AudioInfo.upgrading("yt:$ref", "none")
            } finally { pending.remove(ref) }
        }
    }

    /** Forgets the cached address of a song so the next load resolves it again (e.g. to pick up a better copy). */
    fun forget(source: String, ref: String) {
        cache.keys.removeAll { it.startsWith("$source:$ref:") }
    }

    private fun saavnFind(ytRef: String, title: String, artist: String, durationMs: Long): Boolean {
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
        return match.isNotEmpty()
    }

    /** Tries each enabled add-on in the user's order; the first exact match wins. */
    private fun losslessFind(ytRef: String, title: String, artist: String, durationMs: Long): Boolean {
        val match = losslessMatch.getOrPut(ytRef) {
            Addons.enabled.asSequence().mapNotNull { a ->
                SourceMatch.queries(title, artist).asSequence().mapNotNull { q ->
                    val found = runCatching { Addons.search(a, q).take(20) }
                        .onFailure { UpgradeLog.add("${a.name}: search failed ($it)") }.getOrDefault(emptyList())
                    found.firstOrNull { SourceMatch.same(title, artist, durationMs, it) }
                        .also { if (it == null && found.isNotEmpty()) UpgradeLog.add("${a.name}: no match for \"$q\"; nearest: " + found.take(3).joinToString { c -> "${c.title} / ${c.artist} (${c.durationMs / 1000}s)" }) }
                }.firstOrNull()?.also { losslessInfo[ytRef] = listOf(it.title, it.artist, it.album, "via ${a.name}").filter(String::isNotBlank).joinToString(" · ") }
            }.firstOrNull()?.ref.orEmpty()
        }
        return match.isNotEmpty()
    }

    fun invalidate(source: String, ref: String) {
        cache.keys.removeAll { it.startsWith("$source:$ref:") }
        if (source != "yt") return
        // the copy that failed to play is dropped for this song; the next step down plays instead
        when (using.remove(ref)) {
            "lossless" -> losslessMatch[ref] = ""
            "saavn" -> saavnMatch[ref] = ""
        }
        AudioInfo.event("yt:$ref", "That copy failed to play; stepped down")
        AudioInfo.upgrading("yt:$ref", "none")
    }
}
