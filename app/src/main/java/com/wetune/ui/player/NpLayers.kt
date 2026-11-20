@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.wetune.ui

import android.content.Context
import android.database.ContentObserver
import android.graphics.RectF
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wetune.AudioInfo
import com.wetune.Library
import com.wetune.io
import com.wetune.sources.Track
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Now Playing 2.0 layers. Each takes (style, colours, state) and reads no settings; the playback position is only
// read while drawing (or, for the time labels, once a second).

/** System "Remove animations": ambient motion stops and song changes become short fades. */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** A clock for ambient motion (radians, one turn per [periodMs]); stands still while [running] is false. Read it only in draw. */
@Composable
private fun rememberClock(running: Boolean, periodMs: Int): () -> Float {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(running, periodMs) {
        while (running) {
            clock.animateTo(clock.value + 2 * PI.toFloat(), tween(periodMs, easing = LinearEasing))
            clock.snapTo(clock.value % (2 * PI.toFloat()))
        }
    }
    return { clock.value }
}

// ---------------------------------------------------------------- Background

/**
 * Behind everything: the cover as a soft blur, a drifting mesh of its colours, its edges extended, or a solid tint;
 * then texture, grain and the dim in one draw pass. A new song cross-fades only the image. Works the same on API 26+:
 * the blur is done once on a tiny bitmap and drawn scaled up.
 */
@Composable
fun BackgroundLayer(bg: BackgroundStyle, colors: NpColors, art: ArtData?, geo: NpGeometry, playing: Boolean, still: Boolean, ambient: Boolean, modifier: Modifier = Modifier) {
    val tint by animateColorAsState(colors.tint, tween(600), label = "tint")
    Box(modifier.fillMaxSize().background(colors.base)) {
        Crossfade(art, Modifier.fillMaxSize(), tween(if (still) 150 else 650), label = "bg") { a ->
            when {
                bg.kind == BgKind.Solid -> Box(Modifier.fillMaxSize().drawBehind { drawRect(tint) })
                a == null -> Unit
                bg.kind == BgKind.Mesh -> MeshBackground(a.mesh, bg.motion, playing && ambient && !still)
                bg.kind == BgKind.Extend -> ExtendBackground(a, bg, geo)
                else -> BlurBackground(a, bg)
            }
        }
        val ribs = with(LocalDensity.current) { bg.ribs.dp.toPx() }
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val ribbed = Brush.horizontalGradient(
                    0f to Color.White.copy(alpha = 0.07f), 0.55f to Color.Black.copy(alpha = 0.14f), 1f to Color.White.copy(alpha = 0.07f),
                    startX = 0f, endX = ribs, tileMode = TileMode.Repeated,
                )
                val grain = ShaderBrush(ImageShader(noiseTile, TileMode.Repeated, TileMode.Repeated))
                onDrawBehind {
                    if (bg.texture == Texture.Ribbed) drawRect(ribbed)
                    if (bg.grain > 0f) drawRect(grain, alpha = (bg.grain / 0.3f).coerceIn(0f, 1f))
                    if (colors.dim > 0f) drawRect(Color.Black, alpha = colors.dim)
                }
            },
        )
    }
}

@Composable
private fun BlurBackground(a: ArtData, bg: BackgroundStyle) {
    // a few hundred microseconds on a ~50 px bitmap, so done right here: no empty frame on a song change
    val img = remember(a, bg.blur) { blurredArt(a.bitmap, bg.blur).asImageBitmap() }
    val zoom = bg.zoom
    Canvas(Modifier.fillMaxSize()) {
        val i = img
        val side = maxOf(size.width, size.height) * zoom
        drawImage(
            i, dstOffset = IntOffset(((size.width - side) / 2).roundToInt(), ((size.height - side) / 2).roundToInt()),
            dstSize = androidx.compose.ui.unit.IntSize(side.roundToInt(), side.roundToInt()), filterQuality = FilterQuality.Low,
        )
    }
}

@Composable
private fun ExtendBackground(a: ArtData, bg: BackgroundStyle, geo: NpGeometry) {
    // the cover's place as fractions of the screen, rounded so small layout changes don't rebuild it
    fun r(v: Float) = (v * 100).roundToInt() / 100f
    val rect = RectF(r(geo.coverLeft / geo.width), r(geo.coverTop / geo.height), r((geo.coverLeft + geo.coverW) / geo.width), r(geo.coverBottom / geo.height))
    val h = (48 * geo.height / geo.width).roundToInt().coerceAtLeast(48)
    val img = remember(a, bg.blur, bg.reflection, rect) { extendedArt(a.bitmap, 48, h, rect, bg.reflection, bg.blur).asImageBitmap() }
    Canvas(Modifier.fillMaxSize()) {
        val i = img
        drawImage(i, dstSize = androidx.compose.ui.unit.IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
    }
}

/** UltraBlur-style mesh: four soft colour pools from the cover, drifting slowly while it plays. */
@Composable
private fun MeshBackground(mesh: List<Color>, motion: Float, moving: Boolean) {
    val clock = rememberClock(moving && motion > 0f, (60_000 / (0.2f + motion * 2f)).roundToInt())
    val base = remember(mesh) { androidx.compose.ui.graphics.lerp(mesh[1], Color.Black, 0.55f) }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(base)
        val t = clock()
        val spots = listOf(Offset(0.22f, 0.2f), Offset(0.82f, 0.28f), Offset(0.28f, 0.78f), Offset(0.78f, 0.86f))
        spots.forEachIndexed { i, o ->
            val dx = sin(t + i * 1.7f) * 0.08f; val dy = cos(t * 0.8f + i * 2.3f) * 0.06f
            val c = Offset((o.x + dx) * size.width, (o.y + dy) * size.height)
            val r = size.maxDimension * 0.55f
            drawCircle(Brush.radialGradient(listOf(mesh[i].copy(alpha = 0.85f), mesh[i].copy(alpha = 0f)), c, r), r, c)
        }
    }
}

