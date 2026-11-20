package com.wetune.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.wetune.ui.Motion
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.wetune.AudioInfo
import com.wetune.Playback
import com.wetune.Resolver
import com.wetune.Settings
import com.wetune.sources.Track
import com.wetune.ui.Icon
import com.wetune.ui.Icons
import com.wetune.ui.Txt
import com.wetune.ui.Type
import com.wetune.ui.WT
import com.wetune.ui.press
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Audio, full screen: three tabs in the lyrics / queue pill style.
 *  All: volume, output device, and what's playing with its quality metadata.
 *  Source: every version of the song found across sources, and which one plays.
 *  Pipeline: the signal path, source → stream → decoder → processing → output → device, live.
 */
@Composable
fun AudioPanel(t: Track, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf("all") }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "All", "source" to "Source", "pipeline" to "Pipeline").forEach { (k, label) ->
                ToolPill(label, tab == k, modifier = Modifier.weight(1f)) { tab = k }
            }
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (listOf("all", "source", "pipeline").indexOf(targetState) > listOf("all", "source", "pipeline").indexOf(initialState)) 1 else -1
                (fadeIn(tween(Motion.FOLD, Motion.SMALL / 3)) + slideInHorizontally(Motion.push()) { it / 8 * dir }) togetherWith
                    (fadeOut(Motion.small()) + slideOutHorizontally(Motion.push()) { -it / 8 * dir })
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "audio-tab",
        ) { k ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 16.dp)) {
                when (k) {
                    "all" -> AllTab(t)
                    "source" -> SourceTab(t)
                    else -> PipelineTab(t)
                }
            }
        }
    }
}

@Composable
private fun AllTab(t: Track) {
    val c = WT.c
    val ctx = LocalContext.current
    Volume()
    Gap(12.dp)
    Outputs()

    PanelLabel("Playing now")
    val playing = if (t.source == "yt") Resolver.playingChoice(t.ref, AudioInfo.url) else null
    val label = AudioInfo.label
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (material) 22.dp else 12.dp)).background(c.chip).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(sourceName(t, playing), pt(Type.title.copy(fontSize = 19.sp), 20, 600))
                Txt(label ?: "Loading…", pt(Type.sub, 13, 400), color = c.onSecondary)
            }
            if (label != null) Txt(
                qualityBadge(label), pt(Type.caption, 12, 700),
                Modifier.clip(RoundedCornerShape(6.dp)).background(c.on).padding(horizontal = 8.dp, vertical = 3.dp), color = c.bg,
            )
        }
        Gap(14.dp)
        // the stream's metadata as a two-column grid; unknown values are left out
        val hifi = AudioInfo.hifiNow
        val facts = listOfNotNull(
            AudioInfo.codecName?.let { "Codec" to it },
            AudioInfo.kbps?.let { "Bitrate" to "$it kbps" },
            (hifi?.third?.takeIf { it > 0 } ?: AudioInfo.sampleRate.takeIf { it > 0 })?.let { "Sample rate" to "${AudioInfo.formatKhz(it)} kHz" },
            hifi?.second?.takeIf { it > 0 }?.let { "Bit depth" to "$it-bit" },
            AudioInfo.channels.takeIf { it > 0 }?.let { "Channels" to when (it) { 1 -> "Mono"; 2 -> "Stereo"; else -> "$it" } },
            AudioInfo.container?.let { "Container" to it },
            AudioInfo.contentBytes().takeIf { it > 0 }?.let { "Size" to "%.1f MB".format(it / 1_048_576f) },
            "Loudness" to when { !Settings.normalize -> "Off"; AudioInfo.gainDb < -0.05f -> "−%.1f dB".format(-AudioInfo.gainDb); else -> "As is" },
        )
        facts.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                pair.forEach { (k, v) ->
                    Column(Modifier.weight(1f)) {
                        Txt(k.uppercase(), pt(Type.caption, 11, 600), color = c.onSecondary)
                        Txt(v, pt(Type.body, 15, 500))
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        AudioInfo.match?.let { Txt("Matched: $it", pt(Type.sub, 12, 400), Modifier.padding(top = 6.dp), color = c.onSecondary, lines = 2) }
    }
}

