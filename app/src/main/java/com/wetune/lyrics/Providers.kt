package com.wetune.lyrics

import com.wetune.Lyrics
import com.wetune.Lyrics.Line
import com.wetune.Lyrics.Version
import com.wetune.Lyrics.Word
import com.wetune.httpGet
import com.wetune.objects
import com.wetune.sources.Track
import com.wetune.str
import com.wetune.urlEncode
import com.wetune.youtube.InnerTube
import com.wetune.youtube.findAll
import com.wetune.youtube.findFirst
import com.wetune.youtube.text
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** What to search the lyrics sites for: [title]/[artist] without video noise ("(Official Video)", "feat. X", "Artist - "). */
data class Query(val title: String, val artist: String, val bare: String, val durationMs: Long) {
    companion object {
        private val I = RegexOption.IGNORE_CASE
        private val NOISE = Regex("""\s*[(\[][^)\]]*\b(official|lyrics?|audio|video|visuali[sz]er|hd|hq|4k|m/?v|full song|explicit)\b[^)\]]*[)\]]""", I)
        private val FEAT = Regex("""\s*[(\[]?\s*\b(feat\.?|ft\.?|featuring)\s[^)\]]*[)\]]?""", I)
        private val VERSION = Regex("""\s+-\s+[^-]*\b(remaster(ed)?|version|edit|live|mono|stereo)\b.*$""", I)
        private val ARTIST_SPLIT = Regex(""",|&|、|\s(x|and|feat\.?|ft\.?)\s""", I)

        fun of(t: Track): Query {
            var title = t.title.substringBefore(" | ")
            var artist = t.artist.split(ARTIST_SPLIT).first().replace(Regex("""\s*-\s*Topic$|VEVO$""", I), "").trim()
            title = NOISE.replace(title, "").let { FEAT.replace(it, "") }.let { VERSION.replace(it, "") }.trim()
            // music videos are often titled "Artist - Song"; the channel name stands in as the artist
            if (" - " in title) {
                val (left, right) = title.split(" - ", limit = 2).map { it.trim() }
                when {
                    norm(left).let { it.isNotEmpty() && (norm(artist).contains(it) || it.contains(norm(artist))) } -> {
                        artist = left.split(ARTIST_SPLIT).first().trim(); title = right
                    }
                    norm(right).let { it.isNotEmpty() && (norm(artist).contains(it) || it.contains(norm(artist))) } -> title = left
                    t.source == "yt" && t.album.isBlank() -> { artist = left.split(ARTIST_SPLIT).first().trim(); title = right }
                }
            }
            val bare = title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim().ifEmpty { title }
            return Query(title.ifEmpty { t.title }, artist.ifEmpty { t.artist }, bare, t.durationMs)
        }

        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    }

    /**
     * The [items] whose name and artist match the song (none when nothing matches: no wrong songs); the exact title
     * first (a music video runs longer than the song, so length alone would favour remixes), then closest in length.
     */
    fun <T> pick(items: List<T>, name: (T) -> String, artists: (T) -> String, durMs: (T) -> Long): List<T> {
        val b = norm(bare); val a = norm(artist)
        fun close(x: String, y: String) = x.isNotEmpty() && (x.contains(y) || y.contains(x))
        return items.filter { close(norm(name(it)), b) && close(norm(artists(it)), a) }
            .sortedWith(compareBy<T> { norm(name(it)) != b && norm(name(it)) != norm(title) }.thenBy { diff(durMs(it), durationMs) })
    }
}

/** How far apart two lengths are; unknown counts as a few seconds off. */
fun diff(a: Long, b: Long) = if (a <= 0 || b <= 0) 3_000L else kotlin.math.abs(a - b)

// ---- KuGou: song search → lyric candidates by the song's hash → base64 LRC ----

