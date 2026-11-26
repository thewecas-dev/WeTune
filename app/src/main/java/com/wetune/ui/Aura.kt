package com.wetune.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import com.wetune.Playback

/** The logo's sky blues: the aura's colours when nothing is playing. */
private val BrandA = Color(0xFF74C0FF)
private val BrandB = Color(0xFF3B86F5)

/**
 * A fine grain tile (96 px, tiled): scattered light and dark specks at very low alpha. Laid over the aura it turns
 * flat gradients into something that reads like soft light on paper. Made once per process.
 */
internal val grain by lazy {
    val n = 96
    val rnd = java.util.Random(7)
    val px = IntArray(n * n) {
        val a = rnd.nextInt(28) // 0..27 of 255
        if (rnd.nextBoolean()) android.graphics.Color.argb(a, 255, 255, 255) else android.graphics.Color.argb(a, 0, 0, 0)
    }
    android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** A page's own cover for the backdrop while it shows (Home: the focused pick or the chosen mood); null = the playing song. */
object AuraArt { var art by androidx.compose.runtime.mutableStateOf<String?>(null) }

/**
 * The app's backdrop: a very light, blurred colour wash across the top of every page, with grain, fading to the page
 * background by the middle of the screen. Its colours come from the playing song's cover (brand blue otherwise) and
 * cross-fade when the song changes. Still: drawn once per colour change, no animation running.
 */
@Composable
fun Aura(modifier: Modifier = Modifier, art: String? = AuraArt.art ?: Playback.current?.art, strength: Float = if (WT.c.dark) 0.30f else 0.20f, brand: Boolean = true) {
    val c = WT.c
    val colors = com.wetune.ui.player.coverColors(art)
    // [brand] false (a page's own colour field): nothing until the art's colours are known, then they fade in
    val idleA = if (brand) BrandA else Color.Transparent
    val idleB = if (brand) BrandB else Color.Transparent
    // Settings › General › Background › Colour: none (0 %) to twice the original (100 %); 50 % is the original
    val wash = strength * com.wetune.Settings.bgColor.coerceIn(0, 100) / 50f
    val a by animateColorAsState((colors?.get(0) ?: idleA).copy(alpha = wash.coerceAtMost(1f)), tween(Motion.GLOW * 2), label = "auraA")
    val b by animateColorAsState((colors?.get(3) ?: idleB).copy(alpha = wash.coerceAtMost(1f)), tween(Motion.GLOW * 2), label = "auraB")
    val m by animateColorAsState((colors?.get(1) ?: idleA).copy(alpha = (wash * 0.7f).coerceAtMost(1f)), tween(Motion.GLOW * 2), label = "auraM")
    // Settings › General › Background: grain 0..2× the original, wash blobs 0.5..1.5× as wide (softer, more spread)
    val grainK = com.wetune.Settings.bgNoise.coerceIn(0, 100) / 50f
    val spread = 0.5f + com.wetune.Settings.bgBlur.coerceIn(0, 100) / 100f
    Box(
        modifier.fillMaxWidth().fillMaxHeight(0.55f)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } // so the fade below masks blobs and grain together
            .drawWithCache {
                val w = size.width; val h = size.height
                val blobA = Brush.radialGradient(listOf(a, a.copy(alpha = 0f)), center = Offset(w * 0.12f, h * 0.02f), radius = w * 0.85f * spread)
                val blobB = Brush.radialGradient(listOf(b, b.copy(alpha = 0f)), center = Offset(w * 0.92f, h * 0.12f), radius = w * 0.75f * spread)
                val blobM = Brush.radialGradient(listOf(m, m.copy(alpha = 0f)), center = Offset(w * 0.5f, h * 0.38f), radius = w * 0.6f * spread)
                val noise = ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
                val fade = Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black.copy(alpha = 0.6f), 1f to Color.Transparent)
                onDrawBehind {
                    drawRect(blobA); drawRect(blobB); drawRect(blobM)
                    drawRect(noise, alpha = ((if (c.dark) 0.55f else 0.4f) * grainK).coerceAtMost(1f))
                    drawRect(fade, blendMode = BlendMode.DstIn) // melts into the page background
                }
            },
    )
}
