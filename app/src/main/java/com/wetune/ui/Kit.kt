package com.wetune.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wetune.Playback
import com.wetune.sources.Card
import com.wetune.sources.Track
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// ---- frost (spec §1: real blur, neutral tint, no rim, no shadow) -------------------------------------------------

/** The blurred page behind every bar, mini player and sheet. Set once at the root. */
val LocalHaze = compositionLocalOf { HazeState() }

val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/*
 * Surfaces: ONE style for everything outside Now Playing, from one settings group (Settings › Surfaces): Tint, Blur,
 * Noise. Floating surfaces (dock, mini player, sheets, menus, round ‹ ⋯ buttons) use [frost]: real blur of what's
 * behind. Surfaces that sit on the page (list sheets, cards, buttons, chips) use [glass]: the same tint and grain;
 * nothing moves behind those, so there's nothing for a blur to show. Now Playing's card keeps its own settings.
 */

@Composable
fun frostStyle(): HazeStyle {
    val c = WT.c
    // how much of the theme's frost tint (0..100 %, 62 = the original), how much blur, and how much grain (it reads
    // as frosted glass rather than a plain blur)
    val tint = com.wetune.Settings.glassTint.coerceIn(0, 100) / 100f
    val blur = com.wetune.Settings.glassBlur.coerceIn(0, 60)
    val noise = 0.06f * com.wetune.Settings.glassNoise.coerceIn(0, 100) / 50f
    return remember(c, tint, blur, noise) {
        HazeStyle(backgroundColor = c.bg, tint = HazeTint(c.frost.copy(alpha = tint)), blurRadius = blur.dp, noiseFactor = noise, fallbackTint = HazeTint(c.frostSolid))
    }
}

/**
 * Glass for surfaces that sit on the page itself (list sheets, search box, grouped lists, cards, buttons, chips): a
 * translucent fill that lets the page's colour wash show through, with the same grain as the frost. Follows Surfaces'
 * Tint and Noise.
 */
@Composable
fun Modifier.glass(shape: Shape): Modifier {
    val fill = glassFill()
    val alpha = grainAlpha()
    val noise = remember { grainBrush() }
    return clip(shape).drawBehind { drawRect(fill); drawRect(noise, alpha = alpha) }
}

/** The grain's strength on [glass] surfaces (Surfaces › Noise; 50 % = the original look). */
@Composable
fun grainAlpha(): Float = ((if (WT.c.dark) 0.5f else 0.3f) * com.wetune.Settings.glassNoise.coerceIn(0, 100) / 50f).coerceAtMost(1f)

/** [glass]'s fill, for surfaces that draw their own shape (the album page's list sheet). */
@Composable
fun glassFill(): Color {
    val t = com.wetune.Settings.glassTint.coerceIn(0, 100) / 100f
    return if (WT.c.dark) Color.White.copy(alpha = 0.03f + 0.1f * t) else Color.White.copy(alpha = 0.3f + 0.6f * t)
}

fun grainBrush() = androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(grain, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated))

/**
 * A frosted surface: what lies beneath shows through, blurred. Falls back to a near-solid fill without blur.
 * Pages are blur source 0 and Now Playing is source 1: bars ([overPlayer] false) blur only the pages; sheets and
 * menus ([overPlayer] true) blur Now Playing too when they open over it.
 */
@Composable
fun Modifier.frost(shape: Shape, overPlayer: Boolean = false): Modifier =
    clip(shape).hazeEffect(LocalHaze.current, frostStyle()) { if (!overPlayer) canDrawArea = { it.zIndex < 1f } }

// ---- press feedback ------------------------------------------------------------------------------------------------

