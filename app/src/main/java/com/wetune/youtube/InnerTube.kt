package com.wetune.youtube

import com.wetune.UA
import com.wetune.hash
import com.wetune.http
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/** Minimal YouTube Music (WEB_REMIX) InnerTube client. Signed in when a cookie with SAPISID is set. */
object InnerTube {
    const val ORIGIN = "https://music.youtube.com"
    const val clientVersion = "1.20261027.10.00"

    var cookieProvider: () -> String = { "" }
    var onAuthError: () -> Unit = {}

    val cookie: String get() = cookieProvider()
    val signedIn: Boolean get() = sapisid(cookie) != null

    fun sapisid(cookie: String): String? {
        val parts = cookie.split(';').map { it.trim() }
        fun value(name: String) = parts.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.ifEmpty { null }
        return value("SAPISID") ?: value("__Secure-3PAPISID")
    }

    fun sapisidHash(sapisid: String, timestamp: Long, origin: String = ORIGIN): String =
        "SAPISIDHASH ${timestamp}_" + "$timestamp $sapisid $origin".hash("SHA-1")

    fun headers(): Map<String, String> = buildMap {
        put("Origin", ORIGIN)
        put("X-Origin", ORIGIN)
        put("Referer", "$ORIGIN/")
        put("X-YouTube-Client-Name", "67")
        put("X-YouTube-Client-Version", clientVersion)
        val c = cookie
        sapisid(c)?.let {
            put("Cookie", c)
            put("Authorization", sapisidHash(it, System.currentTimeMillis() / 1000))
            put("X-Goog-AuthUser", "0")
        }
    }

    /** Blocking POST to youtubei/v1/[endpoint]. */
    fun post(endpoint: String, body: JSONObject = JSONObject()): JSONObject {
        body.put(
            "context", JSONObject().put(
                "client", JSONObject()
                    .put("clientName", "WEB_REMIX").put("clientVersion", clientVersion)
                    .put("hl", "en").put("gl", "US")
            )
        )
        val b = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false")
            .header("User-Agent", UA)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        headers().forEach { (k, v) -> b.header(k, v) }
        http.newCall(b.build()).execute().use { r ->
            if ((r.code == 401 || r.code == 403) && signedIn) onAuthError()
            if (r.code == 401) throw IOException("YouTube sign-in expired. Sign in again in Settings.")
            if (!r.isSuccessful) throw IOException("YouTube Music request failed (${r.code})")
            return JSONObject(r.body!!.string())
        }
    }
}