/** A small tile of faint black specks, repeated across the screen as grain. */
private val noiseTile: ImageBitmap by lazy {
    val s = 160
    val rnd = java.util.Random(7)
    val px = IntArray(s * s) { rnd.nextInt(56) shl 24 }
    android.graphics.Bitmap.createBitmap(px, s, s, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Over the cover: a shade behind the header and from the lyric line down behind the controls, never lighter than
 * [TextScrim] where text sits (the contrast solver counts on it). Also the depth effect's vignette.
 */
@Composable
fun ScrimLayer(geo: NpGeometry, vignette: Float, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Spacer(
        modifier.fillMaxSize().drawWithCache {
            val h = size.height
            val hb = (geo.headerTop + geo.headerH).dp.toPx()
            // header: the mockups' 0.42 at the top, TextScrim under the header text, gone well below it
            val top = scrimPaint(listOf(0f to 0.42f, hb to TextScrim, hb + 104.dp.toPx() to 0f), h)
            // controls: from inside the cover's lower part to TextScrim at the lyric line, 0.62 by the seek bar, 0.84 at the bottom
            val cb = geo.coverBottom.dp.toPx()
            val from = cb - geo.coverH.dp.toPx() * 0.3f
            val text = maxOf(cb + 8.dp.toPx(), from + 64.dp.toPx())
            val mid = maxOf(geo.controlsTop.dp.toPx() + geo.controlsH.dp.toPx() * 0.3f, text + 64.dp.toPx())
            val bottom = scrimPaint(listOf(from to 0f, text to TextScrim, mid to 0.62f, maxOf(h, mid + 64.dp.toPx()) to 0.84f), h)
            val vig = Brush.radialGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = vignette), center = size.center, radius = size.maxDimension * 0.62f)
            onDrawBehind {
                drawIntoCanvas { c -> c.nativeCanvas.drawRect(0f, 0f, size.width, h, top); c.nativeCanvas.drawRect(0f, 0f, size.width, h, bottom) }
                if (vignette > 0f) drawRect(vig)
            }
        },
    )
}

/**
 * A smooth, monotone curve through [pts] (y → alpha), flat at both ends (cubic Hermite, Fritsch–Butland slopes): no
 * kink anywhere for the eye to catch as an edge, unlike a few linear stops.
 */
internal fun scrimCurve(pts: List<Pair<Float, Float>>, y: Float): Float {
    if (y <= pts.first().first) return pts.first().second
    if (y >= pts.last().first) return pts.last().second
    val i = pts.indexOfLast { it.first <= y }.coerceAtMost(pts.size - 2)
    fun secant(k: Int) = (pts[k + 1].second - pts[k].second) / (pts[k + 1].first - pts[k].first)
    fun slope(k: Int): Float {
        if (k == 0 || k == pts.size - 1) return 0f
        val a = secant(k - 1); val b = secant(k)
        return if (a * b <= 0f) 0f else 2 * a * b / (a + b)
    }
    val (x0, a0) = pts[i]; val (x1, a1) = pts[i + 1]
    val w = x1 - x0; val t = (y - x0) / w; val t2 = t * t; val t3 = t2 * t
    return (2 * t3 - 3 * t2 + 1) * a0 + (t3 - 2 * t2 + t) * w * slope(i) + (-2 * t3 + 3 * t2) * a1 + (t3 - t2) * w * slope(i + 1)
}

/** [scrimCurve] in ink as a full-height gradient of many stops, dithered so the dark ramp doesn't band. */
private fun scrimPaint(pts: List<Pair<Float, Float>>, height: Float): android.graphics.Paint {
    val n = 48
    val a = pts.first().first; val b = pts.last().first
    val ys = FloatArray(n) { a + (b - a) * it / (n - 1) }
    val colors = IntArray(n) { android.graphics.Color.argb((scrimCurve(pts, ys[it]) * 255).roundToInt().coerceIn(0, 255), 8, 8, 10) }
    val pos = FloatArray(n) { (ys[it] / height).coerceIn(0f, 1f) }
    return android.graphics.Paint().apply {
        isDither = true
        shader = android.graphics.LinearGradient(0f, 0f, 0f, height, colors, pos, android.graphics.Shader.TileMode.CLAMP)
    }
}

// ---------------------------------------------------------------- Cover

/** The cover image: the editor's test cover when one is picked, otherwise the song's art. */
@Composable
fun CoverImage(ui: PlayerUi, t: Track, modifier: Modifier) {
    val test = ui.testArt
    if (test != null && t.id == ui.t.id) Image(remember(test) { test.asImageBitmap() }, null, modifier, contentScale = ContentScale.Crop)
    else Cover(t, modifier)
}

/**
 * The cover in its place ([geo]): a card (optionally a pager with the queue's neighbours), full width, a disc in a
 * progress ring, a record, or motion art. [shift]: the swipe-to-skip nudge, read in graphicsLayer only.
 */
@Composable
fun CoverLayer(cs: CoverStyle, colors: NpColors, ui: PlayerUi, geo: NpGeometry, still: Boolean, ambient: Boolean, shift: () -> Float, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val queue = ui.queue
    if (cs.kind == CoverKind.Pager && queue.size > 1 && ui.index in queue.indices && !ui.preview) return CoverPager(cs, colors, ui, geo, onLongPress, modifier)
    val breathe by animateFloatAsState(if (ui.playing || !cs.breathe || still) 1f else 0.93f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "breathe")
    Box(modifier.offset(geo.coverLeft.dp, geo.coverTop.dp).size(geo.coverW.dp, geo.coverH.dp).graphicsLayer { translationX = shift(); scaleX = breathe; scaleY = breathe }) {
        Crossfade(ui.t, Modifier.fillMaxSize(), tween(if (still) 150 else 500), label = "cover") { t ->
            when (cs.kind) {
                CoverKind.FullWidth -> FullWidthCover(cs, ui, t)
                CoverKind.Disc -> DiscCover(cs, colors, ui, t, ambient && !still)
                CoverKind.Vinyl -> VinylCover(ui, t, cs.spin && ambient && !still)
                CoverKind.Motion -> CardCover(cs, colors, ui, t, motion = ambient && !still && ui.playing)
                else -> CardCover(cs, colors, ui, t, motion = false)
            }
        }
    }
}

