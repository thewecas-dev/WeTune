package com.wetune.ui.player

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.wetune.Playback
import com.wetune.Settings
import com.wetune.sources.Track
import com.wetune.ui.Motion
import com.wetune.ui.WT
import com.wetune.ui.LocalColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Backdrop styles, in the order the picker lists them: value to label. Four, each clearly its own kind of picture. */
val Backdrops = listOf(
    "flow" to "Colour flow", "gradient" to "Gradient", "blur" to "Blurred art", "edge" to "Full-width cover",
    "halo" to "Halo", "aurora" to "Aurora", "film" to "Film", "spot" to "Spotlight", "sweep" to "Sweep",
)

/** The chosen backdrop; retired styles read as the nearest one left (subtle glow, mesh → colour flow; frost, blur with fade, fluted → blurred art). */
val backdrop: String get() = when (val s = Settings.npBackground) {
    "glow", "mesh" -> "flow"
    "frost", "blurdark", "stripes", "video" -> "blur"
    "flow", "gradient", "blur", "edge", "halo", "aurora", "film", "spot", "sweep" -> s
    else -> "flow"
}

/** What's behind the cover (Settings.npBackground), cross-fading 600 ms between songs and styles. [live]: animate the mesh. */
@Composable
fun Backdrop(t: Track, style: String = backdrop, live: Boolean = true) {
    // A new song or style is shown only once its colours and blurred image are loaded, then faded in as a whole: started
    // early it drew a fallback first and filled in step by step (a bright flash, a sharp image, then blur and dimming).
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf(t.art to style) }
    LaunchedEffect(t.art, style) {
        withTimeoutOrNull(1500) { prepare(ctx, t.art) } // offline or slow: show what there is rather than wait forever
        shown = t.art to style
    }
    Crossfade(shown, animationSpec = tween(Motion.GLOW), label = "backdrop") { (art, s) ->
        when (s) {
            "flow" -> Flow(art, live)
            "gradient" -> Gradient(art)
            "blur" -> BlurredArt(art)
            "edge" -> Edge(art)
            "halo" -> Halo(art, live)
            "aurora" -> Aurora(art, live)
            "film" -> Film(art)
            "spot" -> Spotlight(art)
            "sweep" -> Sweep(art, live)
            else -> Flow(art, live)
        }
    }
    ReadableVeil(shown.first)
}

/** Loads what a backdrop needs before it shows: the cover's colours and the small image the blurred styles draw. */
private suspend fun prepare(ctx: android.content.Context, art: String?) {
    if (art == null) return
    kotlinx.coroutines.coroutineScope {
        launch { loadColors(ctx, art) }
        launch(Dispatchers.IO) { runCatching { SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(art).size(BLUR_PX).build()) } }
    }
}

/** The cover's sampled colours (cached), and its own width / height into [ratios]. */
private suspend fun loadColors(ctx: android.content.Context, art: String): List<Color>? = colorCache[art] ?: withContext(Dispatchers.IO) {
    runCatching {
        val r = SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(art).size(12).allowHardware(false).build())
        (r as? SuccessResult)?.image?.toBitmap()?.also { ratios[art] = it.width.toFloat() / it.height.coerceAtLeast(1) }?.let(::sample)
    }.getOrNull()
}?.also { colorCache[art] = it }

/**
 * Keeps Now Playing's text readable on any artwork: a bright cover (white, grey) makes a bright backdrop, and the text
 * is white. Measures the cover's brightness and lays just enough black over the backdrop to bring it under the level
 * white text needs (light theme: white over a dark cover, for black text). Nothing on covers already dark enough.
 */
@Composable
private fun ReadableVeil(art: String?) {
    val c = WT.c
    val colors = coverColors(art)
    val lum = colors?.let { l -> l.sumOf { it.luminance().toDouble() }.toFloat() / l.size } ?: (if (c.dark) 0f else 1f)
    val target = if (c.dark) {
        // overlaying black at a: brightness → lum·(1 − a); keep it ≤ 0.16
        Color.Black.copy(alpha = if (lum > 0.16f) (1f - 0.16f / lum).coerceIn(0f, 0.8f) else 0f)
    } else {
        // white at a: brightness → lum + (1 − lum)·a; keep it ≥ 0.62
        Color.White.copy(alpha = if (lum < 0.62f) ((0.62f - lum) / (1f - lum)).coerceIn(0f, 0.8f) else 0f)
    }
    val veil by androidx.compose.animation.animateColorAsState(target, tween(Motion.GLOW), label = "veil")
    Box(Modifier.fillMaxSize().drawBehind { if (veil.alpha > 0f) drawRect(veil) })
}

