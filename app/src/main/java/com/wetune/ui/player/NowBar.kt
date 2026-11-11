package com.wetune.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.wetune.Lyrics
import com.wetune.Playback
import com.wetune.Settings
import com.wetune.rememberPosition
import com.wetune.sources.Track
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.TransformOrigin
import com.wetune.ui.glass.LiquidBackdrop
import com.wetune.ui.glass.LiquidGlassContainer
import com.wetune.ui.glass.LocalLiquid
import com.wetune.ui.glass.liquidGlass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * ND1 "cover ring" now bar: the round cover is play/pause with the progress as an accent ring around it; beside it a
 * frosted capsule (title, artist → [onOpen]) ending in one action that depends on [mode]:
 * "lyrics" / "queue" open a tray of option pills above the bar; anything else is a chevron that opens the player.
 * [haze] null (inside the player, nothing to blur behind) draws a dark translucent wash instead of glass.
 * Swipe the bar sideways to skip.
 * Liquid: the capsule and the tray pills are one sheet of glass, so the pills well up out of the action button and
 * melt back into the bar; the cover disc and the action button are drops of their own.
 */
@Composable
fun NowBar(t: Track, mode: String, onOpen: () -> Unit, modifier: Modifier = Modifier, haze: HazeState? = null, liquid: LiquidBackdrop? = null, onTray: (Boolean) -> Unit = {}) {
    val style = glassStyle()
    val fg = if (haze == null) Color.White else WT.c.text
    val lq = liquid?.takeIf { Settings.glassStyle == "liquid" }
    // every surface here is a capsule, so liquid glass ignores the shape
    if (lq != null) CompositionLocalProvider(LocalLiquid provides lq) {
        LiquidGlassContainer(lq, modifier.fillMaxWidth(), spacing = 12.dp, dark = fg.luminance() > 0.5f) {
            NowBarBody(t, mode, onOpen, Modifier, fg, onTray) { glassShape() }
        }
    } else NowBarBody(t, mode, onOpen, modifier, fg, onTray) { shape ->
        if (haze != null) glass(haze, style, shape) else clip(shape).background(Color(0xFF1E1E22).copy(alpha = 0.8f))
    }
}

@Composable
private fun NowBarBody(t: Track, mode: String, onOpen: () -> Unit, modifier: Modifier, fg: Color, onTray: (Boolean) -> Unit, surface: @Composable Modifier.(Shape) -> Modifier) {
    val liquid = LocalLiquid.current != null
    val options = mode == "lyrics" || mode == "queue"
    var tray by remember(mode) { mutableStateOf(false) } // closes whenever the pane changes
    androidx.compose.runtime.LaunchedEffect(tray && options) { onTray(tray && options) } // the pane keeps its content clear of the tray
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 80.dp.toPx() }

    Column(modifier.fillMaxWidth()) {
        // the tray rises in above the bar; the bar itself never moves
        // liquid: the pills well up out of the action button (bottom right) with a wobble, merging with the bar on the way
        val drop = TransformOrigin(1f, 1f)
        AnimatedVisibility(
            tray && options,
            enter = if (liquid) fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 420f), 0.25f, drop) + slideInVertically(spring(dampingRatio = 0.6f, stiffness = 420f)) { it }
            else fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.86f)) { it / 3 },
            exit = if (liquid) fadeOut(tween(200)) + scaleOut(tween(240), 0.25f, drop) + slideOutVertically(tween(240)) { it }
            else fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 3 },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).horizontalScroll(rememberScrollState()),
                Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), Alignment.CenterVertically,
            ) {
                if (mode == "queue") QueueTray(fg) { Modifier.surface(it) } else LyricsTray(t, fg) { Modifier.surface(it) }
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .graphicsLayer { translationX = swipe.value * 0.6f; alpha = 1f - (kotlin.math.abs(swipe.value) / (limit * 4)).coerceIn(0f, 0.5f) }
                .pointerInput(t.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when { swipe.value < -limit -> Playback.next(); swipe.value > limit -> Playback.prev() }
                            scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.8f)) }
                        },
                        onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                    ) { change, dx -> change.consume(); scope.launch { swipe.snapTo(swipe.value + dx) } }
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Next song") { Playback.next(); true },
                        CustomAccessibilityAction("Previous song") { Playback.prev(); true },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverRing(t, fg)
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).height(56.dp).surface(CircleShape), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .semantics { contentDescription = "${t.title}, ${t.artist}. Open player" }
                        .pressable(scale = 0.98f, onClick = onOpen).padding(start = 18.dp, end = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Txt(t.title, Modifier.shared("title", text = true).clearAndSetSemantics {}, style = Type.bodyStrong.copy(fontSize = 15.sp, lineHeight = 19.sp), color = fg, weight = FontWeight.SemiBold, maxLines = 1)
                    Txt(t.artist, Modifier.shared("artist", text = true).clearAndSetSemantics {}, style = Type.caption.copy(fontSize = 13.sp, lineHeight = 17.sp), color = fg.copy(alpha = 0.68f), maxLines = 1)
                }
                val hasOptions = options && (mode == "queue" || rememberLyrics(t).orEmpty().isNotEmpty())
                if (!options) ActionBtn(Ic.ChevronUp, "Open player", null, fg, onOpen)
                else if (hasOptions) ActionBtn(Ic.Tune, if (mode == "queue") "Playback options" else "Lyrics options", tray, fg) { tray = !tray }
            }
        }
    }
}

