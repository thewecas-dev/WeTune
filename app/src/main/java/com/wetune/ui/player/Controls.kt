package com.wetune.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.wetune.AudioInfo
import com.wetune.Library
import com.wetune.Playback
import com.wetune.Settings
import com.wetune.rememberPosition
import com.wetune.sources.Track
import com.wetune.ui.Ic
import com.wetune.ui.Icon
import com.wetune.ui.Icons
import com.wetune.ui.MaterialIcons
import com.wetune.ui.Outfit
import com.wetune.ui.Txt
import com.wetune.ui.Type
import com.wetune.ui.WT
import com.wetune.ui.press
import com.wetune.ui.type
import kotlin.math.PI
import kotlin.math.sin

/**
 * Now Playing's two styles (spec §9 `PlayerStyle`): only these leaf controls switch; layout, colours and motion are
 * shared. Clean (apple) is the default; Expressive (material) is chosen in Settings › Now Playing › Style.
 */
val material: Boolean get() = Settings.playerStyle == "material"

/** Apple-like text style, or its Material (Outfit) counterpart. */
fun pt(apple: TextStyle, matSize: Int, matWeight: Int): TextStyle =
    if (material) type(matSize.sp, matWeight, family = Outfit, tracking = 0.sp) else apple

fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun TitleBlock(t: Track, modifier: Modifier = Modifier, small: Boolean = false, onArtist: (() -> Unit)? = null) {
    val c = WT.c
    Column(modifier) {
        Txt(t.title, if (small) pt(Type.row.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), 17, 600) else pt(Type.title, 24, 600))
        Txt(
            t.artist, pt(Type.body, 15, 400),
            if (onArtist != null) Modifier.press("Go to ${t.artist}", scale = 0.98f, onClick = onArtist) else Modifier,
            color = c.onSecondary,
        )
    }
}

@Composable
fun LikeButton(t: Track) {
    val c = WT.c
    val liked = Library.isLiked(t)
    val label = if (liked) "Liked. Remove from liked songs" else "Like"
    if (material) Box(Modifier.size(48.dp).clip(CircleShape).background(c.chip).press(label, scale = 0.9f) { Library.toggleLike(t) }, contentAlignment = Alignment.Center) {
        Icon(if (liked) MaterialIcons.Heart else MaterialIcons.HeartOutline, if (liked) c.liked else c.on, 22.dp)
    } else Box(Modifier.size(44.dp).press(label, scale = 0.88f) { Library.toggleLike(t) }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).background(c.chip, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (liked) Icons.Heart else Icons.HeartOutline, if (liked) c.liked else c.on, 17.dp)
        }
    }
}

/** Small round header button (full screen / exit / more). */
@Composable
fun RoundBtn(apple: Ic, mat: Ic, label: String, onClick: () -> Unit) {
    val c = WT.c
    Box(Modifier.size(if (material) 48.dp else 44.dp).press(label, scale = 0.88f, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (material) 48.dp else 32.dp).background(c.chip, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (material) mat else apple, c.on, if (material) 20.dp else 14.dp)
        }
    }
}

/**
 * Seek bar + elapsed / −remaining (+ quality badge). Owns the position polling so only this leaf redraws 4×/s; the
 * fill is drawn in the draw phase. Drag scrubs locally and seeks on release.
 */
