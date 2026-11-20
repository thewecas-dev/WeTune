package com.wetune.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.roundToInt

// The cover, decoded once per song (a small square software bitmap) and shared by everything that isn't the sharp
// cover itself: the palette and accent, the contrast check, the blurred / extended / mesh backgrounds.

/** [cells]: the cover averaged into a [Grid]×[Grid] grid (row-major ARGB), i.e. what any heavy blur of it looks like. */
@Immutable
class ArtData(val bitmap: Bitmap, val palette: ArtPalette, val mesh: List<Color>, val vibrant: Color, val muted: Color, val cells: IntArray) {
    companion object { const val Grid = 8 }
}

private val artCache = LruCache<String, ArtData>(16)

/** One decode of [url] (256 px, center-cropped square): palette, mesh colours and the averaged grid. Cached by URL. */
suspend fun loadArt(ctx: Context, url: String?): ArtData? {
    if (url == null) return null
    artCache.get(url)?.let { return it }
    val raw = withContext(Dispatchers.IO) {
        runCatching { (ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).size(256).build()) as? SuccessResult)?.image?.toBitmap() }.getOrNull()
    } ?: return null
    return withContext(Dispatchers.Default) { artData(raw) }.also { artCache.put(url, it) }
}

/** Everything derived from a decoded cover (also used for the editor's test covers). */
fun artData(raw: Bitmap): ArtData {
    val side = minOf(raw.width, raw.height)
    val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side)
        .let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
    val p = Palette.from(square).generate()
    fun c(s: Palette.Swatch?) = s?.rgb?.let { Color(it) }
    val band = c(p.vibrantSwatch) ?: c(p.dominantSwatch) ?: Color(0xFF8A8A93)
    val deep = c(p.darkVibrantSwatch) ?: lerp(band, Color.Black, 0.35f)
    var sea = c(p.darkMutedSwatch) ?: lerp(deep, Color.Black, 0.45f)
    if (luminance(sea.toArgb()) > 0.12) sea = lerp(sea, Color.Black, 0.45f) // backdrops stay dark enough for white text
    // the hue of the colour covering most of the cover (weighted by how colourful it is), so the accent clearly belongs to it
    val hue = p.swatches.filter { it.hsl[1] > 0.18f && it.hsl[2] in 0.12f..0.92f }.maxByOrNull { it.population * it.hsl[1] }?.hsl?.get(0)
    val muted = c(p.mutedSwatch) ?: c(p.lightMutedSwatch) ?: lerp(band, Color.Gray, 0.5f)
    val mesh = listOf(band, deep, muted, c(p.lightVibrantSwatch) ?: c(p.dominantSwatch) ?: band)
    val grid = Bitmap.createScaledBitmap(square, ArtData.Grid, ArtData.Grid, true)
    val cells = IntArray(ArtData.Grid * ArtData.Grid).also { grid.getPixels(it, 0, ArtData.Grid, 0, 0, ArtData.Grid, ArtData.Grid) }
    return ArtData(square, ArtPalette(band, sea, hue), mesh, band, muted, cells)
}

// ---- Contrast (WCAG 2.x), on ARGB ints so it is plain math ----

fun luminance(argb: Int): Double {
    fun ch(v: Int) = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
}

fun contrast(a: Int, b: Int): Double {
    val la = luminance(a); val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** [fg] at [alpha] over an opaque [bg] (sRGB blend, like the screen does it). */
fun over(fg: Int, alpha: Float, bg: Int): Int {
    fun mix(s: Int) = (((fg shr s) and 0xFF) * alpha + ((bg shr s) and 0xFF) * (1 - alpha)).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

private const val White = 0xFFFFFFFF.toInt()
private const val Ink = 0xFF08080A.toInt()

/** Muted text (artist, times) is white at this alpha; the dimmest icon (shuffle/repeat off) at [IconAlpha]. */
const val MutedText = 0.74f
const val IconAlpha = 0.6f

/** Text and icon contrast of the player's dimmest text and icon over [bg]. */
fun textIconContrast(bg: Int): Pair<Double, Double> = contrast(over(White, MutedText, bg), bg) to contrast(over(White, IconAlpha, bg), bg)

/**
 * The least black dim over [bg] (the brightest part of the background behind text, already under the [scrim] the
 * layout always draws there) so muted text reaches 4.5:1 and icons 3:1. The user's dim can only add to it.
 */
fun minDim(bg: Int, scrim: Float = 0f): Float {
    var a = 0f
    while (a < 0.95f) {
        val under = over(Ink, a + scrim * (1 - a), bg)
        val (t, i) = textIconContrast(under)
        if (t >= 4.5 && i >= 3.0) return a
        a += 0.01f
    }
    return 0.95f
}

/**
 * The least alpha of a [glass] tint (over any backdrop: the worst cases are pure white and pure black) at which
 * [text] drawn at [textAlpha] on it reaches [min]:1. Used for the frosted glass tints (see [Frost]).
 */
fun minGlassTint(glass: Int, text: Int, textAlpha: Float, min: Double): Float {
    for (i in 0..100) {
        val a = i / 100f
        if (intArrayOf(White, 0xFF000000.toInt()).all { b -> val g = over(glass, a, b); contrast(over(text, textAlpha, g), g) >= min }) return a
    }
    return 1f
}

/** [c] lightened (keeping its hue) until it has at least [target] contrast against [bg]. */
fun ensureContrast(c: Int, bg: Int, target: Double = 3.0): Int {
    var x = c
    var k = 0f
    while (contrast(x, bg) < target && k < 1f) { k += 0.05f; x = over(White, k, c) }
    return x
}

/** Dark ink or white on [fill], whichever reads better. */
fun onFill(fill: Int): Int = if (contrast(Ink, fill) >= contrast(White, fill)) 0xFF141414.toInt() else White

// ---- Backgrounds from the small bitmap: one cheap CPU blur, drawn scaled up (same on every Android version) ----

/** The cover shrunk (more for more [blur]) and box-blurred: drawn stretched to the screen it is a soft, smooth blur. */
fun blurredArt(src: Bitmap, blur: Int): Bitmap {
    if (blur <= 0) return src
    val w = (96 - blur / 100f * 84).roundToInt().coerceIn(10, 96)
    val small = boxBlur(Bitmap.createScaledBitmap(src, w, w, true), if (blur > 30) 2 else 1)
    // blurred again at 128 px: stretched ~25× instead of ~70×, the bilinear grid creases (the "muddy" look) melt away
    val soft = boxBlur(Bitmap.createScaledBitmap(small, 128, 128, true), 128 / w + 1)
    // the mockups' saturate(1.15): blurring greys colours out, this brings the cover's colour back
    val out = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(soft, 0f, 0f, Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(1.15f) }) })
    return out
}