private fun Modifier.lift(shadow: Color, shape: Shape, elevation: Dp = 28.dp) =
    (if (shadow.alpha > 0f) shadow(elevation, shape, ambientColor = shadow, spotColor = shadow) else this).clip(shape)

@Composable
private fun CardCover(cs: CoverStyle, colors: NpColors, ui: PlayerUi, t: Track, motion: Boolean) {
    val shape = RoundedCornerShape(cs.corner.dp)
    if (!motion) return CoverImage(ui, t, Modifier.fillMaxSize().lift(colors.shadow, shape))
    // motion art: a slow pan and zoom (Ken Burns) inside the card
    val k by rememberInfiniteTransition(label = "kb").animateFloat(0f, 1f, infiniteRepeatable(tween(12_000), RepeatMode.Reverse), label = "k")
    Box(Modifier.fillMaxSize().lift(colors.shadow, shape)) {
        CoverImage(ui, t, Modifier.fillMaxSize().graphicsLayer {
            val s = 1f + 0.09f * k
            scaleX = s; scaleY = s; translationX = -8.dp.toPx() * k; translationY = -6.dp.toPx() * k
        })
    }
}

@Composable
private fun FullWidthCover(cs: CoverStyle, ui: PlayerUi, t: Track) {
    val feather = cs.feather
    CoverImage(
        ui, t,
        Modifier.fillMaxSize().graphicsLayer { scaleX = 1f + cs.crop * 2; scaleY = 1f + cs.crop * 2; compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (feather > 0f) drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, feather to Color.Black, 1f - feather to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

@Composable
private fun DiscCover(cs: CoverStyle, colors: NpColors, ui: PlayerUi, t: Track, motion: Boolean) {
    val spin = rememberClock(cs.spin && motion && ui.playing, 24_000)
    val ring = cs.ringWidth.dp
    Box(Modifier.fillMaxSize()) {
        CoverImage(ui, t, Modifier.fillMaxSize().padding(if (cs.ring) ring + 10.dp else 0.dp).graphicsLayer { rotationZ = spin() * 180f / PI.toFloat() }.lift(colors.shadow, CircleShape))
        // the ring reads the position only while drawing, so the cover never recomposes for it
        if (cs.ring) Canvas(Modifier.fillMaxSize()) {
            val stroke = ring.toPx()
            val r = size.minDimension / 2 - stroke / 2 - 2.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            val arc = Size(r * 2, r * 2)
            val f = ui.fraction
            drawArc(Color.White.copy(alpha = 0.2f), 0f, 360f, false, tl, arc, style = Stroke(stroke))
            drawArc(colors.accent, -90f, 360f * f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            val a = Math.toRadians((-90f + 360f * f).toDouble())
            drawCircle(Color.White, stroke + 2.dp.toPx(), Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
        }
    }
}

@Composable
private fun VinylCover(ui: PlayerUi, t: Track, spinOn: Boolean) {
    // the record slides out while playing and tucks back into the sleeve when paused
    val out by animateFloatAsState(if (ui.playing) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow), label = "out")
    val spin = rememberClock(spinOn && ui.playing, 4_000)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = minOf(maxWidth, maxHeight)
        val sleeve = size * 0.8f
        val rec = sleeve * 0.94f
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = -(sleeve * 0.21f * out).toPx() }, Alignment.CenterStart) {
            Box(
                Modifier.offset(x = (size - sleeve) / 2 + (sleeve - rec) / 2).size(rec)
                    .graphicsLayer { translationX = (sleeve * 0.42f * out).toPx(); rotationZ = spin() * 180f / PI.toFloat() },
            ) { Record(ui, t) }
            CoverImage(ui, t, Modifier.offset(x = (size - sleeve) / 2).size(sleeve).lift(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp)))
        }
    }
}

/** A black record: fine grooves, a soft sheen, and the cover as its center label. */
@Composable
private fun Record(ui: PlayerUi, t: Track) = Box(Modifier.fillMaxSize()) {
    Canvas(Modifier.fillMaxSize().shadow(18.dp, CircleShape)) {
        val r = size.minDimension / 2
        drawCircle(Color(0xFF0B0B0C), r)
        var g = r * 0.97f
        while (g > r * 0.38f) { drawCircle(Color.White.copy(alpha = 0.035f), g, style = Stroke(0.8f)); g -= r * 0.025f }
        drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent)), r)
    }
    CoverImage(ui, t, Modifier.fillMaxSize(0.34f).align(Alignment.Center).clip(CircleShape))
}

