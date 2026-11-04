package com.wetune.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.wetune.sources.Track
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.wetune.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the space around the cover is filled. */
val FillModes = listOf("stretch" to "Stretch", "mirror" to "Reflection", "blur" to "Blur")

/** Sharp-cover edges for stretch/mirror: a short eased blend into the extension, since it continues the same pixels. */
/** Soft left and right edges, used when a short screen makes the cover narrower than the screen. */
private val SideFade = Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0f), 0.06f to Color.Black, 0.94f to Color.Black, 1f to Color.Black.copy(alpha = 0f))

private val EdgeFade = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0f), 0.03f to Color.Black.copy(alpha = 0.35f), 0.07f to Color.Black.copy(alpha = 0.8f), 0.1f to Color.Black,
    0.88f to Color.Black, 0.93f to Color.Black.copy(alpha = 0.7f), 0.97f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0f),
)

/** Blur mode's cover: a long eased fade at both ends, as before. */
private val BlurFade = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0f), 0.05f to Color.Black.copy(alpha = 0.05f), 0.09f to Color.Black.copy(alpha = 0.18f),
    0.14f to Color.Black.copy(alpha = 0.45f), 0.2f to Color.Black.copy(alpha = 0.78f), 0.26f to Color.Black,
    0.74f to Color.Black, 0.8f to Color.Black.copy(alpha = 0.78f), 0.86f to Color.Black.copy(alpha = 0.45f),
    0.91f to Color.Black.copy(alpha = 0.18f), 0.95f to Color.Black.copy(alpha = 0.05f), 1f to Color.Black.copy(alpha = 0f),
)

@Composable
private fun rememberCover(url: String?): ImageBitmap? {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(null, url) {
        value = if (url == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).size(900).build())
                (r as SuccessResult).image.toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }.value
}

/**
 * Everything behind the player's text: the cover shown whole at full width, ending where the controls begin,
 * with the space around it filled per [Settings.npFill]. Blur mode keeps the earlier look (8% side crop).
 */
