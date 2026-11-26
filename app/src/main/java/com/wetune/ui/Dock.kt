package com.wetune.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.wetune.Playback
import com.wetune.rememberPosition
import com.wetune.ui.player.NowPlaying
import com.wetune.ui.player.sheetDrag
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Navbar option E (spec §6): at rest the mini player sits over a full-width tab capsule; scrolling down ≥ 48dp folds
 * the capsule into one round button beside the mini player. One instance, at the app root, on every screen.
 */
object Dock {
    var folded by mutableStateOf(false)
    private var down = 0f

    /** Watches every page's scrolling: down folds, up (or reaching the top) unfolds. */
    val scroll = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = consumed.y
            if (dy < 0) { down -= dy; if (down > FOLD_PX) folded = true }
            else if (dy > 0 || available.y > 0) { down = 0f; if (dy > 6f || available.y > 0) folded = false }
            return Offset.Zero
        }
    }
    var FOLD_PX = 140f // 48dp, set from density at first composition

    /** Space a page leaves at its bottom so its last row clears the dock. */
    fun bottomPadding(playing: Boolean): Dp = if (playing) 168.dp else 92.dp

    /** How folded the dock is (0..1, animated by [DockHost]); read in layout by the player, whose mini player sits in [miniSlot]. */
    var fold: androidx.compose.runtime.State<Float> = androidx.compose.runtime.mutableFloatStateOf(0f)

    /**
     * The mini player's slot in the dock (root coordinates, px): above the tab capsule at rest, beside the folded tab
     * button at [f] = 1. The player (NowPlayingLayer) is the one surface drawn there; the dock only leaves the room.
     */
    fun miniSlot(w: Int, h: Int, f: Float, navBottom: Int, d: androidx.compose.ui.unit.Density): androidx.compose.ui.geometry.Rect = with(d) {
        val side = SIDE.roundToPx(); val bottom = BOTTOM.roundToPx() + navBottom
        val navH = NAV_H.roundToPx(); val miniH = MINI_H.roundToPx(); val gap = GAP.roundToPx()
        val groupW = minOf(w - 2 * side, MAX_W.roundToPx()); val x0 = (w - groupW) / 2
        val x = lerp(x0.toFloat(), (x0 + miniH + 8.dp.roundToPx()).toFloat(), f).roundToInt()
        val y = lerp((h - bottom - navH - gap - miniH).toFloat(), (h - bottom - miniH).toFloat(), f).roundToInt()
        androidx.compose.ui.geometry.Rect(x.toFloat(), y.toFloat(), (x0 + groupW).toFloat(), (y + miniH).toFloat())
    }
}

private val SIDE = 10.dp
// the tabs and the mini player never grow past this, centred on wide screens: four tabs then top out near 76dp each
// (the selected one 1.8× that, ≈ 137dp) instead of stretching with tablets and landscape
private val MAX_W = 400.dp
private val BOTTOM = 14.dp
private val NAV_H = 56.dp
private val MINI_H = 60.dp
private val GAP = 12.dp

@Composable
fun DockHost() {
    val c = WT.c
    val density = LocalDensity.current
    Dock.FOLD_PX = with(density) { 48.dp.toPx() }
    val playing = Playback.current != null
    val foldState = animateFloatAsState(if (Dock.folded && playing) 1f else 0f, Motion.fold(), label = "fold")
    Dock.fold = foldState
    val fold by foldState
    val navInset = WindowInsets.navigationBars

    // the mini player above the capsule (or beside it folded) is the player itself, drawn by NowPlayingLayer in
    // [Dock.miniSlot]: the dock lays out only the capsule
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = { TabCapsule(foldProvider = { fold }) },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val side = SIDE.roundToPx(); val bottom = BOTTOM.roundToPx() + navInset.getBottom(this)
        val navH = NAV_H.roundToPx(); val miniH = MINI_H.roundToPx()
        val f = fold
        // one centred column for both: the screen width less the sides, at most MAX_W
        val groupW = minOf(w - 2 * side, MAX_W.roundToPx()); val x0 = (w - groupW) / 2
        // capsule: full width × 56 at rest → 60 × 60 circle when folded
        val capW = lerp(groupW.toFloat(), miniH.toFloat(), f).roundToInt()
        val capH = lerp(navH.toFloat(), miniH.toFloat(), f).roundToInt()
        val cap = measurables[0].measure(Constraints.fixed(capW, capH))
        layout(w, h) { cap.place(x0, h - bottom - capH) }
    }
}

