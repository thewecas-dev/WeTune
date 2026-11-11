package com.wetune.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wetune.sources.Track
import com.wetune.Playback
import com.wetune.rememberPosition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How the cover is framed. "edge" is the cover itself running full width (drawn by [CoverBackdrop]); the rest by [StyledCover]. */
val CoverStyles = listOf("edge" to "Full width", "card" to "Card", "disc" to "Disc", "strips" to "Strips", "vinyl" to "Vinyl")

/** What fills the screen behind the cover; independent of the cover style. The first three are drawn by [CoverBackdrop]. */
val Backgrounds = listOf("stretch" to "Stretch", "mirror" to "Reflection", "blur" to "Blur", "fade" to "Fade", "ribbed" to "Ribbed glass", "black" to "Black")
val CoverFills = setOf("stretch", "mirror", "blur")

/**
 * The backgrounds that don't come from the cover's own edges:
 * - fade: the cover's colors blurred across the top, fading into a deep tint of them
 * - ribbed: the cover's soft colors seen through ribbed (fluted) glass — vertical strips with lit and shaded edges
 * - black: plain near-black, so the cover is the only color on screen
 */
@Composable
fun StyledBackdrop(style: String, pal: ArtPalette, t: Track, split: Dp) {
    if (style == "black") return Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0B)))
    val deep = lerp(pal.sea, Color.Black, 0.72f)
    Box(Modifier.fillMaxSize().background(deep)) {
        if (style == "fade") {
            // the cover's colors across the top ~60%, fading away into the tint
            Cover(
                t,
                Modifier.fillMaxWidth().height(split + 120.dp).graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; compositingStrategy = CompositingStrategy.Offscreen }
                    .blur(48.dp, BlurredEdgeTreatment.Rectangle)
                    .drawWithContent { drawContent(); drawRect(Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black.copy(alpha = 0.8f), 1f to Color.Transparent), blendMode = BlendMode.DstIn) },
                alpha = 0.85f,
            )
        } else Cover(t, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.blur(55.dp, BlurredEdgeTreatment.Rectangle), alpha = 0.9f)
        if (style == "ribbed") Canvas(Modifier.fillMaxSize()) {
            // ribbed glass: each strip is lit on its left edge and shaded on its right, like light bending through flutes
            val w = size.width / 20 // 20 strips across the screen
            var x = 0f
            val strip = Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0.16f), 0.18f to Color.White.copy(alpha = 0.05f), 0.55f to Color.Transparent,
                0.85f to Color.Black.copy(alpha = 0.16f), 1f to Color.Black.copy(alpha = 0.28f), startX = 0f, endX = w,
            )
            while (x < size.width) {
                translate(x, 0f) { drawRect(strip, Offset.Zero, Size(w, size.height)) }
                x += w
            }
        }
        // the lower part darkens so the title, lyrics and controls always read clearly
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.15f), 0.45f to Color.Black.copy(alpha = 0.25f), 0.7f to deep.copy(alpha = 0.75f), 1f to deep)))
    }
}

/**
 * The framed cover, [size] square, centered:
 * - card: rounded card
 * - disc: a circle inside a thin progress ring with a knob at the playhead
 * - strips: the cover cut into five rounded columns at staggered heights, drifting gently while playing
 * - vinyl: a square sleeve with the record sliding out to the right, spinning while playing
 */
@Composable
fun StyledCover(style: String, t: Track, shift: () -> Float, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), Alignment.TopCenter) {
        Box(Modifier.size(size).graphicsLayer { translationX = shift() }) {
            when (style) {
                "disc" -> DiscCover(t, size)
                "strips" -> StripsCover(t, size)
                "vinyl" -> VinylCover(t, size)
                else -> Cover(t, Modifier.fillMaxSize().lift(RoundedCornerShape(22.dp))) // card
            }
        }
    }
}

private fun Modifier.lift(shape: Shape) =
    shadow(28.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f)).clip(shape)