/** Click with a small scale-down while pressed (draw-phase only, no recomposition of the content). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.press(label: String? = null, onLongClick: (() -> Unit)? = null, scale: Float = 0.96f, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scale else 1f, Motion.small(), label = "press")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .combinedClickable(source, indication = null, role = Role.Button, onClickLabel = label, onLongClick = onLongClick, onClick = onClick)
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

// ---- small pieces --------------------------------------------------------------------------------------------------

@Composable
fun Txt(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = WT.c.on, lines: Int = 1) =
    BasicText(text, modifier, style.copy(color = color), overflow = TextOverflow.Ellipsis, maxLines = lines, softWrap = lines > 1)

/** Icon-only button, 44dp target. [circle] puts it on the translucent round chip used in Now Playing headers. */
@Composable
fun IconBtn(ic: Ic, label: String, modifier: Modifier = Modifier, tint: Color = WT.c.on, size: Dp = 22.dp, circle: Boolean = false, frosted: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).press(label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (circle) Box(Modifier.size(32.dp).then(if (frosted) Modifier.frost(CircleShape) else Modifier.glass(CircleShape)), contentAlignment = Alignment.Center) { Icon(ic, tint, size) }
        else Icon(ic, tint, size)
    }
}

/** Cover art on a neutral placeholder of the final size, so nothing jumps when it loads (it fades in). */
@Composable
fun Cover(url: String?, size: Dp, radius: Dp, modifier: Modifier = Modifier, description: String? = null) {
    val shape = if (radius >= size / 2) CircleShape else RoundedCornerShape(radius)
    AsyncImage(
        model = url, contentDescription = description, contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(shape).background(WT.c.surface),
    )
}

/** Pill toggle: selected = solid primary with inverted content (spec §2). */
@Composable
fun Pill(text: String, selected: Boolean, ic: Ic? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = WT.c
    val fg = if (selected) c.onPrimary else c.on
    Row(
        modifier.height(36.dp).then(if (selected) Modifier.clip(CircleShape).background(c.primary) else Modifier.glass(CircleShape)).press(text, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (ic != null) Icon(ic, fg, 16.dp)
        Txt(text, Type.button.copy(fontSize = Type.sub.fontSize), color = fg)
    }
}

/** Large page button (Play / Shuffle on album pages, Sign in). */
@Composable
fun BigButton(text: String, ic: Ic?, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = WT.c
    val fg = if (primary) c.onPrimary else c.on
    Row(
        modifier.height(48.dp).then(if (primary) Modifier.clip(RoundedCornerShape(12.dp)).background(c.primary) else Modifier.glass(RoundedCornerShape(12.dp))).press(text, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (ic != null) { Icon(ic, fg, 18.dp); Spacer(Modifier.width(8.dp)) }
        Txt(text, Type.button, color = fg)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, strap: String = "", onMore: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)
            .then(if (onMore != null) Modifier.press("$title, see all", onClick = onMore) else Modifier),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // [strap]: the small line some shelves carry above their title ("SIMILAR TO")
        Column(Modifier.weight(1f, fill = false)) {
            if (strap.isNotEmpty()) Txt(strap, Type.caption, color = WT.c.onSecondary)
            // the arrow sits in the title's own line, centred on it (not on the strap, not on the row's bottom edge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt(title, Type.section, Modifier.weight(1f, fill = false))
                if (onMore != null) Icon(Icons.Chevron, WT.c.onSecondary, 16.dp)
            }
        }
    }
}

/**
 * A song row: 48dp art, title, artist; the playing one shows a small waveform instead of the title colour change.
 * [swipe]: slide right to add it to the queue, left to play it next (off in rows that sit in a sideways-scrolling shelf).
 */
