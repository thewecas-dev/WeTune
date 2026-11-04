package com.wetune

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.wetune.sources.Track
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Lyrics from LRCLIB: every matching version (synced first), cached per song, with a per-song timing offset. */
object Lyrics {
    /** One sung word (or syllable): when it starts and ends; [text] keeps its trailing space when one follows. */
    data class Word(val ms: Long, val endMs: Long, val text: String)

    /** [ms] is -1 for unsynced (plain) lyrics; [endMs] is -1 when unknown; [words] only for word-synced lyrics. */
    data class Line(val ms: Long, val text: String, val endMs: Long = -1, val words: List<Word>? = null)

    /** One set of lyrics a provider has for a song. [words] = timed word by word. */
    data class Version(
        val id: Long, val title: String, val artist: String, val album: String, val synced: Boolean, val lines: List<Line>,
        val source: String = "LRCLIB", val words: Boolean = false,
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

    // ---- fetching ----

    /** Blocking. All versions for [t], best first (exact match, then synced, then plain); cached. */
    fun versions(t: Track): List<Version> = cache[t.id] ?: load(t).also { cache[t.id] = it }

    private fun load(t: Track): List<Version> {
        // BetterLyrics (word-by-word timing for many songs) and LRCLIB, asked at the same time
        val better = java.util.concurrent.CompletableFuture.supplyAsync { runCatching { betterLyrics(t) }.getOrNull() }
        val lrclib = lrclib(t)
        val all = listOfNotNull(runCatching { better.get(12, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull()) + lrclib
        // word-synced first (when that style is on), then line-synced, then plain
        return all.sortedWith(compareByDescending<Version> { it.words && Settings.lyricsWords }.thenByDescending { it.synced }).take(12)
    }

    /** BetterLyrics: Apple-Music-style TTML, timed word by word ("Word"), by line ("Line"), or not at all. */
    private fun betterLyrics(t: Track): Version? {
        val artist = t.artist.substringBefore(',').trim()
        val url = "https://lyrics-api.boidu.dev/getLyrics?s=${t.title.urlEncode()}&a=${artist.urlEncode()}" +
            (if (t.durationMs > 0) "&d=${t.durationMs / 1000}" else "") + (if (t.album.isNotBlank()) "&al=${t.album.urlEncode()}" else "")
        val ttml = JSONObject(httpGet(url)).str("ttml") ?: return null
        val lines = parseTtml(ttml).takeIf { it.isNotEmpty() } ?: return null
        val words = lines.any { it.words != null }
        return Version(-1, t.title, artist, t.album, lines.first().ms >= 0, lines, source = "BetterLyrics", words = words)
    }

    private val P = Regex("""<p\b([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val SPAN = Regex("""<span\b([^>]*)>([^<]*)</span>(\s?)""")
    private val TAGS = Regex("<[^>]+>")

    /** Lines (and words, when timed per word) from a TTML document. */
    fun parseTtml(ttml: String): List<Line> {
        val wordTimed = Regex("""itunes:timing="Word"""").containsMatchIn(ttml)
        val untimed = Regex("""itunes:timing="None"""").containsMatchIn(ttml)
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

    private fun lrclib(t: Track): List<Version> {
        val api = "https://lrclib.net/api"
        val artist = t.artist.substringBefore(',').trim()
        fun q(title: String) = "track_name=${title.urlEncode()}&artist_name=${artist.urlEncode()}"
        val exact = if (t.durationMs > 0) runCatching {
            JSONObject(httpGet("$api/get?${q(t.title)}&album_name=${t.album.urlEncode()}&duration=${t.durationMs / 1000}"))
        }.getOrNull() else null
        fun search(title: String) = runCatching { JSONArray(httpGet("$api/search?${q(title)}")).objects() }.getOrDefault(emptyList())
        // film songs are often titled "Song (From \"Movie\")"; search with just the song name too
        val bare = t.title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim()
        val found = listOfNotNull(exact) + search(t.title) + (if (bare.isNotBlank() && bare != t.title) search(bare) else emptyList())
        return found.distinctBy { it.optLong("id") }.mapNotNull { o ->
            val synced = o.str("syncedLyrics")?.let(::parseLrc)?.takeIf { it.isNotEmpty() }
            val lines = synced ?: o.str("plainLyrics")?.lines()?.map { Line(-1, it) } ?: return@mapNotNull null
            Version(o.optLong("id"), o.optString("trackName"), o.optString("artistName"), o.optString("albumName"), synced != null, lines)
        }
    }

    /** Blocking. The chosen version's lines for [t], or empty when nothing was found. */
    fun fetch(t: Track): List<Line> = versions(t).let { it.getOrNull(choice[t.id] ?: 0) ?: it.firstOrNull() }?.lines.orEmpty()
}