fun kugou(q: Query): Version? {
    val lyricsApi = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc"
    fun candidates(url: String) = JSONObject(httpGet(url)).optJSONArray("candidates").objects()
    // the lyric search by name is quick but knows mostly Western and Chinese songs; else find the song (slower) and ask by its hash
    val c = q.pick(candidates("$lyricsApi&keyword=${"${q.artist} - ${q.title}".urlEncode()}" + (if (q.durationMs > 0) "&duration=${q.durationMs}" else "")),
        { it.optString("song") }, { it.optString("singer") }, { it.optLong("duration") }).firstOrNull()
        ?: run {
            val songs = JSONObject(httpGet("https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=8&showtype=0&keyword=${"${q.title} ${q.artist}".urlEncode()}"))
                .optJSONObject("data")?.optJSONArray("info").objects()
            val song = q.pick(songs, { it.optString("songname") }, { it.optString("singername") }, { it.optLong("duration") * 1000 }).firstOrNull() ?: return null
            candidates("$lyricsApi&hash=${song.optString("hash")}").firstOrNull()
                ?.put("song", song.optString("songname"))?.put("singer", song.optString("singername"))
        } ?: return null
    val body = httpGet("https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=${c.optString("id")}&accesskey=${c.optString("accesskey")}")
    // fewer than three lines is a placeholder ("纯音乐，请欣赏": an instrumental)
    val lines = kugouLines(body, q.bare).takeIf { it.size >= 3 } ?: return null
    return Version(-1, c.optString("song"), c.optString("singer").replace("、", ", "), "", true, lines, source = "KuGou", durationMs = c.optLong("duration"))
}

/** A KuGou download response: LRC in base64, led by a "Song - Artist" line and credits, which are dropped. */
fun kugouLines(json: String, title: String): List<Line> {
    val content = JSONObject(json).str("content") ?: return emptyList()
    return dropCredits(Lyrics.parseLrc(String(Base64.getDecoder().decode(content), Charsets.UTF_8)), title)
}

private val CREDIT = Regex("""^[^:：]{1,30}[:：]""")

/** Leading credit lines ("作词 : …", "Composed by：…", "Song - Artist") and blanks before the first sung line. */
fun dropCredits(lines: List<Line>, title: String): List<Line> = lines.dropWhile { l ->
    l.text.isBlank() || CREDIT.containsMatchIn(l.text) || (" - " in l.text && Query.norm(l.text).startsWith(Query.norm(title)))
}

// ---- NetEase Cloud Music: cloud search → lyric (word-timed "yrc" when it has one, else LRC) ----

private val NETEASE = mapOf("Referer" to "https://music.163.com/")

fun netease(q: Query): Version? {
    val songs = JSONObject(httpGet("https://music.163.com/api/cloudsearch/pc?s=${"${q.title} ${q.artist}".urlEncode()}&type=1&limit=10", NETEASE))
        .optJSONObject("result")?.optJSONArray("songs").objects()
    fun artists(s: JSONObject) = s.optJSONArray("ar").objects().joinToString(", ") { it.optString("name") }
    val song = q.pick(songs, { it.optString("name") }, ::artists, { it.optLong("dt") }).firstOrNull() ?: return null
    val (lines, words) = neteaseLines(httpGet("https://music.163.com/api/song/lyric/v1?id=${song.optLong("id")}&lv=1&yv=1&tv=-1", NETEASE), q.bare)
    if (lines.size < 3) return null // a placeholder ("纯音乐，请欣赏": an instrumental)
    val artists = artists(song)
    return Version(song.optLong("id"), song.optString("name"), artists, song.optJSONObject("al")?.optString("name").orEmpty(),
        lines.first().ms >= 0, lines, source = "NetEase", words = words, durationMs = song.optLong("dt"))
}

/** A NetEase lyric response: the word-timed lines (true) when present, else the LRC ones (false). */
fun neteaseLines(json: String, title: String): Pair<List<Line>, Boolean> {
    val o = JSONObject(json)
    o.optJSONObject("yrc")?.str("lyric")?.let(::parseYrc)?.let { dropCredits(it, title) }?.takeIf { it.isNotEmpty() }?.let { return it to true }
    return dropCredits(o.optJSONObject("lrc")?.str("lyric")?.let(Lyrics::parseLrc).orEmpty(), title) to false
}

private val YRC_LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
private val YRC_WORD = Regex("""\((\d+),(\d+),-?\d+\)""")

