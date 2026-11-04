package com.wetune

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.datasource.cache.ContentMetadata

/**
 * What the audio path is doing for the current song: stream, format, decoder, processing and output.
 * Written by PlaybackService, read by the player UI.
 * shortcut: shared through a process-wide object because the service and UI run in one process; use session extras if that ever changes.
 */
object AudioInfo {
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { App.ctx.getSharedPreferences("audio_urls", Context.MODE_PRIVATE) }

    /** Stream address per song id. Remembered on disk too, because a song played from the cache is never re-resolved. */
    private val urls = mutableStateMapOf<String, String>()
    val url: String? get() = Playback.current?.id?.let(::urlFor)
    fun urlFor(id: String): String? = urls[id] ?: prefs.getString(id, null)

    /**
     * Cache key for a stream address: YouTube by video + format (its URLs change every time, the content doesn't),
     * everything else by host + path without the query (expiring tokens).
     */
    fun cacheKey(url: String): String = streamCacheKey(url)

    // stream, as the decoder sees it
    var mime by mutableStateOf<String?>(null)
    var container by mutableStateOf<String?>(null)
    var codecs by mutableStateOf<String?>(null)
    var bitrate by mutableIntStateOf(0) // bits per second, 0 if unknown
    var sampleRate by mutableIntStateOf(0)
    var channels by mutableIntStateOf(0)
    // decoder
    var decoder by mutableStateOf<String?>(null)
    var decoderInitMs by mutableLongStateOf(0)
    // processing and output
    var gainDb by mutableFloatStateOf(0f) // loudness normalization, 0 or negative
    var outEncoding by mutableStateOf<String?>(null)
    var outRate by mutableIntStateOf(0)
    var outChannels by mutableIntStateOf(0)
    var outBufferBytes by mutableIntStateOf(0)
    var offload by mutableStateOf(false)
    var underruns by mutableIntStateOf(0)
    // network
    var bandwidthKbps by mutableLongStateOf(0)

    /** Quality upgrade per song: "searching" while looking for a 320 kbps copy, then "upgraded" or "none". */
    private val upgrades = mutableStateMapOf<String, String>()
    val upgrade: String? get() = Playback.current?.id?.let { upgrades[it] }
    fun upgrading(id: String, state: String) = main.post { upgrades[id] = state
        // never show "Finding 320 kbps…" forever: give up on the label after 15 s
        if (state == "searching") main.postDelayed({ if (upgrades[id] == "searching") upgrades[id] = "none" }, 15_000)
    }

    /** Each song's quality over time: song position (ms) → "source · quality" it played, for the pipeline sheet. */
    private val histories = mutableStateMapOf<String, List<Pair<Long, String>>>()
    val history: List<Pair<Long, String>> get() = Playback.current?.id?.let { histories[it] }.orEmpty()
    fun event(id: String, positionMs: Long, text: String) = main.post {
        val h = histories[id].orEmpty()
        if (h.lastOrNull()?.second != text) histories[id] = (h + (positionMs to text)).takeLast(20)
    }

    /** Lossless copy details per song (codec, bit depth, sample rate) when it plays from an add-on. */
    private val hifi = mutableStateMapOf<String, Triple<String, Int, Int>>()
    fun lossless(id: String, codec: String?, bits: Int, rate: Int) = main.post { if (codec == null) hifi.remove(id) else hifi[id] = Triple(codec, bits, rate) }

    /** The playing song's lossless details, only once the decoder confirms it really is playing the lossless file. */
    val hifiNow: Triple<String, Int, Int>?
        get() = Playback.current?.id?.let { hifi[it] }?.takeIf { codecName in setOf("FLAC", "ALAC", "PCM") }

    /** For a YouTube song played from JioSaavn: the JioSaavn song it was matched to (title · artist · album). */
    private val matches = mutableStateMapOf<String, String>()
    val match: String? get() = Playback.current?.id?.let { matches[it] }
    fun matched(id: String, what: String) = main.post { matches[id] = what }

    /** Called (from any thread) when song [id]'s stream URL is known. */
    fun resolved(id: String, url: String) = main.post {
        urls[id] = url
        prefs.edit().apply {
            if (prefs.all.size > 400) clear() // keep it small; addresses are re-learned as songs stream
            putString(id, url)
        }.apply()
    }

    /** "OPUS", "AAC", "MP3", "FLAC"… from the stream's MIME type and codec string. */
    val codecName: String?
        get() {
            val c = codecs.orEmpty().lowercase()
            val m = mime.orEmpty().lowercase()
            return when {
                "opus" in c || "opus" in m -> "OPUS"
                "mp4a" in c || "aac" in m || "mp4a" in m -> "AAC"
                "flac" in c || "flac" in m -> "FLAC"
                "vorbis" in c || "vorbis" in m -> "VORBIS"
                "mpeg" in m || "mp3" in m -> "MP3"
                "wav" in m || "raw" in m -> "PCM"
                m.isNotBlank() -> m.substringAfter('/').uppercase()
                else -> null
            }
        }

