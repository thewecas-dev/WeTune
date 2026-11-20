package com.wetune

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.wetune.lyrics.Query
import com.wetune.lyrics.diff
import com.wetune.lyrics.kugou
import com.wetune.lyrics.lrcRed
import com.wetune.lyrics.netease
import com.wetune.lyrics.youtubeMusic
import com.wetune.sources.Track
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Lyrics from several providers (BetterLyrics, LRCLIB, lrc.red, KuGou, NetEase, YouTube Music), asked at once: every
 * version found (word-timed first, then line-synced, then plain), cached per song, with a per-song timing offset.
 */
object Lyrics {
    /** One sung word (or syllable): when it starts and ends; [text] keeps its trailing space when one follows. */
    data class Word(val ms: Long, val endMs: Long, val text: String)

    /** [ms] is -1 for unsynced (plain) lyrics; [endMs] is -1 when unknown; [words] only for word-synced lyrics. */
    data class Line(val ms: Long, val text: String, val endMs: Long = -1, val words: List<Word>? = null)

    /** One set of lyrics a provider has for a song. [words] = timed word by word. */
    data class Version(
        val id: Long, val title: String, val artist: String, val album: String, val synced: Boolean, val lines: List<Line>,
        val source: String = "LRCLIB", val words: Boolean = false, val durationMs: Long = 0,
    )

    private val TAG = Regex("\\[(\\d+):(\\d+(?:[.:]\\d+)?)]")

    fun parseLrc(lrc: String): List<Line> = lrc.lines().flatMap { line ->
        val tags = TAG.findAll(line).toList()
        if (tags.isEmpty()) return@flatMap emptyList()
        val text = line.substring(tags.last().range.last + 1).trim()
        tags.map { m ->
            val (min, sec) = m.destructured
            Line(min.toLong() * 60_000 + (sec.replace(':', '.').toDouble() * 1000).toLong(), text)
        }
    }.sortedBy { it.ms }

    // ---- cache, chosen version and offset ----

    /** Versions found per song id, kept for the session so reopening the lyrics never refetches. */
    private val cache = ConcurrentHashMap<String, List<Version>>()
    fun cached(id: String): List<Version>? = cache[id]
    /** Forget fetched lyrics (the preferred style changed, so versions are ranked again). */
    fun clearCache() = cache.clear()

    /** Which version (index) the user picked per song; 0 = the best match. */
    val choice = mutableStateMapOf<String, Int>()

    private val prefs by lazy { App.ctx.getSharedPreferences("lyrics_offset", Context.MODE_PRIVATE) }
    private val offsets = mutableStateMapOf<String, Long>()

    /** How much earlier (+) or later (−) the lyrics run for song [id], in ms; remembered across restarts. */
    fun offset(id: String): Long = offsets[id] ?: prefs.getLong(id, 0L).also { if (it != 0L) offsets[id] = it }

    fun setOffset(id: String, ms: Long) {
        offsets[id] = ms
        prefs.edit().apply { if (ms == 0L) remove(id) else putLong(id, ms) }.apply()
    }

    /** How many songs have their own lyric timing. */
    fun offsetCount(): Int = prefs.all.size

    /** Every song back to the lyrics' own timing. */
    fun resetOffsets() {
        offsets.clear()
        prefs.edit().clear().apply()
    }

    // ---- fetching ----

    /** Blocking. All versions for [t], best first (exact match, then synced, then plain); cached. */
    fun versions(t: Track): List<Version> = cache[t.id] ?: load(t).also { if (it.isNotEmpty() || !troubled.remove(t.id)) cache[t.id] = it }

    // songs whose last lookup hit a busy server, a timeout or no connection: "nothing found" isn't trusted for them, so
    // the next look (the next play or reopening the player) asks again instead of remembering "no lyrics"
    private val troubled = ConcurrentHashMap.newKeySet<String>()

    // lookups block on the network, so each provider gets its own thread (the shared ForkJoin pool can be one thread wide)
    private val pool = Executors.newCachedThreadPool()