// ---- cover colours ------------------------------------------------------------------------------------------------

/** Four corners (top-left, top-right, bottom-left, bottom-right) and the bottom edge, averaged from a 12px copy. */
private val colorCache = ConcurrentHashMap<String, List<Color>>()

/** Width / height of each artwork seen, for the "original" cover shape (filled in by the same tiny fetch). */
private val ratios = androidx.compose.runtime.mutableStateMapOf<String, Float>()

/** The artwork's own width / height (1 until known). */
@Composable
fun coverRatio(art: String?): Float { coverColors(art); return art?.let { ratios[it] } ?: 1f }

@Composable
fun coverColors(art: String?): List<Color>? {
    val ctx = LocalContext.current
    return produceState(art?.let { colorCache[it] }, art) {
        // produceState keeps the previous song's value across a key change: start from this artwork's own
        value = art?.let { colorCache[it] }
        if (art == null || value != null) return@produceState
        value = loadColors(ctx, art)
    }.value
}

private fun sample(b: Bitmap): List<Color> {
    val w = b.width; val h = b.height
    fun avg(x0: Int, y0: Int, x1: Int, y1: Int): Color {
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        for (y in y0 until y1) for (x in x0 until x1) { val p = b.getPixel(x, y); r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++ }
        n = n.coerceAtLeast(1)
        return Color((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }
    return listOf(avg(0, 0, w / 2, h / 2), avg(w / 2, 0, w, h / 2), avg(0, h / 2, w / 2, h), avg(w / 2, h / 2, w, h), avg(0, h - 1, w, h))
}

// ---- styles -------------------------------------------------------------------------------------------------------

/** Size (px) of the cover copy the blurred backgrounds draw; [prepare] loads this same size so it's ready on the first frame. */
private const val BLUR_PX = 128

/** A cover image of [px] pixels: backgrounds blur it heavily, so a small copy looks the same and costs far less. */
@Composable
private fun small(art: String?, px: Int): ImageRequest {
    val ctx = LocalContext.current
    return remember(art, px) { ImageRequest.Builder(ctx).data(art).size(px).build() }
}

private fun saturation(s: Float) = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(s) })

/** The cover's most colourful corner (the "vivid" tone), darkened by [dark] (0..1). */
private fun vivid(cs: List<Color>?, fallback: Color, dark: Float): Color {
    val best = cs?.take(4)?.maxByOrNull { c -> val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c.toArgb(), hsv); hsv[1] * hsv[2] } ?: return fallback
    return lerp(best, Color.Black, dark)
}

/** A top-to-bottom wash in the page's background colour (black in dark mode), from [top] to [bottom] strength. */
private fun Modifier.wash(bg: Color, top: Float, bottom: Float) = drawBehind { drawRect(Brush.verticalGradient(listOf(bg.copy(alpha = top), bg.copy(alpha = bottom)))) }

/**
 * Colour flow (default; Apple Music / Metrolist "Liquid" style): three copies of the cover, blurred 46 and 1.7× more
 * saturated, turning slowly at 130 s, 90 s (the other way) and 70 s per turn over the cover's vivid tone, under a wash
 * from 20 % to 50 %. Turns only while music plays; otherwise it holds still.
 */
private class Layer(val angle: State<Float>, val dir: Float, val dx: Float, val dy: Float, val alpha: Float)