/** Pager: the queue as cards, neighbours peeking at the sides; settling on one plays it. */
@Composable
private fun CoverPager(cs: CoverStyle, colors: NpColors, ui: PlayerUi, geo: NpGeometry, onLongPress: () -> Unit, modifier: Modifier) {
    val queue = ui.queue
    val pager = rememberPagerState(initialPage = ui.index) { queue.size }
    val index = ui.index
    LaunchedEffect(index) { if (pager.currentPage != index && index in 0 until pager.pageCount) pager.animateScrollToPage(index) }
    val skip by rememberUpdatedState<(Int) -> Unit> { ui.skipTo(it) }
    val current by rememberUpdatedState(index)
    // only a swipe plays another song (not the pager settling after the queue changed under it)
    var swiped by remember { mutableStateOf(false) }
    LaunchedEffect(pager) { pager.interactionSource.interactions.collect { if (it is androidx.compose.foundation.interaction.DragInteraction.Start) swiped = true } }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { if (swiped) { swiped = false; if (it != current) skip(it) } } }
    val shape = RoundedCornerShape(cs.corner.dp)
    val side = (geo.width - geo.coverW) / 2
    HorizontalPager(
        pager,
        modifier.offset(y = geo.coverTop.dp).fillMaxWidth().height(geo.coverH.dp)
            // the neighbours' peeks fade out toward the screen edges instead of being cut off there
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val f = (side.dp.toPx() / size.width).coerceIn(0.01f, 0.45f)
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, f to Color.Black, 1f - f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
        contentPadding = PaddingValues(horizontal = side.dp), pageSpacing = 14.dp, beyondViewportPageCount = 1,
        key = { queue.getOrNull(it)?.id ?: it },
    ) { page ->
        val t = queue.getOrNull(page) ?: return@HorizontalPager
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val off = kotlin.math.abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                alpha = 1f - 0.45f * off
                val s = 1f - 0.08f * off
                scaleX = s; scaleY = s
            },
        ) { CoverImage(ui, t, Modifier.fillMaxSize().lift(if (page == index) colors.shadow else Color.Transparent, shape)) }
    }
}

// ---------------------------------------------------------------- Controls

/** What the controls need from the screen around them (sheets, menus, the lyric preview while seeking). */
class NpActions(
    val onQuality: () -> Unit,
    val onPipeline: () -> Unit,
    val onSeekDrag: (Float?) -> Unit,
    val menu: @Composable () -> Unit,
)
/** Title, seek, transport and actions arranged per [cs] kind, filling the controls block. */
@Composable
fun ControlsLayer(cs: ControlsStyle, colors: NpColors, ui: PlayerUi, actions: NpActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        when (cs.kind) {
            ControlsKind.Classic -> {
                TitleRow(ui, actions)
                Spacer(Modifier.height(8.dp))
                SeekBar(ui, cs.progress, colors.accent, actions)
                Spacer(Modifier.weight(1f))
                Transport(cs, colors, ui)
                Spacer(Modifier.weight(1f))
                if (cs.volume) { VolumeRow(); Spacer(Modifier.weight(1f)) }
                ActionRow(ui, cs, actions)
            }
            ControlsKind.Expressive -> {
                TitleRow(ui, actions, big = true)
                Spacer(Modifier.height(8.dp))
                SeekBar(ui, cs.progress, colors.accent, actions)
                Spacer(Modifier.weight(1f))
                ButtonGroup(cs, colors, ui)
                Spacer(Modifier.weight(1f))
                if (cs.volume) { VolumeRow(); Spacer(Modifier.weight(1f)) }
                // the button group has no room for shuffle and repeat: they bracket the action row
                if (cs.shuffleRepeat) ActionRow(ui, cs, actions, lead = { ShuffleBtn(ui) }, trail = { RepeatBtn(ui) }) else ActionRow(ui, cs, actions)
            }
            ControlsKind.Dock -> {
                TitleRow(ui, actions)
                Spacer(Modifier.height(8.dp))
                SeekBar(ui, cs.progress, colors.accent, actions, quality = false)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Transport(cs.copy(playSize = minOf(cs.playSize, 64)), colors, ui) }
                Spacer(Modifier.weight(1f))
                if (cs.volume) { VolumeRow(); Spacer(Modifier.weight(1f)) }
                ActionRow(ui, cs, actions)
                Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    if (NpAction.Output !in cs.actions) OutputLine()
                    QualityTag(actions.onQuality)
                }
            }
            ControlsKind.Minimal -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TitleText(ui, Modifier.weight(1f), big = false)
                    Spacer(Modifier.width(12.dp))
                    PlayBtn(cs, colors, ui, cs.playSize.dp)
                }
                Spacer(Modifier.weight(1f))
                SeekBar(ui, cs.progress, colors.accent, actions, inline = true)
                Spacer(Modifier.weight(1f))
                if (cs.volume) { VolumeRow(); Spacer(Modifier.weight(1f)) }
                // the title row holds the play button here, so the options menu ends the transport row
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    if (cs.shuffleRepeat) ShuffleBtn(ui)
                    SkipBtn(ui, false, 52.dp, 26.dp)
                    SkipBtn(ui, true, 52.dp, 26.dp)
                    if (cs.shuffleRepeat) RepeatBtn(ui)
                    actions.menu()
                }
                Spacer(Modifier.weight(1f))
                ActionRow(ui, cs, actions)
            }
            ControlsKind.Waveform -> {
                TitleRow(ui, actions)
                Spacer(Modifier.height(8.dp))
                SeekBar(ui, cs.progress, colors.accent, actions)
                Spacer(Modifier.weight(1f))
                Transport(cs, colors, ui)
                Spacer(Modifier.weight(1f))
                if (cs.volume) { VolumeRow(); Spacer(Modifier.weight(1f)) }
                UpNextCard(ui)
                Spacer(Modifier.weight(1f))
                ActionRow(ui, cs, actions)
            }
        }
    }
}

/** Title and artist; the options menu on the right. */
@Composable
private fun TitleRow(ui: PlayerUi, actions: NpActions, big: Boolean = false) {
    Row(verticalAlignment = if (big) Alignment.Bottom else Alignment.CenterVertically) {
        TitleText(ui, Modifier.weight(1f), big)
        Spacer(Modifier.width(4.dp))
        actions.menu()
    }
}