@Composable
fun CoverBackdrop(pal: ArtPalette, w: Dp, split: Dp, clearTop: Dp, coverAlpha: Float, t: Track, coverShift: () -> Float = { 0f }) {
    // [clearTop]: bottom of the grabber and "Playing from" header; the cover always starts below it
    // (only stable inputs, so the backdrop skips the 4-times-a-second recompositions from the playback position)
    val floor = lerp(pal.sea, Color.Black, 0.6f)
    val mode = Settings.npFill
    val img = rememberCover(t.art)

    when {
        mode == "blur" || img == null -> BlurBackdrop(t, w, split, clearTop, coverAlpha)
        else -> {
            // Whole cover, full width; wide thumbnails stay whole too. Sits right on top of the controls.
            // Placed to never overlap the top buttons or the title: if the full-width cover fits between them it sits
            // on the title and the stretch fills above; if not, it shrinks to fill that space exactly (no top stretch)
            // and blurred cover fills the slim sides.
            val ratio = img.height.toFloat() / img.width
            val topLimit = clearTop
            val room = (split - topLimit).coerceAtLeast(120.dp)
            val fits = w * SideScale * ratio <= room
            val coverW = if (fits) w * SideScale else room / ratio
            val coverH = coverW * ratio
            val top = if (fits) split - coverH else topLimit
            if (!fits) Cover(t, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; alpha = coverAlpha }.blur(60.dp, BlurredEdgeTreatment.Unbounded), alpha = 0.9f)
            if (mode == "mirror") Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }.blur(14.dp, BlurredEdgeTreatment.Rectangle)) {
                extend(img, top.toPx(), coverH.toPx(), mirror = true, coverWidth = coverW.toPx())
            } else {
                // Far from the cover: the edge averaged into ~10 colors and stretched, so busy edges become soft gradients, not streaks.
                val smooth = remember(img) { averagedEdges(img) }
                // (the top uses less blur than the bottom so the sky or edge above stays a little crisper)
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }.blur(18.dp, BlurredEdgeTreatment.Rectangle)) {
                    val t = top.toPx(); val h = coverH.toPx(); val wi = size.width.toInt(); val cw = coverW.toPx(); val left = ((wi - cw) / 2).toInt()
                    drawImage(smooth.first, IntOffset.Zero, IntSize(EdgeColumns, 1), IntOffset.Zero, IntSize(wi, t.toInt() + 2), filterQuality = FilterQuality.High)
                    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(left, t.toInt()), IntSize(cw.toInt(), h.toInt()))
                }
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }.blur(30.dp, BlurredEdgeTreatment.Rectangle)) {
                    val t = top.toPx(); val h = coverH.toPx(); val wi = size.width.toInt(); val cw = coverW.toPx(); val left = ((wi - cw) / 2).toInt()
                    drawImage(smooth.second, IntOffset.Zero, IntSize(EdgeColumns, 1), IntOffset((-0.1f * wi).toInt(), (t + h).toInt() - 2), IntSize((wi * 1.2f).toInt(), (size.height - t - h).toInt() + 2), filterQuality = FilterQuality.High)
                    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(left, t.toInt()), IntSize(cw.toInt(), h.toInt()))
                }
                // Close to the cover: the real edge pixels stretched, fading out over the first third of each gap.
                listOf(true to 10.dp, false to 16.dp).forEach { (isTop, radius) ->
                    Canvas(
                        Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha; compositingStrategy = CompositingStrategy.Offscreen }
                            .blur(radius, BlurredEdgeTreatment.Rectangle)
                            .drawWithContent { drawContent(); drawRect(nearMask(top.toPx(), coverH.toPx(), size.height), blendMode = BlendMode.DstIn) },
                    ) { extend(img, top.toPx(), coverH.toPx(), mirror = false, above = isTop, under = !isTop, coverWidth = coverW.toPx()) }
                }
                Reflection(img, top, coverH, coverW, coverAlpha)
            }
            Image(
                img, null,
                Modifier.offset(y = top).fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally, unbounded = true).requiredSize(coverW, coverH) /* centered whether wider or narrower than the screen */.graphicsLayer { alpha = coverAlpha; compositingStrategy = CompositingStrategy.Offscreen; translationX = coverShift() }
                    .drawWithContent {
                        drawContent(); drawRect(EdgeFade, blendMode = BlendMode.DstIn)
                        if (!fits) drawRect(SideFade, blendMode = BlendMode.DstIn) // soft sides into the blurred fill
                    },
                contentScale = ContentScale.FillBounds,
            )
        }
    }
    // Bright covers (white backgrounds) would swallow the white text and controls, so shade behind them,
    // darker the brighter that edge is. Dark covers get nothing.
    val (lumTop, lumBottom) = remember(img) { img?.let(::edgeLuminance) ?: (0f to 0f) }
    val shadeBottom = ((lumBottom - 0.35f) / 0.35f).coerceIn(0f, 1f) * 0.7f
    val shadeTop = ((lumTop - 0.4f) / 0.35f).coerceIn(0f, 1f) * 0.55f
    val ink = lerp(pal.sea, Color.Black, 0.5f)
    if (shadeBottom > 0f) Box(
        Modifier.fillMaxSize().drawBehind {
            val s = (split - 90.dp).toPx()
            drawRect(Brush.verticalGradient(0f to ink.copy(alpha = 0f), 0.35f to ink.copy(alpha = shadeBottom * 0.8f), 1f to ink.copy(alpha = shadeBottom), startY = s, endY = size.height))
        },
    )
    if (shadeTop > 0f) Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.verticalGradient(0f to ink.copy(alpha = shadeTop), 1f to ink.copy(alpha = 0f), startY = 0f, endY = (clearTop + 64.dp).toPx()))
        },
    )
    // A light touch of the cover's dark shade at the very top, behind the status bar.
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to floor.copy(alpha = 0.6f), 0.06f to floor.copy(alpha = 0.25f), 0.14f to floor.copy(alpha = 0f))))
    // Dark film grain, starting just above the title and building up toward the bottom.
    Box(
        Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawBehind {
            drawRect(ShaderBrush(ImageShader(noiseTile, TileMode.Repeated, TileMode.Repeated)))
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = (split - 24.dp).toPx(), endY = size.height), blendMode = BlendMode.DstIn)
        },
    )
}