@Composable
fun TrackRow(t: Track, modifier: Modifier = Modifier, index: Int? = null, swipe: Boolean = true, ring: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val c = WT.c
    val current = Playback.current?.id == t.id
    val dx = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val reach = with(androidx.compose.ui.platform.LocalDensity.current) { 88.dp.toPx() } // how far a slide must go to count
    Box(modifier.fillMaxWidth().height(58.dp)) {
        if (swipe) {
            // behind the row, only as wide as it has slid: the action, filled in once letting go would do it
            SwipeAction(Icons.Queue, "Queue", Alignment.CenterStart, { dx.value }, reach)
            SwipeAction(Icons.PlayNext, "Play next", Alignment.CenterEnd, { -dx.value }, reach)
        }
        TrackRowContent(t, current, index, ring, trailing, onClick, Modifier.graphicsLayer { translationX = dx.value }.then(
            if (!swipe) Modifier else Modifier.pointerInput(t.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val v = dx.value
                        if (v >= reach) Playback.enqueue(t) else if (v <= -reach) Playback.playNext(t)
                        scope.launch { dx.animateTo(0f, Motion.small()) }
                    },
                    onDragCancel = { scope.launch { dx.animateTo(0f, Motion.small()) } },
                ) { ch, d ->
                    ch.consume()
                    val before = dx.value
                    // past the threshold it drags with resistance, and stops at 1.5× it
                    val next = (before + if (kotlin.math.abs(before) > reach) d * 0.35f else d).coerceIn(-reach * 1.5f, reach * 1.5f)
                    scope.launch { dx.snapTo(next) }
                    if ((kotlin.math.abs(before) < reach) != (kotlin.math.abs(next) < reach)) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                }
            },
        ))
    }
}

