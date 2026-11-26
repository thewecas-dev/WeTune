package com.wetune.ui.player

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.wetune.Playback
import com.wetune.Settings
import com.wetune.sources.Track
import com.wetune.ui.Txt
import com.wetune.ui.Type
import kotlin.math.min

val CoverStyles = listOf(
    "plain" to "Plain", "squircle" to "Squircle", "glow" to "Glow", "tilt" to "Tilt",
    "polaroid" to "Polaroid", "stack" to "Stack", "disc" to "Disc", "record" to "Sleeve and record", "cue" to "WeTune mark", "edge" to "Full width",
)

/** The cover style in use. "edge" (Full width): the cover edge to edge at the very top, fading into the background. */
val coverStyle: String get() = Settings.coverStyle.takeIf { s -> CoverStyles.any { it.first == s } } ?: "plain"

/** Styles drawn inside the cover's own rounded rect: the cover layer clips and shadows them itself. */
fun coverClips(style: String) = style == "plain" || style == "squircle" || style == "edge"

/** The cover's outline for [style]: continuous (smoothed) corners for Squircle, plain rounded corners otherwise. */
fun Corners.shapeFor(style: String): Shape = if (style == "squircle") squircle(this) else shape()

/**
 * The cover in its [style], inside the cover's rect. [art] draws the artwork (and the animated cover over it) filling
 * whatever box it's given. Moving styles move only while music plays and hold still otherwise.
 */
@Composable
fun StyledCover(t: Track, style: String, corners: Corners, art: @Composable () -> Unit) {
    when (style) {
        "glow" -> Glow(t, corners, art)
        "tilt" -> Tilt(corners, art)
        "polaroid" -> Polaroid(t, art)
        "stack" -> Stack(corners, art)
        "disc" -> Disc(t, art)
        "record" -> Record(t, corners, art)
        "cue" -> Cue(t)
        else -> art()
    }
}

/** A 0..1 value swinging back and forth over [ms] each way while music plays; [rest] otherwise. */
@Composable
private fun sway(ms: Int, rest: Float = 0.5f): State<Float> =
    swinging(ms, Playback.playing, rest, FastOutSlowInEasing)

/** Degrees turned, one turn per [ms] while [on]; holds where it is otherwise. */
@Composable
private fun spin(ms: Int, on: Boolean): State<Float> =
    turning(ms, on)

/** Whether the animated cover is showing for [t] (round styles stop turning then: a spinning video is hard to watch). */
private fun videoShowing(t: Track) = Settings.npVideoCover && NowPlaying.videoOn && NowPlaying.videoFor == t.id

/**
 * Glow: the cover floats on a soft copy of itself (blur 36, 1.6× saturation) in its own shape, centred so the glow spreads
 * evenly on every side, breathing over 8 s. The copy sits in a box 30 % larger on every side so its blur can spread past
 * the cover's edges (a blur is cut off at its own layer's bounds, which left a sharp rectangle).
 */
@Composable
private fun Glow(t: Track, corners: Corners, art: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val img = remember(t.art) { ImageRequest.Builder(ctx).data(t.art).size(if (Build.VERSION.SDK_INT >= 31) 96 else 16).build() }
    val sat = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.6f) }) }
    val breath = sway(8_000, rest = 1f)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val pad = min(maxWidth.value, maxHeight.value).dp * 0.3f
        Box(
            Modifier.requiredSize(maxWidth + pad * 2, maxHeight + pad * 2).graphicsLayer {
                val b = breath.value
                scaleX = 1.04f + 0.04f * b; scaleY = scaleX
                alpha = 0.7f + 0.3f * b
            }.then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(36.dp, BlurredEdgeTreatment.Unbounded) else Modifier).padding(pad),
        ) { AsyncImage(img, null, Modifier.fillMaxSize().clip(corners.shape()), contentScale = ContentScale.Crop, colorFilter = sat) }
        Box(Modifier.fillMaxSize().clip(corners.shape())) { art() }
    }
}

/** Tilt: the cover turned in 3D (resting at Y −15°, X 5.5°), swaying Y −20°↔−10° over 10 s, over a soft floor shadow. */
@Composable
private fun Tilt(corners: Corners, art: @Composable () -> Unit) {
    val s = sway(10_000)
    Box(Modifier.fillMaxSize()) {
        // the floor: a blurred dark ellipse just under the cover
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = size.height * 0.5f }.then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(12.dp, BlurredEdgeTreatment.Unbounded) else Modifier).drawBehind {
            drawOval(Color.Black.copy(alpha = 0.5f), Offset(size.width * 0.12f, size.height * 0.47f), Size(size.width * 0.76f, size.height * 0.09f))
        })
        Box(Modifier.fillMaxSize().graphicsLayer {
            scaleX = 0.92f; scaleY = 0.92f
            rotationY = -20f + 10f * s.value
            rotationX = 7f - 3f * s.value
            cameraDistance = 12f * density
            shape = corners.shape(); clip = true
        }) { art() }
    }
}

