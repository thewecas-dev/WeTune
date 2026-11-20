package com.wetune

import android.content.Context
import android.util.Base64
import com.wetune.sources.Track
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Animated album covers ("motion artwork") from Apple Music, for the moving cover in Now Playing.
 *
 * Apple has no public API for these. Like other players do (Vivi Music, Cider), this uses the catalog API behind
 * music.apple.com with the web player's own access key, read from the site's code: unofficial, so Apple can change or
 * block it at any time, in which case the moving cover simply doesn't show. The key is kept until it expires (months),
 * since finding it means downloading the site's page and code (~6 MB).
 */
object Motion {
    private val prefs by lazy { App.ctx.getSharedPreferences("motion", Context.MODE_PRIVATE) }
    private const val SITE = "https://music.apple.com"
    private const val API = "https://amp-api.music.apple.com/v1/catalog/us"

    /** Per song: the cover's video address, or "" when Apple has none for it. */
    private val found = ConcurrentHashMap<String, String>()

    /** The moving cover for [t]: its Spotify Canvas when signed in to Spotify and it has one, else Apple Music's animated album cover. Blocking. */
    fun coverFor(t: Track): String? = SpotifyCanvas.canvasFor(t) ?: appleFor(t)

    /** Apple Music's animated album cover for [t] (an HLS stream address), or null when Apple has none or can't be reached. */
    private fun appleFor(t: Track): String? {
        found[t.id]?.let { return it.ifEmpty { null } }
        val url = runCatching { search(t) }.getOrNull()
        if (url != null || !trouble) found[t.id] = url.orEmpty()
        trouble = false
        return url
    }

    @Volatile private var trouble = false // the last lookup failed for network / key reasons: don't remember "none"

    /** Albums matching the song (by album and artist, then by title and artist); the first that matches and moves. */
    private fun search(t: Track): String? {
        val artist = SourceMatch.firstArtist(t.artist)
        val terms = listOfNotNull(t.album.takeIf { it.isNotBlank() }?.let { "$it $artist" }, "${SourceMatch.clean(t.title)} $artist").distinct()
        for (term in terms) {
            val res = get("$API/search?types=albums&limit=6&extend=editorialVideo&term=" + URLEncoder.encode(term, "UTF-8")) ?: return null
            val albums = res.optJSONObject("results")?.optJSONObject("albums")?.optJSONArray("data") ?: continue
            for (i in 0 until albums.length()) {
                val a = albums.optJSONObject(i)?.optJSONObject("attributes") ?: continue
                if (!sameArtist(t.artist, a.optString("artistName"))) continue
                val name = a.optString("name")
                if (!sameName(name, t.album) && !sameName(name, t.title)) continue
                val video = a.optJSONObject("editorialVideo") ?: continue
                // square suits the cover's frame best; tall only when there's no square one
                listOf("motionDetailSquare", "motionSquareVideo1x1", "motionDetailTall", "motionTallVideo3x4").firstNotNullOfOrNull { k ->
                    video.optJSONObject(k)?.optString("video")?.takeIf { it.startsWith("https://") }
                }?.let { return it }
            }
        }
        return null
    }

    private fun words(s: String) = SourceMatch.clean(s).split(' ').filter { it.length > 1 }.toSet()

    /** Album / song names agree when most of the shorter one's words are in the other ("After Hours (Deluxe)" ~ "After Hours"). */
    internal fun sameName(a: String, b: String): Boolean {
        val x = words(a); val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return (x intersect y).size.toFloat() / minOf(x.size, y.size) >= 0.75f
    }

    /** Any of the song's artists among the album's ("Diljit Dosanjh, Hanumankind" ~ "Diljit Dosanjh & Hanumankind"). */
    internal fun sameArtist(ours: String, theirs: String): Boolean {
        val t = SourceMatch.clean(theirs)
        return ours.split(',', '&', '|').map { SourceMatch.clean(it) }.any { it.length > 2 && (t.contains(it) || it.contains(t)) }
    }

    // ---- the web player's key ----

    /** A GET to Apple's catalog with the key; on 401 / 403 the key is refreshed once. Null on failure. */
    private fun get(url: String, retry: Boolean = true): JSONObject? {
        val key = key() ?: run { trouble = true; return null }
        val req = Request.Builder().url(url).header("Authorization", "Bearer $key")
            .header("Origin", SITE).header("Referer", "$SITE/").header("User-Agent", UA).build()
        http.newCall(req).execute().use { r ->
            if ((r.code == 401 || r.code == 403) && retry) { prefs.edit().clear().apply(); return get(url, retry = false) }
            if (!r.isSuccessful) { trouble = true; return null }
            return JSONObject(r.body?.string().orEmpty())
        }
    }

    /** The saved key while it's valid, else a fresh one from music.apple.com's code. */
    @Synchronized
    private fun key(): String? {
        val saved = prefs.getString("key", null)
        if (saved != null && prefs.getLong("exp", 0) * 1000 > System.currentTimeMillis() + 3_600_000) return saved
        val page = fetch("$SITE/us/browse") ?: return null
        val scripts = Regex("/assets/index[^\"]*\\.js").findAll(page).map { it.value }.distinct().toList()
        for (s in scripts) {
            val code = fetch(SITE + s) ?: continue
            val key = jwt.findAll(code).map { it.value }.firstOrNull { expiry(it) * 1000 > System.currentTimeMillis() } ?: continue
            prefs.edit().putString("key", key).putLong("exp", expiry(key)).apply()
            return key
        }
        return null
    }

    private val jwt = Regex("eyJ[A-Za-z0-9_-]*\\.eyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]+")

    /** A JWT's expiry (seconds since 1970), from its middle part; 0 when unreadable. */
    internal fun expiry(token: String): Long = runCatching {
        val body = token.split('.')[1]
        JSONObject(String(Base64.decode(body, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))).optLong("exp")
    }.getOrDefault(0L)

    private fun fetch(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull()

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
}