/** NetEase "yrc": `[lineStart,lineDur](wordStart,wordDur,0)word(…)word`; credit lines are JSON and skipped. */
fun parseYrc(yrc: String): List<Line> = yrc.lines().mapNotNull { raw ->
    val m = YRC_LINE.find(raw.trim()) ?: return@mapNotNull null
    val start = m.groupValues[1].toLong(); val body = m.groupValues[3]
    val marks = YRC_WORD.findAll(body).toList()
    val words = marks.mapIndexed { i, w ->
        val ms = w.groupValues[1].toLong()
        Word(ms, ms + w.groupValues[2].toLong(), body.substring(w.range.last + 1, marks.getOrNull(i + 1)?.range?.first ?: body.length))
    }.filter { it.text.isNotEmpty() }
    val text = words.joinToString("") { it.text }.trim()
    if (text.isEmpty()) null else Line(start, text, start + m.groupValues[2].toLong(), words.takeIf { it.isNotEmpty() })
}

// ---- lrc.red: open lyrics database (search → TTML, often word-timed); its terms ask for an identifying User-Agent ----

private val LRC_RED = mapOf("User-Agent" to "WeTune (Android music player; https://github.com/thewecas-dev/WeTune)")

fun lrcRed(q: Query): Version? {
    val hits = JSONObject(httpGet("https://lrc.red/search.json?q=${"${q.title} ${q.artist}".urlEncode()}", LRC_RED)).optJSONArray("hits").objects()
    val hit = q.pick(hits, { it.optString("title") }, { it.optString("artist") }, { (it.optDouble("duration", 0.0) * 1000).toLong() }).firstOrNull() ?: return null
    val lines = Lyrics.parseTtml(httpGet("https://lrc.red/s/${hit.optString("isrc")}.ttml", LRC_RED)).takeIf { it.isNotEmpty() } ?: return null
    return Version(-1, hit.optString("title"), hit.optString("artist"), hit.optString("album"), lines.first().ms >= 0, lines,
        source = "lrc.red", words = lines.any { it.words != null }, durationMs = (hit.optDouble("duration", 0.0) * 1000).toLong())
}

// ---- YouTube Music: the song's lyrics tab (next → browseId → browse); the Android client returns timed lines when it has them ----

fun youtubeMusic(t: Track): Version? {
    if (t.source != "yt") return null
    val next = InnerTube.post("next", JSONObject().put("videoId", t.ref))
    val browseId = findAll(next, setOf("browseEndpoint")).map { it.second.optString("browseId") }.firstOrNull { it.startsWith("MPLY") } ?: return null
    val (lines, provider) = parseYtLyrics(InnerTube.post("browse", JSONObject().put("browseId", browseId), "ANDROID_MUSIC", "7.21.50")) ?: return null
    return Version(-1, t.title, t.artist, t.album, lines.first().ms >= 0, lines,
        source = "YouTube Music" + (provider?.let { " · $it" } ?: ""), durationMs = t.durationMs)
}

/** Lines and provider ("Musixmatch", "LyricFind") from a lyrics browse page: timed (mobile clients) or the plain description. */
fun parseYtLyrics(page: JSONObject): Pair<List<Line>, String?>? {
    val timed = (findFirst(page, "timedLyricsData") as? JSONArray).objects()
    val lines = if (timed.isNotEmpty()) {
        val cued = timed.all { it.has("cueRange") }
        timed.map { o ->
            val c = o.optJSONObject("cueRange")
            Line(if (cued) c!!.optLong("startTimeMilliseconds") else -1, o.optString("lyricLine"), if (cued) c!!.optLong("endTimeMilliseconds", -1) else -1)
        }.let { l -> if (cued) l.filter { it.text.isNotBlank() } else l }
    } else {
        val shelf = findAll(page, setOf("musicDescriptionShelfRenderer")).firstOrNull()?.second ?: return null
        shelf.optJSONObject("description").text().lines().map { Line(-1, it) }
    }
    if (lines.none { it.text.isNotBlank() }) return null
    val source = (findFirst(page, "sourceMessage") as? String)
        ?: findAll(page, setOf("musicDescriptionShelfRenderer")).firstOrNull()?.second?.optJSONObject("footer").text()
    return lines to source?.removePrefix("Source:")?.trim()?.ifEmpty { null }
}
