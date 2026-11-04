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

/** The Now Playing looks besides "Bleeding edge": how the background is drawn and how the cover is framed. */
val NowStyles = listOf("bleed" to "Bleeding edge", "classic" to "Classic", "strips" to "Strips", "disc" to "Disc")

/**
 * Background for the classic, strips and disc looks (Bleeding edge uses [CoverBackdrop]).
 * - classic: the cover blurred big behind a framed card, darkening towards the controls
 * - strips: the same soft colors seen through ribbed (fluted) glass — vertical strips with lit and shaded edges
 * - disc: the cover blurred across the top, fading into a deep tint of its colors
 */
@Composable
fun StyledBackdrop(style: String, pal: ArtPalette, t: Track, split: Dp) {
    val deep = lerp(pal.sea, Color.Black, 0.72f)
    Box(Modifier.fillMaxSize().background(deep)) {
        when (style) {
            "disc" -> {
                // the cover's colors across the top ~60%, fading away into the tint
                Cover(
                    t,
                    Modifier.fillMaxWidth().height(split + 120.dp).graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; compositingStrategy = CompositingStrategy.Offscreen }
                        .blur(48.dp, BlurredEdgeTreatment.Rectangle)
                        .drawWithContent { drawContent(); drawRect(Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black.copy(alpha = 0.8f), 1f to Color.Transparent), blendMode = BlendMode.DstIn) },
                    alpha = 0.85f,
                )
            }
            else -> Cover(t, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.blur(if (style == "strips") 56.dp else 80.dp, BlurredEdgeTreatment.Rectangle), alpha = 0.9f)
        }
        if (style == "strips") Canvas(Modifier.fillMaxSize()) {
            // ribbed glass: each strip is lit on its left edge and shaded on its right, like light bending through flutes
            val w = 15.dp.toPx()
            var x = -w * 0.3f
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

/** The framed cover for classic (rounded card), strips (rounded card) and disc (circle), [size] square, centered. */
@Composable
fun StyledCover(style: String, t: Track, shift: () -> Float, size: Dp, modifier: Modifier = Modifier) {
    val shape = if (style == "disc") CircleShape else RoundedCornerShape(if (style == "strips") 18.dp else 22.dp)
    Box(modifier.fillMaxWidth(), Alignment.TopCenter) {
        Cover(
            t,
            Modifier.size(size).graphicsLayer { translationX = shift() }
                .shadow(28.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f)).clip(shape),
        )
    }
}
