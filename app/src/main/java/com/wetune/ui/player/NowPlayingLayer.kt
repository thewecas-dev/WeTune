package com.wetune.ui.player

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.blur
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.wetune.Playback
import com.wetune.Settings
import com.wetune.COVER_AUTO
import com.wetune.sources.Track
import com.wetune.ui.Cover
import com.wetune.ui.Icons
import com.wetune.ui.LocalHaze
import com.wetune.ui.MaterialIcons
import com.wetune.ui.Motion
import com.wetune.ui.Nav
import com.wetune.ui.Route
import com.wetune.ui.TrackMenu
import com.wetune.ui.WT
import com.wetune.ui.press
import com.wetune.ui.LocalColors
import com.wetune.youtube.YouTube
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import com.wetune.ui.Txt
import com.wetune.ui.Type
import com.wetune.ui.drawDepth
import com.wetune.ui.depth
import com.wetune.ui.depthAlpha
import androidx.compose.ui.semantics.onClick

/**
 * The full-size artwork at a fixed 1200 px. Left to the layout, Coil sizes the request to the cover at the moment it
 * loads, and a song changing under Lyrics / Queue (cover = the 52 dp thumbnail) stayed blurry once the cover grew back.
 */
@Composable
private fun hiResArt(art: String?): Any? {
    val ctx = coil3.compose.LocalPlatformContext.current
    return remember(art) { coil3.request.ImageRequest.Builder(ctx).data(com.wetune.youtube.hiRes(art)).size(1200).build() }
}

/** Where the cover sits in the layer at rest (layer coordinates), for the open/close morph from the mini player. */
private class Geo {
    var titleH = 0 // the header title's measured height (px), written while the header measures; read in the same pass
    // the big cover's own rect. Kept still while a panel opens, so the cover (and a Canvas's video view) isn't re-laid out
    // every frame of it.
    var base by mutableStateOf(Rect.Zero); var baseRadius by mutableStateOf(Corners.Zero)
    var card by mutableStateOf(androidx.compose.ui.unit.IntOffset.Zero) // the card's top-left, to line its blurred copy up with the screen
    var screen by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    var slotH by mutableStateOf(0f) // the mini player's height (px), for the card's round ends at rest
    // where the surface is: k = 0 open … 1 the mini player; ivc = into the immersive capsule; small = the title's small look.
    // Written by the layout each frame, read by the shared nodes in their own layout / draw
    var k by androidx.compose.runtime.mutableFloatStateOf(1f); var ivc by androidx.compose.runtime.mutableFloatStateOf(0f); var small by androidx.compose.runtime.mutableFloatStateOf(1f)
}

/**
 * The player: ONE surface for every state, composed at the app root while a song is loaded. At rest it IS the mini
 * player in the dock's slot ([Dock.miniSlot]); opened, the same card is Now Playing's frosted controls sheet (normal →
 * Lyrics / Audio → full screen; Queue straight to full screen); in immersive view, the floating capsule. The cover,
 * the title + artist and play / pause are single nodes moved between those states (never two copies); everything else
 * fades in place. The heavy parts (backdrop, big cover, panels) are composed only while Now Playing is open or moving.
 */
@Composable
fun NowPlayingLayer() {
    val p = NowPlaying.progress
    val visible by remember { derivedStateOf { p.value > 0f || p.isRunning } }
    val t = Playback.current
    if (t == null) { if (visible) LaunchedEffect(Unit) { NowPlaying.close() }; return }
    HideStatusBar(Settings.npHideStatus && NowPlaying.isOpen)
    val geo = remember { Geo() }
    // inside Now Playing, secondary text is see-through foreground rather than fixed grey: grey vanishes on mid-tone backdrops
    val base = WT.c
    val npColors = remember(base) { base.copy(onSecondary = base.on.copy(alpha = 0.68f), onTertiary = base.on.copy(alpha = 0.46f)) }
    val appHaze = LocalHaze.current
    CompositionLocalProvider(LocalColors provides npColors) {
    Box(Modifier.fillMaxSize()) {
        if (visible) {
            // the app beneath dims a little while Now Playing covers it (a still shade: no colour moves across the app)
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.35f * p.value)) })
            Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) // nothing reaches the app beneath
        }
        // at rest nothing full-size is placed, so touches outside the mini player reach the app
        Player(t, geo, base, appHaze, visible)
    }
    }
}

