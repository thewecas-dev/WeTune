package com.wetune.youtube

import com.wetune.sources.Card
import com.wetune.sources.Source
import com.wetune.sources.Track
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException

/** YouTube Music: metadata via our InnerTube client, playable URLs via NewPipe Extractor (handles deciphering). */
object YouTube : Source {
    override val label = "YouTube Music"
    private const val SONGS_FILTER = "EgWKAQIIAWoMEA4QChADEAQQCRAF"

    override fun search(query: String): List<Track> =
        items(InnerTube.post("search", JSONObject().put("query", query).put("params", SONGS_FILTER))).tracks

    /**
     * The unfiltered search's Top result (YouTube Music's own pick: a song, video, album, artist or playlist) and the
     * query YouTube searched instead when it fixed the spelling (null when it didn't). [exact]: no spelling fix.
     */
    fun searchTop(query: String, exact: Boolean = false): Pair<Any?, String?> {
        val root = InnerTube.post("search", JSONObject().put("query", query).apply { if (exact) put("params", EXACT) })
        return topResult(root) to correctedQuery(root)
    }

    /** YouTube Music's own search filters: the params music.youtube.com's chips carry (taken from a search response). */
    val SearchFilters = mapOf(
        "songs" to "EgWKAQIIAWoSEAQQAxAFEAkQEBAKEBUQDhAR",
        "videos" to "EgWKAQIQAWoSEAQQAxAFEAkQEBAKEBUQDhAR",
        "albums" to "EgWKAQIYAWoSEAQQAxAFEAkQEBAKEBUQDhAR",
        "artists" to "EgWKAQIgAWoSEAQQAxAFEAkQEBAKEBUQDhAR",
        "playlists" to "EgeKAQQoAEABahIQBBADEAUQCRAQEAoQFRAOEBE=", // community playlists
    )

    /** "Search instead for …": the params of YouTube's own link, which turn its spelling fix off. */
    private const val EXACT = "QgIIAQ=="

    /** One kind of result only: songs and videos come back as tracks, the rest as cards. [exact]: no spelling fix. */
    fun searchKind(query: String, kind: String, exact: Boolean = false): Items {
        val filter = SearchFilters.getValue(kind)
        // params are a protobuf message, and protobuf messages merge by concatenation: filter + "no spelling fix"
        val params = if (!exact) filter else java.util.Base64.getEncoder().encodeToString(
            java.util.Base64.getDecoder().decode(filter) + java.util.Base64.getDecoder().decode(EXACT)
        )
        return items(InnerTube.post("search", JSONObject().put("query", query).put("params", params)))
    }

    /** A mood / genre chip at the top of Home ("Energize", "Relax"…): its label and the browse params for its feed. */
    data class Chip(val title: String, val params: String)

    /** A page of Home: its sections, the token for the next page (null when none), and the chips offered on it. */
    data class Home(val sections: List<Section>, val token: String?, val chips: List<Chip>)

    /** The first page of Home, or of a chip's feed when [params] is given. */
    fun home(params: String? = null): Home {
        val body = JSONObject().put("browseId", "FEmusic_home")
        if (params != null) body.put("params", params)
        val first = InnerTube.post("browse", body)
        return Home(sections(first), continuation(findFirst(first, "sectionListRenderer")), homeChips(first))
    }

    /** The next page of Home after [token]. */
    fun homeMore(token: String): Pair<List<Section>, String?> {
        val more = InnerTube.post("browse", JSONObject().put("continuation", token))
        return sections(more) to continuation(findFirst(more, "sectionListContinuation"))
    }

    /** YouTube Music's Explore, for the listener's country: what's new, trending, the moods & genres, and the charts. */
    data class Explore(
        val newReleases: List<Card>, val trending: List<Track>, val videos: List<Track>,
        val moods: List<Chip>, val charts: List<Card>, val artists: List<Card>,
    )

    /** Blocking. Two requests: the Explore page and the charts page (the latter may fail on its own). */
    fun explore(): Explore = parseExplore(
        InnerTube.post("browse", JSONObject().put("browseId", "FEmusic_explore")),
        runCatching { InnerTube.post("browse", JSONObject().put("browseId", "FEmusic_charts")) }.getOrNull(),
    )

