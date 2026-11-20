package com.wetune

import com.wetune.sources.Track
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Spotify Canvas (the short looping videos Spotify shows behind songs), for the moving cover in Now Playing.
 *
 * Unofficial, as in other players that show them (Vivi Music): Spotify's web player key is issued for the user's own
 * Spotify login (the sp_dc cookie from signing in inside WeTune) plus a rotating time code, and the Canvas comes from
 * Spotify's internal canvas service. Spotify changes this from time to time; when it fails the cover just stays still.
 * The login cookie stays on the phone and is only ever sent to Spotify.
 */
object SpotifyCanvas {
    val signedIn: Boolean get() = Settings.spotifyDc.isNotBlank()

    /** Per song: the Canvas video address, or "" when the song has none. */
    private val found = ConcurrentHashMap<String, String>()

    /** The Canvas for [t] (an MP4 address), or null. Blocking. */
    fun canvasFor(t: Track): String? {
        if (!signedIn) return null
        found[t.id]?.let { return it.ifEmpty { null } }
        val result = runCatching {
            val id = trackId(t) ?: return@runCatching ""
            canvas(id).orEmpty()
        }
        // a failure (network, expired login, Spotify changes) isn't remembered as "no Canvas": the next play asks again
        result.onSuccess { found[t.id] = it }.onFailure { android.util.Log.w("WeTune", "Spotify Canvas: ${it.message}") }
        return result.getOrNull()?.ifEmpty { null }
    }

    /** The Spotify track for [t]: searched by title and artist, kept only if it's the same recording (title, artist, length). */
    private fun trackId(t: Track): String? {
        val q = SourceMatch.queries(t.title, t.artist).first()
        val res = api("https://api.spotify.com/v1/search?type=track&limit=8&q=" + URLEncoder.encode(q, "UTF-8"))
        val items = res.optJSONObject("tracks")?.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: continue
            val artists = it.optJSONArray("artists")?.let { a -> (0 until a.length()).joinToString(", ") { j -> a.optJSONObject(j)?.optString("name").orEmpty() } }.orEmpty()
            val candidate = Track("spotify", it.optString("id"), it.optString("name"), artists, durationMs = it.optLong("duration_ms"))
            if (SourceMatch.same(t.title, t.artist, t.durationMs, candidate)) return candidate.ref
        }
        return null
    }

    /** Asks Spotify's canvas service for track [id]; the reply (protobuf) carries the video's address as plain text. */
    private fun canvas(id: String): String? {
        val uri = "spotify:track:$id".toByteArray()
        val inner = byteArrayOf(0x0A, uri.size.toByte()) + uri
        val body = byteArrayOf(0x0A, inner.size.toByte()) + inner
        val req = Request.Builder().url("https://spclient.wg.spotify.com/canvaz-cache/v0/canvases")
            .header("Authorization", "Bearer ${token()}").header("Accept", "application/x-protobuf")
            .post(body.toRequestBody("application/x-protobuf".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("canvas service answered ${r.code}")
            val text = String(r.body?.bytes() ?: return null, Charsets.UTF_8)
            return Regex("https://[A-Za-z0-9./_\\-?=&#]+").findAll(text).map { it.value }.firstOrNull { "canvaz" in it || "video" in it }
        }
    }

    private fun api(url: String): JSONObject {
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${token()}").build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401) { token = null; error("Spotify refused the key") }
            if (!r.isSuccessful) error("Spotify search answered ${r.code}")
            return JSONObject(r.body?.string().orEmpty())
        }
    }

    // ---- the web player's key, issued for the user's login ----

    @Volatile private var token: Pair<String, Long>? = null // key, valid until (ms)

    @Synchronized
    private fun token(): String {
        token?.let { (t, until) -> if (until > System.currentTimeMillis() + 60_000) return t }
        for (attempt in 0..1) {
            val (secret, version) = secret(fresh = attempt == 1)
            val code = totp(secret, serverTime())
            val url = "https://open.spotify.com/api/token?reason=transport&productType=web-player&totp=$code&totpServer=$code&totpVer=$version"
            val req = Request.Builder().url(url).header("Cookie", "sp_dc=${Settings.spotifyDc}")
                .header("User-Agent", UA).header("Accept", "application/json").build()
            val o = http.newCall(req).execute().use { r -> if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null } ?: continue
            val key = o.optString("accessToken")
            if (key.isBlank() || o.optBoolean("isAnonymous")) continue // the time code's secret may have rotated: once more with a fresh one
            token = key to o.optLong("accessTokenExpirationTimestampMs", System.currentTimeMillis() + 30 * 60_000)
            return key
        }
        error("Spotify sign-in expired: sign in again in Settings › Now Playing")
    }

    @Volatile private var nuance: Pair<String, Int>? = null

    /** Spotify's current time-code secret and its version, as published by the community (cached for the app run). */
    private fun secret(fresh: Boolean): Pair<String, Int> {
        if (!fresh) nuance?.let { return it }
        val got = runCatching {
            val gist = http.newCall(Request.Builder().url("https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef").build()).execute().use { JSONObject(it.body?.string().orEmpty()) }
            val files = gist.getJSONObject("files")
            val list = JSONArray(files.getJSONObject(files.keys().next()).getString("content"))
            (0 until list.length()).map { list.getJSONObject(it) }.maxBy { it.getInt("v") }.let { it.getString("s") to it.getInt("v") }
        }.getOrNull() ?: error("couldn't get Spotify's time-code secret")
        nuance = got
        return got
    }

    /** Spotify's clock in seconds (the time code must match it); the phone's when it can't be reached. */
    private fun serverTime(): Long = runCatching {
        http.newCall(Request.Builder().url("https://open.spotify.com/api/server-time").header("User-Agent", UA).build()).execute()
            .use { JSONObject(it.body?.string().orEmpty()).getLong("serverTime") }
    }.getOrDefault(System.currentTimeMillis() / 1000)

    /** Standard 6-digit, 30-second TOTP (HMAC-SHA1) for a base32 [secret] at [seconds]. */
    internal fun totp(secret: String, seconds: Long): String {
        val key = base32(secret)
        var counter = seconds / 30
        val msg = ByteArray(8)
        for (i in 7 downTo 0) { msg[i] = (counter and 0xFF).toByte(); counter = counter shr 8 }
        val hash = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }.doFinal(msg)
        val o = hash.last().toInt() and 0x0F
        val n = ((hash[o].toInt() and 0x7F) shl 24) or ((hash[o + 1].toInt() and 0xFF) shl 16) or ((hash[o + 2].toInt() and 0xFF) shl 8) or (hash[o + 3].toInt() and 0xFF)
        return (n % 1_000_000).toString().padStart(6, '0')
    }

    internal fun base32(s: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0; var bits = 0
        for (ch in s.uppercase()) {
            val v = alphabet.indexOf(ch); if (v < 0) continue
            buffer = (buffer shl 5) or v; bits += 5
            if (bits >= 8) { out.write((buffer shr (bits - 8)) and 0xFF); bits -= 8 }
        }
        return out.toByteArray()
    }

    fun signOut() { Settings.spotifyDc = ""; token = null; found.clear() }

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
}
