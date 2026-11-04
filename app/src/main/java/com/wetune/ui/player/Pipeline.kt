package com.wetune.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.wetune.App
import com.wetune.AudioInfo
import com.wetune.Downloads
import com.wetune.Playback
import com.wetune.Resolver
import com.wetune.Settings
import com.wetune.sources.Track
import kotlinx.coroutines.delay

/** Every step a song takes from where it lives to your ears, for the song that's playing. Refreshes every second. */
@Composable
fun PipelineSheet(t: Track, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val tick by produceState(0) { while (true) { delay(1000); value++ } }
    tick // read so the sheet refreshes live (buffer, network, cache)

    val url = AudioInfo.url
    val uri = url?.let(Uri::parse)
    val itag = uri?.getQueryParameter("itag")?.toIntOrNull()
    val downloaded = Downloads.file(t).exists()
    val fromSaavn = t.source == "yt" && url?.contains("saavncdn") == true // a YouTube song played from its JioSaavn match
    val bytes = AudioInfo.contentBytes()
    val cached = url?.let { u -> runCatching { App.cache.getCachedBytes(AudioInfo.cacheKey(u), 0, C.LENGTH_UNSET.toLong()) }.getOrDefault(0L) } ?: 0L
    val output = outputDevice(ctx)
    val network = networkType(ctx)
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val sourceName = when {
        t.source == "yt" && url?.contains("googlevideo") != true && com.wetune.Resolver.copyName(t.ref) != null -> com.wetune.Resolver.copyName(t.ref)!! + " · upgraded from YouTube"
        fromSaavn -> "JioSaavn · 320 kbps · upgraded from YouTube"
        downloaded -> "Downloaded copy"
        else -> when (t.source) { "yt" -> "YouTube Music"; "saavn" -> "JioSaavn"; "subsonic" -> "Subsonic server"; else -> "This phone" }
    }

    Sheet("Audio pipeline", onDismiss, actions = { Btn("Done", onDismiss, kind = BtnKind.Secondary) }) {
        Stage(
            Ic.Library, "SOURCE", sourceName,
            listOf(
                "Stream format" to itag?.let { "itag $it · ${AudioInfo.ItagFormat[it] ?: "audio"}" },
                "Server" to uri?.host?.takeIf { uri.scheme?.startsWith("http") == true },
                "File" to when { downloaded -> Downloads.file(t).name; t.source == "local" -> uri?.lastPathSegment; else -> null },
            ),
        )
        // how this song's quality got where it is: started on…, found…, switched…
        val history = AudioInfo.history
        if (history.isNotEmpty()) {
            val t0 = history.first().first
            Stage(
                Ic.ArrowUp, "UPGRADES", history.last().second,
                history.map { (at, what) -> "+%.1f s".format((at - t0) / 1000f) to what },
            )
        }
        Stage(
            Ic.Download, "TRANSFER",
            when { downloaded || t.source == "local" -> "Read from storage"; bytes > 0 && cached >= bytes -> "Played from the cache"; else -> "Streaming over ${network.first}" },
            listOf(
                "File size" to bytes.takeIf { it > 0 }?.let(::mb),
                "Cached" to if (cached > 0) mb(cached) + (if (bytes > 0) " (${(cached * 100 / bytes).coerceAtMost(100)}%)" else "") else null,
                "Buffered ahead" to ((Playback.buffered() - Playback.position()) / 1000).takeIf { it > 0 }?.let { "$it s" },
                "Network speed" to AudioInfo.bandwidthKbps.takeIf { it > 0 }?.let { if (it > 1000) "%.1f Mbps".format(it / 1000f) else "$it kbps" },
            ),
        )
        Stage(
            Ic.Note, "FORMAT",
            listOfNotNull(AudioInfo.codecName, AudioInfo.kbps?.let { "$it kbps" }).joinToString(" · ").ifBlank { "Waiting for the first audio…" },
            listOf(
                "Codec" to AudioInfo.profile,
                "Bitrate" to AudioInfo.kbps?.let { "$it kbps" + if (AudioInfo.bitrate > 0) "" else " (estimated)" },
                "Sample rate" to AudioInfo.sampleRate.takeIf { it > 0 }?.let { "${AudioInfo.formatKhz(it)} kHz" },
                "Channels" to AudioInfo.channels.takeIf { it > 0 }?.let { when (it) { 1 -> "Mono"; 2 -> "Stereo"; else -> "$it channels" } },
                "Container" to (AudioInfo.container ?: AudioInfo.mime),
            ),
        )
        Stage(
            Ic.Pipeline, "DECODER",
            AudioInfo.decoder?.let { if (it.startsWith("c2.android") || it.startsWith("OMX.google")) "Software (Android)" else "Hardware (phone's chip)" } ?: "Not started yet",
            listOf(
                "Decoder" to AudioInfo.decoder,
                "Start-up time" to AudioInfo.decoderInitMs.takeIf { it > 0 }?.let { "$it ms" },
                "Output" to AudioInfo.outEncoding,
            ),
        )
        val mixer = output.second
        Stage(
            Ic.Volume, "PROCESSING",
            when {
                com.wetune.Effects.summary() != "Off" -> "Add-ons: " + com.wetune.Effects.summary()
                AudioInfo.gainDb < -0.05f -> "Loudness lowered by %.1f dB".format(-AudioInfo.gainDb)
                else -> "No changes to the sound"
            },
            listOf(
                "Normalization" to if (!Settings.normalize) "Off" else if (AudioInfo.gainDb < -0.05f) "%.1f dB (×%.2f)".format(AudioInfo.gainDb, Math.pow(10.0, AudioInfo.gainDb / 20.0)) else "On, no change needed",
                "Add-ons" to com.wetune.Effects.summary(),
                "Speed" to (Playback.speed().takeIf { it != 1f }?.let { "%.2f×".format(it) } ?: "Normal"),
                "Resampling" to when {
                    AudioInfo.offload -> "None (offloaded to the audio chip)"
                    mixer > 0 && AudioInfo.sampleRate > 0 && mixer != AudioInfo.sampleRate -> "${AudioInfo.formatKhz(AudioInfo.sampleRate)} → ${AudioInfo.formatKhz(mixer)} kHz by Android"
                    mixer > 0 && AudioInfo.sampleRate > 0 -> "None"
                    else -> null
                },
            ),
        )
        Stage(
            Ic.Device, "OUTPUT", output.first,
            listOf(
                "Audio track" to listOfNotNull(AudioInfo.outEncoding, AudioInfo.outRate.takeIf { it > 0 }?.let { "${AudioInfo.formatKhz(it)} kHz" }, AudioInfo.outChannels.takeIf { it > 0 }?.let { "$it ch" })
                    .joinToString(" · ").ifBlank { null },
                "Buffer" to bufferMs()?.let { "$it ms" },
                "Mixer" to mixer.takeIf { it > 0 }?.let { "${AudioInfo.formatKhz(it)} kHz" + (am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.let { f -> " · $f frames per burst" } ?: "") },
                "Dropouts" to if (AudioInfo.underruns > 0) "${AudioInfo.underruns}" else "None",
            ),
            last = true,
        )
    }
}