/** Title over artist (shared keys "title" / "artist"); tap for the album or the artist. */
@Composable
fun TitleText(ui: PlayerUi, modifier: Modifier = Modifier, big: Boolean = false) {
    val lift = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 12f)
    Column(modifier) {
        Txt(
            ui.t.title,
            Modifier.shared("title", text = true).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000)
                .pressable(scale = 0.98f) { ui.search(ui.t.album.ifBlank { ui.t.title }) },
            style = if (big) Type.title.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, shadow = lift)
            else Type.title.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, shadow = lift),
            maxLines = 1,
        )
        Txt(
            ui.t.artist,
            Modifier.shared("artist", text = true).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000)
                .pressable(scale = 0.98f) { ui.search(ui.t.artist.substringBefore(",").trim()) },
            style = Type.body.copy(fontSize = if (big) 17.sp else 16.sp, lineHeight = 22.sp, shadow = lift),
            color = Color.White.copy(alpha = MutedText), maxLines = 1,
        )
    }
}

private fun playShape(shape: PlayShape, morphPercent: Int): Shape = when (shape) {
    PlayShape.Circle, PlayShape.Pill -> CircleShape
    PlayShape.Squircle -> RoundedCornerShape(30)
    PlayShape.Morph -> RoundedCornerShape(morphPercent)
}

/**
 * Play/pause: [cs] shape (Morph: a circle while paused, a rounded square while playing) and fill (accent, white or
 * glass); the glyph picks dark or light by contrast. Shared key "play". Breathes while loading.
 */
@Composable
private fun PlayBtn(cs: ControlsStyle, colors: NpColors, ui: PlayerUi, height: Dp, width: Dp = if (cs.playShape == PlayShape.Pill) (height * 1.6f).coerceAtMost(124.dp) else height, shape: Shape? = null) {
    val playing = ui.playing
    val buffering = ui.buffering
    val pulse = if (buffering) rememberInfiniteTransition(label = "buffer").animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p") else null
    val target = when (cs.playFill) { PlayFill.Accent -> colors.accent; PlayFill.White -> Color.White; PlayFill.Glass -> Color.White.copy(alpha = 0.16f) }
    val fill by animateColorAsState(target, tween(650), label = "playFill")
    val morph by animateIntAsState(if (playing) 30 else 50, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "morph")
    val s = shape ?: playShape(cs.playShape, morph)
    val glyph = if (cs.playFill == PlayFill.Glass) Color.White else Color(onFill(fill.toArgb()))
    val view = LocalView.current
    Box(
        Modifier.shared("play").size(width, height).graphicsLayer { alpha = pulse?.value ?: 1f }
            .clip(s).background(fill)
            .semantics { stateDescription = if (buffering) "Loading" else if (playing) "Playing" else "Paused" }
            .pressable {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                ui.togglePlay()
            },
        Alignment.Center,
    ) { Icon(if (playing) Ic.Pause else Ic.Play, if (playing) "Pause" else "Play", size = (height * 0.42f).coerceIn(24.dp, 34.dp), tint = glyph) }
}

@Composable
private fun SkipBtn(ui: PlayerUi, next: Boolean, size: Dp = 56.dp, iconSize: Dp = 28.dp) =
    IconBtn(if (next) Ic.Next else Ic.Prev, if (next) "Next" else "Previous", { if (next) ui.next() else ui.prev() }, size = size, iconSize = iconSize, tint = Color.White, haptic = true)

@Composable
private fun ShuffleBtn(ui: PlayerUi) = ModeBtn(Ic.Shuffle, "Shuffle", if (ui.shuffle) "On" else "Off", ui.shuffle) { com.wetune.Playback.toggleShuffle() }

@Composable
private fun RepeatBtn(ui: PlayerUi) {
    val one = ui.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE
    val on = ui.repeat != androidx.media3.common.Player.REPEAT_MODE_OFF
    ModeBtn(if (one) Ic.RepeatOne else Ic.Repeat, "Repeat", if (one) "This song" else if (on) "All" else "Off", on) { com.wetune.Playback.cycleRepeat() }
}

/** Shuffle / repeat: dimmer when off (still ≥ 3:1), bright with a dot underneath when on. */
@Composable
private fun ModeBtn(icon: ImageVector, desc: String, state: String, on: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (on) 1f else IconAlpha, tween(200), label = "mode")
    Box(Modifier.semantics(mergeDescendants = true) { stateDescription = state }, contentAlignment = Alignment.Center) {
        IconBtn(icon, desc, onClick, size = 48.dp, iconSize = 22.dp, tint = Color.White.copy(alpha = a), haptic = true)
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).graphicsLayer { alpha = if (on) 1f else 0f }.clip(CircleShape).background(Color.White))
    }
}

/** Shuffle · previous · play · next · repeat. */
@Composable
private fun Transport(cs: ControlsStyle, colors: NpColors, ui: PlayerUi) {
    Row(Modifier.fillMaxWidth(), if (cs.shuffleRepeat) Arrangement.SpaceBetween else Arrangement.SpaceEvenly, Alignment.CenterVertically) {
        if (cs.shuffleRepeat) ShuffleBtn(ui)
        SkipBtn(ui, false)
        PlayBtn(cs, colors, ui, cs.playSize.dp)
        SkipBtn(ui, true)
        if (cs.shuffleRepeat) RepeatBtn(ui)
    }
}