    fun parseExplore(root: JSONObject, chartsRoot: JSONObject?): Explore {
        val s = sections(root)
        val charts = sections(chartsRoot)
        // shelves by their (English: hl=en) titles: "New albums & singles", "Trending", "New music videos"
        fun titled(from: List<Section>, word: String) = from.firstOrNull { it.title.contains(word, ignoreCase = true) }
        return Explore(
            titled(s, "albums")?.cards.orEmpty(), titled(s, "trending")?.tracks.orEmpty(), titled(s, "video")?.tracks.orEmpty(),
            moodButtons(root),
            charts.flatMap { it.cards }.filter { it.subtitle.startsWith("Chart") },
            titled(charts, "artists")?.cards.orEmpty(),
        )
    }

    /**
     * [saveId]: the playlist id YouTube's "save to library" uses for this album or playlist (null when it can't be saved).
     * [subscribed]: whether the account follows this artist (null: not an artist page, or YouTube didn't say), and
     * [channel] the id following uses (not the page's own browse id). [about]: an artist's audience line and bio.
     */
    data class Page(
        val tracks: List<Track>, val sections: List<Section>, val saveId: String? = null, val art: String? = null,
        val subscribed: Boolean? = null, val channel: String? = null, val about: About? = null,
    )

    /** An artist's "655M monthly audience" (or views), bio without its licence boilerplate, and where the bio is from. */
    data class About(val stat: String, val bio: String, val source: String)

    /** [About] from an artist page's header, else its "About" shelf; null when it has neither. */
    fun about(root: JSONObject): About? {
        val header = listOf("musicImmersiveHeaderRenderer", "musicVisualHeaderRenderer").firstNotNullOfOrNull { findFirst(root.opt("header"), it) as? JSONObject }
        val shelf = findFirst(root, "musicDescriptionShelfRenderer") as? JSONObject
        val stat = header?.optJSONObject("monthlyListenerCount").text().ifEmpty { shelf?.optJSONObject("subheader").text().orEmpty() }
        val text = header?.optJSONObject("description").text().ifEmpty { shelf?.optJSONObject("description").text().orEmpty() }.trim()
        // "…\n\nFrom Wikipedia (https://…) under Creative Commons …": a small source line instead
        val cut = Regex("""\s*From Wikipedia\b""").find(text)
        val about = About(stat, (if (cut != null) text.substring(0, cut.range.first) else text).trim(), if (cut != null) "From Wikipedia" else "")
        return about.takeIf { it.stat.isNotEmpty() || it.bio.isNotEmpty() }
    }

    /** Album, playlist or artist page; follows playlist continuations. */
    fun page(card: Card, maxPages: Int = 50): Page {
        val root = InnerTube.post("browse", JSONObject().put("browseId", card.browseId))
        val tracks = items(root, setOf(ROW, PANEL, CAROUSEL)).tracks.toMutableList()
        var token = continuation(findFirst(root, "musicPlaylistShelfRenderer"))
        var pages = 0
        while (token != null && pages++ < maxPages) {
            val more = InnerTube.post("browse", JSONObject().put("continuation", token))
            tracks += items(more).tracks
            token = continuation(more)
        }
        val isAlbum = card.browseId.startsWith("MPRE")
        val fallbackArtist = subtitleArtist(card.subtitle)
        val sub = findFirst(root, "subscribeButtonRenderer") as? JSONObject
        return Page(
            tracks.distinctBy { it.id }.map {
                it.copy(
                    art = it.art ?: card.art,
                    artist = it.artist.ifEmpty { fallbackArtist },
                    album = it.album.ifEmpty { if (isAlbum) card.title else "" },
                )
            },
            sections(root).filter { it.cards.isNotEmpty() },
            ((findFirst(findFirst(root, "likeEndpoint"), "target") as? JSONObject)?.optString("playlistId")?.ifEmpty { null }),
            // the page's own artwork, for pages opened from a link that carried none (Go to album / artist)
            listOf("musicResponsiveHeaderRenderer", "musicImmersiveHeaderRenderer", "musicVisualHeaderRenderer", "musicDetailHeaderRenderer")
                .firstNotNullOfOrNull { findFirst(root, it) }?.let(::thumbnail),
            sub?.takeIf { it.has("subscribed") }?.optBoolean("subscribed"),
            sub?.optString("channelId")?.ifEmpty { null },
            if (card.browseId.startsWith("UC")) about(root) else null,
        )
    }