    /** AAC flavour from the codec string (mp4a.40.2 → AAC-LC, .5 → HE-AAC, .29 → HE-AAC v2). */
    val profile: String?
        get() = when (codecs?.lowercase()) {
            "mp4a.40.2" -> "AAC-LC"
            "mp4a.40.5" -> "HE-AAC"
            "mp4a.40.29" -> "HE-AAC v2"
            "opus" -> "Opus"
            else -> codecs
        }

    /** Size of the current song's file in bytes, once the cache knows it (or the downloaded file). */
    fun contentBytes(): Long {
        val t = Playback.current ?: return 0
        Downloads.file(t).takeIf { it.exists() }?.let { return it.length() }
        val key = urlFor(t.id)?.let(::cacheKey) ?: return 0
        return runCatching { ContentMetadata.getContentLength(App.cache.getContentMetadata(key)) }.getOrDefault(-1L).coerceAtLeast(0)
    }

    /**
     * The stream's bitrate in kbps, from the best source available: the decoder's format, what the URL says
     * (YouTube itag, JioSaavn _320), or file size ÷ length.
     */
    val kbps: Int?
        get() {
            if (bitrate > 0) return bitrate / 1000
            url?.let { u ->
                Uri.parse(u).getQueryParameter("itag")?.toIntOrNull()?.let { ItagKbps[it]?.let { k -> return k } }
                Regex("_(\\d{2,3})\\.(mp4|m4a|mp3)").find(u)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            }
            val bytes = contentBytes(); val ms = Playback.duration()
            return if (bytes > 0 && ms > 10_000) (bytes * 8 / ms).toInt() else null // bytes·8 / ms = kbit/s
        }

    /** Tiny quality line for the player, e.g. "OPUS · 160 kbps". Null until something is known. */
    val label: String?
        get() = hifiNow?.let { (codec, bits, rate) -> listOfNotNull(codec, bits.takeIf { it > 0 }?.let { "$it-bit" }, rate.takeIf { it > 0 }?.let { "${formatKhz(it)} kHz" }).joinToString(" · ") }
            ?: listOfNotNull(codecName, kbps?.let { "$it kbps" })
            .takeIf { it.isNotEmpty() }?.joinToString(" · ")

    fun formatKhz(hz: Int) = if (hz % 1000 == 0) "${hz / 1000}" else "%.1f".format(hz / 1000f)

    /** YouTube audio formats by itag: approximate average bitrate in kbps, and what they are. */
    val ItagKbps = mapOf(249 to 50, 250 to 70, 251 to 160, 139 to 48, 140 to 128, 141 to 256, 171 to 128, 172 to 192, 774 to 256)
    val ItagFormat = mapOf(249 to "Opus in WebM", 250 to "Opus in WebM", 251 to "Opus in WebM", 139 to "AAC in MP4", 140 to "AAC in MP4", 141 to "AAC in MP4", 171 to "Vorbis in WebM", 172 to "Vorbis in WebM", 774 to "Opus in WebM")
}

/**
 * A small log of quality upgrades in the app's own files (the phone's system log rolls over in seconds).
 * Read on a debug build with: adb shell run-as com.wetune cat files/upgrade.log
 */
object UpgradeLog {
    private val file by lazy { java.io.File(App.ctx.filesDir, "upgrade.log") }

    @Synchronized
    fun add(line: String) {
        runCatching {
            val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            val lines = (if (file.exists()) file.readLines() else emptyList()).takeLast(300) + "$stamp $line"
            file.writeText(lines.joinToString("\n"))
        }
    }
}

/** Query parameters that only sign or time-limit a link; they change every time and never tell files apart. */
private val volatileParams = setOf(
    "hmac", "etsp", "expires", "expire", "exp", "signature", "sig", "token", "key-pair-id", "policy", "x-amz-signature",
    "x-amz-date", "x-amz-credential", "x-amz-security-token", "x-amz-expires", "x-amz-signedheaders", "x-amz-algorithm", "auth", "hdnts", "hdnea", "ts",
)

/**
 * Cache key for a stream address. YouTube: video + format (its URLs change every time, the content doesn't).
 * Everything else: host + path + the query parameters that pick the file (e.g. Qobuz's ?eid=…&fmt=…, where every
 * song shares the same path), without the signature and expiry ones.
 */
fun streamCacheKey(url: String): String {
    val u = runCatching { java.net.URI(url) }.getOrNull() ?: return url
    val query = u.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.map { it.substringBefore('=') to it.substringAfter('=', "") }
    if (u.host?.endsWith("googlevideo.com") == true) {
        val id = query.firstOrNull { it.first == "id" }?.second; val itag = query.firstOrNull { it.first == "itag" }?.second
        if (id != null && itag != null) return "gv:$id:$itag"
    }
    val keep = query.filter { it.first.lowercase() !in volatileParams }.sortedBy { it.first }.joinToString("&") { "${it.first}=${it.second}" }
    return "${u.scheme}://${u.host}${u.rawPath}" + if (keep.isEmpty()) "" else "?$keep"
}