/** Expressive: previous and next as half-pills hugging a wide play button (an M3 Expressive button group). */
@Composable
private fun ButtonGroup(cs: ControlsStyle, colors: NpColors, ui: PlayerUi) {
    val h = cs.playSize.dp
    @Composable
    fun Half(next: Boolean) {
        val shape = if (next) RoundedCornerShape(12.dp, h / 2, h / 2, 12.dp) else RoundedCornerShape(h / 2, 12.dp, 12.dp, h / 2)
        val view = LocalView.current
        Box(
            Modifier.size(88.dp, h).clip(shape).background(Color.White.copy(alpha = 0.14f))
                .pressable { view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); if (next) ui.next() else ui.prev() },
            Alignment.Center,
        ) { Icon(if (next) Ic.Next else Ic.Prev, if (next) "Next" else "Previous", size = 26.dp, tint = Color.White) }
    }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
        Half(false)
        Box(Modifier.weight(1f), Alignment.Center) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) { PlayBtn(cs, colors, ui, h, maxWidth) }
        }
        Half(true)
    }
}

/**
 * The action row: [ControlsStyle.actions] in order, icon only or icon over label, arranged per
 * [ControlsStyle.actionArrange]. [lead] / [trail] (Expressive: shuffle and repeat) take the corners.
 */
@Composable
private fun ActionRow(ui: PlayerUi, cs: ControlsStyle, actions: NpActions, lead: (@Composable () -> Unit)? = null, trail: (@Composable () -> Unit)? = null) {
    var pick by remember { mutableStateOf(false) }
    val shown = cs.actions.filter { it != NpAction.Download || ui.t.source != "local" }
    val items = buildList<@Composable (Modifier) -> Unit> {
        if (lead != null) add { _ -> lead() }
        shown.forEach { a -> add { m -> ActionItem(a, ui, cs.actionLabels, actions, m) { pick = true } } }
        if (trail != null) add { _ -> trail() }
    }
    // each item takes at most an equal share, so labels shorten instead of pushing the row off screen
    when {
        items.isEmpty() -> Unit
        cs.actionArrange == ActionArrange.Even -> Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            items.forEach { it(Modifier.weight(1f, fill = false)) }
        }
        cs.actionArrange == ActionArrange.Centred || items.size < 2 -> Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), Alignment.CenterVertically) {
            items.forEach { it(Modifier.weight(1f, fill = false)) }
        }
        else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            items.first()(Modifier)
            Row(Modifier.weight(1f), Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), Alignment.CenterVertically) {
                items.subList(1, items.size - 1).forEach { it(Modifier.weight(1f, fill = false)) }
            }
            items.last()(Modifier)
        }
    }
    if (pick) PlaylistPicker(ui.t) { pick = false }
}

/** One action: a 48 dp glyph, or with [label] a glyph over a short label (56 dp target). */
@Composable
private fun ActionItem(a: NpAction, ui: PlayerUi, label: Boolean, actions: NpActions, modifier: Modifier, onPick: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val liked = a == NpAction.Like && Library.isLiked(ui.t)
    val downloaded = a == NpAction.Download && Library.downloads.any { it.id == ui.t.id }
    val downloading = a == NpAction.Download && ui.t.id in com.wetune.Downloads.active
    val (icon, desc, on) = when (a) {
        NpAction.Lyrics -> Triple(Ic.LyricsQuote, "Lyrics", ui.pane == "lyrics")
        NpAction.Queue -> Triple(Ic.QueueList, "Queue", ui.pane == "queue")
        NpAction.Like -> Triple(if (liked) Ic.Heart else Ic.HeartOutline, if (liked) "Remove from Liked" else "Like", liked)
        NpAction.Sleep -> Triple(Ic.Moon, if (ui.sleepOn) "Sleep timer on" else "Sleep timer", ui.sleepOn)
        NpAction.Output -> Triple(Ic.Output, "Playing on ${outputDevice(ctx).first.substringAfter(" · ")}", false)
        NpAction.Playlist -> Triple(Ic.AddPlaylist, "Add to playlist", false)
        NpAction.Download -> Triple(if (downloaded) Ic.Downloaded else Ic.Download, if (downloaded) "Downloaded" else if (downloading) "Downloading" else "Download", downloaded)
        NpAction.Audio -> Triple(Ic.Tune, "Audio options", false)
    }
    val click: () -> Unit = {
        when (a) {
            NpAction.Lyrics -> ui.toggle("lyrics")
            NpAction.Queue -> ui.toggle("queue")
            NpAction.Like -> { view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); io { Library.toggleLike(ui.t) } }
            NpAction.Sleep -> ui.openSleep()
            NpAction.Output -> openOutputSwitcher(ctx)
            NpAction.Playlist -> onPick()
            NpAction.Download -> if (!downloaded && !downloading) com.wetune.Downloads.download(ui.t)
            NpAction.Audio -> actions.onQuality()
        }
    }
    if (!label) {
        Box(modifier) { PaneAction(icon, desc, on, onClick = click) }
        return
    }
    val alpha by animateFloatAsState(if (on) 1f else 0.8f, tween(200), label = "itemA")
    val bg by animateColorAsState(Color.White.copy(alpha = if (on) 0.16f else 0f), tween(200), label = "itemBg")
    val fg = Color.White.copy(alpha = alpha)
    Column(
        modifier.heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp)).background(bg)
            .semantics(mergeDescendants = true) { contentDescription = desc; selected = on }
            .pressable(scale = 0.94f, onClick = click).padding(horizontal = 8.dp, vertical = 6.dp),
        Arrangement.Center, Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, size = 21.dp, tint = fg)
        Txt(a.label, Modifier.padding(top = 3.dp).clearAndSetSemantics {}, style = Type.caption.copy(fontSize = 11.sp, lineHeight = 14.sp), color = fg, maxLines = 1)
    }
}

@Composable
private fun OutputLine() {
    val ctx = LocalContext.current
    Row(
        Modifier.heightIn(min = 48.dp).clip(CircleShape).pressable(scale = 0.96f) { openOutputSwitcher(ctx) }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Device, null, size = 14.dp, tint = Color.White.copy(alpha = MutedText))
        Txt("Playing on " + outputDevice(ctx).first.substringAfter(" · "), Modifier.padding(start = 6.dp), style = TimeStyle, color = Color.White.copy(alpha = MutedText), maxLines = 1)
    }
}

