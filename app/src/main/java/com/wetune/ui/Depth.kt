package com.wetune.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The logo's depth (ic_launcher_foreground.xml, Splash.kt) for floating surfaces: a soft shadow falling straight down
 * (no light source, no rims), drawn like the logo's as gradients fading to nothing, plus a very gentle top-to-bottom
 * tone on the surface. Put it BEFORE the surface's clip / frost so the shadow can spill outside:
 * `Modifier.depth(shape).frost(shape)`.
 *
 * Cheap: no bitmaps, no blur, no elevation. A handful of gradient shaders built in drawWithCache (only when the size
 * changes); moving the surface never redraws it. The shadow is clipped out of the shape itself, so translucent frost
 * never goes muddy.
 */
@Composable
fun Modifier.depth(shape: Shape, strength: Float = 1f, blur: Dp = 12.dp, drop: Dp = 4.dp, tone: Boolean = true): Modifier {
    val dark = WT.c.dark
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        val r = when (outline) { is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x; else -> 0f }
        val shadow = softShadow(Rect(Offset.Zero, size), r, blur.toPx(), drop.toPx(), depthAlpha(dark, strength))
        // a few % lighter at the top, a few % darker at the bottom
        val toneBrush = toneBrush(dark)
        onDrawWithContent {
            clipPath(path, ClipOp.Difference) { shadow(this, 1f) }
            drawContent()
            if (tone) drawPath(path, toneBrush)
        }
    }
}

/**
 * [depth] for a rounded rect whose corner [radius] (px) and visibility [show] (0..1, multiplies shadow and tone) are
 * animated: Now Playing's one surface (mini player ↔ card ↔ capsule ↔ full screen). Both are read at draw time, so
 * nothing recomposes; the shadow's brushes are rebuilt only when the size or radius actually changes (during a morph),
 * and not at all while it's hidden.
 */
fun Modifier.depth(dark: Boolean, radius: androidx.compose.ui.unit.Density.() -> Float, show: () -> Float, strength: Float = 1f, blur: Dp = 12.dp, drop: Dp = 4.dp): Modifier =
    drawWithCache {
        val path = Path()
        val toneBrush = toneBrush(dark)
        var r = -1f
        var shadow: (DrawScope.(Float) -> Unit)? = null
        onDrawWithContent {
            val k = show().coerceIn(0f, 1f)
            if (k > 0f) {
                val nr = radius().coerceIn(0f, size.minDimension / 2f)
                if (nr != r || shadow == null) {
                    r = nr
                    shadow = softShadow(Rect(Offset.Zero, size), r, blur.toPx(), drop.toPx(), depthAlpha(dark, strength))
                    path.reset(); path.addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero, size), androidx.compose.ui.geometry.CornerRadius(r)))
                }
                clipPath(path, ClipOp.Difference) { shadow!!(this, k) }
            }
            drawContent()
            if (k > 0f) drawPath(path, toneBrush, alpha = k)
        }
    }

private fun toneBrush(dark: Boolean) =
    Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.035f else 0.06f), 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = if (dark) 0.08f else 0.045f))

/** The logo peaks around 20 % black; dark pages need a little more for the same lift. */
fun depthAlpha(dark: Boolean, strength: Float = 1f) = ((if (dark) 0.34f else 0.2f) * strength).coerceIn(0f, 0.6f)

/**
 * The same shadow for a bare glyph with no surface (the Apple-style play button): the glyph itself, black, blurred and
 * dropped. Needs real blur, so Android 12+ only; the blur is a layer effect, redrawn only when the glyph changes.
 */
@Composable
fun GlyphDepth(ic: Ic, size: Dp, strength: Float = 1f) {
    if (android.os.Build.VERSION.SDK_INT < 31) return
    val a = depthAlpha(WT.c.dark, strength)
    // room around the glyph so the blur isn't cut at the layer's edge
    androidx.compose.foundation.layout.Box(
        Modifier.size(size + 16.dp).graphicsLayer {
            translationY = 3.dp.toPx()
            renderEffect = BlurEffect(5.dp.toPx(), 5.dp.toPx(), TileMode.Decal)
        },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { Icon(ic, Color.Black.copy(alpha = a), size) }
}

/** One-off form for things drawn by hand (the tab bar's sliding pill): a shadow under [rect] with corner [radius]. */
fun DrawScope.drawDepth(rect: Rect, radius: Float, alpha: Float, blur: Dp = 6.dp, drop: Dp = 2.dp) =
    softShadow(rect, radius, blur.toPx(), drop.toPx(), alpha)(this, 1f)

/**
 * A blurred rounded rect without a blur: the corners are radial gradients and the edges linear ones, all with the
 * same fall-off (a gaussian-ish curve over 2 × [blur] centred on the edge), so they meet seamlessly. [drop] moves it
 * straight down. Returns the draw so callers can cache the brushes.
 */
private fun softShadow(surface: Rect, radius: Float, blur: Float, drop: Float, alpha: Float): DrawScope.(k: Float) -> Unit {
    val b = blur.coerceAtLeast(1f)
    val rc = radius.coerceIn(0f, minOf(surface.width, surface.height) / 2)
    val s = surface.translate(0f, drop)
    val black = Color.Black
    fun stops(from: Float) = arrayOf(from to black.copy(alpha = alpha), from + (1f - from) * 0.35f to black.copy(alpha = alpha * 0.6f), from + (1f - from) * 0.68f to black.copy(alpha = alpha * 0.2f), 1f to Color.Transparent)
    val outer = rc + b
    val corner = Brush.radialGradient(*stops(((rc - b) / outer).coerceAtLeast(0f)), center = Offset.Zero, radius = outer)
    // corner circle centres
    val l = s.left + rc; val t = s.top + rc; val rr = s.right - rc; val bb = s.bottom - rc
    val top = Brush.verticalGradient(*stops(0f), startY = s.top + b, endY = s.top - b)
    val bottom = Brush.verticalGradient(*stops(0f), startY = s.bottom - b, endY = s.bottom + b)
    val left = Brush.horizontalGradient(*stops(0f), startX = s.left + b, endX = s.left - b)
    val right = Brush.horizontalGradient(*stops(0f), startX = s.right - b, endX = s.right + b)
    val cornerSize = Size(outer, outer)
    val corners = listOf(floatArrayOf(l, t, -1f, -1f), floatArrayOf(rr, t, 1f, -1f), floatArrayOf(l, bb, -1f, 1f), floatArrayOf(rr, bb, 1f, 1f))
    return { k ->
        // corners: the same radial brush, moved to each corner centre; each draws only its own quadrant
        for ((cx, cy, dx, dy) in corners) {
            drawContext.transform.translate(cx, cy)
            drawRect(corner, Offset(if (dx < 0) -outer else 0f, if (dy < 0) -outer else 0f), cornerSize, alpha = k)
            drawContext.transform.translate(-cx, -cy)
        }
        if (rr > l) {
            drawRect(top, Offset(l, s.top - b), Size(rr - l, t - s.top + b), alpha = k)
            drawRect(bottom, Offset(l, bb), Size(rr - l, s.bottom + b - bb), alpha = k)
        }
        if (bb > t) {
            drawRect(left, Offset(s.left - b, t), Size(l - s.left + b, bb - t), alpha = k)
            drawRect(right, Offset(rr, t), Size(s.right + b - rr, bb - t), alpha = k)
        }
        if (rr > l && bb > t) drawRect(black.copy(alpha = alpha * k), Offset(l, t), Size(rr - l, bb - t))
    }
}
