package com.wetune.ui.player

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
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

/** Where the cover sits in the layer at rest (layer coordinates), for the open/close morph from the mini player. */
private class Geo {
    var cover by mutableStateOf(Rect.Zero); var coverRadius by mutableStateOf(Corners.Zero); var title by mutableStateOf(Rect.Zero)
    var card by mutableStateOf(androidx.compose.ui.unit.IntOffset.Zero) // the card's top-left, to line its blurred copy up with the screen
    var screen by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
}

/**
 * Now Playing (spec §6): the cover over a chosen backdrop, and one frosted controls sheet whose states
 * (normal → Lyrics / Audio → full screen; Queue straight to full screen) are one layout driven by two animated
 * fractions, so every shared element — cover, title, artist, like, seek, transport, the Lyrics / Queue buttons —
 * glides between positions. Not composed at all while closed.
 */
@Composable
fun NowPlayingLayer() {
    val p = NowPlaying.progress
    val visible by remember { derivedStateOf { p.value > 0f || p.isRunning } }
    if (!visible) return
    val t = Playback.current
    if (t == null) { LaunchedEffect(Unit) { NowPlaying.close() }; return }
    HideStatusBar(Settings.npHideStatus && NowPlaying.isOpen)
    val c = WT.c
    val geo = remember { Geo() }
    // a second blur source for the app's frost, so sheets opened over Now Playing blur it too
    // inside Now Playing, secondary text is see-through foreground rather than fixed grey: grey vanishes on mid-tone backdrops
    val base = WT.c
    val npColors = remember(base) { base.copy(onSecondary = base.on.copy(alpha = 0.68f), onTertiary = base.on.copy(alpha = 0.46f)) }
    CompositionLocalProvider(LocalColors provides npColors) {
    BoxWithConstraints(Modifier.fillMaxSize().hazeSource(LocalHaze.current, zIndex = 1f)) {
        val h = constraints.maxHeight.toFloat()
        val corner = sheetRadiusPx(0.dp)
        // the app beneath dims a little while Now Playing covers it (a still shade: no colour moves across the app)
        Box(Modifier.fillMaxSize().drawBehind { if (p.value > 0f) drawRect(Color.Black.copy(alpha = 0.35f * p.value)) })
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val v = p.value
                    // follows the finger (and the open / close) at full height, as a card: its top corners round off as it
                    // leaves, and it dissolves on the way (half faded halfway, gone before the bottom), so no band of the
                    // backdrop's colour sweeps across the app or the mini player while the cover and title fly to it
                    translationY = (1f - v) * h
                    alpha = ((v - 0.12f) / 0.6f).coerceIn(0f, 1f)
                    shape = RoundedCornerShape(topStart = corner * (1f - v), topEnd = corner * (1f - v))
                    clip = true // the glow and shadows must not show past its edges
                }
                .background(c.bg)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }, // nothing reaches the app beneath
        ) { Player(t, geo) }
        // the cover flying between the mini player and its place here; the real one shows once open
        AsyncImage(
            model = t.art, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.layout { m, cs ->
                val r = geo.cover
                val pl = m.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
                layout(cs.maxWidth, cs.maxHeight) { pl.place(0, 0) }
            }.graphicsLayer {
                val v = p.value
                val r = geo.cover; val m = NowPlaying.miniCover
                alpha = if (v >= 1f || v <= 0f || r.width == 0f || m.width == 0f) 0f else 1f
                if (r.width > 0f) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    val w = lerp(m.width, r.width, v)
                    scaleX = w / r.width; scaleY = w / r.width
                    translationX = lerp(m.left, r.left, v)
                    translationY = lerp(m.top, r.top, v)
                    shape = Corners.all(r.width / 2f).lerp(geo.coverRadius, v).shape()
                    clip = true
                }
            },
        )
        // title + artist flying between the header and the mini player, alongside the cover: both typographies travel
        // the same path and cross-fade, each scaled to the other's size, so it reads as one block changing size
        if (geo.title.width > 0f) {
            val npAlpha = { v: Float -> ((v - 0.25f) / 0.5f).coerceIn(0f, 1f) }
            Box(Modifier.flight(geo, p, mini = false) { v -> npAlpha(v) to lerp(NowPlaying.miniText.height / geo.title.height, 1f, v) }) { TitleBlock(t) }
            Box(Modifier.flight(geo, p, mini = true) { v -> (1f - npAlpha(v)) to lerp(1f, geo.title.height / NowPlaying.miniText.height.coerceAtLeast(1f), v) }) {
                CompositionLocalProvider(LocalColors provides base) { com.wetune.ui.MiniTitle(t) } // lands as the mini player's exact look
            }
        }
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
    if (backdrop == "edge") return Rect(0f, 0f, w.toFloat(), w / ratio) to Corners.Zero
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
private fun Player(t: Track, geo: Geo) {
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
    // fixed insets: hiding the status bar must not move anything
    val status = WindowInsets.statusBarsIgnoringVisibility
    val nav = WindowInsets.navigationBars
    val haze = remember { HazeState() }
    val dragPx = with(density) { 600.dp.toPx() }
    val progress = NowPlaying.progress
    val edge = backdrop == "edge"
    val artRatio = coverRatio(t.art)
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
    val inlineVersions = if (inlineLines > 0) inlineLyricsFor(t) else null
    val hasInline = inlineLines > 0 && inlineVersions?.firstOrNull()?.synced == true
    val loadingInline = inlineLines > 0 && inlineVersions == null
    // the space opens while lyrics load ("Loading lyrics…", so a new song never shrinks then grows it) and stays for timed
    // lyrics; it eases in and out over ~half a second with the standard curve
    val inlineH = animateFloatAsState(if (loadingInline || hasInline) inlineLines * 28f else 0f, tween(520, easing = Motion.Standard), label = "inline") // dp

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
                Box(
                    // recorded each frame into [under] (then drawn as usual), for the card's live blur above it
                    Modifier.fillMaxSize().drawWithContent { under.record { this@drawWithContent.drawContent() }; drawLayer(under) }
                        .hazeSource(haze).sheetDrag(dragPx)
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
                        }
                    },
                ) {
                    Backdrop(t)
                    // its own layer under the controls: sized and placed only by the cover settings, free to slide under the
                    // card. Reports the screen's size so a cover wider than the screen (>100 %) is never re-centred.
                    Box(Modifier.layout { m, cs ->
                        val r = geo.cover
                        val pl = m.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
                        layout(cs.maxWidth, cs.maxHeight) { pl.place(0, 0) }
                    }.graphicsLayer {
                        val r = geo.cover
                        // in full screen the lifted copy (11) shows instead; a sideways swipe fades it as it slides
                        alpha = if (progress.value < 1f || f.value > 0f) 0f else (1f - kotlin.math.abs(coverSwipe.value) / (swipeReach * 2.5f)).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = r.left + coverSwipe.value; translationY = r.top
                        // plain and squircle covers are clipped and shadowed here; the other styles draw (and shadow) their own parts
                        val styled = coverStyle
                        if (coverClips(styled)) { shape = geo.coverRadius.shapeFor(styled); clip = true } else clip = false
                        val lift = if (Settings.coverShadow && !edge && coverClips(styled)) Settings.coverShadowStrength / 100f * (1f - e.value) * (1f - f.value) else 0f
                        shadowElevation = lift * 36.dp.toPx()
                        spotShadowColor = Color.Black; ambientShadowColor = Color.Black
                    }.then(if (edge) Modifier.edgeFade { (1f - e.value) * (1f - f.value) } else Modifier)) {
                        StyledCover(t, coverStyle, geo.coverRadius) {
                            // the usual artwork, with the full-size one laid over it once it arrives (no blank moment)
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(t.art, "Album artwork: ${t.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                AsyncImage(com.wetune.youtube.hiRes(t.art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                if (Settings.npVideoCover) VideoCover(t) // the music video in the cover's place, over the artwork
                            }
                        }
                    }
                }
                // 1: the top strip: drag here to close (no grabber drawn); holds the cover / moving-cover switch
                Box(Modifier.fillMaxWidth().height(56.dp).graphicsLayer { alpha = 1f - f.value }.sheetDrag(dragPx)) {
                    // Cover / Video: shown when the music video cover is on and this song has one
                    if (Settings.npVideoCover && NowPlaying.videoFor == t.id) CoverVideoSwitch(Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                }
                // 2: the card: frosted over the backdrop and cover, corners concentric with the screen's; in full screen it
                // spreads edge to edge, square. Turned off, it shows only behind an open panel.
                Box(
                    Modifier.graphicsLayer {
                        alpha = if (cardOn) 1f else maxOf(e.value, f.value)
                        shape = RoundedCornerShape(lerp(sheetRadius, 0f, f.value)); clip = true
                        // optional soft shadow, gone once it spreads to full screen
                        shadowElevation = if (cardOn && Settings.npCardShadow) Settings.npCardShadowStrength.coerceIn(10, 100) / 100f * 28.dp.toPx() * (1f - f.value) else 0f
                        spotShadowColor = Color.Black; ambientShadowColor = Color.Black
                    },
                ) {
                    // the blur: what's really under the card, live. The backdrop + cover layer (0) is recorded each frame
                    // (see [under]); here that same recording is drawn blurred and shifted to line up with the screen, so
                    // the card follows anything moving beneath it (an animated cover, Colour flow, the cover sliding).
                    // Android 12+; older phones get the tint alone.
                    if (blur > 0 && android.os.Build.VERSION.SDK_INT >= 31) Box(Modifier.matchParentSize().drawBehind {
                        blurred.renderEffect = androidx.compose.ui.graphics.BlurEffect(blur.dp.toPx(), blur.dp.toPx(), androidx.compose.ui.graphics.TileMode.Clamp)
                        blurred.record(androidx.compose.ui.unit.IntSize(geo.screen.width.coerceAtLeast(1), geo.screen.height.coerceAtLeast(1))) { drawLayer(under) }
                        translate(-geo.card.x.toFloat(), -geo.card.y.toFloat()) { drawLayer(blurred) }
                    })
                    val noise = remember { androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(com.wetune.ui.grain, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated)) }
                    Box(Modifier.matchParentSize().drawBehind { drawRect(tint); if (frosted) drawRect(noise, alpha = if (c.dark) 0.5f else 0.3f) }) // frosted: the navbar's grain too
                }
                // 3: header — thumbnail (full screen), title / artist, like
                Row(Modifier.sheetDrag(dragPx).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.layout { m, cs ->
                        val w = (with(density) { 64.dp.toPx() } * f.value).roundToInt()
                        val pl = m.measure(cs.copy(minWidth = 0))
                        layout(w, pl.height) { pl.place(0, 0) }
                    }) {} // room for the cover, which flies into this spot above the card (11)
                    TitleBlock(
                        t, Modifier.weight(1f).onGloballyPositioned {
                            // where the title flight lands: measured at rest, normal state only
                            if (progress.value >= 1f && f.value == 0f && e.value == 0f) geo.title = it.boundsInRoot()
                        }.graphicsLayer {
                            alpha = if (progress.value < 1f && geo.title.width > 0f) 0f else 1f // the flying copy stands in for it
                            val s = lerp(1f, 0.78f, f.value); scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                        onArtist = if (t.source == "yt") ::openArtist else null,
                    )
                    LikeButton(t)
                    if (material) Spacer(Modifier.width(10.dp)) // Material's 48dp circles fill their touch targets; Apple's 32dp ones leave a gap
                    RoundBtn(Icons.More, MaterialIcons.More, "More for ${t.title}") { TrackMenu.show(t) }
                }
                // 4: the open panel
                Box(Modifier.graphicsLayer { alpha = maxOf(e.value, f.value) }.padding(horizontal = 16.dp)) {
                    AnimatedContent(
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
                SeekBar(badge = panel == NowPlaying.Panel.None)
                // 6: transport (shuffle / repeat fade out in full screen)
                Transport(sides = { b.value })
                // 7: the action row (Audio; empty slots under Lyrics and Queue), folding away in full screen
                Box(Modifier.graphicsLayer { alpha = (1f - b.value * 2f).coerceIn(0f, 1f) }) { ActionRow() }
                // 8, 9: Lyrics and Queue, flying between the action row and the sides of the transport
                PanelButton(lyrics = true) { b.value }
                PanelButton(lyrics = false) { b.value }
                // 10: inline lyrics in the normal sheet; tapping them opens full-screen lyrics
                Box(Modifier.graphicsLayer { alpha = (1f - e.value) * (1f - f.value) * (inlineH.value / (inlineLines * 28f).coerceAtLeast(1f)) }) {
                    if (inlineVersions != null && hasInline) InlineLyrics(t, inlineVersions, inlineLines)
                    else if (loadingInline) InlineLoading()
                }
                // 11: the cover lifted above the card while it flies into (and sits in) the full-screen header, left of the title
                Box(Modifier.graphicsLayer { alpha = if (f.value > 0f && progress.value >= 1f) 1f else 0f; shape = geo.coverRadius.shape(); clip = true }) {
                    AsyncImage(t.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    AsyncImage(com.wetune.youtube.hiRes(t.art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            },
        ) { ms, cs ->
            val w = cs.maxWidth; val h = cs.maxHeight
            val side = inset.roundToPx(); val inner = 20.dp.roundToPx()
            val sheetW = w - side * 2; val rowW = sheetW - inner * 2
            val padTop = (if (full) 16.dp else 24.dp).roundToPx(); val padBottom = 24.dp.roundToPx() // room under the Lyrics · Audio · Queue row
            // the card sits [side] above the navigation bar, as far in as from the sides; everything inside follows it
            val top = status.getTop(this); val bottomGap = side + nav.getBottom(this)
            val ev = e.value; val fv = f.value; val bv = b.value
            val header = ms[3].measure(Constraints(maxWidth = sheetW))
            val row = Constraints(maxWidth = rowW, minWidth = rowW)
            val seek = ms[5].measure(row); val transport = ms[6].measure(row); val action = ms[7].measure(row)
            // vertical rhythm under the sheet's content: seek · 20 · transport · 22 · action row (that last part folds away)
            val gapT = 20.dp.roundToPx(); val gapA = 22.dp.roundToPx()
            val actionPart = ((gapA + action.height) * (1f - bv)).roundToInt()
            val bottomH = seek.height + gapT + transport.height + actionPart
            val bottomH0 = seek.height + gapT + transport.height + gapA + action.height
            // header · 24 · [inline lyrics · 20] · seek…; the lyrics' height animates in once the song turns out to have them
            val gapH = 24.dp.roundToPx()
            val inlinePx = inlineH.value.dp.roundToPx()
            val shown = if (inlineLines > 0) inlineH.value / (inlineLines * 28f) else 0f // 0..1 as the lyrics slide in
            val inlinePart = inlinePx + (20.dp.toPx() * shown).roundToInt()
            val natural = padTop + header.height + gapH + inlinePart + bottomH0 + padBottom
            val inline = ms[10].measure(Constraints.fixed(rowW, inlinePx.coerceAtLeast(0)))
            val hExpanded = h - bottomGap - 353.dp.roundToPx()
            val hFull = h - bottomGap - top - 10.dp.roundToPx()
            val sheetH = lerp(lerp(natural.toFloat(), hExpanded.toFloat().coerceAtLeast(natural.toFloat()), ev), hFull.toFloat(), fv).roundToInt()
            val sheetTop = h - bottomGap - sheetH
            val seekY = sheetTop + sheetH - padBottom - bottomH
            val transportY = seekY + seek.height + gapT
            val actionY = transportY + transport.height + gapA
            val midTop = sheetTop + padTop + header.height + 14.dp.roundToPx()
            val midBottom = seekY - 12.dp.roundToPx()
            val middle = ms[4].measure(Constraints.fixed(sheetW, (midBottom - midTop).coerceAtLeast(0)))
            val bar = ms[1].measure(Constraints(maxWidth = w))
            // the card: exactly the area its content uses (so the edge spacing moves the controls too); full screen: every edge
            val cardL = lerp(side.toFloat(), 0f, fv).roundToInt(); val cardT = lerp(sheetTop.toFloat(), 0f, fv).roundToInt()
            val cardR = lerp((w - side).toFloat(), w.toFloat(), fv).roundToInt(); val cardB = lerp((h - bottomGap).toFloat(), h.toFloat(), fv).roundToInt()
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
            val sideY = transportY + (transport.height - sideH) / 2
            val btnW = lerp(slotW.toFloat(), sideW.toFloat(), bv).roundToInt()
            val btnH = lerp(slotH.toFloat(), sideH.toFloat(), bv).roundToInt()
            val btnY = lerp(actionY.toFloat(), sideY.toFloat(), bv).roundToInt()
            val lyricsX = lerp(slot0X.toFloat(), 0f, bv).roundToInt()
            val queueX = lerp(slot2X.toFloat(), (rowW - sideW).toFloat(), bv).roundToInt()
            val lyricsBtn = ms[8].measure(Constraints.fixed(btnW, btnH))
            val queueBtn = ms[9].measure(Constraints.fixed(btnW, btnH))
            // cover: the user's placement, in every state
            // cover: the user's normal placement → top edge when the sheet grows → thumbnail in full screen
            // the cover assumes the inline-lyrics space the setting asks for, so lyrics loading (or not) never moves it
            val reserved = if (inlineLines > 0) (inlineLines * 28).dp.roundToPx() + 20.dp.roundToPx() else 0
            val naturalTop = h - bottomGap - (natural - inlinePart + reserved)
            val (normal, normalRadius) = normalCover(w, h, naturalTop, artRatio)
            // where "default" puts it, in the Position slider's terms (% of the way from the top edge to the bottom edge)
            (if (normal.height >= h) 50 else ((naturalTop - normal.height) / 2f / (h - normal.height) * 100f).roundToInt().coerceIn(0, 100))
                .let { if (it != NowPlaying.coverAutoPos) NowPlaying.coverAutoPos = it }
            // the cover stays put while a panel opens: the card grows over it (the header thumbnail fades in for full screen)
            // full screen (Lyrics / Queue): the cover shrinks into the header, left of the title, following the header as
            // the card rises. Audio leaves it where it is.
            val thumbSize = 52.dp.toPx()
            val thumbTop = sheetTop + padTop + (header.height - thumbSize) / 2f
            val thumb = Rect(side + 20.dp.toPx(), thumbTop, side + 20.dp.toPx() + thumbSize, thumbTop + thumbSize)
            geo.cover = lerpRect(normal, thumb, fv)
            geo.coverRadius = normalRadius.lerp(Corners.all(8.dp.toPx()), fv)
            val lifted = ms[11].measure(Constraints.fixed(geo.cover.width.roundToInt().coerceAtLeast(1), geo.cover.height.roundToInt().coerceAtLeast(1)))
            val back = ms[0].measure(Constraints.fixed(w, h)) // after geo, so the cover lays out at this frame's rect

            layout(w, h) {
                back.place(0, 0)
                bar.place(0, top)
                surface.place(cardL, cardT)
                lifted.place(geo.cover.left.roundToInt(), geo.cover.top.roundToInt())
                header.place(side, sheetTop + padTop)
                inline.place(side + inner, sheetTop + padTop + header.height + gapH)
                middle.place(side, midTop)
                val x0 = side + inner
                seek.place(x0, seekY)
                transport.place(x0, transportY)
                action.place(x0, actionY)
                lyricsBtn.place(x0 + lyricsX, btnY)
                queueBtn.place(x0 + queueX, btnY)
            }
        }
    }
}

private fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

/** "edge" backdrop: the cover's lower part dissolves into the background colour below it ([amount] 0 = no fade). */
internal fun Modifier.edgeFade(amount: () -> Float): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val k = amount()
        val fade = Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Black.copy(alpha = 1f - k))
        onDrawWithContent { drawContent(); if (k > 0f) drawRect(fade, blendMode = BlendMode.DstIn) }
    }