/** Hides the status bar while [hide] (swipe shows it briefly); always restores it when Now Playing goes away. */
@Composable
private fun HideStatusBar(hide: Boolean) {
    val view = LocalView.current
    DisposableEffect(hide) {
        val window = view.context.activity()?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hide) {
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.statusBars())
        } else ctl?.show(WindowInsetsCompat.Type.statusBars())
        onDispose { ctl?.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * The cover's rect and corner radius in the normal state, from the user's size / radius / offset settings, centred
 * between the screen's top edge and the controls card's top ([bottom]) or as the user set it from the centre of [h], cropped to the chosen shape ([art] = the artwork's
 * own width / height, for "original"). "edge" backdrop: edge to edge at the very top.
 */
fun Density.normalCover(w: Int, h: Int, bottom: Int, art: Float = 1f): Pair<Rect, Corners> {
    val ratio = coverAspect(art) // width / height
    // "Fill the space" has no shape of its own: down to the controls card
    if (backdrop == "edge") return Rect(0f, 0f, w.toFloat(), if (Settings.coverAspect == "fill") bottom.toFloat() else w / ratio) to Corners.Zero
    fun corners(cw: Float, ch: Float): Corners {
        val half = minOf(cw, ch) / 2f
        fun r(pct: Int) = half * pct.coerceIn(0, 100) / 100f
        return if (Settings.coverCornersSplit) Corners(r(Settings.coverCornerTL), r(Settings.coverCornerTR), r(Settings.coverCornerBR), r(Settings.coverCornerBL))
        else Corners.all(r(Settings.coverRadius))
    }
    // "Fill the space": edge to edge from the top of the screen down to just above the controls card
    if (Settings.coverAspect == "fill") return Rect(0f, 0f, w.toFloat(), (bottom - 12.dp.toPx()).coerceAtLeast(1f)).let { it to corners(it.width, it.height) }
    val pct = Settings.coverSize.coerceIn(55, 120) / 100f
    // 100 % = edge to edge across the screen, whatever the shape; the % scales that. Never squeezed by the controls: a big
    // cover slides under the card, which is on top.
    val cw = w * pct
    val ch = cw / ratio
    // Default: halfway between the top of the screen and the top of the controls card; once the user moves it, as far
    // between the screen's top and bottom edges as they chose (Settings.coverPlace)
    val place = Settings.coverPlace
    // between the edges: travel runs from the cover touching the top to it touching the bottom (a cover taller than the
    // screen has none and stays centred)
    val cy = if (place == COVER_AUTO) bottom / 2f else if (ch >= h) h / 2f else lerp(ch / 2f, h - ch / 2f, place.coerceIn(0, 100) / 100f)
    val half = minOf(cw, ch) / 2f
    fun r(pct: Int) = half * pct.coerceIn(0, 100) / 100f
    val corners = if (Settings.coverCornersSplit) Corners(r(Settings.coverCornerTL), r(Settings.coverCornerTR), r(Settings.coverCornerBR), r(Settings.coverCornerBL))
    else Corners.all(r(Settings.coverRadius))
    return Rect((w - cw) / 2f, cy - ch / 2f, (w + cw) / 2f, cy + ch / 2f) to corners
}

/**
 * The phone's whole screen shape, width / height in portrait, status and navigation bars included (the app window's
 * size leaves them out and gives too short a shape).
 */
private val screenRatio: Float by lazy {
    val wm = com.wetune.App.ctx.getSystemService(android.view.WindowManager::class.java)
    val (a, b) = if (android.os.Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
    else android.util.DisplayMetrics().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
    minOf(a, b).toFloat() / maxOf(a, b).coerceAtLeast(1)
}

/** Width / height of the cover's crop (Settings.coverAspect); [art] is the artwork's own, used for "original". */
fun coverAspect(art: Float): Float = when (Settings.coverAspect) {
    "4:5" -> 0.8f; "3:4" -> 0.75f; "9:16" -> 9f / 16f; "16:9" -> 16f / 9f
    "original" -> art.coerceIn(0.5f, 2f)
    // the phone's own screen shape (portrait width / height), e.g. 9:20
    "device" -> screenRatio
    else -> 1f
}

/**
 * The controls sheet's corner radius in px: the user's (Settings.sheetRadius) or, on Auto, concentric with the screen's
 * own rounded corners (screen radius − the sheet's [inset]), never under 16dp. No corner info (before Android 12): 30dp.
 */
@Composable
fun sheetRadiusPx(inset: androidx.compose.ui.unit.Dp): Float {
    val d = LocalDensity.current
    val view = LocalView.current
    if (Settings.sheetRadius >= 0) return with(d) { Settings.sheetRadius.coerceIn(0, 48).dp.toPx() }
    val screen = remember(view) {
        if (android.os.Build.VERSION.SDK_INT >= 31) view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0 else 0
    }
    return with(d) { if (screen <= 0) 30.dp.toPx() else maxOf(screen - inset.toPx(), 16.dp.toPx()) }
}

/** The card tint chosen, with retired choices mapped to today's (dense / smoky → black, clear / milky → white, cover → dominant). */
fun cardTintKey(): String = when (val k = Settings.npCardTint) {
    "deep", "dark" -> "black"
    "light", "pill" -> "white"
    "cover" -> "dominant"
    "auto", "black", "white", "accent", "dominant", "neutral" -> k
    else -> "auto"
}

/**
 * Three tones from a cover's sampled colours: accent (the most colourful), dominant (their average) and neutral (the
 * least colourful, dimmed toward grey). Null until the cover's colours are known.
 */
fun coverTones(colors: List<Color>?): Triple<Color, Color, Color>? {
    if (colors.isNullOrEmpty()) return null
    fun sat(c: Color): Float { val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c.toArgb(), hsv); return hsv[1] * hsv[2] }
    val accent = colors.maxBy(::sat)
    val dominant = Color(colors.sumOf { it.red.toDouble() }.toFloat() / colors.size, colors.sumOf { it.green.toDouble() }.toFloat() / colors.size, colors.sumOf { it.blue.toDouble() }.toFloat() / colors.size)
    val neutral = androidx.compose.ui.graphics.lerp(colors.minBy(::sat), Color(0xFF8E8E93), 0.45f)
    return Triple(accent, dominant, neutral)
}

/**
 * The controls card's tint over its blur. Frosted: the navbar's own frost at the app's Glass tint. The others: black,
 * white, or one of the cover's tones, at the card's own tint strength.
 */
@Composable
fun cardTint(art: String?): Color {
    val c = WT.c
    val strength = Settings.npCardStrength.coerceIn(0, 100) / 100f
    val tones = coverTones(coverColors(art))
    return when (cardTintKey()) {
        "black" -> Color.Black.copy(alpha = strength)
        "white" -> Color.White.copy(alpha = strength)
        "accent" -> (tones?.first ?: c.frost).copy(alpha = strength)
        "dominant" -> (tones?.second ?: c.frost).copy(alpha = strength)
        "neutral" -> (tones?.third ?: c.frost).copy(alpha = strength)
        else -> c.frost.copy(alpha = Settings.glassTint.coerceIn(0, 100) / 100f) // frosted, as the navbar
    }
}

/** [c] with its text colours set for reading on a [light] or dark surface (keeps everything else, incl. the theme). */
fun readableOn(c: com.wetune.ui.WeColors, light: Boolean): com.wetune.ui.WeColors = if (light) c.copy(
    on = Color(0xFF111114), onSecondary = Color(0xFF111114).copy(alpha = 0.64f), onTertiary = Color(0xFF111114).copy(alpha = 0.44f),
    chip = Color.Black.copy(alpha = 0.08f), primary = Color(0xFF1C1C1E), onPrimary = Color.White,
) else c.copy(
    on = Color.White, onSecondary = Color.White.copy(alpha = 0.68f), onTertiary = Color.White.copy(alpha = 0.46f),
    chip = Color.White.copy(alpha = 0.12f), primary = Color(0xFFF2F2F2), onPrimary = Color.Black,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Player(t: Track, geo: Geo, appColors: com.wetune.ui.WeColors, appHaze: HazeState, visible: Boolean) {
    val c = WT.c
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val panel = NowPlaying.panel
    val full = panel != NowPlaying.Panel.None // every panel (Lyrics, Queue, Audio) takes the full screen
    // Lyrics / Queue also move their buttons beside prev · play · next; Audio keeps the bottom row (with its own button)
    val beside = panel == NowPlaying.Panel.Queue || panel == NowPlaying.Panel.Lyrics
    val expanded = !full && (panel == NowPlaying.Panel.Lyrics || panel == NowPlaying.Panel.Audio)
    val e = animateFloatAsState(if (expanded) 1f else 0f, Motion.push(), label = "expand")
    val f = animateFloatAsState(if (full) 1f else 0f, Motion.push(), label = "full")
    val b = animateFloatAsState(if (beside) 1f else 0f, Motion.push(), label = "beside")
    // the panel showing or last shown (it stays while it fades out), for the panel's laid-out height
    val lastPanel = remember { arrayOf(NowPlaying.Panel.Lyrics) }
    if (panel != NowPlaying.Panel.None) lastPanel[0] = panel
    // Immersive Canvas: the Canvas full screen over a compact bar (art · title · plain progress · play)
    // whether this song has a moving cover; while a new song's lookup is out, the last answer holds (going out of immersive
    // view and back in, or the switch blinking, at every song change looked broken)
    val videoKnown = !Settings.npVideoCover || videoCheckedFor == t.id
    val hadVideo = remember { booleanArrayOf(false) }
    if (videoKnown) hadVideo[0] = NowPlaying.videoFor == t.id
    val hasVideo = Settings.npVideoCover && hadVideo[0]
    val canImmersive = hasVideo && NowPlaying.videoOn
    // Cover / Canvas comes and goes with the song's moving cover (from the held answer above, so it never blinks while a
    // new song's lookup is out): in = a gentle spring up from 0.85 with a 4dp settle, out = a short fade down to 0.9
    val switchShow = animateFloatAsState(
        if (hasVideo) 1f else 0f,
        if (hasVideo) androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 320f) else tween(180, easing = Motion.Standard),
        label = "switchShow",
    )
    // one even curve both ways: the emphasized one did most of this full-screen morph in its first two frames
    val iv = animateFloatAsState(if (canImmersive && NowPlaying.immersive && !full) 1f else 0f, tween(IMMERSIVE_MS, easing = Motion.Standard), label = "immersive")
    val autoIn = Settings.npImmersiveAuto
    LaunchedEffect(canImmersive && videoKnown && visible, t.id, autoIn) {
        // the auto option: in whenever the song turns out to have a Canvas (while Now Playing shows, or as it opens),
        // except the song the user tapped out of
        if (visible && canImmersive && videoKnown && autoIn && NowPlaying.immersiveAutoFor != t.id) NowPlaying.immersive = true
    }
    // a song that turns out to have no Canvas leaves immersive view (iv eases out); going in again is the tap's or the
    // auto option's call for the next song
    LaunchedEffect(videoKnown && !hasVideo, t.id) { if (videoKnown && !hasVideo) NowPlaying.immersive = false }
    // its style (Settings): the card shrinks into a floating capsule, or fades away for the title on the Canvas;
    // optional lyric lines above either (Settings › Lyrics)
    val capsule = Settings.npImmersiveStyle != "type"
    val immLines = Settings.immersiveLyrics.takeIf { it == 1 || it == 3 } ?: 0
    val immVersions = if (immLines > 0 && visible) inlineLyricsFor(t) else null
    // fixed insets: hiding the status bar must not move anything
    val status = WindowInsets.statusBarsIgnoringVisibility
    val nav = WindowInsets.navigationBars
    val haze = remember { HazeState() }
    // a drag moves the sheet exactly with the finger: progress runs over the sheet's full height (it slides by that much)
    val dragPx = androidx.compose.ui.platform.LocalView.current.height.toFloat().coerceAtLeast(1f)
    val progress = NowPlaying.progress
    val edge = backdrop == "edge"
    val artRatio = if (visible) coverRatio(t.art) else 1f
    // the controls card (Settings › Now Playing › Controls card): blur, with its tint drawn as a plain fill so the
    // card shows even where the blur doesn't
    val cardOn = Settings.npCard
    val inset = Settings.npCardInset.coerceIn(0, 24).dp
    val sheetRadius = sheetRadiusPx(inset)
    val frosted = cardTintKey() == "auto"
    // Frosted = exactly the navbar's glass (Settings › General › Glass); the colours use the card's own strength and blur
    val blur = (if (frosted) Settings.glassBlur else Settings.npCardBlur).coerceIn(0, 80)
    val tint = cardTint(t.art)
    // sideways swipe on the cover: next / previous song
    val coverSwipe = remember { androidx.compose.animation.core.Animatable(0f) }
    val swipeReach = with(density) { 90.dp.toPx() }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // the backdrop + cover as drawn this frame, and its blurred copy for the controls card (a live blur, see the card)
    val under = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val blurred = androidx.compose.ui.graphics.rememberGraphicsLayer()
    // the card's text stays readable on any tint: dark text on a light card, light text on a dark one
    val cardColors = remember(c, tint, cardOn) {
        if (!cardOn) c else {
            val base = if (c.dark) 0.1f else 0.72f // the backdrop under the card, roughly (its readable veil keeps it near this)
            val lum = tint.luminance() * tint.alpha + base * (1f - tint.alpha)
            readableOn(c, light = lum > 0.5f)
        }
    }
    // inline lyrics (Settings.inlineLyrics lines) reserve their height only once the song turns out to have synced lyrics
    val inlineLines = Settings.inlineLyrics.takeIf { it == 1 || it == 3 } ?: 0
    val inlineVersions = if (inlineLines > 0 && visible) inlineLyricsFor(t) else null
    val hasInline = inlineLines > 0 && inlineVersions?.firstOrNull()?.synced == true
    val loadingInline = inlineLines > 0 && inlineVersions == null
    // the space opens while lyrics load ("Loading lyrics…", so a new song never shrinks then grows it) and stays for timed
    // lyrics; it eases in and out over ~half a second with the standard curve
    val inlineH = animateFloatAsState(if (loadingInline || hasInline) inlineLines * 28f else 0f, tween(520, easing = Motion.Standard), label = "inline") // dp

    // the mini player's gestures, on the card and on the title (the parts it shows at rest): drag up opens under the
    // finger, a tap opens, a sideways swipe skips (the title follows the finger, then the next one slides in). Each
    // checks the state when it fires: the modifiers stay the same while the surface morphs, so a drag never restarts.
    val swipe = remember { androidx.compose.animation.core.Animatable(0f) }
    val swipeMini = with(density) { 80.dp.toPx() }
    val miniGestures = Modifier.sheetDrag(dragPx)
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val dx = swipe.value
                    scope.launch {
                        if (kotlin.math.abs(dx) >= swipeMini) {
                            val dir = if (dx < 0) -1f else 1f
                            swipe.animateTo(dir * swipeMini * 2.2f, tween(160, easing = Motion.Standard)) // the old title leaves
                            if (dir < 0) Playback.next() else Playback.prev()
                            swipe.snapTo(-dir * swipeMini * 1.4f) // the new one comes from the other side
                            swipe.animateTo(0f, tween(Motion.FOLD, easing = Motion.Decelerate))
                        } else swipe.animateTo(0f, tween(Motion.SMALL, easing = Motion.Standard))
                    }
                },
                onDragCancel = { scope.launch { swipe.animateTo(0f) } },
            ) { ch, dx ->
                if (progress.value > 0f) return@detectHorizontalDragGestures // only the mini player skips this way
                ch.consume()
                val before = swipe.value
                scope.launch { swipe.snapTo(before + dx) }
                if (kotlin.math.abs(before) < swipeMini && kotlin.math.abs(before + dx) >= swipeMini) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            }
        }
        .pointerInput(Unit) { detectTapGestures { if (progress.value == 0f) NowPlaying.open() } }

    fun openArtist() {
        if (t.source != "yt") return
        scope.launch {
            val card = withContext(Dispatchers.IO) { runCatching { YouTube.links(t.ref) }.getOrDefault(emptyList()) }.firstOrNull() ?: return@launch
            NowPlaying.close(); Nav.open(Route.Collection(card))
        }
    }

    CompositionLocalProvider(LocalHaze provides haze, LocalColors provides cardColors) {
        Layout(
            content = {
                // 0: backdrop + cover, the frosted sheet's backdrop. Tapping the cover closes an open Lyrics / Audio sheet.
                // Only while Now Playing shows (the mini player alone has none).
                if (!visible) Box(Modifier) else Box(
                    // recorded each frame into [under] (then drawn as usual), for the card's live blur above it. Fades
                    // where it is on close (outside the recording: the card keeps its frost colours on its way down)
                    Modifier.fillMaxSize().graphicsLayer { alpha = bgAlpha(progress.value) }.background(c.bg).drawWithContent { under.record { this@drawWithContent.drawContent() }; drawLayer(under) }
                        // the app's blur source too, so sheets opened over Now Playing blur it (the card's own frost
                        // leaves this layer out)
                        .hazeSource(haze).hazeSource(appHaze, zIndex = 1f).sheetDrag(dragPx)
                        // swipe sideways: left = next song, right = previous; the cover follows the finger, the next slides in
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val dx = coverSwipe.value
                                    scope.launch {
                                        if (kotlin.math.abs(dx) >= swipeReach) {
                                            val dir = if (dx < 0) -1f else 1f
                                            coverSwipe.animateTo(dir * swipeReach * 2.5f, tween(170, easing = Motion.Standard)) // the old cover leaves
                                            if (dir < 0) Playback.next() else Playback.prev()
                                            coverSwipe.snapTo(-dir * swipeReach * 1.6f) // the new one comes from the other side
                                            coverSwipe.animateTo(0f, tween(Motion.PUSH, easing = Motion.Decelerate))
                                        } else coverSwipe.animateTo(0f, tween(Motion.SMALL, easing = Motion.Standard))
                                    }
                                },
                                onDragCancel = { scope.launch { coverSwipe.animateTo(0f) } },
                            ) { ch, dx ->
                                ch.consume()
                                val before = coverSwipe.value
                                scope.launch { coverSwipe.snapTo(before + dx) }
                                if (kotlin.math.abs(before) < swipeReach && kotlin.math.abs(before + dx) >= swipeReach) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            }
                        }
                        .pointerInput(Unit) {
                        detectTapGestures {
                            val pn = NowPlaying.panel
                            if (pn == NowPlaying.Panel.Audio || (pn == NowPlaying.Panel.Lyrics && !NowPlaying.lyricsFull)) NowPlaying.panel = NowPlaying.Panel.None
                            // Immersive Canvas: the cover goes in and out (read fresh: this gesture outlives compositions)
                            else if (pn == NowPlaying.Panel.None && Settings.npVideoCover && NowPlaying.videoOn &&
                                NowPlaying.videoFor == Playback.current?.id) {
                                // tapping out keeps the auto option out for this song (the next song with a Canvas goes in again)
                                if (NowPlaying.immersive) NowPlaying.immersiveAutoFor = Playback.current?.id
                                NowPlaying.immersive = !NowPlaying.immersive
                            }
                        }
                    },
                ) {
                    Backdrop(t)
                    // its own layer under the controls: sized and placed only by the cover settings, free to slide under the
                    // card. Reports the screen's size so a cover wider than the screen (>100 %) is never re-centred.
                    Box(Modifier.layout { m, cs ->
                        val r = geo.base
                        val pl = m.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
                        layout(cs.maxWidth, cs.maxHeight) { pl.place(0, 0) }
                    }.graphicsLayer {
                        val r = geo.base
                        // in full screen the lifted copy (11) shows instead; a sideways swipe fades it as it slides
                        // stays put while a panel opens, the card growing over it, and fades away under it (a full-screen
                        // card over a cover read as a light band); while Now Playing opens or closes, only its part under
                        // the card stays here (clipped below), the flying copy is the rest
                        alpha = (1f - f.value) * (1f - kotlin.math.abs(coverSwipe.value) / (swipeReach * 2.5f)).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = r.left + coverSwipe.value; translationY = r.top
                        // plain and squircle covers are clipped and shadowed here; the other styles draw (and shadow) their own parts
                        val styled = coverStyle
                        if (coverClips(styled)) { shape = geo.baseRadius.shapeFor(styled); clip = true } else clip = false
                        val lift = if (Settings.coverShadow && !edge && coverClips(styled)) Settings.coverShadowStrength / 100f * (1f - e.value) * (1f - f.value) * (1f - iv.value) else 0f
                        shadowElevation = lift * 36.dp.toPx()
                        spotShadowColor = Color.Black; ambientShadowColor = Color.Black
                    }.then(if (edge) Modifier.edgeFade { (1f - e.value) * (1f - f.value) * (1f - iv.value) } else Modifier)) {
                        StyledCover(t, coverStyle, geo.baseRadius) {
                            // the usual artwork, with the full-size one laid over it once it arrives (no blank moment)
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(t.art, "Album artwork: ${t.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                AsyncImage(hiResArt(t.art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                if (Settings.npVideoCover) VideoCover(t, active = !full) // over the artwork; paused under a panel
                            }
                        }
                    }
                }
                // 1: the top strip: drag here to close; the grabber and "Playing from" (hidden in immersive view)
                if (!visible) Box(Modifier) else Box(Modifier.fillMaxWidth().height(56.dp).graphicsLayer { alpha = 1f - f.value }.sheetDrag(dragPx)) { // immersive: its placement fades it
                    if (Settings.npGrabber) Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(36.dp, 5.dp).background(c.on.copy(alpha = 0.4f), CircleShape))
                    val from = Playback.from
                    if (Settings.npPlayingFrom && from != null) Column(
                        Modifier.align(Alignment.TopCenter).padding(top = if (Settings.npGrabber) 17.dp else 8.dp).padding(horizontal = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Txt("Playing from ${from.first}".uppercase(), Type.caption.copy(letterSpacing = 0.06.em), color = c.onSecondary)
                        Txt(from.second, Type.sub.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.on)
                    }
                }
                // 2: the card: frosted over the backdrop and cover, corners concentric with the screen's; in full screen it
                // spreads edge to edge, square. Turned off, it shows only behind an open panel.
                // At rest this card IS the mini player's pill; it grows into Now Playing's sheet (or the capsule).
                // immersive: the capsule style keeps the card (it becomes the pill); the title style fades it out.
                // Toward the mini player it is solid (it is the mini player). Title style: it outlasts the controls on
                // it (they're gone by 0.45), so no text is left on nothing.
                val cardAlpha = {
                    val ci = if (capsule) iv.value else 0f
                    val pv = progress.value; val k = 1f - pv
                    val rest = (if (cardOn) 1f else maxOf(e.value, f.value, ci)) * (if (capsule) 1f else 1f - span(iv.value * pv, 0.25f, 0.85f))
                    lerp(rest, 1f, (k / 0.3f).coerceAtMost(1f))
                }
                val cardRadius: androidx.compose.ui.unit.Density.() -> Float = {
                    val ci = if (capsule) iv.value else 0f
                    lerp(lerp(lerp(sheetRadius, 32.dp.toPx(), ci), 0f, f.value), geo.slotH / 2f, 1f - progress.value)
                }
                Box(
                    // the logo's depth (ui/Depth.kt) on this one surface, so the mini player, card, panels and capsule
                    // all share it: outside the clip, following the corner radius; gone as it spreads to full screen.
                    // Settings › Now Playing › Card › Shadow turns it off or scales it.
                    Modifier.then(if (Settings.npCardShadow) Modifier.depth(appColors.dark, cardRadius, { cardAlpha() * (1f - f.value) }, Settings.npCardShadowStrength.coerceIn(10, 100) / 50f) else Modifier)
                    .graphicsLayer {
                        alpha = cardAlpha()
                        shape = RoundedCornerShape(cardRadius()); clip = true
                    }.then(miniGestures).then(
                        if (!NowPlaying.isOpen) Modifier.semantics { contentDescription = "Open Now Playing"; onClick("Open Now Playing") { NowPlaying.open(); true } } else Modifier,
                    ),
                ) {
                    // the app's frost (the mini player's): what's under the card while Now Playing's backdrop is faded;
                    // dropped once the backdrop fully covers it
                    val appFrost by remember { derivedStateOf { bgAlpha(progress.value) < 1f } }
                    if (appFrost) Box(Modifier.matchParentSize().hazeEffect(appHaze, com.wetune.ui.frostStyle()) { canDrawArea = { it.zIndex < 1f } })
                    // Now Playing's own card look over it, as solid as the backdrop under it: one surface, never two
                    if (visible) Box(Modifier.matchParentSize().graphicsLayer { alpha = bgAlpha(progress.value) }) {
                    // the blur: what's really under the card, live. The backdrop + cover layer (0) is recorded each frame
                    // (see [under]); here that same recording is drawn blurred and shifted to line up with the screen, so
                    // the card follows anything moving beneath it (an animated cover, Colour flow, the cover sliding).
                    // Android 12+; older phones get the tint alone.
                    // Blurred at a quarter of the size and scaled back up: a moving cover changes [under] every video
                    // frame, and a full-size blur each frame was most of the render thread's time (Now Playing lagged)
                    if (blur > 0 && android.os.Build.VERSION.SDK_INT >= 31) Box(Modifier.matchParentSize().drawBehind {
                        val k = 4f
                        blurred.renderEffect = androidx.compose.ui.graphics.BlurEffect(blur.dp.toPx() / k, blur.dp.toPx() / k, androidx.compose.ui.graphics.TileMode.Clamp)
                        blurred.record(androidx.compose.ui.unit.IntSize((geo.screen.width / k).roundToInt().coerceAtLeast(1), (geo.screen.height / k).roundToInt().coerceAtLeast(1))) {
                            scale(1f / k, 1f / k, pivot = androidx.compose.ui.geometry.Offset.Zero) { drawLayer(under) }
                        }
                        translate(-geo.card.x.toFloat(), -geo.card.y.toFloat()) {
                            scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) { drawLayer(blurred) }
                        }
                    })
                    val noise = remember { androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(com.wetune.ui.grain, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated)) }
                    Box(Modifier.matchParentSize().drawBehind { drawRect(tint); if (frosted) drawRect(noise, alpha = if (c.dark) 0.5f else 0.3f) }) // frosted: the navbar's grain too
                    }
                }
                // 3: the header's like / more (the header's title and thumbnail are the shared nodes 14 and 11)
                // immersive: gone early, before the title's flight takes them along
                if (!visible) Box(Modifier) else Row(Modifier.sheetDrag(dragPx).graphicsLayer { alpha = ctlAlpha(progress.value) * (1f - span(iv.value, 0f, 0.3f)) }, verticalAlignment = Alignment.CenterVertically) {
                    LikeButton(t)
                    if (material) Spacer(Modifier.width(10.dp)) // Material's 48dp circles fill their touch targets; Apple's 32dp ones leave a gap
                    RoundBtn(Icons.More, MaterialIcons.More, "More for ${t.title}") { TrackMenu.show(t) }
                }
                // 4: the open panel
                // alpha multiplied into each draw: a plain alpha layer copied the whole panel offscreen every frame of the fade
                Box(Modifier.graphicsLayer { alpha = maxOf(e.value, f.value) * ctlAlpha(progress.value); compositingStrategy = CompositingStrategy.ModulateAlpha }.padding(horizontal = 16.dp)) {
                    if (visible) AnimatedContent(
                        targetState = panel,
                        transitionSpec = { fadeIn(tween(Motion.FOLD, Motion.SMALL / 2)) togetherWith fadeOut(tween(Motion.SMALL)) },
                        label = "panel",
                    ) { pnl ->
                        when (pnl) {
                            NowPlaying.Panel.Lyrics -> LyricsPanel(t, big = NowPlaying.lyricsFull)
                            NowPlaying.Panel.Audio -> AudioPanel(t)
                            NowPlaying.Panel.Queue -> QueuePanel()
                            NowPlaying.Panel.None -> Box(Modifier.fillMaxSize())
                        }
                    }
                }
                // 5: seek
                if (!visible) Box(Modifier) else SeekBar(badge = panel == NowPlaying.Panel.None)
                // 6: transport (shuffle / repeat fade out in full screen); its play / pause is the shared node 15
                if (!visible) Box(Modifier) else Transport(sides = { b.value }, play = false)
                // 7: the action row (Audio; empty slots under Lyrics and Queue), folding away in full screen
                Box(Modifier.graphicsLayer { alpha = (1f - b.value * 2f).coerceIn(0f, 1f) }) { if (visible) ActionRow() }
                // 8, 9: Lyrics and Queue, flying between the action row and the sides of the transport
                Box { if (visible) PanelButton(lyrics = true) { b.value } }
                Box { if (visible) PanelButton(lyrics = false) { b.value } }
                // 10: inline lyrics in the normal sheet; tapping them opens full-screen lyrics
                // back only once the panel's own content has gone (they share this spot: both at once overlapped)
                Box(Modifier.graphicsLayer { alpha = (1f - e.value) * (1f - f.value / 0.3f).coerceIn(0f, 1f) * (inlineH.value / (inlineLines * 28f).coerceAtLeast(1f)) }) {
                    if (inlineVersions != null && hasInline) InlineLyrics(t, inlineVersions, inlineLines)
                    else if (loadingInline && visible) InlineLoading()
                }
                // 11: THE cover thumbnail, one node: the mini player's round cover at rest, a dot at the header title's
                // left growing into the 52dp cover in full screen, the capsule's round art. Placed, scaled and clipped
                // by the layout. (The big cover in 0 is Now Playing's own, behind the card.)
                Box(Modifier.background(appColors.surface)) {
                    AsyncImage(t.art, "Album artwork: ${t.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    AsyncImage(hiResArt(t.art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                // 12: Cover / Canvas, inside the cover's bottom edge: springs in and fades out as the song gains or loses a
                // moving cover
                val switchOn by remember { derivedStateOf { switchShow.value > 0f } }
                Box { if (switchOn && visible) CoverVideoSwitch() }
                // 13: immersive view's lyric lines / shade, and the title style's controls (composed only while they show)
                val barOn by remember { derivedStateOf { iv.value > 0f } }
                Box { if (barOn && visible) ImmersiveBar(t, capsule, immLines, immVersions) { iv.value } } // the close morph fades its placement (pv)
                // 14: THE title + artist, one node: the mini player's, the header's, the capsule's. Its colours follow
                // what's under it (the app's frost → the card), in the draw phase.
                MorphTitle(
                    t, appColors, cardColors, mix = { bgAlpha(progress.value) }, small = { geo.small },
                    onOpenH = { geo.titleH = it }, shift = { swipe.value },
                    onArtist = if (t.source == "yt" && NowPlaying.isOpen) ::openArtist else null,
                    modifier = miniGestures,
                )
                // 15: THE play / pause, one node: on the mini player's cover (ringed with progress), the transport's
                // middle button (the capsule shares the mini player's look)
                PlayMorph(t, appColors, small = { geo.small }, mix = { bgAlpha(progress.value) })
                // 16: the mini player's open button (44dp target, 36dp chip)
                Box(Modifier.press("Open Now Playing", scale = 0.9f) { NowPlaying.open() }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp).background(appColors.chip, CircleShape), contentAlignment = Alignment.Center) { com.wetune.ui.Icon(Icons.Up, appColors.on, 16.dp) }
                }
            },
        ) { ms, cs ->
            val w = cs.maxWidth; val h = cs.maxHeight
            val side = inset.roundToPx(); val inner = 20.dp.roundToPx()
            val sheetW = w - side * 2; val rowW = sheetW - inner * 2
            // eased with the card (a switch here jumped the card's top edge and the cover on the first frame of a panel)
            val padTop0 = 24.dp.roundToPx(); val padTop1 = 16.dp.roundToPx()
            val padTop = lerp(padTop0.toFloat(), padTop1.toFloat(), f.value).roundToInt(); val padBottom = 24.dp.roundToPx() // room under the Lyrics · Audio · Queue row
            // the card sits [side] above the navigation bar, as far in as from the sides; everything inside follows it
            val top = status.getTop(this); val bottomGap = side + nav.getBottom(this)
            val ev = e.value; val fv = f.value; val bv = b.value
            val like = ms[3].measure(Constraints(maxWidth = sheetW))
            // where the surface is between its states: k = 0 open … 1 the mini player (in the dock's slot, folded or not);
            // ivc = how far into the immersive capsule
            val pv = progress.value; val k = 1f - pv
            val ivv = iv.value
            val ivc = if (capsule) ivv else 0f
            val slot = com.wetune.ui.Dock.miniSlot(w, h, com.wetune.ui.Dock.fold.value, nav.getBottom(this), this)
            val small = 1f - (1f - ivc) * (1f - k) // the title's small look: the mini player's and the capsule's
            geo.slotH = slot.height; geo.k = k; geo.ivc = ivc; geo.small = small
            // the shared title, measured first: its open height sets the header's. Its width: the header's room (beside
            // the thumbnail and like / more) → the capsule's → the mini player's
            val titleW = lerp(
                lerp((sheetW - 2 * inner - 64.dp.toPx() * fv - like.width), w - 122.dp.toPx(), ivc),
                slot.width - 128.dp.toPx(), k,
            ).roundToInt().coerceAtLeast(1)
            val title = ms[14].measure(Constraints(minWidth = titleW, maxWidth = titleW))
            val headerH = maxOf(geo.titleH, like.height)
            val row = Constraints(maxWidth = rowW, minWidth = rowW)
            val seek = ms[5].measure(row); val transport = ms[6].measure(row); val action = ms[7].measure(row)
            // vertical rhythm under the sheet's content: seek · 20 · transport · 22 · action row (that last part folds away)
            val gapT = 20.dp.roundToPx(); val gapA = 22.dp.roundToPx()
            // Lyrics / Queue open: the action row stays exactly where it is and prev · play · next come down into it (their
            // centre on its centre), so the cluster shrinks from the top: seek · 20 · [transport in the action row]
            val bottomH1 = seek.height + gapT + (action.height + transport.height) / 2
            val bottomH0 = seek.height + gapT + transport.height + gapA + action.height
            val bottomH = lerp(bottomH0.toFloat(), bottomH1.toFloat(), bv).roundToInt()
            // header · 24 · [inline lyrics · 20] · seek…; the lyrics' height animates in once the song turns out to have them
            val gapH = 24.dp.roundToPx()
            val inlinePx = inlineH.value.dp.roundToPx()
            val shown = if (inlineLines > 0) inlineH.value / (inlineLines * 28f) else 0f // 0..1 as the lyrics slide in
            val inlinePart = inlinePx + (20.dp.toPx() * shown).roundToInt()
            val natural = padTop + headerH + gapH + inlinePart + bottomH0 + padBottom
            val inline = ms[10].measure(Constraints.fixed(rowW, inlinePx.coerceAtLeast(0)))
            val hExpanded = h - bottomGap - 353.dp.roundToPx()
            val hFull = h - bottomGap - top - 10.dp.roundToPx()
            val sheetH = lerp(lerp(natural.toFloat(), hExpanded.toFloat().coerceAtLeast(natural.toFloat()), ev), hFull.toFloat(), fv).roundToInt()
            val sheetTop = h - bottomGap - sheetH
            val seekY = sheetTop + sheetH - padBottom - bottomH
            val transportY = seekY + seek.height + gapT
            val actionY = sheetTop + sheetH - padBottom - action.height // fixed to the card's bottom in every state
            val midTop = sheetTop + padTop + headerH + 14.dp.roundToPx()
            val midBottom = seekY - 12.dp.roundToPx()
            // the panel is laid out once at its full-screen height and only uncovered as the card grows: sizing it to the
            // card each frame re-laid out its list (and resized its offscreen layers) on every frame of the transition
            val cluster = if (lastPanel[0] == NowPlaying.Panel.Audio) bottomH0 else bottomH1
            val midFull = (h - bottomGap - padBottom - cluster - 12.dp.roundToPx()) -
                (top + 10.dp.roundToPx() + padTop1 + headerH + 14.dp.roundToPx())
            val midShown = (midBottom - midTop).coerceAtLeast(0)
            val middle = ms[4].measure(Constraints.fixed(sheetW, midFull.coerceAtLeast(0)))
            val bar = ms[1].measure(Constraints(maxWidth = w))
            // immersive view, capsule style: the card shrinks into a 64dp pill, 16dp in from the sides and 24dp above the
            // navigation bar (ImmersiveBar lays its row out on exactly that rect); title style: the card fades where it is
            val ci = ivc
            val capB = h - nav.getBottom(this) - 24.dp.roundToPx(); val capT = capB - 64.dp.roundToPx(); val capX = 16.dp.toPx()
            // the card: exactly the area its content uses (so the edge spacing moves the controls too); full screen: every edge
            val cardL0 = lerp(lerp(side.toFloat(), capX, ci), 0f, fv); val cardT0 = lerp(lerp(sheetTop.toFloat(), capT.toFloat(), ci), 0f, fv)
            val cardR0 = lerp(lerp((w - side).toFloat(), w - capX, ci), w.toFloat(), fv); val cardB0 = lerp(lerp((h - bottomGap).toFloat(), capB.toFloat(), ci), h.toFloat(), fv)
            // open / close: the card IS the mini player's pill at k = 1; everything that rides it (title, thumbnail, play)
            // is interpolated with the same k, so it stays on the card the whole way
            val ive = ivv * pv // immersive's own controls give way to the morph
            val ctl = ctlAlpha(pv)
            // a drag moves the card's top exactly with the finger (never less than a quarter screen, e.g. from the capsule);
            // measured only while Now Playing's parts are composed (at rest they measure nothing)
            if (visible) NowPlaying.dragSpan = maxOf(slot.top - cardT0, h / 4f)
            val cardL = lerp(cardL0, slot.left, k).roundToInt(); val cardT = lerp(cardT0, slot.top, k).roundToInt()
            val cardR = lerp(cardR0, slot.right, k).roundToInt(); val cardB = lerp(cardB0, slot.bottom, k).roundToInt()
            geo.card = androidx.compose.ui.unit.IntOffset(cardL, cardT); geo.screen = androidx.compose.ui.unit.IntSize(w, h)
            val surface = ms[2].measure(Constraints.fixed((cardR - cardL).coerceAtLeast(0), (cardB - cardT).coerceAtLeast(0)))

            // Lyrics / Queue: action-row slot (first / last) → beside prev · play · next (shuffle / repeat's places)
            val (aw, ah) = actionButtonSize; val (sw, sh) = sideButtonSize
            val slotW = if (material) (rowW - 4.dp.roundToPx()) / 3 else aw.roundToPx()
            val slotGap = if (material) 0 else (rowW - 3 * slotW) / 3
            val slotH = ah.roundToPx()
            val slot0X = if (material) 0 else slotGap / 2
            val slot2X = if (material) rowW - slotW else slotGap / 2 + 2 * (slotW + slotGap)
            val sideW = sw.roundToPx(); val sideH = sh.roundToPx()
            val btnW = lerp(slotW.toFloat(), sideW.toFloat(), bv).roundToInt()
            val btnH = slotH // Lyrics / Queue only ever slide sideways: same height, same row, every frame
            val btnY = actionY
            val lyricsX = lerp(slot0X.toFloat(), 0f, bv).roundToInt()
            val queueX = lerp(slot2X.toFloat(), (rowW - sideW).toFloat(), bv).roundToInt()
            val lyricsBtn = ms[8].measure(Constraints.fixed(btnW, btnH))
            val queueBtn = ms[9].measure(Constraints.fixed(btnW, btnH))
            // cover: the user's placement, in every state
            // cover: the user's normal placement → top edge when the sheet grows → thumbnail in full screen
            // the cover assumes the inline-lyrics space the setting asks for, so lyrics loading (or not) never moves it
            val reserved = if (inlineLines > 0) (inlineLines * 28).dp.roundToPx() + 20.dp.roundToPx() else 0
            val naturalTop = h - bottomGap - (natural - padTop + padTop0 - inlinePart + reserved) // the normal state's, in every state
            val (normal, normalRadius) = normalCover(w, h, naturalTop, artRatio)
            // where "default" puts it, in the Position slider's terms (% of the way from the top edge to the bottom edge)
            (if (normal.height >= h) 50 else ((naturalTop - normal.height) / 2f / (h - normal.height) * 100f).roundToInt().coerceIn(0, 100))
                .let { if (it != NowPlaying.coverAutoPos) NowPlaying.coverAutoPos = it }
            // the cover stays put while a panel opens: the card grows over it. It never leaves Now Playing: on close it
            // fades where it is (with the backdrop, child 0)
            // immersive view: the cover grows to the whole screen, square-cornered
            geo.base = lerpRect(normal, Rect(0f, 0f, w.toFloat(), h.toFloat()), ivv)
            geo.baseRadius = normalRadius.lerp(Corners.Zero, ivv)
            val back = ms[0].measure(Constraints.fixed(w, h)) // after geo, so the cover lays out at this frame's rect
            val switch = ms[12].measure(Constraints())
            val switchA = (1f - ev) * (1f - fv) * (1f - ivv)
            val compact = ms[13].measure(Constraints(maxWidth = w, maxHeight = h))

            // The three shared nodes, each at one spot per state: the header (open), the capsule's row, the mini player.
            // Mini player: cover centre 30dp in (concentric with the 60dp pill), title from 66dp, chevron chip at the end.
            val hx = side; val hy = sheetTop + padTop
            val x0 = side + inner
            val openCy = hy + headerH / 2f; val capCy = capT + 32.dp.toPx(); val miniCy = slot.center.y
            val discX = slot.left + 30.dp.toPx()
            // the title: the full-screen header's is smaller (0.78); the small look is its own (MorphTitle)
            val outer = lerp(lerp(1f, 0.78f, fv), 1f, small)
            val titleX = lerp(lerp(x0 + 64.dp.toPx() * fv, 84.dp.toPx(), ivc), slot.left + 66.dp.toPx(), k)
            val titleY = lerp(lerp(openCy, capCy, ivc), miniCy, k) - title.height * outer / 2f

            // the cover: a dot at the title's left edge that grows into the 52dp cover as the title moves right (full
            // screen), the 40dp round cover of the capsule and of the mini player (the same look)
            val thumbSize = 52.dp.toPx()
            val tsz0 = lerp(thumbSize * fv, 40.dp.toPx(), ivc)
            val tcx = lerp(lerp(x0 + 26.dp.toPx() * fv, 48.dp.toPx(), ivc), discX, k)
            val tcy = lerp(lerp(openCy, capCy, ivc), miniCy, k)
            val tsz = lerp(tsz0, 40.dp.toPx(), k)
            val trad = lerp(lerp(lerp(tsz0 / 2f, 8.dp.toPx(), fv), tsz0 / 2f, ivc), 20.dp.toPx(), k)
            val lifted = ms[11].measure(Constraints.fixed(thumbSize.roundToInt(), thumbSize.roundToInt()))

            // play / pause: the transport's middle button → the ringed 52dp disc over the cover (mini player and capsule alike)
            val pw = lerp((if (material) 96.dp else 64.dp).toPx(), 52.dp.toPx(), small).roundToInt()
            val ph = lerp((if (material) 66.dp else 64.dp).toPx(), 52.dp.toPx(), small).roundToInt()
            // it crosses prev only once the transport has faded (gone by ivc 0.45 / k 0.3), and is beside the disc before the
            // title comes down to its row: sideways in the middle of the move, down / up last (capsule)
            val pcx = lerp(lerp(x0 + rowW / 2f, 48.dp.toPx(), smooth(span(ivc, 0.3f, 0.8f))), discX, smooth(span(k, 0.25f, 0.7f)))
            val pcy = lerp(lerp(transportY + transport.height / 2f, capCy, smooth(span(ivc, 0.45f, 1f))), miniCy, k)
            val play = ms[15].measure(Constraints.fixed(pw, ph))
            val chevron = ms[16].measure(Constraints.fixed(44.dp.roundToPx(), 44.dp.roundToPx()))

            layout(w, h) {
                // the normal controls fade out for immersive view (gone and untouchable once it's in), and early on close
                // (immersive: gone in the first 45 %, before the card has shrunk past them and before its own controls come)
                val out = 1f - span(ive, 0f, 0.45f)
                fun androidx.compose.ui.layout.Placeable.put(x: Int, y: Int, a: Float = out * ctl) { if (a > 0.01f) placeWithLayer(x, y) { alpha = a } }
                if (visible) back.place(0, 0)
                // the top strip and the header carry the drag: kept placed while they fade, or the drag would end
                if (visible && 1f - ive > 0.01f) bar.placeWithLayer(0, top) { alpha = out * ctl }
                surface.place(cardL, cardT)
                if (visible && 1f - ive > 0.01f) like.placeWithLayer(side + sheetW - inner - like.width, hy + (headerH - like.height) / 2) { alpha = out }
                inline.put(side + inner, sheetTop + padTop + headerH + gapH)
                // clipped to the card as well, which shrinks away from it on close
                val clipTop = (cardT - midTop).coerceAtLeast(0).toFloat(); val clipBottom = minOf(midShown, cardB - midTop).toFloat()
                middle.placeWithLayer(side, midTop) { clip = true; shape = androidx.compose.foundation.shape.GenericShape { s, _ -> addRect(Rect(0f, clipTop, s.width, clipBottom.coerceAtLeast(clipTop))) } }
                seek.put(x0, seekY)
                transport.put(x0, transportY)
                action.put(x0, actionY)
                lyricsBtn.put(x0 + lyricsX, btnY)
                queueBtn.put(x0 + queueX, btnY)
                // centred in the cover's bottom edge (above the card if the cover runs under it); 44dp tall, 32 of it drawn
                // its slot never moves; it springs in (0.85 → 1, settling 4dp up) or fades out (→ 0.9) in the draw phase
                val sv = switchShow.value
                val swA = switchA * ctl * sv.coerceIn(0f, 1f)
                if (swA > 0.01f) switch.placeWithLayer((normal.center.x - switch.width / 2f).roundToInt(), minOf(normal.bottom.roundToInt(), cardT) - 8.dp.roundToPx() - switch.height) {
                    alpha = swA
                    val s = lerp(if (hasVideo) 0.85f else 0.9f, 1f, sv); scaleX = s; scaleY = s
                    translationY = if (hasVideo) (1f - sv) * 4.dp.toPx() else 0f
                }
                // capsule: its lyric lines and shade ride the card's bottom edge as the card shrinks into it, and fade on
                // close; title style: its own title and play are gone before the header's title comes back (no two at once)
                compact.put(0, h - compact.height + (if (capsule) (cardB0 - capB).roundToInt() else 0), if (ive > 0.01f) (if (capsule) pv else span(pv, 0.5f, 1f)) else 0f)
                // the shared nodes, on top of the card. Title style: the header's title and play fade out for the Canvas's own
                val shared = if (capsule) 1f else out
                if (tsz > 0.5f) lifted.placeWithLayer(0, 0) {
                    val s = tsz / thumbSize
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = tcx - tsz / 2f; translationY = tcy - tsz / 2f
                    scaleX = s; scaleY = s
                    shape = RoundedCornerShape(trad / s); clip = true
                }
                val swipeA = (1f - kotlin.math.abs(swipe.value) / (swipeMini * 2.2f)).coerceIn(0f, 1f)
                if (shared * swipeA > 0.01f) title.placeWithLayer(titleX.roundToInt(), titleY.roundToInt()) {
                    alpha = shared * swipeA
                    transformOrigin = TransformOrigin(0f, 0f); scaleX = outer; scaleY = outer
                    clip = swipe.value != 0f // a skip slides the lines inside the title's own box, never over the cover
                }
                if (shared > 0.01f) play.placeWithLayer((pcx - pw / 2f).roundToInt(), (pcy - ph / 2f).roundToInt()) { alpha = shared }
                val chevA = span(k, 0.6f, 1f)
                if (chevA > 0.01f) chevron.placeWithLayer(slot.right.roundToInt() - 8.dp.roundToPx() - chevron.width, (miniCy - chevron.height / 2f).roundToInt()) { alpha = chevA }
            }
        }
    }
}