@Composable
private fun SourceTab(t: Track) {
    val c = WT.c
    if (t.source != "yt") {
        Txt("This song comes from ${sourceName(t, null)}; it has one version.", pt(Type.body, 15, 400), Modifier.padding(4.dp), color = c.onSecondary, lines = 3)
        return
    }
    Txt("Pick the copy that plays. Automatic chooses the best match from your sources; a pick sticks for this song.", pt(Type.sub, 13, 400), Modifier.padding(horizontal = 4.dp), color = c.onSecondary, lines = 3)
    Versions(t, Resolver.playingChoice(t.ref, AudioInfo.url))
}

@Composable
private fun Volume() {
    val c = WT.c
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    // follows the phone's volume keys too
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { vol = am.getStreamVolume(AudioManager.STREAM_MUSIC) } }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED) else ctx.registerReceiver(r, filter)
        onDispose { ctx.unregisterReceiver(r) }
    }
    fun set(f: Float) { val v = (f * max).roundToInt().coerceIn(0, max); vol = v; am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.VolLow, c.onSecondary, 18.dp)
        Box(
            Modifier.weight(1f).height(32.dp)
                .semantics {
                    contentDescription = "Volume"
                    progressBarRangeInfo = ProgressBarRangeInfo(vol.toFloat() / max, 0f..1f, max - 1)
                    setProgress { set(it); true }
                }
                .pointerInput(Unit) { detectTapGestures { set(it.x / size.width) } }
                .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> ch.consume(); set((ch.position.x / size.width).coerceIn(0f, 1f)) } }
                .drawBehind {
                    val h = if (material) 12.dp.toPx() else 7.dp.toPx()
                    val top = (size.height - h) / 2
                    val r = CornerRadius(h / 2)
                    drawRoundRect(c.on.copy(alpha = 0.18f), androidx.compose.ui.geometry.Offset(0f, top), Size(size.width, h), r)
                    drawRoundRect(c.on.copy(alpha = 0.88f), androidx.compose.ui.geometry.Offset(0f, top), Size(size.width * vol / max, h), r)
                },
        )
        Icon(Icons.VolHigh, c.onSecondary, 18.dp)
    }
}

@Composable
private fun Versions(t: Track, playing: String?) {
    val c = WT.c
    PanelLabel("Other versions")
    val pinned = Resolver.pin(t.ref)
    val sources = remember(t.id) { Resolver.versionSources(t) } // usually fetched already while the song played
    val tick by produceState(0, sources) { while (sources.any { !it.future.isDone }) { delay(150); value++ }; value++ }
    tick // re-read as lookups finish
    val found = sources.filter { it.future.isDone }.flatMap { runCatching { it.future.get() }.getOrDefault(emptyList()) } + Resolver.original(t)
    val waiting = sources.count { !it.future.isDone }
    val rows = buildList<@Composable () -> Unit> {
        add { VersionRow("Automatic", "Best match from your sources", null, pinned == null) { Playback.useVersion(t, null) } }
        found.forEach { v ->
            add { VersionRow(v.title, "${v.source} · ${v.quality}" + if (v.durationMs > 0) " · ${fmt(v.durationMs)}" else "", v.choice == playing, v.choice == pinned) { Playback.useVersion(t, v.choice) } }
        }
    }
    PanelGroup(rows)
    if (waiting > 0) Txt("Searching $waiting more source${if (waiting > 1) "s" else ""}…", pt(Type.sub, 13, 400), Modifier.padding(start = 4.dp, top = 8.dp), color = c.onSecondary)
}

@Composable
private fun VersionRow(title: String, detail: String, playing: Boolean?, chosen: Boolean, onClick: () -> Unit) {
    val c = WT.c
    Row(
        Modifier.fillMaxWidth().press("$title, $detail" + if (chosen) ", chosen" else "", scale = 0.98f, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, pt(Type.row, 15, 500))
            Txt(detail, pt(Type.sub, 13, 400), color = c.onSecondary)
        }
        if (playing == true) Icon(Icons.Audio, c.on, 16.dp, Modifier.padding(start = 8.dp))
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterEnd) { if (chosen) Icon(Icons.Check, c.on, 16.dp) }
    }
}

/** One stage of the signal path: what it is, what it's doing right now, and details. */
private class Stage(val name: String, val value: String, val detail: String?)

/**
 * The signal path as a chain: Source → Stream → Decoder → Processing → Output → Device, refreshed every second.
 * While playing, dots run down the line joining the stages (one infinite animation, drawn in the draw phase).
 */