/** A small tile of faint black specks, repeated across the screen as grain. */
private val noiseTile: ImageBitmap by lazy {
    val s = 160
    val rnd = java.util.Random(7)
    val px = IntArray(s * s) { rnd.nextInt(56) shl 24 } // black at a random, low alpha
    android.graphics.Bitmap.createBitmap(px, s, s, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Every mode crops about 6% off each side of the cover (1 / 0.88), leaving less to fill. */
private const val SideScale = 1.14f
private const val EdgeColumns = 10
private const val FanSlices = 28
private const val FanSpread = 0.9f // how much wider the stretch gets by the screen edge

/** The cover's top and bottom bands (3% each) averaged into [EdgeColumns] colors, as 1-pixel-tall strips. */
private fun averagedEdges(img: ImageBitmap): Pair<ImageBitmap, ImageBitmap> {
    val b = img.asAndroidBitmap()
    val band = (b.height * 0.03f).toInt().coerceAtLeast(2)
    fun strip(y0: Int): ImageBitmap {
        val px = IntArray(b.width * band).also { b.getPixels(it, 0, b.width, 0, y0, b.width, band) }
        val out = IntArray(EdgeColumns) { col ->
            val x0 = col * b.width / EdgeColumns; val x1 = (col + 1) * b.width / EdgeColumns
            var r = 0L; var g = 0L; var bl = 0L; var n = 0L
            for (y in 0 until band) for (x in x0 until x1) { val p = px[y * b.width + x]; r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++ }
            (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
        }
        return android.graphics.Bitmap.createBitmap(out, EdgeColumns, 1, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    return strip(0) to strip(b.height - band)
}

/** Opaque over the cover and the third of each gap nearest to it, fading to clear further out. */
private fun nearMask(top: Float, h: Float, height: Float): Brush {
    val bottom = top + h
    val a0 = (top * 0.65f / height).coerceIn(0f, 1f)
    val a1 = (top / height).coerceIn(a0 + 0.0001f, 1f)
    val b1 = (bottom / height).coerceIn(a1 + 0.0001f, 1f)
    val b2 = ((bottom + (height - bottom) * 0.35f) / height).coerceIn(b1 + 0.0001f, 1f)
    return Brush.verticalGradient(0f to Color.Transparent, a0 to Color.Transparent, a1 to Color.Black, b1 to Color.Black, b2 to Color.Transparent, 1f to Color.Transparent)
}

/** Fills above [top] and below [top]+[h] by stretching the cover's edge rows, or by reflecting the cover. */
private fun DrawScope.extend(img: ImageBitmap, top: Float, h: Float, mirror: Boolean, above: Boolean = true, under: Boolean = true, coverWidth: Float = size.width * SideScale) {
    val w = coverWidth // the cover's drawn width (wider than the screen by the side crop, or narrower on short screens)
    val left = ((size.width - w) / 2).toInt()
    val bottom = top + h
    val below = size.height - bottom
    val pxPerDp = img.height / h // image pixels per drawn pixel
    if (mirror) {
        val a = (top * pxPerDp).toInt().coerceIn(1, img.height)
        val b = (below * pxPerDp).toInt().coerceIn(1, img.height)
        scale(1f, -1f, Offset(0f, top)) { drawImage(img, IntOffset.Zero, IntSize(img.width, a), IntOffset(left, top.toInt()), IntSize(w.toInt(), top.toInt() + 1)) }
        scale(1f, -1f, Offset(0f, bottom)) { drawImage(img, IntOffset(0, img.height - b), IntSize(img.width, b), IntOffset(left, (bottom - below).toInt()), IntSize(w.toInt(), below.toInt() + 1)) }
    } else {
        // The edge rows spread outward as they leave the cover, like light fanning out, instead of running dead straight.
        val band = (img.height * 0.015f).toInt().coerceAtLeast(2)
        fun fan(srcY: Int, from: Float, gap: Float, up: Boolean) {
            if (gap <= 0f) return
            val slice = gap / FanSlices
            for (i in 0 until FanSlices) {
                val d = (i + 0.5f) / FanSlices // 0 at the cover, 1 at the screen edge
                val sw = w * (1f + FanSpread * d)
                val y = if (up) from - (i + 1) * slice else from + i * slice
                drawImage(img, IntOffset(0, srcY), IntSize(img.width, band), IntOffset(((size.width - sw) / 2).toInt(), y.toInt()), IntSize(sw.toInt(), slice.toInt() + 2))
            }
        }
        if (above) fan(0, top, top, up = true)
        if (under) fan(img.height - band, bottom, below, up = false)
    }
    // the cover itself too, so the blur blends across the seams
    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(left, top.toInt()), IntSize(w.toInt(), h.toInt()))
}

/** The earlier look: cover 14% wider than the screen (6% cropped per side, same as the others) over a zoomed blur of itself. */
@Composable
private fun BlurBackdrop(t: Track, w: Dp, split: Dp, clearTop: Dp, coverAlpha: Float) {
    // on short screens the cover shrinks so it never runs under the title, nor up under the header
    val size = minOf(w * SideScale, (split - clearTop) / 0.95f).coerceAtLeast(120.dp)
    val top = maxOf(split + size * 0.05f - size, clearTop)
    val coverMod = Modifier.offset(y = top).fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally, unbounded = true).requiredSize(size)
    Cover(t, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.blur(70.dp, BlurredEdgeTreatment.Unbounded), alpha = 0.85f)
    Cover(t, coverMod.graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }.blur(40.dp, BlurredEdgeTreatment.Unbounded), alpha = 0.9f)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.1f), 0.5f to Color.Black.copy(alpha = 0.2f), 1f to Color.Black.copy(alpha = 0.5f))))
    Cover(
        t,
        coverMod.graphicsLayer { alpha = coverAlpha; compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent { drawContent(); drawRect(BlurFade, blendMode = BlendMode.DstIn) },
    )
}