/**
 * Immersive Canvas's controls, laid out from the bottom of the screen. [capsule]: a 64dp row on the card, which the
 * layout has shrunk into a pill 24dp above the navigation bar (round art, title over artist, play with a progress ring);
 * else straight on the Canvas: a soft bottom shade, a big title and artist, a frosted round play button and a hairline
 * of progress. [lines] of lyrics (0, 1 or 3) sit above either, white on the shade; tapping them opens full lyrics.
 * [a]: how far in immersive view is (0..1, draw phase); each part fades in its own stretch of it, after the normal
 * controls have gone, and the capsule's title takes over from the flying header title only at the end.
 */
@Composable
private fun ImmersiveBar(t: Track, capsule: Boolean, lines: Int, versions: List<com.wetune.Lyrics.Version>?, a: () -> Float) {
    val base = WT.c
    // over the Canvas text is always white (the card's colours suit the card, not the video)
    val white = remember(base) { base.copy(on = Color.White, onSecondary = Color.White.copy(alpha = 0.72f), onTertiary = Color.White.copy(alpha = 0.5f)) }
    val pos = com.wetune.rememberPosition()
    val playing = Playback.playing
    val progress = { (pos.value.toFloat() / t.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) }
    val lyrics = lines > 0 && versions?.firstOrNull()?.synced == true
    // the lyrics' room opens while they load and eases shut if the song has none; they (and, in the capsule, the shade
    // under them) fade in once they come: nothing pops in over the Canvas
    val room = animateFloatAsState(if (lines > 0 && (versions == null || lyrics)) lines * 28f + 18f else 0f, tween(520, easing = Motion.Standard), label = "room")
    val shown = animateFloatAsState(if (lyrics) 1f else 0f, tween(Motion.PUSH, easing = Motion.Standard), label = "lines")
    val shadeBrush = remember { Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Black.copy(alpha = 0.5f), 1f to Color.Black.copy(alpha = 0.78f)) }
    Column(
        Modifier.fillMaxWidth()
            .drawBehind { drawRect(shadeBrush, alpha = span(a(), 0.2f, 0.8f) * (if (capsule) shown.value else 1f)) }
            .padding(top = 72.dp),
    ) {
        Box(Modifier.layout { m, cs ->
            val hh = room.value.dp.roundToPx()
            val pl = m.measure(cs.copy(minHeight = 0, maxHeight = (lines * 28).dp.roundToPx().coerceAtLeast(0)))
            layout(pl.width, hh) { pl.place(0, 0) }
        }.graphicsLayer { alpha = span(a(), 0.45f, 1f) * shown.value }) {
            if (lyrics) CompositionLocalProvider(LocalColors provides white) {
                Box(Modifier.padding(horizontal = 22.dp).fillMaxWidth().height((lines * 28).dp)) { InlineLyrics(t, versions!!, lines) }
            }
        }
        if (capsule) {
            // the pill's own row (art · title · ringed play) is the player's shared cover, title and play nodes, which
            // the layout moves onto the pill: here only its room
            Spacer(Modifier.height(64.dp + 24.dp))
        } else Column(Modifier.graphicsLayer { alpha = span(a(), 0.45f, 1f) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Txt(t.title, Type.title.copy(fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, letterSpacing = (-0.02).em), color = Color.White, lines = 2)
                    Txt(t.artist, Type.body, Modifier.padding(top = 2.dp), color = Color.White.copy(alpha = 0.75f))
                }
                Box(
                    Modifier.padding(start = 16.dp).size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                        .press(if (playing) "Pause" else "Play", scale = 0.9f) { Playback.toggle() },
                    contentAlignment = Alignment.Center,
                ) { com.wetune.ui.Icon(if (playing) Icons.Pause else Icons.Play, Color.White, 22.dp) }
            }
            // the hairline: how far into the song, nothing to grab
            Box(Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 22.dp).fillMaxWidth().height(2.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f)).drawBehind { drawRect(Color.White, size = size.copy(width = size.width * progress())) })
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * THE title + artist of the player, one node in every state. Laid out at Now Playing's header size; [small] (0..1, read
 * in layout) shrinks each line on its own to the mini player's look (row title over sub artist), so the lines keep
 * their real width (ellipsis in the right place) at every size. Colours blend from [from] (over the app's frost) to
 * [to] (over the card) by [mix], in the draw phase. [onOpenH] gets the header-size height (title + artist);
 * [shift]: the mini player's skip swipe, sliding the lines (the caller clips them to the title's box).
 */
