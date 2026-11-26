package com.wetune.youtube

import com.wetune.objects
import com.wetune.sources.Card
import com.wetune.sources.Track
import org.json.JSONArray
import org.json.JSONObject

// InnerTube responses are deeply nested and change often, so instead of fixed paths we
// search the tree for the renderer objects we understand.

internal const val ROW = "musicResponsiveListItemRenderer"
internal const val CARD = "musicTwoRowItemRenderer"
internal const val PANEL = "playlistPanelVideoRenderer"
internal const val CAROUSEL = "musicCarouselShelfRenderer"
internal const val SHELF = "musicShelfRenderer"

internal val LABELS = setOf("Song", "Video", "Album", "Single", "EP", "Playlist", "Artist", "Episode", "Podcast", "Profile")
private val DURATION = Regex("^(\\d+:)?\\d+:\\d{2}$")
private val YEAR = Regex("^\\d{4}$")

/** Depth-first in document order; does not descend into a match. */
fun findAll(node: Any?, keys: Set<String>, out: MutableList<Pair<String, JSONObject>> = mutableListOf()): List<Pair<String, JSONObject>> {
    when (node) {
        is JSONObject -> for (k in node.keys()) {
            val v = node.opt(k)
            if (k in keys && v is JSONObject) out += k to v else findAll(v, keys, out)
        }
        is JSONArray -> for (i in 0 until node.length()) findAll(node.opt(i), keys, out)
    }
    return out
}

fun findFirst(node: Any?, key: String): Any? {
    when (node) {
        is JSONObject -> {
            node.opt(key)?.let { return it }
            for (k in node.keys()) findFirst(node.opt(k), key)?.let { return it }
        }
        is JSONArray -> for (i in 0 until node.length()) findFirst(node.opt(i), key)?.let { return it }
    }
    return null
}

internal fun JSONObject?.runs(): List<JSONObject> = this?.optJSONArray("runs").objects()
internal fun JSONObject?.text(): String = runs().joinToString("") { it.optString("text") }
private fun JSONObject.obj(vararg path: String): JSONObject? = path.fold(this as JSONObject?) { o, k -> o?.optJSONObject(k) }
private fun String?.nonEmpty() = this?.ifEmpty { null }

fun thumbnail(node: Any?): String? {
    val arr = findFirst(node, "thumbnails") as? JSONArray ?: return null
    val url = arr.objects().lastOrNull()?.optString("url").nonEmpty() ?: return null
    val big = url.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
    return if (big.startsWith("//")) "https:$big" else big
}

/** A page header's totals made short: "14 songs • 56 minutes" → "14 songs · 56 min", "1 hour, 22 minutes" → "1 h 22 min". */
fun shortTotals(s: String): String =
    s.split(" • ").joinToString(" · ").replace(Regex(" hours?,?"), " h").replace(Regex(" minutes?"), " min")

fun parseDuration(s: String): Long = s.split(':').fold(0L) { acc, p -> acc * 60 + (p.toLongOrNull() ?: 0) } * 1000

private fun pageType(run: JSONObject) = run.obj(
    "navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig"
)?.optString("pageType")

private fun segments(runs: List<JSONObject>) =
    runs.joinToString("") { it.optString("text") }.split(" • ").map { it.trim() }.filter { it.isNotEmpty() }

private val STAT = Regex(".*\\b(plays|views|subscribers|monthly audience|songs)$", RegexOption.IGNORE_CASE)

fun subtitleArtist(subtitle: String): String = subtitle.split(" • ").map { it.trim() }.firstOrNull {
    it.isNotEmpty() && it !in LABELS && !YEAR.matches(it) && !DURATION.matches(it) && !STAT.matches(it)
}.orEmpty()

/**
 * All of a song's artists. YouTube links only some of them to artist pages ("Sachin-Jigar, Ed Sheeran, Arijit Singh
 * & Madhubanti Bagchi" links two), so the whole artist part of the line is used — the " • " segment holding the first
 * linked artist — with "&" turned into a comma.
 */
fun artistOf(runs: List<JSONObject>, segs: List<String> = segments(runs)): String {
    val linked = runs.filter { pageType(it) == "MUSIC_PAGE_TYPE_ARTIST" }.map { it.optString("text") }
    if (linked.isEmpty()) return subtitleArtist(segs.joinToString(" • "))
    val segment = segs.firstOrNull { linked.first() in it } ?: return linked.joinToString(", ")
    return segment.split(", ", " & ").map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
}

private fun albumOf(runs: List<JSONObject>) =
    runs.firstOrNull { pageType(it) == "MUSIC_PAGE_TYPE_ALBUM" }?.optString("text").orEmpty()