@Composable
private fun Flow(art: String?, live: Boolean) {
    val bg = WT.c.bg
    val cs = coverColors(art)
    val img = small(art, BLUR_PX)
    val sat = remember { saturation(1.7f) }
    val moving = live && Playback.playing
    @Composable fun turn(ms: Int): State<Float> = turning(ms, moving, start = ms / 1000f) // a different starting angle per layer
    // (turn, direction, nudge off centre as a fraction of the screen width, alpha)
    val layers = listOf(Layer(turn(130_000), 1f, 0f, 0f, 0.85f), Layer(turn(90_000), -1f, -0.1f, 0.12f, 0.7f), Layer(turn(70_000), 1f, 0.1f, -0.12f, 0.55f))
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(vivid(cs, bg, 0.25f)), contentAlignment = Alignment.Center) {
        // each copy is a square wider than the screen's diagonal (plus its nudge), so no edge or corner ever turns into view
        val side = kotlin.math.hypot(maxWidth.value, maxHeight.value).dp * 1.4f
        val w = maxWidth
        layers.forEach { l ->
            AsyncImage(
                img, null, contentScale = ContentScale.Crop, colorFilter = sat,
                modifier = Modifier.requiredSize(side).offset(w * l.dx, w * l.dy).graphicsLayer {
                    rotationZ = l.angle.value * l.dir; alpha = l.alpha
                }.then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(46.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
            )
        }
        Box(Modifier.fillMaxSize().wash(bg, 0.2f, 0.5f))
    }
}

/** Gradient (Spotify style): the cover's most colourful corner, darkened 25 %, fading into the page colour by 70 % down. */
@Composable
private fun Gradient(art: String?) {
    val bg = WT.c.bg
    val top = vivid(coverColors(art), bg, 0.25f)
    Box(Modifier.fillMaxSize().drawBehind { drawRect(Brush.verticalGradient(0f to top, 0.7f to bg, 1f to bg)) })
}

/** Blurred art: a small copy of the cover enlarged 1.4×, blurred 58 and 1.2× more saturated, under a wash from 34 % to 64 %. */
@Composable
private fun BlurredArt(art: String?) {
    val bg = WT.c.bg
    val sat = remember { saturation(1.2f) }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            small(art, if (Build.VERSION.SDK_INT >= 31) BLUR_PX else 24), null, contentScale = ContentScale.Crop, colorFilter = sat,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(58.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
        )
        Box(Modifier.fillMaxSize().wash(bg, 0.34f, 0.64f))
    }
}

/** A cover colour at [f] brightness (0..1): the backgrounds below use the cover's tones dimmed to set a dark stage. */
private fun dim(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)

/** Where the cover sits by default, as a fraction of the screen (for lights and glows centred on it). */
private const val COVER_Y = 0.36f

/** A 0..1 value that eases back and forth over [ms] while music plays; [rest] when it doesn't. */
@Composable
private fun swing(live: Boolean, ms: Int, rest: Float = 0.5f, easing: Easing = LinearEasing): State<Float> =
    swinging(ms, live && Playback.playing, rest, easing)

/**
 * Halo: a soft glow of the cover's vivid tone behind it, breathing (scale 0.94↔1.06, strength 70↔100 %, 6 s each way)
 * while music plays, over that tone at 10 % brightness. One gradient, no blur.
 */
@Composable
private fun Halo(art: String?, live: Boolean) {
    val bg = WT.c.bg
    val v = vivid(coverColors(art), bg, 0f)
    val breath = swing(live, 6_000, rest = 1f, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    Box(Modifier.fillMaxSize().background(dim(v, 0.1f)).drawBehind {
        val b = breath.value
        // a circle 1.3 screen-widths across (its corner reach ≈ 0.92 widths), fading out at 68 % of that
        val r = size.width * 0.92f * (0.94f + 0.12f * b)
        val a = 0.7f + 0.3f * b
        drawRect(Brush.radialGradient(0f to v.copy(alpha = 0.8f * a), 0.35f to v.copy(alpha = 0.35f * a), 0.68f to Color.Transparent, center = Offset(size.width / 2f, size.height * COVER_Y), radius = r))
        drawRect(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))) // calmer under the controls
    })
}

/**
 * Aurora: three slow ribbons of the cover's colours across the top half (at 4 / 17 / 30 % down, tilted −14°, blurred 26),
 * sliding ±16 % over 60 / 80 / 100 s while music plays, over the vivid tone at 14 % fading to black by 70 %.
 */
