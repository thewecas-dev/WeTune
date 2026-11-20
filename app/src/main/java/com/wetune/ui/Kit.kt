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

@Composable
fun frostStyle(): HazeStyle {
    val c = WT.c
    // Settings › General › Glass: how much of the theme's frost tint (0..100 %, 62 = the original) and how much blur;
    // a faint grain makes it read as frosted glass rather than a plain blur
    val tint = com.wetune.Settings.glassTint.coerceIn(0, 100) / 100f
    val blur = com.wetune.Settings.glassBlur.coerceIn(0, 60)
    return remember(c, tint, blur) {
        HazeStyle(backgroundColor = c.bg, tint = HazeTint(c.frost.copy(alpha = tint)), blurRadius = blur.dp, noiseFactor = 0.06f, fallbackTint = HazeTint(c.frostSolid))
    }
}

/**
 * Glass for cards and rows that sit on the page itself (search box, grouped lists, profile card): a translucent fill
 * that lets the page's colour wash show through, with the same fine grain as the frost. Real blur isn't used here:
 * these cards are part of the page, so nothing ever moves behind them to blur. Follows the Glass tint setting.
 */
@Composable
fun Modifier.glass(shape: Shape): Modifier {
    val c = WT.c
    val t = com.wetune.Settings.glassTint.coerceIn(0, 100) / 100f
    val fill = if (c.dark) Color.White.copy(alpha = 0.03f + 0.1f * t) else Color.White.copy(alpha = 0.3f + 0.6f * t)
    val noise = remember { androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(grain, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated)) }
    return clip(shape).drawBehind { drawRect(fill); drawRect(noise, alpha = if (c.dark) 0.5f else 0.3f) }
}

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
fun IconBtn(ic: Ic, label: String, modifier: Modifier = Modifier, tint: Color = WT.c.on, size: Dp = 22.dp, circle: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).press(label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (circle) Box(Modifier.size(32.dp).background(WT.c.chip, CircleShape), contentAlignment = Alignment.Center) { Icon(ic, tint, size) }
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
        modifier.height(36.dp).clip(CircleShape).background(if (selected) c.primary else c.chip).press(text, onClick = onClick).padding(horizontal = 14.dp),
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
fun SectionHeader(title: String, modifier: Modifier = Modifier, onMore: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)
            .then(if (onMore != null) Modifier.press("$title, see all", onClick = onMore) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Txt(title, Type.section)
        if (onMore != null) Icon(Icons.Chevron, WT.c.onSecondary, 16.dp)
    }
}

/** A song row: 48dp art, title, artist; the playing one shows a small waveform instead of the title colour change. */
@Composable
fun TrackRow(t: Track, modifier: Modifier = Modifier, index: Int? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val c = WT.c
    val current = Playback.current?.id == t.id
    Row(
        modifier.fillMaxWidth().height(58.dp).press(onLongClick = { TrackMenu.show(t) }, scale = 0.985f, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (index != null) Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (current) Icon(Icons.Audio, c.on, 16.dp) else Txt("$index", Type.sub, color = c.onSecondary)
        } else Box {
            Cover(t.art, 48.dp, 6.dp)
            if (current) Box(Modifier.size(48.dp).background(Color(0x66000000), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Audio, Color.White, 18.dp) }
        }
        Column(Modifier.weight(1f)) {
            Txt(t.title, Type.row)
            Txt(t.artist, Type.sub, color = c.onSecondary)
        }
        if (trailing != null) trailing() else IconBtn(Icons.More, "More for ${t.title}", tint = c.onSecondary, size = 18.dp) { TrackMenu.show(t) }
    }
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