@Composable
private fun MorphTitle(
    t: Track, from: com.wetune.ui.WeColors, to: com.wetune.ui.WeColors, mix: () -> Float, small: () -> Float,
    onOpenH: (Int) -> Unit, shift: () -> Float, onArtist: (() -> Unit)?, modifier: Modifier = Modifier,
) {
    val ts = pt(Type.title, 24, 600)
    val ars = pt(Type.body, 15, 400)
    val tk = Type.row.fontSize.value / ts.fontSize.value
    val ak = Type.sub.fontSize.value / ars.fontSize.value
    Layout(
        modifier = modifier,
        content = {
            androidx.compose.foundation.text.BasicText(t.title, style = ts, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, maxLines = 1, softWrap = false,
                color = { androidx.compose.ui.graphics.lerp(from.on, to.on, mix()) })
            androidx.compose.foundation.text.BasicText(
                t.artist, if (onArtist != null) Modifier.press("Go to ${t.artist}", scale = 0.98f, onClick = onArtist) else Modifier,
                style = ars, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, maxLines = 1, softWrap = false,
                color = { androidx.compose.ui.graphics.lerp(from.onSecondary, to.onSecondary, mix()) },
            )
        },
    ) { ms, cs ->
        val s = small()
        val st = lerp(1f, tk, s); val sa = lerp(1f, ak, s)
        val w = cs.maxWidth
        val a = ms[0].measure(Constraints(maxWidth = (w / st).roundToInt().coerceAtLeast(1)))
        val b = ms[1].measure(Constraints(maxWidth = (w / sa).roundToInt().coerceAtLeast(1)))
        onOpenH(a.height + b.height)
        val ah = (a.height * st).roundToInt()
        layout(w, ah + (b.height * sa).roundToInt()) {
            a.placeWithLayer(0, 0) { transformOrigin = TransformOrigin(0f, 0f); scaleX = st; scaleY = st; translationX = shift() }
            b.placeWithLayer(0, ah) { transformOrigin = TransformOrigin(0f, 0f); scaleX = sa; scaleY = sa; translationX = shift() }
        }
    }
}