@Composable
private fun Aurora(art: String?, live: Boolean) {
    val cs = coverColors(art)
    val bg = WT.c.bg
    val v = vivid(cs, bg, 0f)
    val q = cs?.take(4) ?: List(4) { v }
    val ribbons = listOf(Triple(0.04f to 0.22f, q[0] to v, swing(live, 60_000)), Triple(0.17f to 0.20f, v to q[1], swing(live, 80_000)), Triple(0.30f to 0.18f, q[3] to q[0], swing(live, 100_000)))
    Box(Modifier.fillMaxSize().drawBehind { drawRect(Brush.verticalGradient(0f to dim(v, 0.14f), 0.7f to Color.Black, 1f to Color.Black)) }) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = -14f }
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(26.dp, BlurredEdgeTreatment.Unbounded) else Modifier)
                .drawBehind {
                    for ((place, cols, slide) in ribbons) {
                        val (top, h) = place; val (a, b) = cols
                        val w = size.width * 2.2f
                        val x = -size.width * 0.6f + (slide.value - 0.5f) * 2f * 0.16f * w
                        drawOval(
                            Brush.horizontalGradient(0.1f to Color.Transparent, 0.38f to a.copy(alpha = 0.75f), 0.62f to b.copy(alpha = 0.6f), 0.9f to Color.Transparent, startX = x, endX = x + w),
                            topLeft = Offset(x, size.height * top), size = androidx.compose.ui.geometry.Size(w, size.height * h),
                        )
                    }
                },
        )
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))) })
    }
}

/** Film: a 165° wash from the top-left tone (55 %) through the bottom-right tone (30 %) to near-black, with fine grain (≈7 %). Still. */
@Composable
private fun Film(art: String?) {
    val cs = coverColors(art)
    val bg = WT.c.bg
    val tl = cs?.get(0) ?: bg; val br = cs?.get(3) ?: bg
    val noise = remember { androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(com.wetune.ui.grain, TileMode.Repeated, TileMode.Repeated)) }
    Box(Modifier.fillMaxSize().drawBehind {
        // 165°: from the top (slightly left) to the bottom (slightly right)
        val dx = size.height * kotlin.math.tan(Math.toRadians(15.0)).toFloat() / 2f
        drawRect(Brush.linearGradient(0f to dim(tl, 0.55f), 0.55f to dim(br, 0.3f), 1f to Color(0xFF050505), start = Offset(size.width / 2f - dx, 0f), end = Offset(size.width / 2f + dx, size.height)))
        drawRect(noise, alpha = 0.6f)
    })
}

/**
 * Spotlight: a stage light in the cover's vivid tone (−10 %) shining down from above the screen (an ellipse 78 % wide ×
 * 62 % tall, 85 % → 0), a faint pool of it under the cover (28 %), black everywhere else. Still.
 */
@Composable
private fun Spotlight(art: String?) {
    val v = dim(vivid(coverColors(art), WT.c.bg, 0f), 0.9f)
    Box(Modifier.fillMaxSize().background(Color(0xFF050507)).drawBehind {
        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float) =
            scale(1f, ry / rx, Offset(cx, cy)) { drawCircle(Brush.radialGradient(listOf(v.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), rx), rx, Offset(cx, cy)) }
        ellipse(size.width / 2f, -size.height * 0.04f, size.width * 0.78f, size.height * 0.62f, 0.85f) // the cone
        ellipse(size.width / 2f, size.height * 0.53f, size.width * 0.6f, size.height * 0.16f, 0.28f) // the pool under the cover
    })
}

/**
 * Sweep: the cover's four tones in a colour wheel around it (top-left → top-right → bottom-right → bottom-left), blurred
 * 40 and 1.3× more saturated, turning once every 120 s while music plays, under a wash from 35 % to 70 %. The blurred
 * wheel is drawn once and only its layer turns.
 */
@Composable
private fun Sweep(art: String?, live: Boolean) {
    val cs = coverColors(art)
    val bg = WT.c.bg
    val q = cs?.take(4)?.map { richer(it) } ?: List(4) { bg }
    val turn = turning(120_000, live && Playback.playing)
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        // a square whose inner circle reaches past the screen corner farthest from the wheel's centre, so turning never shows an edge
        val reach = kotlin.math.hypot(maxWidth.value / 2f, maxHeight.value * (1f - COVER_Y))
        val d = (reach * 2.2f).dp
        Box(
            Modifier.requiredSize(d).offset(y = maxHeight * COVER_Y - maxHeight / 2f)
                .graphicsLayer { rotationZ = turn.value; alpha = 0.9f }
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(40.dp, BlurredEdgeTreatment.Unbounded) else Modifier)
                .drawBehind { drawRect(Brush.sweepGradient(listOf(q[0], q[1], q[3], q[2], q[0]), center)) },
        )
        Box(Modifier.fillMaxSize().wash(Color.Black, 0.35f, 0.7f))
    }
}

