package com.wetune.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Cream = Color(0xFFF3EFE6)
private val CreamTop = Color(0xFFF8F5EE) // the tile's gentle top-to-bottom tone, as on the launcher icon
private val CreamBottom = Color(0xFFE8E3D8)
private val Amber = Color(0xFFFFB020)
private val Ink = Color(0xFF141414)

private const val FADE_AT = 1000f
private const val END = 1280f

/** Once per process: the launch screen is for cold starts, not for coming back to the app. */
var splashPlayed = false

private val Overshoot = Easing { val t = it - 1f; t * t * (3f * t + 2f) + 1f } // android OvershootInterpolator, tension 2

/** The part of [t] (ms) that falls in [start, start + dur], eased. */
private fun seg(t: Float, start: Float, dur: Float, e: Easing = FastOutSlowInEasing) = e.transform(((t - start) / dur).coerceIn(0f, 1f))

/**
 * The launch screen, over the app's first frames: the app's own ground (page background + the Aura wash, the very
 * same composable Home draws at its top, in the app's theme) and the solid cream Cue tile; the tile fades in, the
 * disc pops, the playhead runs one lap and settles at half past one, then the overlay fades into the app (~1.3 s).
 * Its ground is identical to Home's, so that fade only shows the tile going and Home's content arriving. Shadows fall
 * straight down, soft (no light source, no rims). The clock is read only in the draw/layer phase, so nothing
 * recomposes per frame and the app keeps loading underneath.
 */
@Composable
fun Splash(onDone: () -> Unit) = WeTheme {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(END, tween(END.toInt(), easing = LinearEasing))
        onDone()
    }
    // one layer fades out as a whole (no see-through between tile and mark); pointerInput makes it the touch target,
    // so taps don't reach the app under it while it shows
    Box(Modifier.fillMaxSize().pointerInput(Unit) {}.graphicsLayer { alpha = 1f - seg(clock.value, FADE_AT, END - FADE_AT) }.background(WT.c.bg)) {
        // the system launch screen is the plain page colour: the wash eases in rather than appearing in one frame
        Aura(Modifier.graphicsLayer { alpha = seg(clock.value, 0f, 300f) })
        Canvas(Modifier.fillMaxSize()) {
            cue(clock.value, (min(size.width, size.height) * 0.30f).coerceAtMost(144.dp.toPx()))
        }
    }
}

/** A soft shadow: dark in the middle, gone at [r]; drawn under something and offset down. */
private fun DrawScope.softShadow(at: Offset, r: Float, k: Float, inner: Float = 0.7f) = drawCircle(
    Brush.radialGradient(0f to Color.Black.copy(alpha = k * 0.6f), inner to Color.Black.copy(alpha = k), 1f to Color.Transparent, center = at, radius = r),
    r, at,
)

/** The tile and mark in the launcher icon's units: the visible tile is 72 of its 108, the mark centred on it. */
private fun DrawScope.cue(t: Float, side: Float) {
    val u = side / 72f
    val c = center
    val tileIn = seg(t, 0f, 260f)
    scale(0.85f + 0.15f * tileIn, c) {
        // the tile's own shadow on the ground: wider than tall, mostly below it
        val s = Offset(c.x, c.y + side * 0.1f)
        scale(0.95f, 0.85f, s) { softShadow(s, side * 0.7f, 0.4f * tileIn, 0.5f) }
        drawRoundRect(
            Brush.verticalGradient(listOf(CreamTop, CreamBottom), c.y - side / 2, c.y + side / 2),
            Offset(c.x - side / 2, c.y - side / 2), Size(side, side), CornerRadius(side * 0.22f), alpha = tileIn,
        )
    }
    val disc = seg(t, 120f, 320f, Overshoot)
    if (disc > 0f) scale(disc, c) {
        softShadow(Offset(c.x, c.y + 2.5f * u), 28f * u, 0.18f * min(disc, 1f), 0.82f)
        // the punched centre is a real hole: the tile (and the disc's shadow in it) shows through
        drawPath(Path().apply {
            addOval(Rect(c, 23f * u)); addOval(Rect(c, 4f * u)); fillType = PathFillType.EvenOdd
        }, Ink)
    }
    val dot = seg(t, 300f, 160f)
    if (dot > 0f) {
        // starts at twelve, one lap + 45 deg to half past one; placed by hand so its shadow still falls straight down
        val a = Math.toRadians(-405.0 * (1f - seg(t, 300f, 650f))).toFloat()
        val x = 16.26f * u; val y = -16.26f * u
        val p = Offset(c.x + x * cos(a) - y * sin(a), c.y + x * sin(a) + y * cos(a))
        scale(dot, p) {
            softShadow(p + Offset(0f, 1.8f * u), 10f * u, 0.22f, 0.6f)
            drawCircle(Cream, 7.75f * u, p) // the tile-coloured ring (stroke 2.5 on r 6.5)
            drawCircle(Amber, 5.25f * u, p)
        }
    }
}
