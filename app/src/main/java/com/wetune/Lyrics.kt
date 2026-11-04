package com.wetune

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.wetune.sources.Track
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Lyrics from LRCLIB: every matching version (synced first), cached per song, with a per-song timing offset. */
object Lyrics {
    /** [ms] is -1 for unsynced (plain) lyrics. */
    data class Line(val ms: Long, val text: String)

    /** One set of lyrics LRCLIB has for a song. */
    data class Version(val id: Long, val title: String, val artist: String, val album: String, val synced: Boolean, val lines: List<Line>)

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
        }.sortedByDescending { it.synced }.take(12)
    }

    /** Blocking. The chosen version's lines for [t], or empty when nothing was found. */
    fun fetch(t: Track): List<Line> = versions(t).let { it.getOrNull(choice[t.id] ?: 0) ?: it.firstOrNull() }?.lines.orEmpty()
}