/** A cover tone made livelier for Sweep: 1.3× more saturated and never too dark to show as colour. */
private fun richer(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1f)
    hsv[2] = hsv[2].coerceIn(0.32f, 0.9f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Apple Music style: the colour along the cover's bottom edge fills the screen (the cover itself fades into it). */
@Composable
private fun Edge(art: String?) {
    val bg = WT.c.bg
    val cs = coverColors(art)
    Box(Modifier.fillMaxSize().drawWithCache {
        val edge = cs?.get(4) ?: bg
        val fill = Brush.verticalGradient(0f to edge, 0.6f to edge, 1f to lerp(edge, bg, 0.55f))
        onDrawBehind { drawRect(fill) }
    })
}


/**
 * A moving cover in place of the artwork (source: [animatedCoverUrl]): muted, looping,
 * cropped to the cover's own frame and shape (the caller clips), fading in over the artwork at its first frame. Nothing
 * on mobile data when Settings.npVideoWifiOnly, nor when the song has no video: the artwork simply stays. Follows the
 * music (paused with it) and is released as soon as it leaves the screen.
 */
@Composable
fun VideoCover(t: Track, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val wifiOnly = Settings.npVideoWifiOnly
    // per song: (song id, its video or null). Keyed by the id, so a song change never shows (or offers the switch for) the
    // previous song's video while the new one is looked up: produceState keeps its last value across key changes
    val found by produceState<Pair<String, String?>?>(null, t.id, wifiOnly) {
        value = null
        value = t.id to withContext(Dispatchers.IO) {
            runCatching {
                if (wifiOnly && !onWifi(ctx)) null
                else animatedCoverUrl(t)
            }.getOrNull()
        }
    }
    val u = found?.takeIf { it.first == t.id }?.second ?: return
    androidx.compose.runtime.SideEffect { NowPlaying.videoFor = t.id } // offers the Cover / Video switch for this song
    val on = NowPlaying.videoOn
    val player = remember(u) {
        androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build().apply {
            volume = 0f
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            // Apple's covers are HLS streams (said so, as their addresses don't always end in .m3u8); Spotify's Canvas is a plain MP4
            val hls = "itunes.apple.com" in u || ".m3u8" in u
            setMediaItem(androidx.media3.common.MediaItem.Builder().setUri(u).apply { if (hls) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8) }.build())
            prepare()
        }
    }
    var aspect by remember(u) { mutableFloatStateOf(0f) } // width / height, once the video says
    var shown by remember(u) { androidx.compose.runtime.mutableStateOf(false) }
    DisposableEffect(player) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(v: androidx.media3.common.VideoSize) { if (v.height > 0) aspect = v.width * v.pixelWidthHeightRatio / v.height }
            override fun onRenderedFirstFrame() { shown = true }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(player, Playback.playing, on) { player.playWhenReady = Playback.playing && on } // paused while the artwork shows
    val fade by androidx.compose.animation.core.animateFloatAsState(if (shown && on) 1f else 0f, tween(Motion.GLOW), label = "video")
    Box(modifier.fillMaxSize().clipToBounds()) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { android.view.TextureView(it).also(player::setVideoTextureView) },
            modifier = Modifier
                .layout { m, cs ->
                    // fill the cover's frame at the video's own shape, centred (the overflow is clipped): never stretched
                    val w = cs.maxWidth; val h = cs.maxHeight
                    val a = if (aspect > 0f) aspect else 16f / 9f
                    val (vw, vh) = if (w.toFloat() / h > a) w to (w / a).toInt() else (h * a).toInt() to h
                    val pl = m.measure(androidx.compose.ui.unit.Constraints.fixed(vw.coerceAtLeast(1), vh.coerceAtLeast(1)))
                    layout(w, h) { pl.place((w - vw) / 2, (h - vh) / 2) }
                }
                .graphicsLayer { alpha = fade },
        )
    }
}

/** Whether the phone is on Wi-Fi (or Ethernet) right now. */
private fun onWifi(ctx: android.content.Context): Boolean {
    val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
}

/** Where a moving cover's video comes from: Apple Music's animated album cover (never YouTube videos). Blocking. */
private fun animatedCoverUrl(t: Track): String? = com.wetune.Motion.coverFor(t)
