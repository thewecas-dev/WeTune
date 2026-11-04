package com.wetune.sources

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wetune.App
import com.wetune.UA
import com.wetune.http
import com.wetune.objects
import com.wetune.str
import com.wetune.urlEncode
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Lossless source add-ons (the BitChord / Eclipse add-on format): a server you paste the link of, offering
 *   GET {base}/manifest.json           → id, name, version, resources, settings
 *   GET {base}/search?q=…              → { tracks: [ { id, title, artist, album, duration (s), artworkURL, audioQuality } ] }
 *   GET {base}/stream/{id}             → { url, codec, format, quality, sampleRate, bitDepth, encrypted }
 * They're tried in the user's order when a song steps up to lossless. WeTune ships with none; the user adds them.
 */
object Addons {
    data class Addon(
        val base: String,
        val id: String,
        val name: String,
        val version: String,
        val downloads: Boolean,
        val enabled: Boolean = true,
        val qualityKey: String? = null, // the manifest's quality setting, sent with every request
        val quality: String? = null,
        val qualities: List<Pair<String, String>> = emptyList(), // value to label
    )

    data class Stream(val url: String, val codec: String, val sampleRate: Int, val bitDepth: Int, val quality: String)

    private val prefs by lazy { App.ctx.getSharedPreferences("addons", Context.MODE_PRIVATE) }

    // free hosts (Render, Vercel) sleep and take up to a minute to wake, so these calls wait longer than the rest of the app
    private val client by lazy { http.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build() }

    var list by mutableStateOf(load())
        private set

    val enabled: List<Addon> get() = list.filter { it.enabled }

    private fun get(url: String): JSONObject {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).header("Accept", "application/json").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("The add-on answered ${r.code}")
            return JSONObject(r.body!!.string())
        }
    }

    /** "https://x.app/manifest.json/" → "https://x.app". */
    fun normalize(link: String): String = link.trim().removeSuffix("/").removeSuffix("/manifest.json").removeSuffix("/")

    /** Blocking: reads the add-on's manifest and adds it to the end of the list (or refreshes it if already there). */
    fun add(link: String): Addon {
        val base = normalize(link)
        require(base.startsWith("https://") || base.startsWith("http://")) { "That isn't a web link" }
        val m = runCatching { get("$base/manifest.json") }.getOrElse { throw IOException("No add-on found at that link (${it.message})") }
        val resources = m.optJSONArray("resources")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
        require("search" in resources && "stream" in resources) { "That add-on can't search and stream songs" }
        val q = m.optJSONArray("settings").objects().firstOrNull { "quality" in it.optString("key").lowercase() }
        val qualities = q?.optJSONArray("options").objects().map { o -> o.optString("value") to (o.str("label") ?: o.optString("value").lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }
        val a = Addon(
            base = base, id = m.optString("id", base), name = m.str("name") ?: base.substringAfter("//"),
            version = m.optString("version"), downloads = m.optInt("allowDownloads", 0) == 1 || m.optBoolean("allowDownloads", false),
            qualityKey = q?.optString("key"), quality = q?.str("default"), qualities = qualities,
        )
        val existing = list.indexOfFirst { it.base == base }
        save(if (existing >= 0) list.toMutableList().also { it[existing] = a.copy(enabled = list[existing].enabled, quality = list[existing].quality ?: a.quality) } else list + a)
        return a
    }

    fun remove(a: Addon) = save(list.filter { it.base != a.base })
    fun setEnabled(a: Addon, on: Boolean) = save(list.map { if (it.base == a.base) it.copy(enabled = on) else it })
    fun setQuality(a: Addon, value: String) = save(list.map { if (it.base == a.base) it.copy(quality = value) else it })
    fun move(a: Addon, by: Int) {
        val i = list.indexOfFirst { it.base == a.base }; val j = (i + by).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return
        save(list.toMutableList().also { val x = it.removeAt(i); it.add(j, x) })
    }

    private fun params(a: Addon) = if (a.qualityKey != null && a.quality != null) "${a.qualityKey}=${a.quality.urlEncode()}" else ""

    /** Blocking. Songs [a] finds for [query], as tracks of source "addon" (ref = add-on id + "|" + its track id). */
    fun search(a: Addon, query: String): List<Track> {
        val o = get("${a.base}/search?q=${query.urlEncode()}" + params(a).let { if (it.isEmpty()) "" else "&$it" })
        return o.optJSONArray("tracks").objects().mapNotNull { t ->
            val id = t.str("id") ?: return@mapNotNull null
            Track(
                source = "addon", ref = "${a.id}|$id", title = t.optString("title"), artist = t.optString("artist"),
                album = t.optString("album"), art = t.str("artworkURL") ?: t.str("artwork"), durationMs = t.optLong("duration") * 1000,
            )
        }
    }

    /** Blocking. The playable file for [ref] ("addonId|trackId"); null when the add-on is gone or the stream is encrypted. */
    fun stream(ref: String): Stream? {
        val addonId = ref.substringBefore('|'); val trackId = ref.substringAfter('|')
        val a = list.firstOrNull { it.id == addonId } ?: return null
        val o = get("${a.base}/stream/${trackId.urlEncode()}" + params(a).let { if (it.isEmpty()) "" else "?$it" })
        if (o.optBoolean("encrypted", false)) return null // DRM'd files can't be played
        val url = o.str("url") ?: return null
        return Stream(url, (o.str("codec") ?: o.str("format") ?: "flac").uppercase(), o.optInt("sampleRate"), o.optInt("bitDepth"), o.optString("quality"))
    }

    fun nameOf(ref: String): String? = list.firstOrNull { it.id == ref.substringBefore('|') }?.name

    /** Wakes sleeping add-on servers in the background so the first upgrade doesn't wait for them. */
    fun warmUp() = Thread { enabled.forEach { a -> runCatching { get("${a.base}/manifest.json") } } }.start()

    private fun save(l: List<Addon>) {
        list = l
        prefs.edit().putString("list", JSONArray(l.map { a ->
            JSONObject().put("base", a.base).put("id", a.id).put("name", a.name).put("version", a.version).put("downloads", a.downloads)
                .put("enabled", a.enabled).put("qualityKey", a.qualityKey).put("quality", a.quality)
                .put("qualities", JSONArray(a.qualities.map { (v, l) -> JSONObject().put("value", v).put("label", l) }))
        }).toString()).apply()
    }

    private fun load(): List<Addon> = runCatching {
        JSONArray(prefs.getString("list", "[]")).objects().map { o ->
            Addon(
                o.getString("base"), o.getString("id"), o.optString("name"), o.optString("version"), o.optBoolean("downloads"),
                o.optBoolean("enabled", true), o.str("qualityKey"), o.str("quality"),
                o.optJSONArray("qualities").objects().map { it.optString("value") to it.optString("label") },
            )
        }
    }.getOrDefault(emptyList())
}