@Composable
private fun PipelineTab(t: Track) {
    val c = WT.c
    val ctx = LocalContext.current
    val tick by produceState(0) { while (true) { delay(1000); value++ } }
    tick
    val playing = if (t.source == "yt") Resolver.playingChoice(t.ref, AudioInfo.url) else null
    val host = AudioInfo.url?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }
    val ahead = ((Playback.buffered() - Playback.position()) / 1000).coerceAtLeast(0)
    val dec = AudioInfo.decoder
    val stages = listOf(
        Stage("Source", sourceName(t, playing), listOfNotNull(host?.takeIf { t.source != "local" }, AudioInfo.container ?: AudioInfo.mime).joinToString(" · ").ifBlank { null }),
        Stage(
            "Stream", if (AudioInfo.kbps != null) "${AudioInfo.kbps} kbps" else "Waiting for audio…",
            listOfNotNull("$ahead s buffered ahead", AudioInfo.bandwidthKbps.takeIf { it > 0 }?.let { "network ≈ %.1f Mbps".format(it / 1000f) }).joinToString(" · "),
        ),
        Stage(
            "Decoder", listOfNotNull(AudioInfo.codecName, dec?.let { if (it.startsWith("c2.android") || it.startsWith("OMX.google")) "software" else "hardware" }).joinToString(" · ").ifBlank { "Starting…" },
            listOfNotNull(dec, AudioInfo.decoderInitMs.takeIf { it > 0 }?.let { "ready in $it ms" }).joinToString(" · ").ifBlank { null },
        ),
        Stage(
            "Processing",
            when { !Settings.normalize -> "Loudness as recorded"; AudioInfo.gainDb < -0.05f -> "Loudness −%.1f dB".format(-AudioInfo.gainDb); else -> "Loudness unchanged" },
            listOfNotNull(if (Settings.skipSilence) "skipping silence" else null, Playback.speed().takeIf { it != 1f }?.let { "speed ${it}×" }).joinToString(" · ").ifBlank { null },
        ),
        Stage(
            "Output",
            listOfNotNull(AudioInfo.outEncoding, AudioInfo.outRate.takeIf { it > 0 }?.let { "${AudioInfo.formatKhz(it)} kHz" }).joinToString(" · ").ifBlank { "—" },
            listOfNotNull(
                AudioInfo.outChannels.takeIf { it > 0 }?.let { if (it == 2) "stereo" else "$it ch" },
                if (AudioInfo.offload) "offloaded to the audio chip (saves battery)" else null,
                AudioInfo.outBufferBytes.takeIf { it > 0 && AudioInfo.outRate > 0 }?.let { b -> "${b * 1000L / (AudioInfo.outRate * 4L)} ms buffer" },
            ).joinToString(" · ").ifBlank { null },
        ),
        Stage("Device", outputName(ctx), if (AudioInfo.underruns > 0) "${AudioInfo.underruns} dropouts" else "no dropouts"),
    )
    val running = Playback.playing
    val flow = if (running) rememberInfiniteTransition(label = "flow").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "flow")
    else remember { mutableFloatStateOf(0f) }
    val line = c.on.copy(alpha = 0.18f); val dot = c.on.copy(alpha = 0.85f)
    Column(
        Modifier.fillMaxWidth().drawBehind {
            // the line through the stage markers (x = 11dp), from the first marker to the last
            val x = 11.dp.toPx(); val top = 22.dp.toPx(); val bottom = size.height - 30.dp.toPx()
            drawLine(line, androidx.compose.ui.geometry.Offset(x, top), androidx.compose.ui.geometry.Offset(x, bottom), 2.dp.toPx())
            if (running) for (k in 0 until 4) {
                val y = top + (bottom - top) * ((flow.value + k / 4f) % 1f)
                drawCircle(dot, 2.5.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
            }
        },
    ) {
        stages.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 14.dp).width(22.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(c.bg).padding(2.dp).clip(CircleShape).background(c.on))
                }
                Gap(0.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f).clip(RoundedCornerShape(if (material) 18.dp else 12.dp)).background(c.chip).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Txt(s.name.uppercase(), pt(Type.caption, 11, 600), color = c.onSecondary)
                    Txt(s.value, pt(Type.body, 15, 500), lines = 2)
                    s.detail?.let { Txt(it, pt(Type.sub, 12, 400), color = c.onSecondary, lines = 3) }
                }
            }
        }
    }
}