fun parseRow(r: JSONObject): Any? {
    val flex = r.optJSONArray("flexColumns").objects().map { it.obj("musicResponsiveListItemFlexColumnRenderer", "text").runs() }
    val first = flex.firstOrNull()?.firstOrNull() ?: return null
    val title = first.optString("text").nonEmpty() ?: return null
    val rest = flex.drop(1).flatten()
    val segs = flex.drop(1).flatMap { segments(it) } // per column: columns have no separator between them
    val art = thumbnail(r.optJSONObject("thumbnail"))
    r.obj("navigationEndpoint", "browseEndpoint")?.optString("browseId").nonEmpty()?.let {
        return Card(it, title, segs.joinToString(" • "), art)
    }
    val videoId = r.obj("playlistItemData")?.optString("videoId").nonEmpty()
        ?: first.obj("navigationEndpoint", "watchEndpoint")?.optString("videoId").nonEmpty()
        ?: (findFirst(r.optJSONObject("overlay"), "watchEndpoint") as? JSONObject)?.optString("videoId").nonEmpty()
        ?: return null
    val fixed = r.optJSONArray("fixedColumns").objects()
        .flatMap { it.obj("musicResponsiveListItemFixedColumnRenderer", "text").runs() }.map { it.optString("text").trim() }
    val duration = (fixed + segs).firstOrNull { DURATION.matches(it) }
    return Track(
        "yt", videoId, title, artistOf(rest, segs), albumOf(rest), art,
        duration?.let(::parseDuration) ?: 0,
        r.obj("playlistItemData")?.optString("playlistSetVideoId").nonEmpty(),
        segs.firstOrNull { it.endsWith(" views", ignoreCase = true) }.orEmpty(),
    )
}

/** An unfiltered search's Top result card: a [Track] (song or video) or a [Card] (album, artist, playlist); null when there's none. */
fun topResult(root: Any?): Any? {
    val c = findFirst(root, "musicCardShelfRenderer") as? JSONObject ?: return null
    val title = c.optJSONObject("title").runs().firstOrNull() ?: return null
    val sub = c.optJSONObject("subtitle").runs()
    val segs = segments(sub)
    val art = thumbnail(c.optJSONObject("thumbnail"))
    title.obj("navigationEndpoint", "watchEndpoint")?.optString("videoId").nonEmpty()?.let {
        val duration = segs.firstOrNull { s -> DURATION.matches(s) }?.let(::parseDuration) ?: 0
        return Track("yt", it, title.optString("text"), artistOf(sub, segs), albumOf(sub), art, duration, views = segs.firstOrNull { s -> s.endsWith(" views") }.orEmpty())
    }
    return title.obj("navigationEndpoint", "browseEndpoint")?.optString("browseId").nonEmpty()?.let { Card(it, title.optString("text"), segs.joinToString(" • "), art) }
}

/** The query YouTube searched instead when it fixed the spelling ("Showing results for …"), or null. */
fun correctedQuery(root: Any?): String? =
    (findFirst(root, "showingResultsForRenderer") as? JSONObject)?.obj("correctedQueryEndpoint", "searchEndpoint")?.optString("query").nonEmpty()

fun parseCard(c: JSONObject): Any? {
    val title = c.optJSONObject("title").text().nonEmpty() ?: return null
    val sub = c.optJSONObject("subtitle").runs()
    val art = thumbnail(c.optJSONObject("thumbnailRenderer"))
    val nav = c.optJSONObject("navigationEndpoint") ?: return null
    nav.obj("watchEndpoint")?.optString("videoId").nonEmpty()?.let { return Track("yt", it, title, artistOf(sub), "", art) }
    nav.obj("browseEndpoint")?.optString("browseId").nonEmpty()?.let { return Card(it, title, segments(sub).joinToString(" • "), art) }
    return null
}

fun parsePanel(p: JSONObject): Track? {
    val id = p.optString("videoId").nonEmpty() ?: return null
    val byline = p.optJSONObject("longBylineText").runs()
    val len = p.optJSONObject("lengthText").text()
    return Track(
        "yt", id, p.optJSONObject("title").text(), artistOf(byline), albumOf(byline),
        thumbnail(p.optJSONObject("thumbnail")), if (DURATION.matches(len)) parseDuration(len) else 0,
    )
}

data class Items(val tracks: List<Track>, val cards: List<Card>)

fun items(node: Any?, keys: Set<String> = setOf(ROW, CARD, PANEL)): Items {
    val parsed = findAll(node, keys).mapNotNull { (k, o) ->
        when (k) {
            ROW -> parseRow(o)
            CARD -> parseCard(o)
            PANEL -> parsePanel(o)
            else -> null
        }
    }
    return Items(parsed.filterIsInstance<Track>().distinctBy { it.id }, parsed.filterIsInstance<Card>().distinctBy { it.browseId })
}

/** A shelf of a feed. [strap]: the small line YouTube sets above some titles ("SIMILAR TO"); [wide]: 16:9 items (music videos). */
data class Section(val title: String, val tracks: List<Track>, val cards: List<Card>, val strap: String = "", val wide: Boolean = false)