/**
 * What a slide on a song row does, revealed behind it from [align]'s edge as far as [amount] px, on the Like button's
 * chip fill; once ≥ [reach] (letting go would do it) its icon and label go from dim to full strength and the icon grows.
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.SwipeAction(ic: Ic, label: String, align: Alignment, amount: () -> Float, reach: Float) {
    val c = WT.c
    val on by remember { androidx.compose.runtime.derivedStateOf { amount() >= reach } }
    val armed by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, Motion.small(), label = "swipe")
    Box(
        Modifier.align(align).fillMaxHeight().padding(vertical = 5.dp)
            .layout { m, cs ->
                val w = amount().coerceAtLeast(0f).toInt()
                val p = m.measure(Constraints.fixed(w, cs.maxHeight))
                layout(w, cs.maxHeight) { p.place(0, 0) }
            }
            .drawBehind { drawRoundRect(c.chip, cornerRadius = CornerRadius(12.dp.toPx())) }
            .graphicsLayer { clip = true; shape = RoundedCornerShape(12.dp) },
        contentAlignment = if (align == Alignment.CenterStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp).graphicsLayer { alpha = (amount() / reach).coerceIn(0f, 1f) * (0.5f + 0.5f * armed) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.graphicsLayer { val s = 1f + 0.15f * armed; scaleX = s; scaleY = s }) { Icon(ic, c.on, 18.dp) }
            Txt(label, Type.sub, color = c.on)
        }
    }
}

@Composable
private fun TrackRowContent(t: Track, current: Boolean, index: Int?, ring: Boolean, trailing: (@Composable () -> Unit)?, onClick: () -> Unit, modifier: Modifier) {
    val c = WT.c
    Row(
        modifier.fillMaxWidth().height(58.dp).press(onLongClick = { TrackMenu.show(t) }, scale = 0.985f, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (index != null) Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (current) { if (ring) ProgressRing(24.dp, c.on); Icon(Icons.Audio, c.on, if (ring) 12.dp else 16.dp) }
            else Txt("$index", Type.sub, color = c.onSecondary)
        } else Box(contentAlignment = Alignment.Center) {
            // on album / playlist pages (ring) a video keeps its 16:9 frame (64×36) instead of a square crop; its title sits 16dp further in
            val w = if (ring && com.wetune.youtube.isVideoArt(t.art)) 64.dp else 48.dp
            val h = if (w > 48.dp) 36.dp else 48.dp
            AsyncImage(t.art, null, Modifier.size(w, h).clip(RoundedCornerShape(6.dp)).background(c.surface), contentScale = ContentScale.Crop)
            if (current) Box(Modifier.size(w, h).background(Color(0x66000000), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                if (ring) ProgressRing(36.dp, Color.White)
                Icon(Icons.Audio, Color.White, if (ring) 14.dp else 18.dp)
            }
        }
        Column(Modifier.weight(1f)) {
            Txt(t.title, Type.row)
            Txt(t.artist, Type.sub, color = c.onSecondary)
        }
        if (trailing != null) trailing() else IconBtn(Icons.More, "More for ${t.title}", tint = c.onSecondary, size = 18.dp) { TrackMenu.show(t) }
    }
}

/** The mini player's progress ring (a faint track, the played part bright) for the playing row; position changes only redraw it. */
@Composable
private fun ProgressRing(size: Dp, ink: Color) {
    val pos = com.wetune.rememberPosition()
    Spacer(Modifier.size(size).drawBehind {
        val sw = 2.dp.toPx()
        val r = this.size.minDimension / 2f - sw / 2f
        drawCircle(ink.copy(alpha = 0.18f), r, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        val d = Playback.duration()
        if (d > 0) drawArc(
            ink, -90f, 360f * (pos.value.toFloat() / d).coerceIn(0f, 1f), false,
            androidx.compose.ui.geometry.Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    })
}

/** 150dp square tile for albums / playlists / artists (artists are round). */
@Composable
fun Tile(card: Card, modifier: Modifier = Modifier, size: Dp = 150.dp, onClick: () -> Unit) {
    val round = card.subtitle.startsWith("Artist") || card.browseId.startsWith("UC")
    Column(modifier.width(size).press(card.title, onClick = onClick)) {
        Cover(card.art, size, if (round) size / 2 else 10.dp)
        Spacer(Modifier.height(6.dp))
        Txt(card.title, Type.tile)
        if (card.subtitle.isNotEmpty()) Txt(card.subtitle, Type.sub, color = WT.c.onSecondary)
    }
}

/** Grouped list (spec §4, Apple-like): 12dp corners, inset separators. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(12.dp))) { content() }
}

@Composable
fun GroupRow(title: String, modifier: Modifier = Modifier, ic: Ic? = null, value: String? = null, chevron: Boolean = true, last: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val c = WT.c
    Column(if (onClick != null) modifier.press(title, scale = 0.985f, onClick = onClick) else modifier) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ic != null) { Icon(ic, c.on, 20.dp); Spacer(Modifier.width(12.dp)) }
            Txt(title, Type.body, Modifier.weight(1f))
            if (value != null) Txt(value, Type.body, color = c.onSecondary)
            if (trailing != null) trailing()
            if (chevron && onClick != null) { Spacer(Modifier.width(6.dp)); Icon(Icons.Chevron, c.onTertiary, 14.dp) }
        }
        if (!last) Box(Modifier.padding(start = if (ic != null) 48.dp else 16.dp).fillMaxWidth().height(0.5.dp).background(c.separator))
    }
}

/** iOS-style switch; the knob slides. */
@Composable
fun Toggle(on: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val c = WT.c
    val x by animateFloatAsState(if (on) 1f else 0f, Motion.small(), label = "toggle")
    Box(
        Modifier.size(51.dp, 31.dp).clip(CircleShape)
            .background(androidx.compose.ui.graphics.lerp(c.surfaceHigh, c.success, x))
            .press(label, scale = 1f) { onChange(!on) }
            .semantics { stateDescription = if (on) "On" else "Off" },
    ) {
        Box(Modifier.padding(2.dp).size(27.dp).graphicsLayer { translationX = x * 20.dp.toPx() }.background(Color.White, CircleShape))
    }
}

// ---- one bottom sheet for the whole app (song menus, pickers) ----------------------------------------------------

object Sheet {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null); private set
    fun show(c: @Composable () -> Unit) { content = c }
    fun hide() { content = null }
}

object TrackMenu {
    /** Song actions; the sheet's body lives in Menus.kt. */
    fun show(t: Track) = Sheet.show { TrackMenuBody(t) }
}