@Composable
fun SeekBar(badge: Boolean, modifier: Modifier = Modifier, previewFrac: Float? = null) {
    val c = WT.c
    val pos = rememberPosition()
    var scrub by remember { mutableStateOf<Float?>(null) }
    val fracState = remember { derivedStateOf { previewFrac ?: scrub ?: Playback.knownDuration.let { d -> if (d > 0) (pos.value.toFloat() / d).coerceIn(0f, 1f) else 0f } } }
    val frac by fracState
    val mat = material
    Column(modifier) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(24.dp)
                .semantics {
                    contentDescription = "Song position"
                    progressBarRangeInfo = ProgressBarRangeInfo(frac, 0f..1f)
                    setProgress { v -> Playback.seek((v * Playback.duration()).toLong()); true }
                }
                .pointerInput(Unit) {
                    detectTapGestures { o -> Playback.seek((o.x / size.width * Playback.duration()).toLong()) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> scrub = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { scrub?.let { Playback.seek((it * Playback.duration()).toLong()) }; scrub = null },
                        onDragCancel = { scrub = null },
                        onHorizontalDrag = { ch, _ -> ch.consume(); scrub = (ch.position.x / size.width).coerceIn(0f, 1f) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val dragging = scrub != null
            // the bar in the cover's colour when chosen (Settings › Now Playing › Seek bar colour); its times stay neutral
            CompositionLocalProvider(com.wetune.ui.LocalColors provides seekColors(c)) {
                val c = WT.c
                when (seekStyle()) {
                    "thin" -> ThinSeek(fracState, dragging)
                    "squiggly" -> WavySeek(fracState, dragging, wavelength = 20.dp, amplitude = 2.6.dp)
                    "wavy" -> WavySeek(fracState, dragging, wavelength = 56.dp, amplitude = 4.dp)
                    "waves" -> WavesSeek(fracState, dragging)
                    else -> Box(
                        Modifier.fillMaxWidth().height(if (dragging) 11.dp else 7.dp).drawBehind {
                            val r = CornerRadius(size.height / 2)
                            drawRoundRect(c.on.copy(alpha = 0.18f), cornerRadius = r)
                            drawRoundRect(c.on.copy(alpha = 0.88f), size = Size(size.width * fracState.value, size.height), cornerRadius = r)
                        },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val d = Playback.duration()
            val shownMs by remember { derivedStateOf { (frac * Playback.knownDuration).toLong() / 1000 * 1000 } } // recompose once a second
            val cap = pt(Type.caption, 12, 500).copy(fontFeatureSettings = "tnum")
            Txt(fmt(shownMs), cap, color = c.onSecondary)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val label = AudioInfo.label
                if (badge && label != null) Txt(
                    qualityBadge(label), cap.copy(fontSize = 10.sp),
                    Modifier.clip(RoundedCornerShape(4.dp)).background(c.chip).padding(horizontal = 6.dp, vertical = 1.dp),
                    color = c.onSecondary,
                )
            }
            Txt("−" + fmt((d - shownMs).coerceAtLeast(0)), cap.copy(textAlign = TextAlign.End), color = c.onSecondary)
        }
    }
}

/** HI-RES / LOSSLESS / HQ (256 kbps and up) / SQ from the playing stream's label. */
fun qualityBadge(label: String): String {
    val l = label.lowercase()
    val bits = Regex("""(\d+)-bit""").find(l)?.groupValues?.get(1)?.toIntOrNull()
    if ("flac" in l || bits != null) return if ((bits ?: 16) > 16 || (Regex("""([\d.]+) khz""").find(l)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f) > 48f) "HI-RES" else "LOSSLESS"
    val kbps = Regex("""(\d+) kbps""").find(l)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return if (kbps >= 256) "HQ" else "SQ"
}

/** The seek bar look: the user's choice, or the player style's own (capsule for Apple-like, squiggle for Material). */
fun seekStyle(): String = Settings.seekStyle.takeIf { it != "auto" } ?: if (material) "squiggly" else "thick"

/** 0→1 repeating while [running]; a still 0 otherwise, so nothing ticks while paused. */
@Composable
private fun ticker(running: Boolean, periodMs: Int): androidx.compose.runtime.State<Float> =
    if (running) rememberInfiniteTransition(label = "phase").animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    else remember { mutableFloatStateOf(0f) }

/** Thin line with a round thumb. */
@Composable
private fun ThinSeek(frac: androidx.compose.runtime.State<Float>, dragging: Boolean) {
    val c = WT.c
    Box(Modifier.fillMaxWidth().height(16.dp).drawBehind {
        val cy = size.height / 2; val x = size.width * frac.value; val s = 3.dp.toPx()
        drawLine(c.on.copy(alpha = 0.2f), Offset(x, cy), Offset(size.width, cy), s, StrokeCap.Round)
        drawLine(c.on, Offset(0f, cy), Offset(x, cy), s, StrokeCap.Round)
        drawCircle(c.on, (if (dragging) 8.dp else 6.dp).toPx(), Offset(x, cy))
    })
}

private val WAVE_AMP = floatArrayOf(7f, 9.5f, 5f) // dp
private val WAVE_LEN = floatArrayOf(70f, 110f, 46f) // dp
private val WAVE_SPEED = floatArrayOf(1f, -1f, 2f)

/** Several translucent waves layered over the played part, swelling in the middle and meeting the line at both ends, and a haloed thumb. */
@Composable
private fun WavesSeek(frac: androidx.compose.runtime.State<Float>, dragging: Boolean) {
    val c = WT.c
    val playing = Playback.playing && !dragging
    val phase = ticker(playing, 3200)
    val paths = remember { List(3) { Path() } }
    Box(Modifier.fillMaxWidth().height(24.dp).drawBehind {
        val w = size.width; val cy = size.height / 2; val x = w * frac.value; val base = 3.dp.toPx()
        drawLine(c.on.copy(alpha = 0.2f), Offset(x, cy), Offset(w, cy), base, StrokeCap.Round)
        if (x > 1f) for (k in 0..2) {
            val path = paths[k]; val amp = WAVE_AMP[k].dp.toPx(); val wl = WAVE_LEN[k].dp.toPx()
            path.reset(); path.moveTo(0f, cy)
            var px = 0f
            while (px <= x) {
                val env = sin(PI * px / x).toFloat()
                val s = 0.5f + 0.5f * sin((px / wl + phase.value * WAVE_SPEED[k]) * 2 * PI).toFloat()
                path.lineTo(px, cy - amp * env * s)
                px += 3f
            }
            drawPath(path, if (k == 1) c.onSecondary.copy(alpha = 0.55f) else c.on.copy(alpha = if (k == 0) 0.55f else 0.35f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        drawLine(c.on, Offset(0f, cy), Offset(x, cy), base, StrokeCap.Round)
        drawCircle(c.on.copy(alpha = 0.25f), (if (dragging) 13.dp else 11.dp).toPx(), Offset(x, cy))
        drawCircle(c.on, 7.dp.toPx(), Offset(x, cy))
    })
}

/** Wavy played part (moving while playing), flat remaining part, 4×16 handle. Short [wavelength] = squiggle, long = gentle wave. */
@Composable
private fun WavySeek(frac: androidx.compose.runtime.State<Float>, dragging: Boolean, wavelength: Dp, amplitude: Dp) {
    val c = WT.c
    val playing = Playback.playing && !dragging
    val phase = ticker(playing, 1400)
    val path = remember { Path() }
    Box(Modifier.fillMaxWidth().height(16.dp).drawBehind {
        val w = size.width; val cy = size.height / 2; val x = w * frac.value
        val stroke = 3.5.dp.toPx(); val gap = 6.dp.toPx()
        val amp = if (playing) amplitude.toPx() else 0f
        val wl = wavelength.toPx()
        path.reset()
        path.moveTo(0f, cy)
        var px = 0f
        val end = (x - gap).coerceAtLeast(0f)
        while (px <= end) {
            path.lineTo(px, cy + amp * sin((px / wl + phase.value) * 2 * PI).toFloat())
            px += 2f
        }
        drawPath(path, c.on, style = Stroke(stroke, cap = StrokeCap.Round))
        if (x + gap < w) drawLine(c.on.copy(alpha = 0.22f), Offset(x + gap, cy), Offset(w, cy), stroke, StrokeCap.Round)
        drawRoundRect(c.on, Offset(x - 2.dp.toPx(), cy - 8.dp.toPx()), Size(4.dp.toPx(), 16.dp.toPx()), CornerRadius(2.dp.toPx()))
    })
}

/**
 * Shuffle · prev · play · next · repeat. In full screen ([sides] → 1) shuffle and repeat fade out and the Lyrics and
 * Queue buttons fly into their places (NowPlayingLayer positions them), so the row itself never changes shape.
 */
@Composable
fun Transport(modifier: Modifier = Modifier, sides: () -> Float = { 0f }) {
    val c = WT.c
    val mat = material
    val playing = Playback.playing
    val fadeSides = Modifier.graphicsLayer { alpha = 1f - sides() }
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Shuffle and Repeat can be hidden (Settings › Now Playing); their space stays, so play keeps its place and the
        // Lyrics / Queue buttons still land there in full screen. They remain in the queue's pills either way.
        val sides = Settings.npShuffleRepeat
        val (sw, sh) = sideButtonSize
        if (sides) Box(fadeSides) { ModeButton(if (mat) MaterialIcons.Shuffle else Icons.Shuffle, "Shuffle", Playback.shuffle) { Playback.toggleShuffle() } }
        else Spacer(Modifier.size(sw, sh))
        if (mat) {
            Tonal(MaterialIcons.Prev, "Previous") { Playback.prev() }
            Box(
                Modifier.size(96.dp, 66.dp).clip(RoundedCornerShape(24.dp)).background(c.primary).press(if (playing) "Pause" else "Play", scale = 0.94f) { Playback.toggle() },
                contentAlignment = Alignment.Center,
            ) { Icon(if (playing) MaterialIcons.Pause else MaterialIcons.Play, c.onPrimary, 34.dp) }
            Tonal(MaterialIcons.Next, "Next") { Playback.next() }
        } else {
            Glyph(Icons.Prev, "Previous", 60.dp, 38.dp) { Playback.prev() }
            Glyph(if (playing) Icons.Pause else Icons.Play, if (playing) "Pause" else "Play", 64.dp, 42.dp) { Playback.toggle() }
            Glyph(Icons.Next, "Next", 60.dp, 38.dp) { Playback.next() }
        }
        val mode = Playback.repeat
        val ic = when { mode == Player.REPEAT_MODE_ONE -> if (mat) MaterialIcons.RepeatOne else Icons.RepeatOne; mat -> MaterialIcons.Repeat; else -> Icons.Repeat }
        if (sides) Box(fadeSides) {
            ModeButton(ic, when (mode) { Player.REPEAT_MODE_ONE -> "Repeat one"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat" }, mode != Player.REPEAT_MODE_OFF) { Playback.cycleRepeat() }
        } else Spacer(Modifier.size(sw, sh))
    }
}

/** Width × height of shuffle / repeat: where Lyrics and Queue land in full screen. */
val sideButtonSize: Pair<Dp, Dp> get() = if (material) 48.dp to 48.dp else 40.dp to 44.dp
/** Width (Apple; Material segments share the row) × height of a Lyrics · Audio · Queue button. */
val actionButtonSize: Pair<Dp, Dp> get() = if (material) 0.dp to 48.dp else 56.dp to 44.dp

@Composable
private fun Glyph(ic: Ic, label: String, box: Dp, size: Dp, onClick: () -> Unit) =
    Box(Modifier.size(box).press(label, scale = 0.86f, onClick = onClick), contentAlignment = Alignment.Center) { Icon(ic, WT.c.on, size) }

@Composable
private fun Tonal(ic: Ic, label: String, onClick: () -> Unit) =
    Box(Modifier.size(56.dp).clip(CircleShape).background(WT.c.chip).press(label, scale = 0.9f, onClick = onClick), contentAlignment = Alignment.Center) { Icon(ic, WT.c.on, 28.dp) }

@Composable
private fun ModeButton(ic: Ic, label: String, on: Boolean, onClick: () -> Unit) {
    val c = WT.c
    Box(
        Modifier.size(if (material) 48.dp else 40.dp, 44.dp).press(label, scale = 0.88f, onClick = onClick)
            .semantics { stateDescription = if (on) "On" else "Off" },
        contentAlignment = Alignment.Center,
    ) { Icon(ic, if (on) c.on else c.onSecondary, if (material) 22.dp else 21.dp) }
}

/**
 * Lyrics · Audio · Queue, icons only (spec §6). Only Audio is drawn here: Lyrics and Queue are [PanelButton]s the
 * layer positions over the empty first and last slots, so they can fly to the transport row in full screen.
 */
@Composable
fun ActionRow(modifier: Modifier = Modifier) {
    val c = WT.c
    val on = NowPlaying.panel == NowPlaying.Panel.Audio
    val label = "Audio: volume, output and quality" + if (on) ", open" else ""
    if (material) Row(modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(if (on) 24.dp else 6.dp)).background(if (on) c.primary else c.chip)
                .press(label, scale = 0.95f) { NowPlaying.tapAudio() },
            contentAlignment = Alignment.Center,
        ) { Icon(MaterialIcons.Audio, if (on) c.onPrimary else c.on, 22.dp) }
        Spacer(Modifier.weight(1f))
    } else Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        Spacer(Modifier.size(56.dp, 44.dp))
        Box(
            Modifier.size(56.dp, 44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.on.copy(alpha = 0.14f) else Color.Transparent)
                .press(label, scale = 0.9f) { NowPlaying.tapAudio() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Audio, if (on) c.on else c.onSecondary, 23.dp) }
        Spacer(Modifier.size(56.dp, 44.dp))
    }
}

/**
 * The Lyrics or Queue button. The layer sizes and places it: in the Lyrics · Audio · Queue row normally, beside
 * prev / play / next in full screen ([full] = how far along). [lyrics] = which one.
 */
@Composable
fun PanelButton(lyrics: Boolean, full: () -> Float) {
    val c = WT.c
    val mat = material
    val panel = if (lyrics) NowPlaying.Panel.Lyrics else NowPlaying.Panel.Queue
    val on = NowPlaying.panel == panel
    val label = when {
        lyrics && on -> "Lyrics, open. Close lyrics"
        lyrics -> "Lyrics"
        on -> "Queue, open. Close queue"
        else -> "Queue"
    }
    val ic = if (lyrics) (if (mat) MaterialIcons.Lyrics else Icons.Lyrics) else (if (mat) MaterialIcons.Queue else Icons.Queue)
    val bg = if (mat) (if (on) c.primary else c.chip) else (if (on) c.on.copy(alpha = 0.14f) else Color.Transparent)
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val f = full()
                // Material: the group's outer end stays round, the inner end rounds off as it leaves the group
                val inner = if (mat && !on) lerp(6.dp.toPx(), 24.dp.toPx(), f) else if (mat) 24.dp.toPx() else 14.dp.toPx()
                val outer = if (mat) 24.dp.toPx() else 14.dp.toPx()
                shape = if (lyrics) RoundedCornerShape(outer, inner, inner, outer) else RoundedCornerShape(inner, outer, outer, inner)
                clip = true
            }
            .drawBehind { drawRect(bg) }
            .press(label, scale = 0.92f) { if (lyrics) NowPlaying.tapLyrics() else NowPlaying.tapQueue() },
        contentAlignment = Alignment.Center,
    ) { Icon(ic, if (mat && on) c.onPrimary else if (on || mat) c.on else c.onSecondary, if (mat) 22.dp else 23.dp) }
}

/** Shuffle / Repeat / Automix as in R8-A5 (equal segments) and R8-M5 (outlined chips); selected = solid white, black text. */
@Composable
fun QueuePills(modifier: Modifier = Modifier) {
    val repeatOne = Playback.repeat == Player.REPEAT_MODE_ONE
    val items = listOf(
        Triple(if (material) MaterialIcons.Shuffle else Icons.Shuffle, "Shuffle", Playback.shuffle),
        Triple(if (repeatOne) (if (material) MaterialIcons.RepeatOne else Icons.RepeatOne) else (if (material) MaterialIcons.Repeat else Icons.Repeat), if (repeatOne) "Repeat one" else "Repeat", Playback.repeat != Player.REPEAT_MODE_OFF),
        Triple(if (material) MaterialIcons.Automix else Icons.Automix, "Automix", Settings.autoplay),
    )
    val actions = listOf({ Playback.toggleShuffle() }, { Playback.cycleRepeat() }, { Settings.autoplay = !Settings.autoplay })
    val c = WT.c
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, (ic, text, on) ->
            val (offBg, fg) = pillColors(on, c)
            val shape = RoundedCornerShape(10.dp)
            Row(
                // one layout for both styles: equal thirds, filled (Material only keeps its own icons)
                Modifier.weight(1f).height(36.dp).clip(shape)
                    .background(if (on) Color.White else offBg)
                    .press(text, scale = 0.95f, onClick = { actions[i]() })
                    .semantics { stateDescription = if (on) "On" else "Off" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                Icon(ic, fg, 16.dp)
                Txt(text, pt(Type.sub.copy(fontWeight = FontWeight.SemiBold), 13, 600), color = fg)
            }
        }
    }
}

/** A list group in the Audio and Queue panels: Apple = one inset group with separators; Material = stepped cards. */
@Composable
fun PanelGroup(rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    val c = WT.c
    if (material) Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, row ->
            val top = if (i == 0) 22.dp else 6.dp; val bottom = if (i == rows.lastIndex) 22.dp else 6.dp
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(top, top, bottom, bottom)).background(c.chip)) { row() }
        }
    } else Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.chip)) {
        rows.forEachIndexed { i, row ->
            row()
            if (i < rows.lastIndex) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(c.separator))
        }
    }
}

@Composable
fun PanelLabel(text: String) = Txt(text.uppercase(), pt(Type.caption, 12, 600), Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp), color = WT.c.onSecondary)

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

/**
 * The seek bar's colours: the theme's, or with "cover colour" chosen, the cover's accent tone, lifted (or deepened on a
 * light card) until it stands out from what's behind it.
 */
@Composable
fun seekColors(c: com.wetune.ui.WeColors): com.wetune.ui.WeColors {
    if (Settings.seekColor != "cover") return c
    val accent = coverTones(coverColors(Playback.current?.art))?.first ?: return c
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
    val onDark = c.on.luminance() > 0.5f // light text = a dark surface behind the bar
    hsv[1] = hsv[1].coerceAtLeast(0.45f)
    hsv[2] = if (onDark) hsv[2].coerceAtLeast(0.9f) else hsv[2].coerceAtMost(0.55f)
    return c.copy(on = Color(android.graphics.Color.HSVToColor(hsv)))
}