/**
 * Extend: a small picture of the screen ([w]×[h] px) with the cover at its place ([cover], as fractions of the
 * screen) and its top and bottom edge rows stretched (fanning out) to the screen edges, or the cover mirrored there.
 */
fun extendedArt(src: Bitmap, w: Int, h: Int, cover: RectF, mirror: Boolean, blur: Int): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    val dst = RectF(cover.left * w, cover.top * h, cover.right * w, cover.bottom * h)
    val band = (src.height * 0.02f).roundToInt().coerceAtLeast(1)
    if (mirror) {
        cv.save(); cv.scale(1f, -1f, 0f, dst.top); cv.drawBitmap(src, null, dst, paint); cv.restore()
        cv.save(); cv.scale(1f, -1f, 0f, dst.bottom); cv.drawBitmap(src, null, dst, paint); cv.restore()
    } else {
        fun fan(srcRow: Int, from: Float, to: Float) {
            val slices = 8
            for (i in 0 until slices) {
                val d = (i + 0.5f) / slices
                val sw = dst.width() * (1f + 0.9f * d)
                val y0 = from + (to - from) * i / slices; val y1 = from + (to - from) * (i + 1) / slices
                cv.drawBitmap(src, Rect(0, srcRow, src.width, srcRow + band), RectF((w - sw) / 2, minOf(y0, y1) - 0.5f, (w + sw) / 2, maxOf(y0, y1) + 0.5f), paint)
            }
        }
        fan(0, dst.top, 0f)
        fan(src.height - band, dst.bottom, h.toFloat())
    }
    cv.drawBitmap(src, null, dst, paint)
    return boxBlur(out, 1 + (blur / 100f * 3).roundToInt())
}

/** Three box-blur passes (≈ gaussian) of [radius] px, horizontal then vertical. */
private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
    val tmp = IntArray(px.size)
    repeat(3) {
        pass(px, tmp, w, h, radius, horizontal = true)
        pass(tmp, px, w, h, radius, horizontal = false)
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    for (l in 0 until lines) {
        for (i in 0 until len) {
            var rr = 0; var gg = 0; var bb = 0; var n = 0
            for (k in -r..r) {
                val j = (i + k).coerceIn(0, len - 1)
                val p = src[if (horizontal) l * w + j else j * w + l]
                rr += (p shr 16) and 0xFF; gg += (p shr 8) and 0xFF; bb += p and 0xFF; n++
            }
            dst[if (horizontal) l * w + i else i * w + l] = (0xFF shl 24) or ((rr / n) shl 16) or ((gg / n) shl 8) or (bb / n)
        }
    }
}

/** The Customize editor's worst-case test covers: 0 white, 1 black, 2 busy, 3 saturated. */
internal fun testCover(kind: Int): Bitmap {
    val s = 128
    val px = IntArray(s * s) { i ->
        val x = i % s; val y = i / s
        when (kind) {
            0 -> 0xFFF7F7F2.toInt() // white
            1 -> 0xFF050505.toInt() // black
            2 -> { // busy: bright checker of colours
                val r = (x * 37 + y * 11) % 256; val g = (x * 7 + y * 41) % 256; val b = ((x xor y) * 5) % 256
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            else -> if ((x + y) % 64 < 32) 0xFFFF2A2A.toInt() else 0xFFFFD000.toInt() // saturated
        }
    }
    return Bitmap.createBitmap(px, s, s, Bitmap.Config.ARGB_8888)
}