/** A plain glyph button, brighter with a soft wash while [on]. */
@Composable
private fun PaneAction(icon: ImageVector, desc: String, on: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (on) 1f else 0.8f, tween(200), label = "action")
    val bg by animateColorAsState(Color.White.copy(alpha = if (on) 0.16f else 0f), tween(200), label = "actionBg")
    IconBtn(icon, desc, onClick, Modifier.semantics { selected = on }, size = 48.dp, iconSize = 21.dp, tint = Color.White.copy(alpha = a), background = bg)
}

/** The song after this one; tap for the queue. */
@Composable
private fun UpNextCard(ui: PlayerUi) {
    // shortcut: the queue's next row, like QueueView shows it; with shuffle on the player may pick another, use the timeline's next index if that matters
    val next = ui.queue.getOrNull(ui.index + 1)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.1f))
            .pressable(scale = 0.98f) { ui.toggle("queue") }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (next != null) Cover(next, Modifier.padding(end = 12.dp).size(40.dp).clip(RoundedCornerShape(10.dp)))
        else Spacer(Modifier.width(4.dp).height(40.dp))
        Column(Modifier.weight(1f)) {
            Txt("UP NEXT", style = Type.overline.copy(fontSize = 10.sp), color = Color.White.copy(alpha = MutedText), maxLines = 1)
            Txt(next?.let { "${it.title} · ${it.artist}" } ?: "End of queue", style = Type.body.copy(fontSize = 14.sp), color = Color.White, weight = FontWeight.SemiBold, maxLines = 1)
        }
        Icon(Ic.QueueList, "Open queue", Modifier.padding(horizontal = 10.dp), size = 20.dp, tint = Color.White)
    }
}

/** The phone's media volume: quiet speaker · slider · loud speaker. Follows the volume buttons too. */
@Composable
private fun VolumeRow() {
    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { level = audio.getStreamVolume(AudioManager.STREAM_MUSIC) }
        }
        ctx.contentResolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, observer)
        onDispose { ctx.contentResolver.unregisterContentObserver(observer) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Ic.VolumeLow, null, size = 16.dp, tint = Color.White.copy(alpha = MutedText))
        SeekSlider(
            level.toFloat() / max,
            { (it * max).roundToInt().let { v -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); level = v } }, {},
            Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = "Volume"; stateDescription = "$level of $max" }, color = Color.White, thickness = 4.dp,
        )
        Icon(Ic.Volume, null, size = 18.dp, tint = Color.White.copy(alpha = MutedText))
    }
}

/** Times use equal-width digits ("tnum"), so the row doesn't shift as 1:19 becomes 1:20. */
internal val TimeStyle = Type.caption.copy(fontSize = 12.sp, fontFeatureSettings = "tnum")

/**
 * The seek bar in a [progress] style. The played part is drawn from the position at draw time, so playback never
 * recomposes it; the times and TalkBack's "1:12 of 3:20" update once a second. While dragging, a bubble shows where
 * you'd land and the lyric line previews it ([NpActions.onSeekDrag]).
 */