private fun sourceName(t: Track, playing: String?): String = when {
    t.source == "local" -> "This phone"
    t.source == "subsonic" -> "Your server"
    t.source == "saavn" -> "JioSaavn"
    playing == null || playing == "yt" -> "YouTube Music"
    playing.startsWith("saavn|") -> "JioSaavn"
    else -> Resolver.copyName(t.ref)?.substringAfter(" · ") ?: "Add-on"
}

/**
 * The device playing: the user's pick in Audio, else the most likely one.
 * shortcut: without a pick it guesses by priority; Android 13's getAudioDevicesForAttributes would be exact.
 */
private fun outputName(ctx: Context): String {
    val list = outputs(ctx.getSystemService(AudioManager::class.java))
    return activeOutput(list)?.let { d -> deviceName(d, list.first { it.first.id == d.id }.second) } ?: "This phone"
}

/** Android's own output picker (Android 10+), else Bluetooth settings. */
private fun openOutputSwitcher(ctx: Context) {
    val panel = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").putExtra("com.android.settings.panel.extra.PACKAGE_NAME", ctx.packageName)
    runCatching { if (Build.VERSION.SDK_INT >= 29) ctx.startActivity(panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else error("old") }
        .recoverCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Output kinds worth playing music through (no earpiece, call audio, or system plumbing), with their label. */
private fun kindOf(d: AudioDeviceInfo): String? = when (d.type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26 /* BLE headset */, 27 /* BLE speaker */ -> "Bluetooth"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line out"
    else -> null
}

private fun deviceName(d: AudioDeviceInfo, kind: String): String = when (kind) {
    "Speaker" -> "This phone"
    "Wired" -> "Wired headphones"
    else -> d.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL } ?: kind
}

/** Connected outputs worth listing, one per kind + name (Bluetooth devices often appear twice). */
private fun outputs(am: AudioManager): List<Pair<AudioDeviceInfo, String>> =
    am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).mapNotNull { d -> kindOf(d)?.let { d to it } }.distinctBy { (d, k) -> k + deviceName(d, k) }

/** Which listed output is playing: the user's pick if still connected, else Android's usual order (Bluetooth > USB > wired > speaker). */
private fun activeOutput(list: List<Pair<AudioDeviceInfo, String>>): AudioDeviceInfo? =
    list.firstOrNull { it.first.id == Playback.outputId }?.first
        ?: listOf("Bluetooth", "USB", "Wired", "HDMI", "Line out", "Speaker").firstNotNullOfOrNull { k -> list.firstOrNull { it.second == k }?.first }

/**
 * Every connected output (phone speaker, wired, USB, Bluetooth…), live as devices come and go; tapping one plays through
 * it, e.g. the phone's speaker while headphones stay connected. "More outputs" opens Android's own picker (casting).
 */
@Composable
private fun Outputs() {
    val c = WT.c
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    var list by remember { mutableStateOf(outputs(am)) }
    DisposableEffect(Unit) {
        val cb = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { list = outputs(am) }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { list = outputs(am) }
        }
        am.registerAudioDeviceCallback(cb, null)
        onDispose { am.unregisterAudioDeviceCallback(cb) }
    }
    val active = activeOutput(list)
    PanelLabel("Play on")
    PanelGroup(list.map { (d, kind) ->
        @Composable {
            val on = d.id == active?.id
            val name = deviceName(d, kind)
            Row(
                Modifier.fillMaxWidth().height(56.dp).press("$name, $kind" + if (on) ", playing here" else "", scale = 0.98f) {
                    // the usual device again = back to Android's own routing
                    Playback.setOutput(if (Playback.outputId < 0 && on) -1 else d.id)
                }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(if (kind == "Speaker") Icons.Phone else Icons.Headphones, if (on) c.on else c.onSecondary, 20.dp)
                Column(Modifier.weight(1f)) {
                    Txt(name, pt(Type.body, 15, if (on) 600 else 500))
                    Txt(kind, pt(Type.sub, 12, 400), color = c.onSecondary)
                }
                if (on) Icon(Icons.Check, c.on, 16.dp)
            }
        }
    } + @Composable {
        Row(
            Modifier.fillMaxWidth().height(48.dp).press("More outputs, including casting", scale = 0.98f) { openOutputSwitcher(ctx) }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Txt("More outputs…", pt(Type.body, 15, 500), Modifier.weight(1f), color = c.onSecondary)
            Icon(Icons.Chevron, c.onTertiary, 14.dp)
        }
    })
}
