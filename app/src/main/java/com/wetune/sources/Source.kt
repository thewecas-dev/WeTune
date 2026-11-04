package com.wetune.sources

import org.json.JSONObject

/**
 * A playable song from any source.
 * [ref] is the source's own id: YouTube videoId, content:// uri, JioSaavn encrypted url, Subsonic song id.
 */
data class Track(
    val source: String, // yt | local | saavn | subsonic
    val ref: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val art: String? = null,
    val durationMs: Long = 0,
    val setVideoId: String? = null, // YouTube playlist entry id, needed to remove it from a playlist
) {
    val id: String get() = "$source:$ref"

    fun toJson(): JSONObject = JSONObject()
        .put("source", source).put("ref", ref).put("title", title).put("artist", artist)
        .put("album", album).put("art", art).put("durationMs", durationMs)

    companion object {
        fun fromJson(o: JSONObject) = Track(
            source = o.getString("source"),
            ref = o.getString("ref"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            album = o.optString("album"),
            art = if (o.isNull("art")) null else o.optString("art").ifEmpty { null },
            durationMs = o.optLong("durationMs"),
        )
    }
}

/** A browsable YouTube Music page: album, playlist or artist. */
data class Card(val browseId: String, val title: String, val subtitle: String, val art: String?)

interface Source {
    val label: String

    /** Blocking; call off the main thread. */
    fun search(query: String): List<Track>

    /** Blocking; returns a URL ExoPlayer can play. */
    fun streamUrl(ref: String, high: Boolean): String
}