@Composable
private fun TabCapsule(foldProvider: () -> Float) {
    val c = WT.c
    val fold = foldProvider()
    Box(Modifier.depth(RoundedCornerShape(30.dp)).frost(RoundedCornerShape(30.dp))) {
        // the four tabs, laid out at full width so folding clips them instead of squeezing them
        if (fold < 0.99f) TabRow(
            Modifier.fillMaxHeight().wrapContentWidth(Alignment.Start, unbounded = true).requiredWidth(minOf(WidthOfScreen() - SIDE * 2, MAX_W)) // the capsule's own resting width
                .graphicsLayer { alpha = (1f - foldProvider() * 1.8f).coerceIn(0f, 1f) }.padding(4.dp),
        )
        if (fold > 0.01f) Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = (foldProvider() * 1.8f - 0.8f).coerceIn(0f, 1f) }
                .press("Tabs, current: ${Nav.tab.label}. Show all tabs") { Dock.folded = false },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(48.dp).depth(CircleShape, 0.8f, blur = 6.dp, drop = 2.dp).background(c.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(Nav.tab.ic, c.onPrimary, 22.dp) }
        }
    }
}

@Composable
private fun WidthOfScreen(): Dp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp

/**
 * The four tabs and ONE white pill that slides between them. Everything follows a single animated value, the
 * selected index as a float: tab widths, the pill's position and width, and each label's fade all come from it,
 * so they move together. Read only during layout and draw: switching tabs relayouts, never recomposes.
 */
@Composable
private fun TabRow(modifier: Modifier) {
    val c = WT.c
    val p = animateFloatAsState(Tab.entries.indexOf(Nav.tab).toFloat(), Motion.fold(), label = "tabs")
    val gap = 4.dp
    val geo = remember { TabGeometry() }
    Layout(
        modifier = modifier.drawBehind {
            geo.update(size.width, p.value, gap.toPx())
            val i = p.value.toInt().coerceIn(0, 3); val j = (i + 1).coerceAtMost(3); val f = p.value - i
            val left = lerp(geo.left[i], geo.left[j], f)
            val width = lerp(geo.width[i], geo.width[j], f)
            // the pill sits on the capsule like the logo's dot on its disc: a small shadow straight down
            drawDepth(androidx.compose.ui.geometry.Rect(Offset(left, 0f), Size(width, size.height)), size.height / 2, depthAlpha(c.dark, 0.8f))
            drawRoundRect(c.primary, Offset(left, 0f), Size(width, size.height), androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        },
        content = { Tab.entries.forEachIndexed { i, t -> TabItem(t) { (1f - abs(p.value - i)).coerceIn(0f, 1f) } } },
    ) { ms, cons ->
        geo.update(cons.maxWidth.toFloat(), p.value, gap.toPx())
        val placeables = ms.mapIndexed { i, m -> m.measure(Constraints.fixed(geo.width[i].roundToInt(), cons.maxHeight)) }
        layout(cons.maxWidth, cons.maxHeight) { placeables.forEachIndexed { i, pl -> pl.place(geo.left[i].roundToInt(), 0) } }
    }
}

/** Tab widths at fractional selection [p]: the selected tab is 1.8× as wide, blending between neighbours. */
private class TabGeometry {
    val left = FloatArray(4)
    val width = FloatArray(4)
    fun update(total: Float, p: Float, gap: Float) {
        val weights = FloatArray(4) { 1f + 0.8f * (1f - abs(p - it)).coerceIn(0f, 1f) }
        val unit = (total - gap * 3) / weights.sum()
        var x = 0f
        for (i in 0..3) { left[i] = x; width[i] = weights[i] * unit; x += width[i] + gap }
    }
}

/** Icon (grey → dark as the pill arrives) and a label that fades and slides in; [on] is 0…1. */
@Composable
private fun TabItem(t: Tab, on: () -> Float) {
    val c = WT.c
    Layout(
        modifier = Modifier.fillMaxHeight().press(t.label, scale = 0.94f) { Nav.select(t) },
        content = {
            Icon(t.ic, if (c.dark) Color.White.copy(alpha = 0.78f) else Color(0xFF8E8E93), 21.dp, Modifier.graphicsLayer { alpha = 1f - on() })
            Icon(t.ic, c.onPrimary, 21.dp, Modifier.graphicsLayer { alpha = on() })
            Txt(t.label, Type.button.copy(fontSize = Type.tile.fontSize), Modifier.graphicsLayer { alpha = on() }, color = c.onPrimary)
        },
    ) { ms, cons ->
        val loose = cons.copy(minWidth = 0, minHeight = 0)
        val grey = ms[0].measure(loose); val dark = ms[1].measure(loose); val label = ms[2].measure(loose.copy(maxWidth = Constraints.Infinity))
        val o = on(); val space = 8.dp.toPx()
        val content = grey.width + (space + label.width) * o
        val x = ((cons.maxWidth - content) / 2).roundToInt()
        layout(cons.maxWidth, cons.maxHeight) {
            val iy = (cons.maxHeight - grey.height) / 2
            grey.place(x, iy); dark.place(x, iy)
            label.place((x + grey.width + space * o).roundToInt(), (cons.maxHeight - label.height) / 2)
        }
    }
}