/**
 * Polaroid: an off-white instant print around the cover in its own shape (border 4.5 %, a deeper 14 % bottom with the
 * song's title), resting at −2.5° and sized to fit the cover's space.
 */
@Composable
private fun Polaroid(t: Track, art: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val r = maxWidth / maxHeight // the cover's shape, width / height
        // print width W: photo (W − 2·4.5 %) at the cover's shape, plus 4.5 % on top and 14 % below, fitting 94 % of the space
        val w = min(maxWidth.value * 0.94f, maxHeight.value * 0.94f / (0.185f + 0.91f / r)).dp
        val pad = w * 0.045f; val photoW = w - pad * 2
        Column(
            Modifier.width(w).graphicsLayer { rotationZ = -2.5f }
                .shadow(14.dp, RoundedCornerShape(3.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .background(Color(0xFFF4F2EE), RoundedCornerShape(3.dp))
                .padding(start = pad, end = pad, top = pad),
        ) {
            Box(Modifier.size(photoW, photoW / r).clip(RoundedCornerShape(1.dp))) { art() }
            Box(Modifier.fillMaxWidth().height(w * 0.14f), contentAlignment = Alignment.CenterStart) {
                Txt(t.title, Type.sub.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = Color(0xFF3A3A3E))
            }
        }
    }
}

/** Stack: the previous and next songs' covers fanned out behind (scale 0.88, ±16 % sideways, ±7°, dimmed by half). */
@Composable
private fun Stack(corners: Corners, art: @Composable () -> Unit) {
    val q = Playback.queue; val i = Playback.index
    StackedCovers(q.getOrNull(i - 1)?.art, q.getOrNull(i + 1)?.art, corners.shape(), art = art)
}

/**
 * Stack's drawing, also the playlist pages' art: [left] and [right] fanned out behind [art]. [fan] 0..1 is how far they
 * have come out from behind it (1 = resting; read in draw only); [shadow] the front cover's shadow.
 */
@Composable
fun StackedCovers(left: String?, right: String?, shape: Shape, fan: () -> Float = { 1f }, shadow: androidx.compose.ui.unit.Dp = 18.dp, art: @Composable () -> Unit) {
    val dim = remember { ColorFilter.tint(Color.Black.copy(alpha = 0.5f), BlendMode.SrcAtop) }
    Box(Modifier.fillMaxSize()) {
        listOf(left to -1f, right to 1f).forEach { (a, side) ->
            if (a != null) AsyncImage(
                a, null, contentScale = ContentScale.Crop, colorFilter = dim,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val f = fan()
                    scaleX = 0.88f; scaleY = 0.88f
                    translationX = side * size.width * 0.16f * f; rotationZ = side * 7f * f
                    this.shape = shape; clip = true
                },
            )
        }
        Box(Modifier.fillMaxSize().shadow(shadow, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape)) { art() }
    }
}

/** Disc: the cover as a round disc with a clear centre hole and hub, turning once every 30 s while music plays. */
@Composable
private fun Disc(t: Track, art: @Composable () -> Unit) {
    val turn = spin(30_000, Playback.playing && !videoShowing(t))
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val d = min(maxWidth.value, maxHeight.value).dp
        Box(
            Modifier.size(d).shadow(16.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .graphicsLayer { rotationZ = turn.value; shape = CircleShape; clip = true; compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val r = size.minDimension / 2f
                    drawCircle(Color.White.copy(alpha = 0.22f), r * 0.30f) // the hub
                    drawCircle(Color.Black.copy(alpha = 0.22f), r * 0.30f, style = Stroke(r * 0.012f))
                    drawCircle(Color.Black, r * 0.12f, blendMode = BlendMode.Clear) // the hole: the background shows through
                },
        ) { art() }
    }
}

/**
 * Sleeve and record: the sleeve (the cover in its own shape) with the record slid half out to its right (94 % of the
 * sleeve's shorter side, the cover as its label), turning once every 12 s while music plays. Both fit the cover's space.
 */