/** The cover as a disc inside an accent progress ring, with a soft scrim and the play/pause glyph over it. */
@Composable
private fun CoverRing(t: Track, fg: Color) {
    val pos by rememberPosition()
    val duration = Playback.duration().takeIf { it > 0 } ?: t.durationMs
    val fraction = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val accent by animateColorAsState(NowColors.palette?.hue?.let { hueAccent(it, dark = true) } ?: WT.c.accent, tween(650), label = "ring")
    val playing = Playback.playing
    val track = fg.copy(alpha = 0.16f)
    val view = androidx.compose.ui.platform.LocalView.current
    val lq = LocalLiquid.current
    Box(
        Modifier.shared("play").size(64.dp)
            // liquid: a ring of glass around the disc (the cover covers its middle)
            .then(if (lq != null) Modifier.liquidGlass(lq, dark = fg.luminance() > 0.5f) else Modifier)
            .semantics {
                contentDescription = "${if (playing) "Pause" else "Play"}, ${t.title}, ${formatTime(pos)} of ${formatTime(duration)}"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
            .pressable(scale = 0.92f) { view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); Playback.toggle() },
        Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 2.5.dp.toPx()
            val inset = 2.dp.toPx() // ring hugs the 54 dp cover with a 3 dp gap
            val ring = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), ring, style = Stroke(w))
            if (fraction > 0f) drawArc(accent, -90f, 360f * fraction, false, Offset(inset, inset), ring, style = Stroke(w, cap = StrokeCap.Round))
        }
        Art(t.art, Modifier.size(52.dp), CircleShape)
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.34f)), Alignment.Center) {
            Icon(if (playing) Ic.Pause else Ic.Play, null, size = 18.dp, tint = Color.White)
        }
    }
}

/** The capsule's one action: a 44 dp circle (48 dp target), filled while its tray is open ([open] null: not a toggle). */
@Composable
private fun ActionBtn(icon: ImageVector, label: String, open: Boolean?, fg: Color, onClick: () -> Unit) {
    val on = open == true
    val bg by animateColorAsState(if (on) fg else fg.copy(alpha = 0f), tween(200), label = "action")
    Box(
        Modifier.padding(end = 4.dp).size(48.dp)
            .semantics { contentDescription = label; if (open != null) stateDescription = if (on) "expanded" else "collapsed" }
            .pressable(scale = 0.9f, onClick = onClick),
        Alignment.Center,
    ) {
        // liquid: a drop of glass, brighter (tinted with the text color) while its tray is open
        val lq = LocalLiquid.current
        val face = if (lq != null) Modifier.liquidGlass(lq, tint = if (on) fg.copy(alpha = 0.85f) else null, dark = fg.luminance() > 0.5f)
        else Modifier.clip(CircleShape).background(bg)
        Box(Modifier.size(44.dp).then(face), Alignment.Center) {
            Icon(icon, null, size = 20.dp, tint = if (on) onFill(fg) else fg.copy(alpha = 0.75f))
        }
    }
}

private fun onFill(fg: Color) = if (fg.luminance() > 0.5f) Color(0xFF141414) else Color.White

/**
 * One tray pill: 36 dp visual in a 44 dp target; on = solid [fg] with dark text, off = frosted.
 * [on] null: an action rather than a switch.
 */
@Composable
private fun Pill(icon: ImageVector?, label: String, on: Boolean?, fg: Color, frost: Modifier, desc: String = label, onClick: () -> Unit) {
    val lit = on == true
    Box(
        Modifier.height(44.dp)
            .semantics { contentDescription = desc; if (on != null) toggleableState = ToggleableState(lit) }
            .pressable(scale = 0.94f, role = if (on != null) Role.Switch else Role.Button, onClick = onClick),
        Alignment.Center,
    ) { PillBody(icon, lit, fg, frost) { Txt(label, style = Type.caption.copy(fontSize = 12.sp), color = if (lit) onFill(fg) else fg, weight = FontWeight.SemiBold, maxLines = 1) } }
}

