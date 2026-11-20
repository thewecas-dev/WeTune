package com.wetune.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * The app's backdrop: a very light, blurred colour wash across the top of every page, with grain, fading to the page
 * background by the middle of the screen. Its colours come from the playing song's cover (brand blue otherwise) and
 * cross-fade when the song changes. Still: drawn once per colour change, no animation running.
 */
@Composable
fun Aura(modifier: Modifier = Modifier) {
    val c = WT.c
    val colors = com.wetune.ui.player.coverColors(Playback.current?.art)
    val strength = if (c.dark) 0.30f else 0.20f
    val a by animateColorAsState((colors?.get(0) ?: BrandA).copy(alpha = strength), tween(Motion.GLOW * 2), label = "auraA")
    val b by animateColorAsState((colors?.get(3) ?: BrandB).copy(alpha = strength), tween(Motion.GLOW * 2), label = "auraB")
    val m by animateColorAsState((colors?.get(1) ?: BrandA).copy(alpha = strength * 0.7f), tween(Motion.GLOW * 2), label = "auraM")
    Box(
        modifier.fillMaxWidth().fillMaxHeight(0.55f)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } // so the fade below masks blobs and grain together
            .drawWithCache {
                val w = size.width; val h = size.height
                val blobA = Brush.radialGradient(listOf(a, a.copy(alpha = 0f)), center = Offset(w * 0.12f, h * 0.02f), radius = w * 0.85f)
                val blobB = Brush.radialGradient(listOf(b, b.copy(alpha = 0f)), center = Offset(w * 0.92f, h * 0.12f), radius = w * 0.75f)
                val blobM = Brush.radialGradient(listOf(m, m.copy(alpha = 0f)), center = Offset(w * 0.5f, h * 0.38f), radius = w * 0.6f)
                val noise = ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
                val fade = Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black.copy(alpha = 0.6f), 1f to Color.Transparent)
                onDrawBehind {
                    drawRect(blobA); drawRect(blobB); drawRect(blobM)
                    drawRect(noise, alpha = if (c.dark) 0.55f else 0.4f)
                    drawRect(fade, blendMode = BlendMode.DstIn) // melts into the page background
                }
            },
    )
}
