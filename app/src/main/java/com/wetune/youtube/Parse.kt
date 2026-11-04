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
    )
}

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

data class Section(val title: String, val tracks: List<Track>, val cards: List<Card>)

fun sections(root: Any?): List<Section> = findAll(root, setOf(CAROUSEL, SHELF)).mapNotNull { (_, s) ->
    val title = (s.optJSONObject("header")?.let { findFirst(it, "title") } as? JSONObject).text()
        .ifEmpty { s.optJSONObject("title").text() }
    val it = items(s.optJSONArray("contents"))
    if (it.tracks.isEmpty() && it.cards.isEmpty()) null else Section(title, it.tracks, it.cards)
}

fun continuation(node: Any?): String? =
    (findFirst(node, "continuationCommand") as? JSONObject)?.optString("token").nonEmpty()
        ?: (findFirst(node, "nextContinuationData") as? JSONObject)?.optString("continuation").nonEmpty()
