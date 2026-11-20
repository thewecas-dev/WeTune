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

    fun searchCards(query: String): List<Card> =
        items(InnerTube.post("search", JSONObject().put("query", query))).cards

    /** YouTube Music's own search filters (the params its web client sends for each chip). */
    val SearchFilters = mapOf(
        "songs" to SONGS_FILTER,
        "videos" to "EgWKAQIQAWoMEA4QChADEAQQCRAF",
        "albums" to "EgWKAQIYAWoMEA4QChADEAQQCRAF",
        "artists" to "EgWKAQIgAWoMEA4QChADEAQQCRAF",
        "playlists" to "EgeKAQQoAEABagwQDhAKEAMQBBAJEAU=",
    )

    /** One kind of result only: songs and videos come back as tracks, the rest as cards. */
    fun searchKind(query: String, kind: String): Items =
        items(InnerTube.post("search", JSONObject().put("query", query).put("params", SearchFilters.getValue(kind))))

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

    /** [saveId]: the playlist id YouTube's "save to library" uses for this album or playlist (null when it can't be saved). */
    data class Page(val tracks: List<Track>, val sections: List<Section>, val saveId: String? = null, val art: String? = null)

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
        )
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