    private fun load(t: Track): List<Version> {
        val q = Query.of(t)
        val jobs: List<() -> List<Version>> = listOf(
            { listOfNotNull(betterLyrics(q, t.album)) },
            { lrclib(t, q) { troubled.add(t.id) } },
            { listOfNotNull(lrcRed(q)) },
            { listOfNotNull(kugou(q)) },
            { listOfNotNull(netease(q)) },
            { listOfNotNull(youtubeMusic(t)) },
        )
        val futures = jobs.map { job -> pool.submit(Callable { runCatching(job).getOrDefault(emptyList()) }) }
        val start = System.currentTimeMillis()
        fun collect(budgetMs: Long) = futures.flatMap { f ->
            runCatching { f.get((start + budgetMs - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS) }.getOrDefault(emptyList())
        }
        // what arrived within 4 s; only when nothing did, wait longer for the slow ones (a bad network)
        val found = collect(4_000).ifEmpty { collect(15_000) }
        if (found.isEmpty() && futures.any { !it.isDone }) troubled.add(t.id)
        return rank(found, t.durationMs, Settings.lyricsWords, t.title, t.artist, Settings.lyricsOrder.split(',').filter { it.isNotBlank() })
    }

    /** Lyric sources, in the order Settings › Lyrics lists them for reordering. */
    val Sources = listOf("BetterLyrics", "LRCLIB", "lrc.red", "KuGou", "NetEase", "YouTube Music")

    /**
     * Best first. Versions that match the song (its title, and its artist where both say one) always come before ones
     * that don't. Among those: with [order] (source names, the user's preference) the earlier source wins; without it
     * (automatic), word-timed (when [words] style is on), then line-synced, then plain. Ties: closest in length.
     * Repeats dropped.
     */
    fun rank(all: List<Version>, durationMs: Long, words: Boolean, title: String = "", artist: String = "", order: List<String> = emptyList()): List<Version> {
        fun matches(v: Version): Boolean {
            if (title.isBlank()) return true
            val a = Query.norm(title); val b = Query.norm(v.title)
            val titleOk = b.isNotEmpty() && (a.contains(b) || b.contains(a))
            val ar = Query.norm(artist); val vr = Query.norm(v.artist)
            val artistOk = ar.isEmpty() || vr.isEmpty() || ar.contains(vr) || vr.contains(ar) ||
                artist.split(',', '&').any { p -> Query.norm(p).let { it.length > 2 && vr.contains(it) } }
            return titleOk && artistOk
        }
        fun place(v: Version) = order.indexOfFirst { v.source.startsWith(it) }.let { if (it < 0) order.size else it }
        val byQuality = compareByDescending<Version> { matches(it) }
        val cmp = if (order.isEmpty()) byQuality.thenByDescending { it.words && words }.thenByDescending { it.synced }
        else byQuality.thenBy { place(it) }.thenByDescending { it.synced }
        return all.sortedWith(cmp.thenBy { diff(it.durationMs, durationMs) })
            .distinctBy { v -> Triple(v.words, v.synced, v.lines.joinToString("\n") { Query.norm(it.text) }) }
            .take(15)
    }

    /** BetterLyrics: Apple-Music-style TTML, timed word by word ("Word"), by line ("Line"), or not at all. */
    private fun betterLyrics(q: Query, album: String): Version? {
        fun url(withLength: Boolean) = "https://lyrics-api.boidu.dev/getLyrics?s=${q.title.urlEncode()}&a=${q.artist.urlEncode()}" +
            (if (withLength && q.durationMs > 0) "&d=${q.durationMs / 1000}" else "") + (if (album.isNotBlank()) "&al=${album.urlEncode()}" else "")
        // it only accepts a length within 2 s; music videos run longer than the song, so ask again without it
        var length = q.durationMs
        val body = runCatching { httpGet(url(true)) }.getOrElse { e ->
            if (e.message?.contains("(404)") == true && length > 0) httpGet(url(false)).also { length = 0 } else throw e
        }
        val ttml = JSONObject(body).str("ttml") ?: return null
        val lines = parseTtml(ttml).takeIf { it.isNotEmpty() } ?: return null
        val words = lines.any { it.words != null }
        return Version(-1, q.title, q.artist, album, lines.first().ms >= 0, lines, source = "BetterLyrics", words = words, durationMs = length)
    }

    private val P = Regex("""<p\b([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val SPAN = Regex("""<span\b([^>]*)>([^<]*)</span>(\s?)""")
    private val TAGS = Regex("<[^>]+>")

    /** Lines (and words, when timed per word) from a TTML document. */
    fun parseTtml(ttml: String): List<Line> {
        // itunes:timing (BetterLyrics) or lrc:timing (lrc.red)
        val wordTimed = Regex("""\w+:timing="Word"""").containsMatchIn(ttml)
        val untimed = Regex("""\w+:timing="None"""").containsMatchIn(ttml)
        return P.findAll(ttml).mapNotNull { p ->
            val attrs = p.groupValues[1]; val body = p.groupValues[2]
            val begin = attr(attrs, "begin")?.let(::ttmlTime); val end = attr(attrs, "end")?.let(::ttmlTime) ?: -1
            val words = if (wordTimed) SPAN.findAll(body).mapNotNull { s ->
                val a = s.groupValues[1]
                val ms = attr(a, "begin")?.let(::ttmlTime) ?: return@mapNotNull null
                Word(ms, attr(a, "end")?.let(::ttmlTime) ?: ms, unescape(s.groupValues[2]) + s.groupValues[3])
            }.toList().takeIf { it.isNotEmpty() } else null
            val text = (words?.joinToString("") { it.text } ?: unescape(TAGS.replace(body, ""))).trim()
            if (text.isEmpty()) null else Line(if (untimed) -1 else begin ?: -1, text, end, words)
        }.toList()
    }

    private fun attr(attrs: String, name: String) = Regex("""(?<![\w:])$name="([^"]*)"""").find(attrs)?.groupValues?.get(1)

    /** "27.395", "1:02.345", "00:01:02.345" or "12.3s" → ms. */
    fun ttmlTime(s: String): Long {
        val parts = s.removeSuffix("s").split(':')
        var sec = 0.0
        parts.forEach { sec = sec * 60 + (it.toDoubleOrNull() ?: 0.0) }
        return (sec * 1000).toLong()
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")

    private fun lrclib(t: Track, query: Query, onTrouble: () -> Unit): List<Version> {
        // LRCLIB answers 404 when it simply has no lyrics; anything else (busy, timeout, offline) is trouble
        fun trouble(e: Throwable) { if (e.message?.contains("(404)") != true) onTrouble() }
        val api = "https://lrclib.net/api"
        fun q(title: String) = "track_name=${title.urlEncode()}&artist_name=${query.artist.urlEncode()}"
        val exact = if (t.durationMs > 0) runCatching {
            JSONObject(httpGet("$api/get?${q(query.title)}&album_name=${t.album.urlEncode()}&duration=${t.durationMs / 1000}"))
        }.onFailure(::trouble).getOrNull() else null
        fun search(title: String) = runCatching { JSONArray(httpGet("$api/search?${q(title)}")).objects() }.onFailure(::trouble).getOrDefault(emptyList())
        // film songs are often titled "Song (From \"Movie\")"; search with just the song name too
        val found = listOfNotNull(exact) + search(query.title) + (if (query.bare != query.title) search(query.bare) else emptyList())
        return found.distinctBy { it.optLong("id") }.mapNotNull { o ->
            val synced = o.str("syncedLyrics")?.let(::parseLrc)?.takeIf { it.isNotEmpty() }
            val lines = synced ?: o.str("plainLyrics")?.lines()?.map { Line(-1, it) } ?: return@mapNotNull null
            Version(o.optLong("id"), o.optString("trackName"), o.optString("artistName"), o.optString("albumName"), synced != null, lines,
                durationMs = (o.optDouble("duration", 0.0) * 1000).toLong())
        }
            // LRCLIB often holds a dozen uploads of one song; keep the closest few so the other providers still show
            .sortedWith(compareByDescending<Version> { it.synced }.thenBy { diff(it.durationMs, t.durationMs) }).take(6)
    }

    /** Blocking. The chosen version's lines for [t], or empty when nothing was found. */
    fun fetch(t: Track): List<Line> = versions(t).let { it.getOrNull(choice[t.id] ?: 0) ?: it.firstOrNull() }?.lines.orEmpty()
}