@Composable
private fun SeekBar(ui: PlayerUi, progress: Progress, accent: Color, actions: NpActions, inline: Boolean = false, quality: Boolean = true) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    var width by remember { mutableFloatStateOf(1f) }
    val drag by rememberUpdatedState(dragging)
    val shown = { drag ?: ui.fraction }
    val sec by remember(ui) { derivedStateOf { ui.pos / 1000 } }
    val durSec = (ui.duration / 1000).coerceAtLeast(1)
    val view = LocalView.current
    var tick by remember { mutableIntStateOf(-1) }
    val change: (Float) -> Unit = { f ->
        val b = (f * 20).toInt()
        if (b != tick) { if (tick >= 0) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); tick = b }
        dragging = f; actions.onSeekDrag(f)
    }
    val done = { dragging?.let(ui::seekTo); dragging = null; tick = -1; actions.onSeekDrag(null) }
    val waving = ui.playing && dragging == null
    val amp by animateFloatAsState(if (progress == Progress.Wavy && waving) 1f else 0f, tween(400), label = "amp")
    val phase = rememberClock(progress == Progress.Wavy && waving && !rememberReduceMotion(), 2_000)
    val bars = remember(ui.t.id) { val r = kotlin.random.Random(ui.t.id.hashCode()); FloatArray(56) { 0.22f + 0.78f * r.nextFloat() } }
    val thick = progress == Progress.Thick
    val bar = @Composable { m: Modifier ->
        Box(
            m.height(if (progress == Progress.Bars) 40.dp else 32.dp).onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) { detectTapGestures { change((it.x / width).coerceIn(0f, 1f)); done() } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { change((it.x / width).coerceIn(0f, 1f)) }, onDragEnd = { done() }, onDragCancel = { done() },
                    ) { ch, _ -> ch.consume(); change((ch.position.x / width).coerceIn(0f, 1f)) }
                }
                .semantics {
                    contentDescription = "Seek"
                    stateDescription = "${formatTime(sec * 1000)} of ${formatTime(ui.duration)}"
                    progressBarRangeInfo = ProgressBarRangeInfo(sec.toFloat().coerceAtMost(durSec.toFloat()), 0f..durSec.toFloat())
                    setProgress { v -> ui.seekToMs((v * 1000).toLong()); true }
                    customActions = listOf(
                        CustomAccessibilityAction("Back 10 seconds") { ui.seekToMs(ui.pos - 10_000); true },
                        CustomAccessibilityAction("Forward 10 seconds") { ui.seekToMs(ui.pos + 10_000); true },
                    )
                }
                .drawBehind {
                    val f = shown().coerceIn(0f, 1f)
                    val cy = size.height / 2
                    val rest = Color.White.copy(alpha = 0.28f)
                    val x = size.width * f
                    when (progress) {
                        Progress.Bars -> {
                            val bw = 3.dp.toPx(); val step = (size.width - bw) / (bars.size - 1)
                            bars.forEachIndexed { i, h ->
                                val bh = size.height * h
                                drawRoundRect(if ((i + 0.5f) / bars.size <= f) accent else rest, Offset(i * step, cy - bh / 2), Size(bw, bh), CornerRadius(bw / 2))
                            }
                        }
                        Progress.Wavy -> {
                            val sw = 4.dp.toPx()
                            drawLine(rest, Offset(x + 6.dp.toPx(), cy), Offset(size.width, cy), sw, StrokeCap.Round)
                            val path = Path(); val a = 3.dp.toPx() * amp; val wl = 16.dp.toPx(); val ph = phase()
                            var px = 0f
                            path.moveTo(0f, cy + sin(ph) * a)
                            while (px < x - 3.dp.toPx()) { px += 2f; path.lineTo(px, cy + sin(px / wl * 2 * PI.toFloat() + ph) * a) }
                            drawPath(path, accent, style = Stroke(sw, cap = StrokeCap.Round))
                            drawRoundRect(accent, Offset(x - 2.dp.toPx(), cy - 9.dp.toPx()), Size(4.dp.toPx(), 18.dp.toPx()), CornerRadius(2.dp.toPx()))
                        }
                        else -> {
                            val t = (if (thick) 8.dp else 4.dp).toPx() * (if (drag != null) 1.5f else 1f)
                            drawLine(rest, Offset(t / 2, cy), Offset(size.width - t / 2, cy), t, StrokeCap.Round)
                            drawLine(Color.White, Offset(t / 2, cy), Offset(x.coerceAtLeast(t / 2), cy), t, StrokeCap.Round)
                            if (!thick) drawCircle(Color.White, (if (drag != null) 8.dp else 6.dp).toPx(), Offset(x, cy))
                        }
                    }
                },
        ) {
            val d = dragging
            if (d != null) {
                val density = LocalDensity.current
                val bubbleW = with(density) { 60.dp.toPx() }
                Box(
                    Modifier.offset { IntOffset((d * width - bubbleW / 2).coerceIn(0f, width - bubbleW).roundToInt(), (-30).dp.roundToPx()) }
                        .width(60.dp).clip(CircleShape).background(Color.White).padding(vertical = 4.dp),
                    Alignment.Center,
                ) { Txt(formatTime((d * ui.duration).toLong()), style = TimeStyle, color = Color(0xFF1A1210), weight = FontWeight.SemiBold) }
            }
        }
    }
    val timeColor = Color.White.copy(alpha = MutedText)
    val at = dragging?.let { (it * ui.duration).toLong() / 1000 } ?: sec
    Column(Modifier.fillMaxWidth().shared("seek")) {
        if (inline) Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(formatTime(at * 1000), Modifier.semantics { invisibleToUser() }, style = TimeStyle, color = timeColor)
            bar(Modifier.weight(1f).padding(horizontal = 12.dp))
            Txt(formatTime(ui.duration), Modifier.semantics { invisibleToUser() }, style = TimeStyle, color = timeColor)
        } else {
            bar(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(formatTime(at * 1000), Modifier.semantics { invisibleToUser() }, style = TimeStyle, color = timeColor)
                Box(Modifier.weight(1f), Alignment.Center) { if (quality) QualityTag(actions.onQuality) }
                Txt("-" + formatTime(ui.duration - at * 1000), Modifier.semantics { invisibleToUser() }, style = TimeStyle, color = timeColor)
            }
        }
    }
}

/**
 * The tiny quality line between the times; tap for the audio options. While WeTune looks for a 320 kbps copy it says
 * so, otherwise it shows the badge and format.
 */
@Composable
fun QualityTag(onClick: () -> Unit) {
    val searching = AudioInfo.upgrade == "searching"
    val text = AudioInfo.hifiNow?.first ?: AudioInfo.codecName
    val badge = AudioInfo.hifiNow?.let { (_, bits, rate) -> if (bits > 16 || rate > 48_000) "HI-RES" else "LOSSLESS" }
        ?: AudioInfo.kbps?.let { if (it >= 256) "HQ" else "SQ" }
    if (text == null && !searching) return
    AnimatedContent(text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "quality") { s ->
        Row(
            // small on screen; Compose's hit testing still gives it a 48 dp touch area (minimumTouchTargetSize)
            Modifier.clip(CircleShape).pressable(scale = 0.96f, onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Audio quality: ${badge ?: ""} ${s ?: "upgrading"}. Audio options" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (badge != null && s != null) Txt(
                badge, Modifier.padding(end = 6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 3.dp),
                style = Type.overline.copy(fontSize = 8.sp, letterSpacing = 0.3.sp, lineHeight = 11.sp), color = Color(0xFF1A1210), weight = FontWeight.Bold,
            )
            val style = Type.caption.copy(fontSize = 11.sp, fontFeatureSettings = "tnum")
            if (s != null) Txt(s, style = style, color = Color.White.copy(alpha = MutedText), maxLines = 1)
            if (searching) Txt(if (s != null) " · Upgrading…" else "Upgrading…", style = style, color = Color.White, maxLines = 1)
        }
    }
}

/** Android's own "play audio on" picker; older phones get the volume panel or Bluetooth settings. */
fun openOutputSwitcher(ctx: Context) {
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 34) { android.media.MediaRouter2.getInstance(ctx).showSystemOutputSwitcher(); return }
        val intent = if (android.os.Build.VERSION.SDK_INT >= 29) android.content.Intent(android.provider.Settings.Panel.ACTION_VOLUME) else android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
        ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