fun sections(root: Any?): List<Section> = findAll(root, setOf(CAROUSEL, SHELF)).mapNotNull { (_, s) ->
    val title = (s.optJSONObject("header")?.let { findFirst(it, "title") } as? JSONObject).text()
        .ifEmpty { s.optJSONObject("title").text() }
    val it = items(s.optJSONArray("contents"))
    val strap = (s.optJSONObject("header")?.let { findFirst(it, "strapline") } as? JSONObject).text()
    val wide = findFirst(s.optJSONArray("contents"), "aspectRatio") == "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_RECTANGLE_16_9"
    if (it.tracks.isEmpty() && it.cards.isEmpty()) null else Section(title, it.tracks, it.cards, strap, wide)
}

fun continuation(node: Any?): String? =
    (findFirst(node, "continuationCommand") as? JSONObject)?.optString("token").nonEmpty()
        ?: (findFirst(node, "nextContinuationData") as? JSONObject)?.optString("continuation").nonEmpty()

/**
 * The artist and album pages a song links to, from its byline in YouTube's queue data (artists first, then the album).
 * Only names YouTube links come back; the rest have no page to open.
 */
fun pageLinks(byline: List<JSONObject>): List<Card> = byline.mapNotNull { r ->
    val id = r.obj("navigationEndpoint", "browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    when (pageType(r)) {
        "MUSIC_PAGE_TYPE_ARTIST" -> Card(id, r.optString("text"), "Artist", null)
        "MUSIC_PAGE_TYPE_ALBUM" -> Card(id, r.optString("text"), "Album", null)
        else -> null
    }
}.sortedBy { it.subtitle != "Artist" }

/** The mood / genre chips at the top of a Home page, each with the browse params of its feed. */
fun homeChips(root: Any?): List<YouTube.Chip> = findAll(root, setOf("chipCloudChipRenderer")).mapNotNull { (_, c) ->
    val p = c.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("params")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    c.optJSONObject("text").text().takeIf { it.isNotBlank() }?.let { YouTube.Chip(it, p) }
}.distinctBy { it.title }.filterNot { it.title.equals("Podcasts", ignoreCase = true) } // a music app: no podcast feed

/** Explore's mood & genre buttons ("Chill", "Bhojpuri"…), each with the params of its playlists page. */
fun moodButtons(root: Any?): List<YouTube.Chip> = findAll(root, setOf("musicNavigationButtonRenderer")).mapNotNull { (_, b) ->
    val e = b.obj("clickCommand", "browseEndpoint")?.takeIf { it.optString("browseId") == "FEmusic_moods_and_genres_category" } ?: return@mapNotNull null
    val p = e.optString("params").nonEmpty() ?: return@mapNotNull null
    b.optJSONObject("buttonText").text().nonEmpty()?.let { YouTube.Chip(it, p) }
}.distinctBy { it.title }

/**
 * The same artwork at full size for big displays like Now Playing; lists keep the lighter default. Covers: 1200 px.
 * A video's frame (i.ytimg.com): the largest size, maxresdefault (1280×720); [ArtFallback] steps down when it's missing.
 */
fun hiRes(url: String?): String? {
    url ?: return null
    YTIMG.find(url)?.let { return frame(it.groupValues[1], YT_SIZES[0]) }
    return url.replace(Regex("=w[0-9]+-h[0-9]+"), "=w1200-h1200")
}

/** A video's thumbnail (16:9 frame) rather than a song's square cover. */
fun isVideoArt(url: String?) = url != null && YTIMG.containsMatchIn(url)

// any ytimg host / webp variant of a frame; the sizes are fetched as plain public jpgs
private val YTIMG = Regex("""^https?://i\d?\.ytimg\.com/vi(?:_webp)?/([^/]+)/([a-z0-9_]+)\.(?:jpg|webp)""")
private fun frame(id: String, size: String) = "https://i.ytimg.com/vi/$id/$size.jpg"
// best first: hq720 (1280×720, no bars) before sddefault (640×480 with bars)
private val YT_SIZES = listOf("maxresdefault", "hq720", "sddefault", "hqdefault")

/** The next smaller frame after [url]'s ytimg size, or null (not a ytimg frame, or already the smallest we try). */
fun smallerYtimg(url: String): String? {
    val m = YTIMG.find(url) ?: return null
    val i = YT_SIZES.indexOf(m.groupValues[2])
    return if (i < 0 || i == YT_SIZES.lastIndex) null else frame(m.groupValues[1], YT_SIZES[i + 1])
}

/** Not every video has every frame size: a missing one (404) loads the next size down; misses are remembered. */
object ArtFallback : coil3.intercept.Interceptor {
    private val missing = java.util.Collections.synchronizedSet(HashSet<String>())
    override suspend fun intercept(chain: coil3.intercept.Interceptor.Chain): coil3.request.ImageResult {
        var url = chain.request.data as? String ?: return chain.proceed()
        while (url in missing) url = smallerYtimg(url) ?: break
        var result = chain.withRequest(chain.request.newBuilder().data(url).build()).proceed()
        while (result is coil3.request.ErrorResult) {
            val next = smallerYtimg(url) ?: break
            missing += url; url = next
            result = chain.withRequest(chain.request.newBuilder().data(url).build()).proceed()
        }
        return result
    }
}