/**
 * One copy of the title block on its flight between the mini player (v = 0) and Now Playing's header (v = 1): measured
 * at its own end's width ([mini] = the mini player's copy), moved along the shared path, [look] gives alpha and scale.
 * Hidden at rest and until both ends have been measured.
 */
private fun Modifier.flight(geo: Geo, p: androidx.compose.animation.core.Animatable<Float, *>, mini: Boolean, look: (Float) -> Pair<Float, Float>): Modifier =
    layout { m, cs ->
        val w = (if (mini) NowPlaying.miniText.width else geo.title.width).roundToInt().coerceAtLeast(1)
        val pl = m.measure(Constraints(minWidth = w, maxWidth = w))
        layout(cs.maxWidth, cs.maxHeight) { pl.place(0, 0) }
    }.graphicsLayer {
        val v = p.value; val r = geo.title; val m = NowPlaying.miniText
        if (v <= 0f || v >= 1f || m.width == 0f || r.width == 0f) { alpha = 0f; return@graphicsLayer }
        val (a, s) = look(v)
        alpha = a
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = s; scaleY = s
        translationX = lerp(m.left, r.left, v); translationY = lerp(m.top, r.top, v)
    }

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
 * Cover or video at the top of Now Playing: a capsule of two equal halves (a picture icon, a play icon: one matching
 * pair), the chosen half under a white capsule that slides. Same shape language as the app's segmented controls.
 */
@Composable
private fun CoverVideoSwitch(modifier: Modifier = Modifier) {
    val on = NowPlaying.videoOn
    val x by animateFloatAsState(if (on) 1f else 0f, Motion.small(), label = "switch")
    val half = 32.dp; val inset = 2.5.dp
    Box(modifier.size(half * 2 + inset * 2, 27.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)).padding(inset)) {
        // the marker: exactly one half wide, sliding between them
        Box(Modifier.size(half, 22.dp).graphicsLayer { translationX = x * half.toPx() }.background(Color.White, CircleShape))
        Row(Modifier.fillMaxSize()) {
            listOf(Triple(false, "cover", com.wetune.ui.Icons.Picture), Triple(true, "moving cover", com.wetune.ui.Icons.Video)).forEach { (v, label, ic) ->
                Box(
                    Modifier.size(half, 22.dp).press("Show the $label" + if (on == v) ", showing" else "", scale = 0.94f) { NowPlaying.videoOn = v },
                    contentAlignment = Alignment.Center,
                ) { com.wetune.ui.Icon(ic, if (on == v) Color.Black else Color.White.copy(alpha = 0.9f), 14.dp) }
            }
        }
    }
}