private fun mb(bytes: Long) = "%.1f MB".format(bytes / 1_048_576f)

/** The audio track's buffer in milliseconds, from its size in bytes and the output format. */
private fun bufferMs(): Long? {
    val sampleBytes = when (AudioInfo.outEncoding) { "16-bit PCM" -> 2; "24-bit PCM" -> 3; "32-bit PCM", "32-bit float PCM" -> 4; "8-bit PCM" -> 1; else -> return null }
    val frameBytes = sampleBytes * AudioInfo.outChannels
    if (frameBytes <= 0 || AudioInfo.outRate <= 0 || AudioInfo.outBufferBytes <= 0) return null
    return AudioInfo.outBufferBytes * 1000L / (frameBytes * AudioInfo.outRate)
}

@Composable
private fun Stage(icon: ImageVector, step: String, value: String, rows: List<Pair<String, String?>>, last: Boolean = false) {
    val c = WT.c
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // a dot per stage, joined by a line, so it reads as one path from top to bottom
        Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.text.copy(alpha = 0.1f)), Alignment.Center) { Icon(icon, null, size = 16.dp, tint = c.text) }
            if (!last) Box(Modifier.width(1.5.dp).weight(1f).background(c.text.copy(alpha = 0.15f)))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, bottom = if (last) 0.dp else 18.dp)) {
            Txt(step, style = Type.overline, color = c.muted)
            Txt(value, Modifier.padding(top = 2.dp), style = Type.bodyStrong)
            rows.forEach { (k, v) ->
                if (v != null) Row(Modifier.padding(top = 5.dp)) {
                    Txt(k, Modifier.width(110.dp), style = Type.caption, color = c.muted, maxLines = 1)
                    Txt(v, Modifier.weight(1f), style = Type.caption, weight = FontWeight.Medium, align = TextAlign.Start)
                }
            }
        }
    }
    if (last) Spacer(Modifier.height(4.dp))
}

/** Network kind ("Wi-Fi", "mobile data", …) and whether it's metered. */
private fun networkType(ctx: Context): Pair<String, Boolean> {
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    val kind = when {
        caps == null -> "no network"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "the network"
    }
    return kind to cm.isActiveNetworkMetered
}

/**
 * The device Android is most likely playing through, and the mixer's sample rate.
 * shortcut: picks by priority (Bluetooth > USB > wired > speaker); Android 13's getAudioDevicesForAttributes would be exact.
 */
internal fun outputDevice(ctx: Context): Pair<String, Int> {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    fun named(d: AudioDeviceInfo, kind: String) = d.productName?.toString()?.takeIf { it.isNotBlank() && it != android.os.Build.MODEL }?.let { "$kind · $it" } ?: kind
    val name = outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == 26 /* BLE headset */ }?.let { named(it, "Bluetooth") }
        ?: outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE }?.let { named(it, "USB audio") }
        ?: outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }?.let { "Wired headphones" }
        ?: "Phone speaker"
    val rate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
    return name to rate
}
