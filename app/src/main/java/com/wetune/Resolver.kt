package com.wetune

import android.net.ConnectivityManager
import com.wetune.sources.Addons
import com.wetune.sources.JioSaavn
import com.wetune.sources.Local
import com.wetune.sources.Source
import com.wetune.sources.Subsonic
import com.wetune.youtube.YouTube
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

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

    /**
     * Blocking. The playable address for a track: the best copy allowed by the ceiling.
     * A YouTube song whose copies aren't known yet waits up to [START_WAIT] ms while the searches run (alongside
     * YouTube's own lookup), then starts on the best one found — so it never has to switch mid-song. Anything found
     * later is kept for the song's next play. Songs coming up in the queue are matched ahead of time and start at once.
     */
    fun streamUrl(source: String, ref: String, high: Boolean = highQuality(), title: String? = null, artist: String? = null, durationMs: Long = 0): String {
        val key = "$source:$ref:$high:${wantsMax()}:${wantsLossless()}"
        cache[key]?.let { (url, expires) -> if (expires > System.currentTimeMillis()) return url }
        val climbs = source == "yt" && title != null
        if (source == "yt") UpgradeLog.add("resolve $ref level=${level()} hints=${title != null} 320=${state(saavnMatch, ref)} lossless=${state(losslessMatch, ref)}")
        if (!climbs) return source(source).streamUrl(ref, high).also { cache[key] = it to System.currentTimeMillis() + TTL }

        bestKnown(ref, high)?.let { (url, ttl) -> cache[key] = url to System.currentTimeMillis() + ttl; return url }
        if (needsSearch(ref) || inflight.containsKey(ref)) {
            val id = "yt:$ref"
            AudioInfo.upgrading(id, "searching")
            val (lossless, saavn) = searches(ref, title!!, artist.orEmpty(), durationMs)
            val yt = worker.submit<String> { source(source).streamUrl(ref, high) }
            val deadline = System.currentTimeMillis() + START_WAIT
            fun left() = (deadline - System.currentTimeMillis()).coerceAtLeast(1)
            runCatching { saavn?.get(left(), java.util.concurrent.TimeUnit.MILLISECONDS) }
            runCatching { lossless?.get(left(), java.util.concurrent.TimeUnit.MILLISECONDS) }
            bestKnown(ref, high)?.let { (url, ttl) -> yt.cancel(true); cache[key] = url to System.currentTimeMillis() + ttl; return url }
            AudioInfo.upgrading(id, "none")
            return yt.get().also { cache[key] = it to System.currentTimeMillis() + TTL }
        }
        return source(source).streamUrl(ref, high).also { cache[key] = it to System.currentTimeMillis() + TTL }
    }

    /** How long a not-yet-matched song waits for its better copies before starting on what's ready (kept short so play feels instant). */
    private const val START_WAIT = 1_200L

    private fun state(m: Map<String, String>, ref: String) = m[ref]?.let { if (it.isEmpty()) "none" else "found" } ?: "unknown"

    private fun needsSearch(ref: String) = pin(ref) == null && ((wantsLossless() && !losslessMatch.containsKey(ref)) || (wantsMax() && !saavnMatch.containsKey(ref)))

    /** The best already-matched copy allowed by the ceiling, with how long its address stays valid. */
    private fun bestKnown(ref: String, high: Boolean): Pair<String, Long>? {
        // a version the user picked in Audio options wins over everything
        pin(ref)?.let { p ->
            return when {
                p.startsWith("addon|") -> playLossless(ref, p.removePrefix("addon|"))
                p.startsWith("saavn|") -> playSaavn(ref, p.removePrefix("saavn|"), high)
                else -> null // "yt": the original
            }
        }
        // otherwise the first source in the user's order that has the same recording, within the quality ceiling
        for (s in order()) {
            val got = if (s == "saavn") saavnMatch[ref]?.takeIf { wantsMax() && it.isNotEmpty() }?.let { playSaavn(ref, it, high) }
            else losslessMatch[ref]?.takeIf { wantsLossless() && it.startsWith("$s|") }?.let { playLossless(ref, it) }
            if (got != null) return got
        }
        return null
    }

    private fun playLossless(ref: String, m: String): Pair<String, Long>? {
        val id = "yt:$ref"
        val s = runCatching { Addons.stream(m) }.getOrNull() ?: return null
        using[ref] = "lossless"; usingRef[ref] = m
        AudioInfo.lossless(id, s.codec, s.bitDepth, s.sampleRate)
        losslessInfo[ref]?.let { AudioInfo.matched(id, it) }
        AudioInfo.upgrading(id, "upgraded")
        return s.url to LOSSLESS_TTL
    }

    private fun playSaavn(ref: String, m: String, high: Boolean): Pair<String, Long>? {
        val id = "yt:$ref"
        val url = runCatching { JioSaavn.streamUrl(m, high) }.getOrNull() ?: return null
        using[ref] = "saavn"; usingRef[ref] = m
        AudioInfo.lossless(id, null, 0, 0)
        saavnInfo[ref]?.let { AudioInfo.matched(id, it) }
        AudioInfo.upgrading(id, "upgraded")
        return url to TTL
    }

    /** Where better copies are looked for, in the user's order (Settings → Sources): add-on ids with "saavn" among them. */
    fun order(): List<String> = fullOrder().filter { it == "saavn" || Addons.enabled.any { a -> a.id == it } }

    /** Every source in order, switched off or not ("saavn" among add-on ids), as Settings → Sources lists them. */
    fun fullOrder(): List<String> {
        val ids = Addons.list.map { it.id }
        val r = Settings.saavnRank.coerceIn(0, ids.size)
        return ids.take(r) + "saavn" + ids.drop(r)
    }

    /** Moves one source up (-1) or down (+1) in [fullOrder]. */
    fun move(key: String, by: Int) {
        val l = fullOrder().toMutableList(); val i = l.indexOf(key); val j = i + by
        if (i < 0 || j !in l.indices) return
        l[i] = l[j].also { l[j] = l[i] }
        Settings.saavnRank = l.indexOf("saavn")
        Addons.reorder(l.filter { it != "saavn" })
    }

    // ---- versions the user picks (Audio options) ----

    /** The exact ref each YouTube song is playing from ("addonId|trackId" or a JioSaavn ref). */
    private val usingRef = ConcurrentHashMap<String, String>()
    private val pins by lazy { App.ctx.getSharedPreferences("pins", android.content.Context.MODE_PRIVATE) }

    /** The version the user chose for a YouTube song: "yt", "saavn|<ref>" or "addon|<addonId|trackId>"; null = automatic. */
    fun pin(ytRef: String): String? = pins.getString(ytRef, null)

    /** Plays [choice] for this song from now on (remembered); null goes back to automatic. */
    fun choose(ytRef: String, choice: String?) {
        pins.edit().apply { if (choice == null) remove(ytRef) else putString(ytRef, choice) }.apply()
        forget("yt", ytRef)
    }

    /** Which version is playing right now, as a choice key ("yt", "saavn|…", "addon|…"). */
    fun playingChoice(ytRef: String, url: String?): String = when {
        url == null || "googlevideo" in url -> "yt"
        using[ytRef] == "saavn" -> "saavn|" + usingRef[ytRef]
        using[ytRef] == "lossless" -> "addon|" + usingRef[ytRef]
        else -> "yt"
    }

    /** One version of a song the user can switch to. */
    data class Version(val choice: String, val source: String, val title: String, val detail: String, val quality: String, val durationMs: Long)

    /** One source's versions of a song, arriving on its own. */
    class SourceVersions(val key: String, val name: String, val future: Future<List<Version>>)

    // per song: each source's lookup, kept for the session (a source that found nothing is asked again next time)
    private val versionCache = ConcurrentHashMap<String, ConcurrentHashMap<String, SourceVersions>>()

    /** Starts looking up [t]'s versions in the background (no audio), so Audio options opens with them ready. */
    fun prefetchVersions(t: com.wetune.sources.Track) { if (t.source == "yt") versionSources(t) }

    /**
     * Each source's lookup for [t], in the user's order — already running or done, so the Audio sheet can show every
     * source's results as soon as that source answers. The YouTube original is always last (see [original]).
     */
    fun versionSources(t: com.wetune.sources.Track): List<SourceVersions> {
        if (versionCache.size > 150) versionCache.clear() // shortcut: drop-all instead of LRU; fine at this size
        val perSong = versionCache.getOrPut(t.id) { ConcurrentHashMap() }
        val q = SourceMatch.queries(t.title, t.artist).first()
        fun keep(key: String, name: String, fetch: () -> List<Version>): SourceVersions {
            val old = perSong[key]
            val stale = old != null && old.future.isDone && runCatching { old.future.get().isEmpty() }.getOrDefault(true)
            if (old != null && !stale) return old
            return SourceVersions(key, name, worker.submit<List<Version>> { fetch() }).also { perSong[key] = it }
        }
        return order().mapNotNull { s ->
            if (s == "saavn") keep("saavn", "JioSaavn") {
                runCatching { JioSaavn.search(q).take(5) }.getOrDefault(emptyList()).map {
                    Version("saavn|" + it.ref, "JioSaavn", it.title, listOf(it.artist, it.album).filter(String::isNotBlank).joinToString(" · "),
                        if (JioSaavn.has320(it.ref)) "AAC · 320 kbps" else "AAC · 160 kbps", it.durationMs)
                }
            }
            else Addons.enabled.firstOrNull { it.id == s }?.let { a ->
                keep(a.id, a.name) {
                    runCatching { Addons.search(a, q).take(5) }.getOrDefault(emptyList()).map {
                        Version("addon|" + it.ref, a.name, it.title, listOf(it.artist, it.album).filter(String::isNotBlank).joinToString(" · "),
                            Addons.qualityOf(it.ref) ?: "Lossless", it.durationMs)
                    }
                }
            }
        }
    }

    /** The YouTube original of a song, as a version. */
    fun original(t: com.wetune.sources.Track) = Version("yt", "YouTube", t.title, t.artist, "Opus · up to 160 kbps", t.durationMs)

    /** Called with a YouTube id once a better copy is found in the background (the player may switch to it). */
    var onUpgrade: ((ytRef: String) -> Unit)? = null
    private val worker = java.util.concurrent.Executors.newCachedThreadPool()

    /** Looks up a song's better copies ahead of time (no audio is downloaded), so it starts at its best when it plays. */
    fun prepare(ref: String, title: String, artist: String, durationMs: Long) {
        if (needsSearch(ref)) searches(ref, title, artist, durationMs)
    }

    /** Searches running per YouTube id: (lossless, JioSaavn), each true when it found the same recording. */
    private val inflight = ConcurrentHashMap<String, Pair<Future<Boolean>?, Future<Boolean>?>>()

    /** Starts (or joins) the add-on and JioSaavn searches for a song; both run at the same time. */
    private fun searches(ref: String, title: String, artist: String, durationMs: Long): Pair<Future<Boolean>?, Future<Boolean>?> =
        inflight.computeIfAbsent(ref) {
            val lossless = if (wantsLossless() && !losslessMatch.containsKey(ref)) worker.submit<Boolean> {
                UpgradeLog.add("searching add-ons for $ref: $title / $artist")
                runCatching { losslessFind(ref, title, artist, durationMs) }.getOrDefault(false)
                    .also { UpgradeLog.add("add-ons for $ref: ${if (it) "found " + losslessMatch[ref] else "no match"}") }
            } else null
            val saavn = if (wantsMax() && !saavnMatch.containsKey(ref)) worker.submit<Boolean> {
                UpgradeLog.add("searching JioSaavn for $ref: $title / $artist")
                runCatching { saavnFind(ref, title, artist, durationMs) }.getOrDefault(false)
                    .also { UpgradeLog.add("JioSaavn for $ref: ${if (it) "found" else "no match"}") }
            } else null
            // when both are done: songs further down the queue that were already loaded get reloaded at the better copy
            worker.execute {
                runCatching { lossless?.get() }; runCatching { saavn?.get() }
                inflight.remove(ref)
                if (losslessMatch[ref].orEmpty().isNotEmpty() || saavnMatch[ref].orEmpty().isNotEmpty()) onUpgrade?.invoke(ref)
            }
            lossless to saavn
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
        AudioInfo.upgrading("yt:$ref", "none")
    }
}
