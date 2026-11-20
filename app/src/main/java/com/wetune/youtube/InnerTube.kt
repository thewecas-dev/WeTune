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
    class HttpError(val code: Int, message: String) : IOException(message)

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

    /** Blocking POST to youtubei/v1/[endpoint]; [client]/[version] override the context client (e.g. ANDROID_MUSIC for timed lyrics). */
    /**
     * The visitor id YouTube hands out with every answer. It has to go back with later requests: without it, the next
     * pages of Home (and other continuations) come back empty, so Home stopped after its first two sections.
     */
    @Volatile private var visitor: String? = null

    /** The listener's country (the phone's region), so Home, charts and new releases are local rather than the US's. */
    private val country: String by lazy { java.util.Locale.getDefault().country.takeIf { it.length == 2 } ?: "US" }

    fun post(endpoint: String, body: JSONObject = JSONObject(), client: String = "WEB_REMIX", version: String = clientVersion): JSONObject {
        val v = visitor
        body.put(
            "context", JSONObject().put(
                "client", JSONObject()
                    .put("clientName", client).put("clientVersion", version)
                    .put("hl", "en").put("gl", country)
                    .apply { if (v != null) put("visitorData", v) }
            )
        )
        val b = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false")
            .header("User-Agent", UA)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        headers().forEach { (k, v) -> b.header(k, v) }
        if (v != null) b.header("X-Goog-Visitor-Id", v)
        http.newCall(b.build()).execute().use { r ->
            if ((r.code == 401 || r.code == 403) && signedIn) onAuthError()
            if (r.code == 401) throw HttpError(401, "YouTube sign-in expired. Sign in again from the You tab.")
            if (!r.isSuccessful) throw HttpError(r.code, "YouTube Music request failed (${r.code})")
            return JSONObject(r.body!!.string()).also { o ->
                o.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotBlank() }?.let { visitor = it }
            }
        }
    }
}