@Composable
private fun PillBody(icon: ImageVector?, lit: Boolean, fg: Color, frost: Modifier, content: @Composable RowScope.() -> Unit) {
    // liquid: always glass; on = a bright inset fill of the text color so the rim still shows around it
    val face = when {
        LocalLiquid.current != null -> if (lit) frost.padding(2.dp).clip(CircleShape).background(fg.copy(alpha = 0.85f)).padding(horizontal = 10.dp) else frost.padding(horizontal = 12.dp)
        lit -> Modifier.clip(CircleShape).background(fg).padding(horizontal = 12.dp)
        else -> frost.padding(horizontal = 12.dp)
    }
    Row(
        Modifier.height(36.dp).then(face),
        Arrangement.spacedBy(6.dp), Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, size = 16.dp, tint = if (lit) onFill(fg) else fg)
        content()
    }
}

/** Queue options: shuffle, repeat (off → all → one) and autoplay (similar songs keep coming when the queue ends). */
@Composable
private fun QueueTray(fg: Color, frost: @Composable (Shape) -> Modifier) {
    val f = frost(CircleShape)
    Pill(Ic.Shuffle, "Shuffle", Playback.shuffle, fg, f) { Playback.toggleShuffle() }
    val rep = Playback.repeat
    val repLabel = when (rep) { Player.REPEAT_MODE_ALL -> "Repeat all"; Player.REPEAT_MODE_ONE -> "Repeat one"; else -> "Repeat" }
    Pill(if (rep == Player.REPEAT_MODE_ONE) Ic.RepeatOne else Ic.Repeat, repLabel, rep != Player.REPEAT_MODE_OFF, fg, f, desc = if (rep == Player.REPEAT_MODE_OFF) "Repeat off" else repLabel) { Playback.cycleRepeat() }
    Pill(Ic.Infinite, "Autoplay", Settings.autoplay, fg, f, desc = "Autoplay: similar songs keep coming") { Settings.autoplay = !Settings.autoplay }
}

/**
 * Lyrics options: the source (provider name, with an icon for how it's timed: karaoke = word by word, dotted lines =
 * by line, speech bubble = not synced), the timing offset stepper, and Latin letters when the song can have them.
 */
@Composable
private fun LyricsTray(t: Track, fg: Color, frost: @Composable (Shape) -> Modifier) {
    val f = frost(CircleShape)
    val versions = rememberVersions(t).orEmpty()
    val chosen = Lyrics.choice[t.id] ?: 0
    var picking by remember { mutableStateOf(false) }
    versions.getOrNull(chosen)?.let { v ->
        val (icon, timing) = lyricsKind(v)
        val more = versions.size > 1
        Box {
            Box(
                Modifier.height(44.dp)
                    .semantics { contentDescription = "Lyrics source: ${v.source}, ${timing.lowercase()}" + if (more) ". Choose another" else "" }
                    .pressable(enabled = more, scale = 0.94f) { picking = true },
                Alignment.Center,
            ) {
                PillBody(icon, false, fg, f) {
                    Txt(v.source, style = Type.caption.copy(fontSize = 12.sp), color = fg, weight = FontWeight.SemiBold, maxLines = 1)
                    if (more) Icon(Ic.Down, null, size = 16.dp, tint = fg.copy(alpha = 0.7f))
                }
            }
            Menu(picking, { picking = false }, title = "Lyrics versions") {
                versions.forEachIndexed { i, it -> LyricsVersionRow(it, i == chosen) { Lyrics.choice[t.id] = i; picking = false } }
            }
        }
    }
    val lines = rememberLyrics(t).orEmpty()
    if (lines.firstOrNull()?.let { it.ms >= 0 } == true) {
        // timing offset: + shows the words earlier, − later (half a second a step, remembered per song)
        val off = Lyrics.offset(t.id)
        val shown = (if (off > 0) "+" else if (off < 0) "−" else "") + "%.1f s".format(kotlin.math.abs(off) / 1000f)
        PillBody(null, false, fg, f) {
            Icon(Ic.Clock, null, size = 16.dp, tint = fg)
            Step(Ic.Minus, "Lyrics later by half a second", fg) { Lyrics.setOffset(t.id, off - 500) }
            Txt(shown, Modifier.semantics { contentDescription = "Lyrics timing $shown" }, style = Type.caption.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"), color = fg, weight = FontWeight.SemiBold, maxLines = 1)
            Step(Ic.Add, "Lyrics earlier by half a second", fg) { Lyrics.setOffset(t.id, off + 500) }
        }
    }
    val canRomanize = remember(lines) { lines.any { romanize(it.text) != null } }
    if (canRomanize) Pill(Ic.Latin, "Romanize", Settings.lyricsRoman, fg, f, desc = "Show lyrics in Latin letters") { Settings.lyricsRoman = !Settings.lyricsRoman }
}

/** A − / + inside the offset pill; the 36 dp pill keeps it a 36 dp target, with the pill's 44 dp row around it. */
@Composable
private fun Step(icon: ImageVector, desc: String, fg: Color, onClick: () -> Unit) =
    Box(Modifier.size(36.dp).semantics { contentDescription = desc }.pressable(scale = 0.85f, onClick = onClick), Alignment.Center) {
        Icon(icon, null, size = 16.dp, tint = fg)
    }