@Composable
private fun DiscCover(t: Track, size: Dp) {
    val pos = rememberPosition()
    Cover(t, Modifier.fillMaxSize().padding(14.dp).lift(CircleShape))
    // the ring redraws on its own; reading the position only in the draw step keeps the cover from recomposing
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Song progress" }) {
        val stroke = 2.dp.toPx()
        val r = this.size.minDimension / 2 - 5.dp.toPx()
        val tl = Offset(center.x - r, center.y - r)
        val arc = Size(r * 2, r * 2)
        val d = Playback.duration().takeIf { it > 0 } ?: t.durationMs
        val f = if (d > 0) (pos.value.toFloat() / d).coerceIn(0f, 1f) else 0f
        drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, tl, arc, style = Stroke(stroke))
        drawArc(Color.White.copy(alpha = 0.9f), -90f, 360f * f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        val a = Math.toRadians((-90f + 360f * f).toDouble())
        drawCircle(Color.White, 5.dp.toPx(), Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
    }
}

// top and bottom of each column as a fraction of the cover's height (a soft wave, like the reference)
private val StripTops = floatArrayOf(0f, 0.14f, 0.22f, 0.15f, 0.24f)
private val StripBottoms = floatArrayOf(0.93f, 1f, 0.94f, 0.85f, 0.85f)

@Composable
private fun StripsCover(t: Track, size: Dp) {
    val playing = Playback.playing
    val clock by rememberInfiniteTransition(label = "strips").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "c")
    val amp by animateFloatAsState(if (playing) 1f else 0f, tween(800), label = "amp") // drift eases in and out with play/pause
    val gap = 6.dp
    val colW = size / 5
    repeat(5) { i ->
        val x = colW * i + gap / 2
        val top = size * StripTops[i]
        Box(
            Modifier.offset(x = x, y = top).size(colW - gap, size * (StripBottoms[i] - StripTops[i]))
                .graphicsLayer { translationY = sin(clock + i * 1.1f) * 5.dp.toPx() * amp }
                .lift(RoundedCornerShape(14.dp)),
        ) {
            // each column shows its own slice of one continuous cover
            Cover(t, Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).offset(x = -x, y = -top).requiredSize(size))
        }
    }
}

@Composable
private fun VinylCover(t: Track, size: Dp) {
    val playing = Playback.playing
    // the record slides out while playing and tucks back into the sleeve when paused
    val out by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow), label = "out")
    val spin = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        while (playing) spin.animateTo(spin.value + 360f, tween(4000, easing = LinearEasing)).also { spin.snapTo(spin.value % 360f) }
    }
    val sleeve = size * 0.8f
    val rec = sleeve * 0.94f
    // sleeve + the part of the record that shows stays centered as it slides
    Box(Modifier.fillMaxSize().graphicsLayer { translationX = -(sleeve * 0.21f * out).toPx() }, Alignment.CenterStart) {
        Box(
            Modifier.offset(x = (size - sleeve) / 2 + (sleeve - rec) / 2).size(rec)
                .graphicsLayer { translationX = (sleeve * 0.42f * out).toPx(); rotationZ = spin.value },
        ) { Record(t) }
        Cover(t, Modifier.offset(x = (size - sleeve) / 2).size(sleeve).lift(RoundedCornerShape(10.dp)))
    }
}

/** A black record: fine grooves, a soft sheen, and the cover as its center label. */
@Composable
private fun Record(t: Track) = Box(Modifier.fillMaxSize()) {
    Canvas(Modifier.fillMaxSize().shadow(18.dp, CircleShape)) {
        val r = size.minDimension / 2
        drawCircle(Color(0xFF0B0B0C), r)
        var g = r * 0.97f
        while (g > r * 0.38f) { drawCircle(Color.White.copy(alpha = 0.035f), g, style = Stroke(0.8f)); g -= r * 0.025f }
        drawCircle(
            Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent)),
            r,
        )
    }
    Cover(t, Modifier.fillMaxSize(0.34f).align(Alignment.Center).clip(CircleShape))
}