/**
 * THE play / pause of the player, one node in every state, drawn in the draw phase so it morphs without recomposing:
 * [small] (0..1) toward the mini player's look, which the immersive capsule shares (a small white glyph on a dimmed
 * disc over the round cover, ringed with the song's progress); otherwise the transport's (Clean: a big glyph with its
 * straight-down shadow; Expressive: the glyph on a primary pill). [mix]: the app's colours (0) → the card's (1), for
 * the ring. The layout sizes and places it.
 */
@Composable
private fun PlayMorph(t: Track, app: com.wetune.ui.WeColors, small: () -> Float, mix: () -> Float) {
    val c = WT.c
    val mat = material
    val playing = Playback.playing
    val ic = if (playing) (if (mat) MaterialIcons.Pause else Icons.Pause) else (if (mat) MaterialIcons.Play else Icons.Play)
    val painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(ic.vector)
    val pos = com.wetune.rememberPosition()
    val openInk = if (mat) c.onPrimary else c.on
    val openG = if (mat) 34.dp else 42.dp
    Box(
        Modifier.press(if (playing) "Pause ${t.title}" else "Play ${t.title}", scale = 0.9f) { Playback.toggle() },
        contentAlignment = Alignment.Center,
    ) {
        // Clean's lifted glyph: its shadow belongs to the transport look only
        if (!mat) Box(Modifier.graphicsLayer {
            val km = small()
            alpha = 1f - km
            val s = lerp(1f, 15f / 42f, km); scaleX = s; scaleY = s
        }) { com.wetune.ui.GlyphDepth(ic, openG) }
        Spacer(Modifier.matchParentSize().drawBehind {
            val km = small()
            val ko = 1f - km
            // Expressive's pill
            if (mat && ko > 0f) {
                val r = lerp(size.minDimension / 2f, 24.dp.toPx(), ko)
                drawDepth(Rect(androidx.compose.ui.geometry.Offset.Zero, size), r, depthAlpha(c.dark) * ko, blur = 12.dp, drop = 4.dp)
                drawRoundRect(c.primary.copy(alpha = c.primary.alpha * ko), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            }
            // the mini player's dimmed disc over its cover
            if (km > 0f) drawCircle(Color.Black.copy(alpha = 0.32f * km), 20.dp.toPx())
            // the progress ring: round the cover on the mini player, round the button on the capsule
            val ring = km
            if (ring > 0.01f) {
                val sw = lerp(3.dp.toPx(), 2.6.dp.toPx(), km)
                val r = size.minDimension / 2f - lerp(sw / 2f, sw, km)
                val ink = androidx.compose.ui.graphics.lerp(app.on, c.on, mix())
                val at = androidx.compose.ui.geometry.Offset(center.x - r, center.y - r); val box = androidx.compose.ui.geometry.Size(r * 2f, r * 2f)
                drawCircle(ink.copy(alpha = 0.18f * ring), r, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                val d = Playback.duration()
                if (d > 0) drawArc(ink.copy(alpha = ring), -90f, 360f * (pos.value.toFloat() / d).coerceIn(0f, 1f), false, at, box,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            // the glyph
            val g = lerp(openG.toPx(), 15.dp.toPx(), km)
            val ink = androidx.compose.ui.graphics.lerp(openInk, Color.White, km)
            translate(center.x - g / 2f, center.y - g / 2f) {
                with(painter) { draw(androidx.compose.ui.geometry.Size(g, g), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(ink)) }
            }
        })
    }
}

private fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

/** Immersive Canvas's morph, in and out. */
private const val IMMERSIVE_MS = 520
/** Smoothstep: eases in and out over 0..1. */
private fun smooth(t: Float) = t * t * (3f - 2f * t)
/** [v] (0..1) as it passes [from]..[to], 0..1: lets what leaves and what arrives take turns instead of overlapping. */
private fun span(v: Float, from: Float, to: Float) = ((v - from) / (to - from)).coerceIn(0f, 1f)

/** "edge" backdrop: the cover's lower part dissolves into the background colour below it ([amount] 0 = no fade). */
internal fun Modifier.edgeFade(amount: () -> Float): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val k = amount()
        val fade = Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Black.copy(alpha = 1f - k))
        onDrawWithContent { drawContent(); if (k > 0f) drawRect(fade, blendMode = BlendMode.DstIn) }
    }

// The open / close morph's curves over progress p (1 open … 0 at the mini player):
/** The backdrop and big cover: gone by halfway, so the app never shows through a half-faded Now Playing for long. */
private fun bgAlpha(p: Float) = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
/** The card's controls (seek, transport, panel, like / more…): gone early, before the card shrinks past them. */
private fun ctlAlpha(p: Float) = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)

/** A cover's four corner radii in px (top left, top right, bottom right, bottom left), for shapes that animate. */
@androidx.compose.runtime.Immutable
data class Corners(val tl: Float, val tr: Float, val br: Float, val bl: Float) {
    fun lerp(to: Corners, t: Float) = Corners(lerp(tl, to.tl, t), lerp(tr, to.tr, t), lerp(br, to.br, t), lerp(bl, to.bl, t))
    fun shape() = RoundedCornerShape(tl, tr, br, bl)
    companion object {
        val Zero = Corners(0f, 0f, 0f, 0f)
        fun all(r: Float) = Corners(r, r, r, r)
    }
}


/**
 * Cover / Canvas, inside the cover's bottom edge: the app's pill language made compact and frosted over the art. A
 * frosted track (blur + a little dark tint, no rim), 64×30dp, holds two 30dp icon segments (still picture, Canvas); the
 * chosen one sits under a solid white 28×24dp pill that springs between them, and the icons turn black exactly where
 * that pill covers them (a clipped black copy, so the colour change follows the pill instead of snapping). Touch reaches
 * past the drawn track: 44dp tall, the two hit boxes split at the centre line (74dp wide in all).
 * Screen readers get "Cover" / "Canvas" tabs with their selected state.
 */
@Composable
private fun CoverVideoSwitch() {
    val on = NowPlaying.videoOn
    val x = animateFloatAsState(if (on) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 500f), label = "switch")
    val haze = LocalHaze.current
    val frost = remember {
        HazeStyle(backgroundColor = Color.Black, tint = HazeTint(Color.Black.copy(alpha = 0.32f)), blurRadius = 18.dp, noiseFactor = 0.06f, fallbackTint = HazeTint(Color.Black.copy(alpha = 0.55f)))
    }
    val track = RoundedCornerShape(10.dp); val pill = RoundedCornerShape(8.dp)
    val segs = listOf(Triple(false, "Cover", com.wetune.ui.Icons.Picture), Triple(true, "Canvas", com.wetune.ui.Icons.Video))
    @Composable fun icon(ic: com.wetune.ui.Ic, ink: Color) =
        Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) { com.wetune.ui.Icon(ic, ink, 16.dp) }
    Layout(
        content = {
            // 0 track, 1 pill, 2–3 hit boxes, 4–5 white icons, 6–7 black icons (shown only under the pill)
            Box(Modifier.clip(track).then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.hazeEffect(haze, frost) { canDrawArea = { true } } else Modifier.background(Color.Black.copy(alpha = 0.55f))))
            Box(Modifier.background(Color.White, pill))
            segs.forEach { (v, label, _) ->
                Box(Modifier.selectable(on == v, interactionSource = null, indication = null, role = androidx.compose.ui.semantics.Role.Tab) { NowPlaying.videoOn = v }.semantics { contentDescription = label })
            }
            segs.forEach { (_, _, ic) -> icon(ic, Color.White.copy(alpha = 0.92f)) }
            segs.forEach { (_, _, ic) -> icon(ic, Color.Black) }
        },
        modifier = Modifier.selectableGroup(),
    ) { ms, _ ->
        val hh = 44.dp.roundToPx(); val tw = 64.dp.roundToPx(); val th = 30.dp.roundToPx()
        val seg = 30.dp.roundToPx(); val pad = (tw - seg * 2) / 2
        val pw = 28.dp.roundToPx(); val ph = 24.dp.roundToPx()
        val w = seg + 44.dp.roundToPx() // from 22dp left of the first icon's centre to 22dp right of the second's
        val tx = (w - tw) / 2; val ty = (hh - th) / 2
        val sx = tx + pad // first segment's left edge
        val mid = w / 2
        val bg = ms[0].measure(Constraints.fixed(tw, th))
        val ind = ms[1].measure(Constraints.fixed(pw, ph))
        val hit0 = ms[2].measure(Constraints.fixed(mid, hh)); val hit1 = ms[3].measure(Constraints.fixed(w - mid, hh))
        val icons = (4..7).map { ms[it].measure(Constraints.fixed(seg, th)) }
        val ix = (seg - pw) / 2; val iy = (th - ph) / 2 // the pill inside a segment
        layout(w, hh) {
            bg.place(tx, ty)
            ind.placeWithLayer(sx + ix, ty + iy) { translationX = x.value * seg }
            icons[0].place(sx, ty); icons[1].place(sx + seg, ty)
            for (i in 0..1) icons[2 + i].placeWithLayer(sx + i * seg, ty) {
                val l = ix + (x.value - i) * seg
                clip = true
                shape = androidx.compose.foundation.shape.GenericShape { _, _ -> addRect(Rect(l, iy.toFloat(), l + pw, (iy + ph).toFloat())) }
            }
            hit0.place(0, 0); hit1.place(mid, 0)
        }
    }
}