    /**
     * Blocking. An album's or playlist's size, "14 songs · 56 min", from one request: the page header's totals, else
     * the first page of songs ("100+ songs" when more pages follow; their length only when it's the whole list).
     */
    fun totals(card: Card): String {
        val root = InnerTube.post("browse", JSONObject().put("browseId", card.browseId))
        val header = listOf("musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer", "musicEditablePlaylistDetailHeaderRenderer")
            .firstNotNullOfOrNull { findFirst(root, it) as? JSONObject }
        header?.let { findFirst(it, "secondSubtitle") as? JSONObject }?.text()?.ifEmpty { null }?.let { return shortTotals(it) }
        val tracks = items(root, setOf(ROW, PANEL, CAROUSEL)).tracks
        if (tracks.isEmpty()) return ""
        if (continuation(findFirst(root, "musicPlaylistShelfRenderer")) != null) return "${tracks.size}+ songs"
        val m = tracks.sumOf { it.durationMs } / 60_000
        return shortTotals("${tracks.size} song${if (tracks.size == 1) "" else "s"}" + when { m >= 60 -> " • ${m / 60} hours, ${m % 60} minutes"; m > 0 -> " • $m minutes"; else -> "" })
    }

    fun radio(videoId: String): List<Track> = items(
        InnerTube.post("next", JSONObject().put("videoId", videoId).put("playlistId", "RDAMVM$videoId").put("isAudioOnly", true)),
        setOf(PANEL),
    ).tracks.filter { it.ref != videoId }

    /** Blocking. Songs by [artist] (its first name when several), for autoplay's "Same artist". */
    fun byArtist(artist: String): List<Track> {
        val name = artist.substringBefore(",").trim()
        return if (name.isEmpty()) emptyList() else search(name).filter { it.artist.contains(name, ignoreCase = true) }
    }

    /** Blocking. The artist and album pages song [videoId] links to (for "Go to artist / album"). */
    fun links(videoId: String): List<Card> {
        val r = InnerTube.post("next", JSONObject().put("videoId", videoId).put("isAudioOnly", true))
        val panel = findFirst(r, "playlistPanelVideoRenderer") as? JSONObject ?: return emptyList()
        return pageLinks(panel.optJSONObject("longBylineText").runs())
    }

    /**
     * What kind of YouTube Music item [videoId] is: MUSIC_VIDEO_TYPE_ATV (a song: audio with its cover), _OMV (an
     * official music video), _UGC (a user upload)… Null when YouTube doesn't say.
     */
    fun videoType(videoId: String): String? =
        findFirst(InnerTube.post("next", JSONObject().put("videoId", videoId).put("isAudioOnly", true)), "musicVideoType") as? String

    data class PlayerInfo(val loudnessDb: Double?, val trackingUrl: String?)

    fun playerInfo(videoId: String): PlayerInfo {
        // Without the player JS signature timestamp YouTube answers UNPLAYABLE with no tracking/loudness data.
        val sts = runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }.getOrDefault(0)
        val r = InnerTube.post(
            "player", JSONObject().put("videoId", videoId)
                .put("playbackContext", JSONObject().put("contentPlaybackContext", JSONObject().put("signatureTimestamp", sts)))
        )
        val audio = r.optJSONObject("playerConfig")?.optJSONObject("audioConfig")
        val db = audio?.takeIf { it.has("loudnessDb") }?.optDouble("loudnessDb")
        val url = r.optJSONObject("playbackTracking")?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")?.ifEmpty { null }
        return PlayerInfo(db, url)
    }

    override fun streamUrl(ref: String, high: Boolean): String {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$ref")
        val streams = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedBy { it.averageBitrate }
        val pick = (if (high) streams.lastOrNull() else streams.firstOrNull()) ?: throw IOException("No audio stream found for this song")
        return pick.content
    }
}