@Composable
private fun Record(t: Track, corners: Corners, art: @Composable () -> Unit) {
    val turn = spin(12_000, Playback.playing)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        val r = maxWidth / maxHeight // the cover's shape, width / height
        val k = 0.94f * min(1f, 1f / r) // record ÷ sleeve width
        // sleeve width: the record's outer half still fits on the right, and the sleeve's height fits
        val sw = min(maxWidth.value / (1f + k / 2f), maxHeight.value * r).dp
        val sleeveSize = androidx.compose.ui.unit.DpSize(sw, sw / r); val disc = sw * k; val sleeve = sw
        // the record, centred on the sleeve's right edge
        Box(
            Modifier.offset(x = sleeve - disc / 2f).size(disc)
                .shadow(14.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .graphicsLayer { rotationZ = turn.value; shape = CircleShape; clip = true }
                .drawBehind {
                    val r = size.minDimension / 2f
                    drawCircle(Color(0xFF111113), r)
                    var g = r * 0.97f // grooves, 1.8 % apart, out to the label
                    while (g > r * 0.40f) { drawCircle(Color(0xFF1C1C20), g, style = Stroke(r * 0.012f)); g -= r * 0.036f }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(t.art, null, Modifier.size(disc * 0.38f).clip(CircleShape), contentScale = ContentScale.Crop)
            Box(Modifier.size(disc * 0.03f).background(Color(0xFF0A0A0C), CircleShape))
        }
        val sleeveShape = corners.scale(sw.value / maxWidth.value).shape() // the cover's corners, at the sleeve's size
        Box(Modifier.size(sleeveSize).shadow(14.dp, sleeveShape, ambientColor = Color.Black, spotColor = Color.Black).clip(sleeveShape)) { art() }
    }
}

/**
 * WeTune mark: the app's logo itself as the cover ("Cue", res/drawable/ic_launcher_background + _foreground): the cream
 * tile's gentle top-to-bottom tone, the record with its centre punched through to the tile, the logo's own soft
 * fall-off shadows, and the playhead dot with its cream ring, the dot in the song's vivid tone (easing over to the next
 * song's). Round like the app's badge (Home's AppMark), as big as fits the cover's space. The mark breathes (1↔1.03×,
 * 6 s each way) while music plays and holds still otherwise; only drawing reads the motion.
 */
@Composable
private fun Cue(t: Track) {
    val dot = androidx.compose.animation.animateColorAsState(vivid(coverColors(t.art), Color(0xFFFFB020), 0f), tween(com.wetune.ui.Motion.GLOW), label = "dot")
    val breath = sway(6_000, rest = 0f)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val d = min(maxWidth.value, maxHeight.value).dp
        Box(Modifier.size(d).clip(CircleShape).drawWithCache {
            // the tile shows 72 of its 108 units (18..90): logo units → px
            val k = size.minDimension / 72f
            fun at(x: Float, y: Float) = Offset((x - 18f) * k, (y - 18f) * k)
            val tile = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF8F5EE), Color(0xFFE8E3D8)))
            val record = androidx.compose.ui.graphics.Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addOval(androidx.compose.ui.geometry.Rect(at(54f, 54f), 23f * k))
                addOval(androidx.compose.ui.geometry.Rect(at(54f, 54f), 4f * k))
            }
            fun shade(c: Offset, r: Float, a: Color, b: Color, mid: Float) =
                androidx.compose.ui.graphics.Brush.radialGradient(0f to a, mid to b, 1f to Color.Transparent, center = c, radius = r)
            val recordShade = shade(at(54f, 56.5f), 28f * k, Color(0x1C000000), Color(0x2E000000), 0.82f)
            val dotShade = shade(at(70.26f, 39.54f), 10f * k, Color(0x21000000), Color(0x38000000), 0.6f)
            onDrawBehind {
                drawRect(tile)
                scale(1f + 0.03f * breath.value, pivot = at(54f, 54f)) {
                    drawCircle(recordShade, 28f * k, at(54f, 56.5f))
                    drawPath(record, Color(0xFF141414))
                    drawCircle(dotShade, 10f * k, at(70.26f, 39.54f))
                    drawCircle(dot.value, 6.5f * k, at(70.26f, 37.74f))
                    drawCircle(Color(0xFFF3EFE6), 6.5f * k, at(70.26f, 37.74f), style = Stroke(2.5f * k))
                }
            }
        })
    }
}

/**
 * Continuous ("squircle") corners: each corner's curve starts 1.5× its radius from the corner and eases in with one
 * smooth cubic, so the edge bends gradually instead of meeting a circular arc abruptly (iOS-style smoothing).
 */
fun squircle(c: Corners): Shape = GenericShape { size, _ ->
    val w = size.width; val h = size.height
    fun a(r: Float) = (r * 1.5f).coerceAtMost(min(w, h) / 2f)
    val k = 0.28f // control points: how far from the corner, as a share of the curve's length (0.45 ≈ circular)
    val tl = a(c.tl); val tr = a(c.tr); val br = a(c.br); val bl = a(c.bl)
    moveTo(tl, 0f)
    lineTo(w - tr, 0f); cubicTo(w - tr * k, 0f, w, tr * k, w, tr)
    lineTo(w, h - br); cubicTo(w, h - br * k, w - br * k, h, w - br, h)
    lineTo(bl, h); cubicTo(bl * k, h, 0f, h - bl * k, 0f, h - bl)
    lineTo(0f, tl); cubicTo(0f, tl * k, tl * k, 0f, tl, 0f)
    close()
}