/** A faint, softened mirror image of the cover's lower part just under it, as if the cover stood on dark glass. */
@Composable
private fun Reflection(img: ImageBitmap, top: Dp, coverH: Dp, coverW: Dp, coverAlpha: Float) {
    Canvas(
        Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha; compositingStrategy = CompositingStrategy.Offscreen }
            .blur(4.dp, BlurredEdgeTreatment.Rectangle)
            .drawWithContent {
                drawContent()
                val b = (top + coverH).toPx()
                drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.32f), 1f to Color.Transparent, startY = b, endY = b + coverH.toPx() * 0.3f), blendMode = BlendMode.DstIn)
            },
    ) {
        val b = (top + coverH).toPx()
        val r = coverH.toPx() * 0.3f // reflected depth
        val src = (img.height * 0.3f).toInt()
        scale(1f, -1f, Offset(0f, b)) {
            drawImage(img, IntOffset(0, img.height - src), IntSize(img.width, src), IntOffset(((size.width - coverW.toPx()) / 2).toInt(), (b - r).toInt()), IntSize(coverW.toPx().toInt(), r.toInt()))
        }
    }
}


/** Brightness (0..1) of the cover's top and bottom 15%: the bright end (85th percentile), so one white corner counts. */
private fun edgeLuminance(img: ImageBitmap): Pair<Float, Float> {
    val b = img.asAndroidBitmap()
    val band = (b.height * 0.15f).toInt().coerceAtLeast(1)
    fun lum(y0: Int): Float {
        val values = ArrayList<Float>()
        val step = (b.width / 24).coerceAtLeast(1)
        for (y in y0 until y0 + band step (band / 6).coerceAtLeast(1)) for (x in 0 until b.width step step) {
            val p = b.getPixel(x, y)
            values += ((0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)) / 255.0).toFloat()
        }
        return if (values.isEmpty()) 0f else values.sorted()[(values.size * 0.85f).toInt().coerceAtMost(values.size - 1)]
    }
    return lum(0) to lum(b.height - band)
}
