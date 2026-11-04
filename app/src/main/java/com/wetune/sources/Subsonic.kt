package com.wetune.sources

import com.wetune.Settings
import com.wetune.hash
import com.wetune.httpGet
import com.wetune.objects
import com.wetune.urlEncode
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** Subsonic API (Navidrome, Airsonic, Gonic…) with salted-token auth. */
object Subsonic : Source {
    override val label = "Subsonic"

    fun token(password: String, salt: String) = (password + salt).hash("MD5")

    private fun url(method: String, params: String): String {
        val base = Settings.subsonicUrl.trim().trimEnd('/')
        if (base.isEmpty()) throw IOException("Add your Subsonic or Navidrome server in Settings")
        val salt = UUID.randomUUID().toString().take(8)
        return "$base/rest/$method.view?$params&u=${Settings.subsonicUser.urlEncode()}" +
            "&t=${token(Settings.subsonicPass, salt)}&s=$salt&v=1.16.1&c=WeTune&f=json"
    }

    private fun call(method: String, params: String): JSONObject {
        val r = JSONObject(httpGet(url(method, params))).getJSONObject("subsonic-response")
        if (r.optString("status") != "ok") throw IOException(r.optJSONObject("error")?.optString("message") ?: "Subsonic server error")
        return r
    }

    fun ping() {
        call("ping", "")
    }

    override fun search(query: String): List<Track> =
        call("search3", "query=${query.urlEncode()}&songCount=50&albumCount=0&artistCount=0")
            .optJSONObject("searchResult3")?.optJSONArray("song").objects().map { s ->
                Track(
                    source = "subsonic",
                    ref = s.getString("id"),
                    title = s.optString("title"),
                    artist = s.optString("artist"),
                    album = s.optString("album"),
                    art = s.optString("coverArt").ifEmpty { null }?.let { url("getCoverArt", "id=${it.urlEncode()}&size=500") },
                    durationMs = s.optLong("duration") * 1000,
                )
            }

    override fun streamUrl(ref: String, high: Boolean) =
        url("stream", "id=${ref.urlEncode()}" + if (high) "" else "&maxBitRate=128")
}
