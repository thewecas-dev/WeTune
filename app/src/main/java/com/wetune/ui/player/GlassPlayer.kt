@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.wetune.ui

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wetune.AudioInfo
import com.wetune.Downloads
import com.wetune.Library
import com.wetune.Lyrics
import com.wetune.Playback
import com.wetune.Settings
import com.wetune.io
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Settings → Now Playing layout: how the controls under the cover are arranged. */
val NpLayouts = listOf("classic" to "Classic", "dock" to "Glass dock", "bigtitle" to "Big title", "wave" to "Waveform", "beside" to "Play beside title")

// The player (canvas "P5"): the cover over its own extended colors, soft round buttons in the four corners
// and quiet controls in the bottom 40%.

@Composable
fun GlassPlayer(ui: PlayerUi) {
    // lyrics or queue open: the background blurs heavily and dims, so the text on top reads clearly
    val coverAlpha = 1f
    val paneOpen = ui.pane != "art"
    // the blur switches on at once (animating its radius re-blurs every frame); the fading dim and layout morph hide the step
    val paneBlur = if (paneOpen) 44.dp else 0.dp
    val paneDim by animateFloatAsState(if (paneOpen) 0.45f else 0f, tween(400), label = "paneDim")
    val statusTop = statusBarHeight()
    // where the cover may start: under the status bar or camera cutout, the grabber and "Playing from", plus a gap
    var headerH by remember { mutableStateOf(64.dp) }
    val cutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val headerTop = (maxOf(statusTop, cutTop) - 8.dp).coerceAtLeast(2.dp) // the header tucks a little into the cutout band
    val clearTop = headerTop + (if (Settings.npHeader) headerH else 0.dp) // the cover starts right under the header (or at the top when it's hidden)
    var seekDrag by remember { mutableStateOf<Float?>(null) } // while dragging the seek bar: where you'd land
    var coverMenu by remember { mutableStateOf(false) }
    var pipeline by remember { mutableStateOf(false) }
    var audioOptions by remember { mutableStateOf(false) } // tap the quality tag: versions + sources, then the pipeline
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // depth effect: tilting the phone moves the background against the tilt and the cover with it (read in graphicsLayer only)
    val depthOn = Settings.npDepth
    val tilt = rememberTilt(depthOn, Settings.depthInvert)
    val depthK = 0.25f + Settings.depthIntensity / 100f * 1.5f // intensity 0…100 → 0.25…1.75 (50 = the original 1.0)
    // where the cover ends (each style reports it), so the lyrics can sit midway between it and the title
    var coverBottom by remember { mutableStateOf(0.dp) }
    var frame by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().background(lerp(ui.pal.sea, Color.Black, 0.35f)).onGloballyPositioned { frame = it }) {
        if (maxWidth > maxHeight) return@BoxWithConstraints LandscapePlayer(ui, seekDrag) { seekDrag = it }
        val w = maxWidth
        // Title, controls and corner buttons live in the bottom 40% (never less than they need); the cover owns the rest.
        // The controls never move. With no lyric line (none for this song, or switched off) only the cover and background
        // slide down into the empty lyrics space.
        val lyricLines = rememberLyrics(ui.t)
        var hasLyric by remember { mutableStateOf(true) }
        if (lyricLines != null) hasLyric = lyricLines.isNotEmpty() && lyricLines.first().ms >= 0
        val lower by animateDpAsState(if (hasLyric && Settings.showInline) 0.dp else 56.dp, tween(450), label = "lower")
        // the controls block (title → bottom row) and, above it, a fixed space for the lyrics
        val layout = Settings.npLayout
        val blockH = maxOf(if (layout == "classic") 270.dp else 320.dp, maxHeight * 0.3f) // the other layouts have an extra row
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val controlsH = blockH + 124.dp + navBottom + 8.dp // 124 dp for the lyrics and the gaps around them, whatever the navigation bar takes
        val titleTop = maxHeight - blockH - navBottom - 8.dp // top of the controls block
        // header → cover → lyrics → title, all gaps equal; the lyrics get a fixed reserve for their style, so a line that
        // wraps doesn't move the cover
        val lyricsOn = hasLyric && Settings.showInline
        val fullReserve = if (Settings.inlineStyle == "stack") 112.dp else 48.dp
        val reserve by animateDpAsState(if (lyricsOn) fullReserve else 0.dp, tween(450), label = "reserve")
        val space = CoverSpace(clearTop, titleTop, reserve, 2f + (reserve / fullReserve).coerceIn(0f, 1f))
        // with lyrics showing the cover has less room, so Bleeding edge shows it whole (no side crop); without, the usual slight trim
        val sideScale by animateFloatAsState(if (hasLyric && Settings.showInline) 1f else SideScale, tween(450), label = "crop")
        val split = maxHeight - controlsH + lower // where the cover area ends (the controls block itself stays fixed)

        val bg = Settings.npBackground
        val edge = Settings.npCover == "edge"
        val ambientBlur = if (bg in CoverFills) 40.dp else 0.dp // a cover-made background behind a framed cover, softened
        Box(Modifier.fillMaxSize()) {
        // A new song crossfades in; dragging sideways moves only the cover (not the background) to show the skip coming.
        Crossfade(
            ui.t,
            Modifier.graphicsLayer {
                if (depthOn) {
                    // zoomed a little so its edges never show while it shifts
                    scaleX = 1.08f; scaleY = 1.08f
                    translationX = -tilt.value.x * 14.dp.toPx() * depthK; translationY = tilt.value.y * 14.dp.toPx() * depthK
                }
            }.blur(if (edge) paneBlur else maxOf(paneBlur, ambientBlur), BlurredEdgeTreatment.Rectangle),
            animationSpec = tween(650), label = "song",
        ) { t ->
            // Background and cover style are separate choices:
            // - full-width cover: the cover is part of the background; non-cover backgrounds go underneath it
            // - framed covers: a stretch / reflection / blur background is the full-width backdrop softened into ambient
            //   color (so the cover isn't shown twice); the framed cover is drawn on top further down
            Box(Modifier.fillMaxSize()) {
                if (bg !in CoverFills) StyledBackdrop(bg, ui.pal, t, split)
                if (edge) CoverBackdrop(ui.pal, w, split, clearTop, coverAlpha, t, sideScale, space, fill = if (bg in CoverFills) bg else "none", onCoverBottom = { if (t.id == ui.t.id) coverBottom = it }) { swipe.value * 0.5f }
                else if (bg in CoverFills) CoverBackdrop(ui.pal, w, split, clearTop, coverAlpha, t, sideScale, space, fill = bg)
            }
        }
        // depth effect: the edges sink into shadow so the background reads as far behind the glass
        if (depthOn) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = (0.55f * depthK).coerceAtMost(0.8f)), center = center, radius = size.maxDimension * 0.62f))
        }
        if (paneDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = paneDim)))
        }

        // Lyrics or queue open: immersive. Only the panel, plus a slim bar at the bottom (title, play/pause, close) and the seek bar.
        BackHandler(paneOpen) { ui.setPane("art") }
        // The two layouts morph into each other: title, artist, play button and seek bar glide to their new places
        // while everything else fades.
        SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(
            paneOpen, Modifier.fillMaxSize(), label = "immersive",
            transitionSpec = { (fadeIn(tween(380, delayMillis = 60)) togetherWith fadeOut(tween(220))).using(SizeTransform(clip = false)) },
        ) { open ->
        CompositionLocalProvider(LocalShared provides SharedScopes(this@SharedTransitionLayout, this@AnimatedContent)) {
        if (open) ImmersivePane(ui) else Box(Modifier.fillMaxSize()) {

        // Top 60%: the corner buttons. On the cover: swipe to skip, long-press for a menu.
        Column(Modifier.fillMaxWidth().height(titleTop).padding(start = 16.dp, end = 16.dp, top = headerTop)) {
          // the header (grabber + Playing from) is measured so the cover never starts underneath it; it can be hidden in Settings
          if (Settings.npHeader) Column(Modifier.onSizeChanged { headerH = with(density) { it.height.toDp() } }) {
            // grabber: tap to close, or drag down (the whole player follows a downward drag)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(scale = 0.95f, onClick = ui.close)
                    .semantics { contentDescription = "Close player" }.padding(top = 0.dp, bottom = 8.dp),
                Alignment.Center,
            ) { Box(Modifier.size(40.dp, 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f))) }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (layout == "dock") Spacer(Modifier.width(46.dp)) // balances the ⋯ on the right, so "Playing from" stays centered
                // (no close button: swipe down or Back closes the player)
                // what the queue was started from: "PLAYING FROM PLAYLIST" over "Tamil Hits 2025"
                Column(Modifier.weight(1f).heightIn(min = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Playback.from?.let { (kind, name) ->
                        Txt("PLAYING FROM ${kind.uppercase()}", style = Type.overline.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.65f), maxLines = 1)
                        Txt(name, Modifier.basicMarquee(iterations = Int.MAX_VALUE), style = Type.caption.copy(fontSize = 13.sp), color = Color.White, weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
                    }
                }
                if (layout == "dock") PlayerMenu(ui) { pipeline = true } // Glass dock: ⋯ moves up here
            }
          }
            SleepBadge(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp))
            Box(
                Modifier.weight(1f).fillMaxWidth()
                        .pointerInput(ui.t.id) {
                            val limit = with(density) { 90.dp.toPx() }
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        swipe.value < -limit -> Playback.next()
                                        swipe.value > limit -> Playback.prev()
                                    }
                                    scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.8f)) }
                                },
                                onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                            ) { change, dx -> change.consume(); scope.launch { swipe.snapTo(swipe.value + dx) } }
                        }
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { coverMenu = true }) },
            ) {
                Box(Modifier.align(Alignment.Center)) { CoverMenu(ui, coverMenu) { coverMenu = false } }
            }
        }

        // card, disc, strips and vinyl: the framed cover, evenly spaced like Bleeding edge's (which draws it in the background)
        if (!edge) {
            val base = when (Settings.npCover) { "disc" -> w * 0.8f; "strips" -> w * 0.84f; "vinyl" -> w * 0.8f; else -> w - 48.dp }
            // one size whatever the lyrics (on, off, one line or three): sized for the most room lyrics can take; only its place changes
            val size = minOf(base, CoverSpace(clearTop, titleTop, 112.dp, 3f).room).coerceAtLeast(120.dp)
            val top = space.topFor(size)
            androidx.compose.runtime.SideEffect { coverBottom = top + size }
            Crossfade(
                ui.t,
                Modifier.offset(y = top).fillMaxWidth().graphicsLayer {
                    // the cover floats toward you
                    if (depthOn) { translationX = tilt.value.x * 7.dp.toPx() * depthK; translationY = -tilt.value.y * 7.dp.toPx() * depthK }
                },
                animationSpec = tween(500), label = "cover",
            ) { StyledCover(Settings.npCover, it, { swipe.value * 0.5f }, size) }
        }
        // Lyrics (one line or three): centered between where the cover ends and the title; they open and close inside this
        // space, so nothing else moves
        val lyricsFrom = coverBottom.coerceIn(clearTop, titleTop - 60.dp)
        Box(Modifier.offset(y = lyricsFrom).fillMaxWidth().height(titleTop - lyricsFrom).padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 10.dp), Alignment.Center) {
            LyricLine(ui, seekDrag?.let { (it * ui.duration).toLong() })
        }
        // Bottom: title · seek · times and quality · transport · volume · lyrics | sleep+output | queue · device.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) {
            // the controls: a fixed-height block pinned to the bottom, rows spaced evenly inside it
            Column(Modifier.fillMaxWidth().height(blockH)) {
                val accent = ui.pal.hue?.let { hueAccent(it, dark = true) } ?: Color.White
                val onQuality = { audioOptions = true }
                val onPipeline = { pipeline = true }
                val onSeek: (Float?) -> Unit = { seekDrag = it }
                when (layout) {
                    "dock" -> DockControls(ui, onQuality, onPipeline, onSeek)
                    "bigtitle" -> BigTitleControls(ui, accent, onQuality, onPipeline, onSeek)
                    "wave" -> WaveControls(ui, accent, onQuality, onPipeline, onSeek)
                    "beside" -> BesideControls(ui, accent, onQuality, onPipeline, onSeek)
                    else -> {
                        TitleRow(ui, onPipeline)
                        Spacer(Modifier.height(8.dp)) // the seek bar sits close under the title
                        GlassSeek(ui, onQuality = onQuality, onSeekDrag = onSeek)
                        Spacer(Modifier.weight(1f))
                        Transport(accent)
                        Spacer(Modifier.weight(1f))
                        if (Settings.showVolume) {
                            VolumeRow()
                            Spacer(Modifier.weight(1f))
                        }
                        BottomBar(ui)
                    }
                }
            }
        }
        }
        }
        }
        }
    }
    if (audioOptions) AudioOptionsSheet(ui.t, onPipeline = { audioOptions = false; pipeline = true }) { audioOptions = false }
    if (pipeline) PipelineSheet(ui.t) { pipeline = false }
}

/** Title and artist on the left; heart and ⋯ as soft round buttons on the right. */
@Composable
private fun TitleRow(ui: PlayerUi, onPipeline: (() -> Unit)? = null, menu: Boolean = true, big: Boolean = false, accent: Color = Color.White) {
    Row(verticalAlignment = if (big) Alignment.Top else Alignment.CenterVertically) {
        TitleText(ui, Modifier.weight(1f), big, accent)
        Spacer(Modifier.width(if (big) 4.dp else 12.dp))
        LikeBtn(ui)
        if (menu) {
            Spacer(Modifier.width(if (big) 0.dp else 10.dp))
            PlayerMenu(ui, onPipeline)
        }
    }
}

/** Title over artist (shared keys "title" / "artist"); [big]: Big title's 32 sp title and the artist in the accent. */
@Composable
private fun TitleText(ui: PlayerUi, modifier: Modifier = Modifier, big: Boolean = false, accent: Color = Color.White) {
    val lift = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 12f)
    Column(modifier) {
        // long titles scroll slowly; tap the title for its album, the artist for the artist
        Txt(
            ui.t.title, Modifier.shared("title", text = true).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000).pressable(scale = 0.98f) { ui.search(ui.t.album.ifBlank { ui.t.title }) },
            style = if (big) Type.title.copy(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, shadow = lift)
            else Type.title.copy(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, shadow = lift),
            maxLines = 1,
        )
        Txt(
            ui.t.artist, Modifier.padding(top = if (big) 4.dp else 0.dp).shared("artist", text = true).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000).pressable(scale = 0.98f) { ui.search(ui.t.artist.substringBefore(",").trim()) },
            style = Type.body.copy(fontSize = if (big) 16.sp else 15.sp, fontWeight = if (big) FontWeight.SemiBold else null, shadow = lift),
            color = if (big) accent else Color.White.copy(alpha = 0.7f), maxLines = 1,
        )
    }
}

@Composable
private fun LikeBtn(ui: PlayerUi) {
    val liked = Library.isLiked(ui.t)
    val view = androidx.compose.ui.platform.LocalView.current
    SoftCircle(if (liked) Ic.Heart else Ic.HeartOutline, if (liked) "Remove from Liked" else "Like", size = 44) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        io { Library.toggleLike(ui.t) }
    }
}

/**
 * Play/pause filled with [fill] (dark glyph on a light fill); circle when square, pill when wider. Shared key "play":
 * the bounds glide to the pane's button while the two fills crossfade; the glyph keeps its size. Breathes while loading.
 */
@Composable
private fun PlayBtn(fill: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp = width, iconSize: androidx.compose.ui.unit.Dp = 32.dp) {
    val playing = Playback.playing
    val pulse by rememberInfiniteTransition(label = "buffer").animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "p")
    val bg by animateColorAsState(fill, tween(650), label = "accent")
    val view = androidx.compose.ui.platform.LocalView.current
    Box(
        Modifier.shared("play").size(width, height).graphicsLayer { alpha = if (Playback.buffering) pulse else 1f }
            .clip(CircleShape).background(bg).pressable {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                Playback.toggle()
            },
        Alignment.Center,
    ) {
        Icon(
            if (playing) Ic.Pause else Ic.Play, if (Playback.buffering) "Loading" else if (playing) "Pause" else "Play",
            size = iconSize, tint = if (bg.luminance() > 0.45f) Color(0xFF111111) else Color.White,
        )
    }
}

@Composable
private fun SkipBtn(next: Boolean, size: androidx.compose.ui.unit.Dp = 56.dp, iconSize: androidx.compose.ui.unit.Dp = 28.dp) =
    IconBtn(if (next) Ic.Next else Ic.Prev, if (next) "Next" else "Previous", { if (next) Playback.next() else Playback.prev() }, size = size, iconSize = iconSize, tint = Color.White, haptic = true)

@Composable
private fun ShuffleBtn() = ModeBtn(Ic.Shuffle, if (Playback.shuffle) "Shuffle on" else "Shuffle off", Playback.shuffle) { Playback.toggleShuffle() }

@Composable
private fun RepeatBtn() {
    val one = Playback.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE
    val on = Playback.repeat != androidx.media3.common.Player.REPEAT_MODE_OFF
    ModeBtn(if (one) Ic.RepeatOne else Ic.Repeat, if (one) "Repeat this song" else if (on) "Repeat all" else "Repeat off", on) { Playback.cycleRepeat() }
}

/** Shuffle · previous · play/pause in a filled circle ([fill]: the cover's accent, or white) · next · repeat. */
@Composable
private fun Transport(fill: Color, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        ShuffleBtn()
        SkipBtn(false, iconSize = if (compact) 24.dp else 28.dp)
        PlayBtn(fill, if (compact) 64.dp else 74.dp, iconSize = if (compact) 28.dp else 32.dp)
        SkipBtn(true, iconSize = if (compact) 24.dp else 28.dp)
        RepeatBtn()
    }
}

/** Glass dock: title + heart; thin seek; the transport in one frosted bar; Lyrics / Sleep / Queue pills; output · quality. */
@Composable
private fun ColumnScope.DockControls(ui: PlayerUi, onQuality: () -> Unit, onPipeline: () -> Unit, onSeek: (Float?) -> Unit) {
    val ctx = LocalContext.current
    TitleRow(ui, onPipeline, menu = !Settings.npHeader) // ⋯ sits in the header; it comes back here when the header is hidden
    Spacer(Modifier.height(8.dp))
    GlassSeek(ui, quality = false, onSeekDrag = onSeek)
    Spacer(Modifier.weight(1f))
    // a translucent wash, no rim or shadow (there's no haze source behind the player to blur)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Transport(Color.White, compact = true)
    }
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        val m = Modifier.weight(1f)
        LabelAction(Ic.LyricsQuote, "Lyrics", ui.pane == "lyrics", m, 0.07f) { ui.toggle("lyrics") }
        LabelAction(Ic.Moon, "Sleep", Playback.sleepEndsAt > 0, m, 0.07f, ui.openSleep)
        LabelAction(Ic.QueueList, "Queue", ui.pane == "queue", m, 0.07f) { ui.toggle("queue") }
    }
    Row(Modifier.align(Alignment.CenterHorizontally).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(CircleShape).pressable(scale = 0.96f) { openOutputSwitcher(ctx) }.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.Device, null, size = 14.dp, tint = Color.White.copy(alpha = 0.6f))
            Txt("Playing on " + outputDevice(ctx).first.substringAfter(" · "), Modifier.padding(start = 6.dp), style = TimeStyle, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
        }
        QualityTag(onQuality)
    }
}

/** Big title: 32 sp title, accent artist; thick seek; prev · wide accent pill · next; shuffle · lyrics · sleep · queue · repeat. */
@Composable
private fun ColumnScope.BigTitleControls(ui: PlayerUi, accent: Color, onQuality: () -> Unit, onPipeline: () -> Unit, onSeek: (Float?) -> Unit) {
    TitleRow(ui, onPipeline, big = true, accent = accent)
    Spacer(Modifier.height(6.dp))
    GlassSeek(ui, onQuality, kind = "thick", onSeekDrag = onSeek)
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), Alignment.CenterVertically) {
        SkipBtn(false, 60.dp, 30.dp)
        PlayBtn(accent, 132.dp, 68.dp, 30.dp)
        SkipBtn(true, 60.dp, 30.dp)
    }
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        ShuffleBtn()
        PaneAction(Ic.LyricsQuote, "Lyrics", ui.pane == "lyrics") { ui.toggle("lyrics") }
        PaneAction(Ic.Moon, if (Playback.sleepEndsAt > 0) "Sleep timer on" else "Sleep timer", Playback.sleepEndsAt > 0, onClick = ui.openSleep)
        PaneAction(Ic.QueueList, "Queue", ui.pane == "queue") { ui.toggle("queue") }
        RepeatBtn()
    }
}

/** Waveform: compact title; the seek bar as bars; transport with a white play; the next song; Lyrics · Sleep · output. */
@Composable
private fun ColumnScope.WaveControls(ui: PlayerUi, accent: Color, onQuality: () -> Unit, onPipeline: () -> Unit, onSeek: (Float?) -> Unit) {
    val ctx = LocalContext.current
    TitleRow(ui, onPipeline)
    Spacer(Modifier.height(4.dp))
    GlassSeek(ui, onQuality, kind = "wave", accent = accent, onSeekDrag = onSeek)
    Spacer(Modifier.weight(1f))
    Transport(Color.White)
    Spacer(Modifier.weight(1f))
    UpNextCard(ui)
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        LabelAction(Ic.LyricsQuote, "Lyrics", ui.pane == "lyrics") { ui.toggle("lyrics") }
        LabelAction(Ic.Moon, "Sleep", Playback.sleepEndsAt > 0, onClick = ui.openSleep)
        LabelAction(Ic.Output, outputDevice(ctx).first.substringBefore(" · "), false) { openOutputSwitcher(ctx) }
    }
}

/** Play beside title: title + 64 dp accent play; seek with times either side; heart · shuffle · prev · next · repeat · ⋯; a 3-way switch. */
@Composable
private fun ColumnScope.BesideControls(ui: PlayerUi, accent: Color, onQuality: () -> Unit, onPipeline: () -> Unit, onSeek: (Float?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TitleText(ui, Modifier.weight(1f))
        Spacer(Modifier.width(14.dp))
        PlayBtn(accent, 64.dp, iconSize = 28.dp)
    }
    Spacer(Modifier.weight(1f))
    GlassSeek(ui, onQuality, kind = "inline", onSeekDrag = onSeek)
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        LikeBtn(ui)
        ShuffleBtn()
        SkipBtn(false, 52.dp, 26.dp)
        SkipBtn(true, 52.dp, 26.dp)
        RepeatBtn()
        PlayerMenu(ui, onPipeline)
    }
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).padding(3.dp)) {
        val m = Modifier.weight(1f)
        LabelAction(null, "Lyrics", ui.pane == "lyrics", m) { ui.toggle("lyrics") }
        LabelAction(null, "Up next", ui.pane == "queue", m) { ui.toggle("queue") }
        LabelAction(null, "Sleep", Playback.sleepEndsAt > 0, m, onClick = ui.openSleep)
    }
}

/** A labelled pill button (optional icon); a brighter wash while [on]. The label is what TalkBack reads. */
@Composable
private fun LabelAction(icon: ImageVector?, label: String, on: Boolean, modifier: Modifier = Modifier, fill: Float = 0f, onClick: () -> Unit) {
    val bg by animateColorAsState(Color.White.copy(alpha = if (on) 0.16f else fill), tween(200), label = "labelBg")
    val fg = Color.White.copy(alpha = if (on || fill > 0f) 1f else 0.75f)
    Row(
        modifier.height(44.dp).clip(CircleShape).background(bg).semantics { selected = on }
            .pressable(scale = 0.96f, onClick = onClick).padding(horizontal = 12.dp),
        Arrangement.Center, Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.padding(end = 8.dp), size = 17.dp, tint = fg)
        Txt(label, style = Type.caption.copy(fontSize = 13.sp), color = fg, weight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

/** The song after this one; tap for the queue. */
@Composable
private fun UpNextCard(ui: PlayerUi) {
    // shortcut: the queue's next row, like QueueView shows it; with shuffle on the player may pick another, use the timeline's next index if that matters
    val next = Playback.queue.getOrNull(Playback.index + 1)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f))
            .pressable(scale = 0.98f) { ui.toggle("queue") }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (next != null) Cover(next, Modifier.padding(end = 12.dp).size(40.dp).clip(RoundedCornerShape(10.dp)))
        else Spacer(Modifier.width(4.dp).height(40.dp))
        Column(Modifier.weight(1f)) {
            Txt("UP NEXT", style = Type.overline.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.55f), maxLines = 1)
            Txt(next?.let { "${it.title} · ${it.artist}" } ?: "End of queue", style = Type.body.copy(fontSize = 14.sp), color = Color.White, weight = FontWeight.SemiBold, maxLines = 1)
        }
        Icon(Ic.QueueList, "Open queue", Modifier.padding(horizontal = 10.dp), size = 20.dp, tint = Color.White)
    }
}

/** Shuffle / repeat: dim when off, bright with a small dot underneath when on. */
@Composable
private fun ModeBtn(icon: ImageVector, desc: String, on: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (on) 1f else 0.55f, tween(200), label = "mode")
    Box(contentAlignment = Alignment.Center) {
        IconBtn(icon, desc, onClick, size = 48.dp, iconSize = 22.dp, tint = Color.White.copy(alpha = a), haptic = true)
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).graphicsLayer { alpha = if (on) 1f else 0f }.clip(CircleShape).background(Color.White))
    }
}

/** The phone's media volume, always visible: quiet speaker · slider · loud speaker. Follows the volume buttons too. */
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
        Icon(Ic.VolumeLow, null, size = 16.dp, tint = Color.White.copy(alpha = 0.7f))
        SeekSlider(
            level.toFloat() / max,
            { (it * max).roundToInt().let { v -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); level = v } }, {},
            Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = "Volume" }, color = Color.White, thickness = 4.dp,
        )
        Icon(Ic.Volume, null, size = 18.dp, tint = Color.White.copy(alpha = 0.7f))
    }
}

/** Lyrics | sleep | queue: plain glyphs evenly spaced, split by thin dividers. */
@Composable
private fun BottomBar(ui: PlayerUi) {
    val sleeping = Playback.sleepEndsAt > 0
    @Composable fun Divider() = Box(Modifier.size(1.dp, 18.dp).background(Color.White.copy(alpha = 0.2f)))
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
        PaneAction(Ic.LyricsQuote, "Lyrics", ui.pane == "lyrics") { ui.toggle("lyrics") }
        Divider()
        PaneAction(Ic.Moon, if (sleeping) "Sleep timer on" else "Sleep timer", sleeping, onClick = ui.openSleep)
        Divider()
        PaneAction(Ic.QueueList, "Queue", ui.pane == "queue") { ui.toggle("queue") }
    }
}

/** A plain glyph button, brighter with a soft wash while [on]. */
@Composable
private fun PaneAction(icon: ImageVector, desc: String, on: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (on) 1f else 0.75f, tween(200), label = "action")
    val bg by animateColorAsState(Color.White.copy(alpha = if (on) 0.16f else 0f), tween(200), label = "actionBg")
    IconBtn(icon, desc, onClick, size = 46.dp, iconSize = 21.dp, tint = Color.White.copy(alpha = a), background = bg)
}

/** Android's own "play audio on" picker; older phones get the volume panel or Bluetooth settings. */
private fun openOutputSwitcher(ctx: Context) {
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 34) { android.media.MediaRouter2.getInstance(ctx).showSystemOutputSwitcher(); return }
        val intent = if (android.os.Build.VERSION.SDK_INT >= 29) android.content.Intent(android.provider.Settings.Panel.ACTION_VOLUME) else android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
        ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Sideways phone: the cover on the left, everything else on the right, over a soft blur of the cover. */
@Composable
private fun LandscapePlayer(ui: PlayerUi, seekDrag: Float?, onSeekDrag: (Float?) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Cover(ui.t, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }.blur(60.dp, BlurredEdgeTreatment.Unbounded), alpha = 0.8f)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        Row(Modifier.fillMaxSize().statusPad().navigationBarsPadding().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                when (ui.pane) {
                    "lyrics" -> LyricsView(ui.t, ui.pos)
                    "queue" -> QueueView()
                    else -> Cover(ui.t, Modifier.fillMaxSize().shadow(24.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)))
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    SoftCircle(Ic.Lyrics, "Lyrics", ui.pane == "lyrics") { ui.toggle("lyrics") }
                    Spacer(Modifier.width(10.dp))
                    SoftCircle(Ic.Queue, "Queue", ui.pane == "queue") { ui.toggle("queue") }
                }
                Spacer(Modifier.weight(1f))
                TitleRow(ui)
                if (ui.pane != "lyrics") LyricLine(ui, seekDrag?.let { (it * ui.duration).toLong() })
                Spacer(Modifier.height(14.dp))
                GlassSeek(ui, onSeekDrag = onSeekDrag)
                Spacer(Modifier.height(4.dp))
                Transport(ui.pal.hue?.let { hueAccent(it, dark = true) } ?: Color.White, compact = true)
            }
        }
    }
}

/**
 * The line being sung right now, above the seek bar; tap for the full lyrics. Between sung lines it shows pulsing
 * notes. While you drag the seek bar ([previewMs]) it shows the line you'd land on.
 */
@Composable
private fun LyricLine(ui: PlayerUi, previewMs: Long?) {
    val loaded = rememberLyrics(ui.t) // null while the new song's lyrics load
    val lines = loaded.orEmpty()
    // while loading keep the line's space as it was, then open or close it once we know; on every song change the
    // title, heart and ⋯ above it glide instead of jumping
    var synced by remember { mutableStateOf(lines.isNotEmpty() && lines.first().ms >= 0) } // no animation when the player opens
    if (loaded != null) synced = lines.isNotEmpty() && lines.first().ms >= 0
    if (!Settings.showInline) synced = false // Settings → Appearance → Now Playing → Lyrics on Now Playing
    AnimatedVisibility(
        synced,
        enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 300f), expandFrom = Alignment.Top) + fadeIn(tween(300, delayMillis = 120)),
        exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 300f), shrinkTowards = Alignment.Top) + fadeOut(tween(180)),
    ) {
        // empty while loading or closing: a blank of the same height, so the line has something to shrink
        // (the three-line style keeps its full height while the next song's lyrics load, so nothing shrinks and regrows)
        if (Settings.inlineStyle == "stack") StackLines(ui, lines, previewMs) else LyricLineContent(ui, lines, previewMs)
    }
}

@Composable
private fun LyricLineContent(ui: PlayerUi, lines: List<Lyrics.Line>, previewMs: Long?) {
    val i = currentLine(lines, previewMs ?: ui.pos, Lyrics.offset(ui.t.id))
    // "" = blank placeholder (next song's lyrics loading, or the line closing); null = music between lines
    val text = if (lines.isEmpty()) "" else lines.getOrNull(i)?.text?.takeIf { it.isNotBlank() }
    // lines roll like a drum turning upward: the old one rotates up and away, the new one rotates in from below
    AnimatedContent(
        text, Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 22.dp)
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 300f)) // a 2-row line grows smoothly too
            .pressable(scale = 0.98f) { ui.toggle("lyrics") },
        transitionSpec = { fadeIn(tween(380)) togetherWith fadeOut(tween(300)) }, label = "line",
    ) { line ->
        // -1 entering (below), 0 in place, 1 leaving (above)
        val roll by transition.animateFloat({ tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing) }, label = "roll") {
            when (it) { androidx.compose.animation.EnterExitState.PreEnter -> -1f; androidx.compose.animation.EnterExitState.Visible -> 0f; androidx.compose.animation.EnterExitState.PostExit -> 1f }
        }
        Row(
            Modifier.fillMaxWidth().graphicsLayer {
                rotationX = 80f * roll // top tips back as it rises, bottom tips back as it arrives
                translationY = -roll * size.height * 0.6f
                cameraDistance = 10f * density
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center, // centered, like the three-line style
        ) {
            if (line == "") return@Row Spacer(Modifier.height(22.dp))
            // optional: the inline line in Latin letters for Tamil, Hindi, Telugu… (the full lyrics panel is unchanged)
        val shownLine = line?.let { if (Settings.romanInline) romanize(it) ?: it else it }
        val sung = lines.getOrNull(i)?.takeIf { it.text.trim() == line?.trim() } // this line's timing (not for one that's leaving)
        val inlineStyle = Type.body.copy(fontFamily = LyricFont, fontSize = 21.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) // same as the three-line current line; its own font marks it as lyrics
        if (shownLine != null && sung != null && Settings.inlineWords && previewMs == null)
            InlineSung(sung, shownLine, ui.pos + Lyrics.offset(ui.t.id), inlineStyle, Modifier.weight(1f, fill = false))
        else if (shownLine != null) Txt(shownLine, Modifier.weight(1f, fill = false), style = inlineStyle, color = Color.White, maxLines = 2)
            else MusicBreak()
        }
    }
}

/** Shown in the lyric line while the music plays between sung lines: three notes that softly pulse in turn. */
@Composable
private fun MusicBreak() {
    val t by rememberInfiniteTransition(label = "notes").animateFloat(0f, 3f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "t")
    Row(Modifier.height(22.dp).semantics { contentDescription = "Instrumental" }, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val near = 1f - kotlin.math.abs(((t - i + 3f) % 3f) - 1f).coerceAtMost(1f) // 1 when it's this note's turn
            Icon(Ic.Note, null, Modifier.padding(end = 4.dp), size = 16.dp, tint = Color.White.copy(alpha = 0.4f + 0.45f * near))
        }
    }
}

/** "Ends in 23 min" while a sleep timer runs. */
@Composable
private fun SleepBadge(modifier: Modifier) {
    val end = Playback.sleepEndsAt
    val minutes by produceState(0L, end) {
        while (end > 0) { value = ((end - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(0); delay(20_000) }
    }
    AnimatedVisibility(end > 0, modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).pressable(scale = 0.96f) { Playback.sleep(0) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.Moon, null, size = 14.dp, tint = Color.White.copy(alpha = 0.85f))
            Txt("Ends in $minutes min", Modifier.padding(start = 6.dp), style = Type.caption.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.85f))
        }
    }
}

/** Long-press on the cover: the things you'd want about this song, in one place. */
@Composable
private fun CoverMenu(ui: PlayerUi, open: Boolean, onDismiss: () -> Unit) {
    var pick by remember { mutableStateOf(false) }
    fun close(a: () -> Unit): () -> Unit = { onDismiss(); a() }
    Menu(open, onDismiss, header = { MenuTrackHeader(ui.t) }) {
        val liked = Library.isLiked(ui.t)
        MenuItem(if (liked) "Remove from Liked" else "Like", close { io { Library.toggleLike(ui.t) } }, if (liked) Ic.Heart else Ic.HeartOutline)
        if (ui.t.source == "yt") MenuItem("Start radio", close { Playback.startRadio(ui.t) }, Ic.Radio)
        MenuItem("Add to playlist…", close { pick = true }, Ic.AddPlaylist)
        if (ui.t.source != "local" && Library.downloads.none { it.id == ui.t.id }) MenuItem("Download", close { Downloads.download(ui.t) }, Ic.Download)
        GoToItems(ui, open) { a -> close(a)() }
    }
    if (pick) PlaylistPicker(ui.t) { pick = false }
}

/** A faint dark layer under the soft white buttons, so they still show on white covers. */
private val SoftBase = Color.Black.copy(alpha = 0.16f)

/** Round button in the style of P1's "This phone" pill: a soft white wash, no frame. */
@Composable
private fun SoftCircle(icon: ImageVector, label: String, active: Boolean = false, size: Int = 46, onClick: () -> Unit) {
    // plain glyph like the transport and bottom actions; a soft wash only while active
    val bg by animateColorAsState(Color.White.copy(alpha = if (active) 0.16f else 0f), tween(200), label = "soft")
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg)
            .semantics { contentDescription = label; selected = active }
            .pressable(scale = 0.9f, onClick = onClick),
        Alignment.Center,
    ) { Icon(icon, null, size = 22.dp, tint = Color.White.copy(alpha = if (active) 1f else 0.9f)) }
}

/** ⋯ in the player: everything that isn't needed every second lives here. */
@Composable
private fun PlayerMenu(ui: PlayerUi, onPipeline: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    fun close(a: () -> Unit): () -> Unit = { open = false; a() }
    Box {
        SoftCircle(Ic.More, "More options") { open = true }
        Menu(open, { open = false }, header = { MenuTrackHeader(ui.t) }) {
            MenuItem("Add to playlist…", close { pick = true }, Ic.AddPlaylist)
            if (ui.t.source != "local") {
                val done = Library.downloads.any { it.id == ui.t.id }
                if (done) MenuItem("Remove download", close { Downloads.delete(ui.t) }, Ic.Delete)
                else MenuItem(if (ui.t.id in Downloads.active) "Downloading…" else "Download", close { Downloads.download(ui.t) }, Ic.Download, enabled = ui.t.id !in Downloads.active)
            }
            MenuItem("Play next", close { Playback.playNext(ui.t) }, Ic.PlayNext)
            if (ui.t.source == "yt") MenuItem("Start radio", close { Playback.startRadio(ui.t) }, Ic.Radio)
            MenuItem("Playing on: ${outputDevice(ctx).first.substringAfter(" · ")}", close { openOutputSwitcher(ctx) }, Ic.Output)
            GoToItems(ui, open) { a -> close(a)() }
            if (onPipeline != null) MenuItem("Audio pipeline", close(onPipeline), Ic.Pipeline)
        }
    }
    if (pick) PlaylistPicker(ui.t) { pick = false }
}

/** Thin track with a small dot; while dragging, a bubble shows the time you'd land on. Elapsed left, time left right. */
/**
 * [kind]: "thin" (above), "thick" (8 dp, bold times with the total on the right), "inline" (times either side of the bar)
 * or "wave" (bars, played part in [accent]).
 */
@Composable
private fun GlassSeek(ui: PlayerUi, onQuality: () -> Unit = {}, kind: String = "thin", accent: Color = Color.White, quality: Boolean = true, onSeekDrag: (Float?) -> Unit) {
    val onDrag = onSeekDrag
    var dragging by remember { mutableStateOf<Float?>(null) }
    var width by remember { mutableFloatStateOf(1f) }
    val shown = dragging ?: ui.fraction
    val density = LocalDensity.current
    val at = (shown * ui.duration).toLong()
    val bold = kind == "thick"
    val timeStyle = if (bold) TimeStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold) else TimeStyle
    val timeColor = Color.White.copy(alpha = if (bold) 0.75f else 0.6f)
    val end = if (kind == "thick" || kind == "inline") formatTime(ui.duration) else "-" + formatTime(ui.duration - at)
    val bar = @Composable { m: Modifier ->
    Box(m.onSizeChanged { width = it.width.toFloat() }) {
        val change: (Float) -> Unit = { dragging = it; onDrag(it) }
        val done = { dragging?.let(ui::seekTo); dragging = null; onDrag(null) }
        if (kind == "wave") {
            // shortcut: decorative bars seeded by the track id (no real waveform data); draw the real one if it ever appears
            val heights = remember(ui.t.id) { val r = kotlin.random.Random(ui.t.id.hashCode()); List(56) { 0.22f + 0.78f * r.nextFloat() } }
            val rest = Color.White.copy(alpha = 0.28f)
            Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                val bw = 3.dp.toPx()
                val step = (size.width - bw) / (heights.size - 1)
                heights.forEachIndexed { i, h ->
                    val bh = size.height * h
                    drawRoundRect(
                        if ((i + 0.5f) / heights.size <= shown) accent else rest,
                        androidx.compose.ui.geometry.Offset(i * step, (size.height - bh) / 2), androidx.compose.ui.geometry.Size(bw, bh),
                        androidx.compose.ui.geometry.CornerRadius(bw / 2),
                    )
                }
            }
            SeekSlider(shown, change, done, color = Color.Transparent) // invisible: just the touch, drag and accessibility handling
        } else SeekSlider(shown, change, done, color = Color.White, thickness = if (bold) 8.dp else 3.dp, thumb = !bold)
        dragging?.let { f ->
            val bubbleW = with(density) { 60.dp.toPx() }
            Box(
                Modifier.offset { IntOffset((f * width - bubbleW / 2).coerceIn(0f, width - bubbleW).roundToInt(), with(density) { (-30).dp.roundToPx() }) }
                    .width(60.dp).clip(CircleShape).background(Color.White).padding(vertical = 4.dp),
                Alignment.Center,
            ) { Txt(formatTime((f * ui.duration).toLong()), style = TimeStyle, color = Color(0xFF1A1210), weight = FontWeight.SemiBold) }
        }
    }
    }
    Column(Modifier.fillMaxWidth().shared("seek")) {
        if (kind == "inline") Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(formatTime(at), style = timeStyle, color = timeColor)
            bar(Modifier.weight(1f).padding(horizontal = 12.dp))
            Txt(end, style = timeStyle, color = timeColor)
        } else {
            bar(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth()) {
                Txt(formatTime(at), style = timeStyle, color = timeColor)
                // the stream quality, tiny, between the times; tap it for the whole audio pipeline
                Box(Modifier.weight(1f), Alignment.Center) { if (quality) QualityTag(onQuality) }
                Txt(end, style = timeStyle, color = timeColor)
            }
        }
    }
}

/** Times use equal-width digits ("tnum"), so the row doesn't shift as 1:19 becomes 1:20. */
private val TimeStyle = Type.caption.copy(fontSize = 12.sp, fontFeatureSettings = "tnum")

/**
 * The tiny quality line between the times; tap for the audio pipeline. While WeTune looks for a 320 kbps copy it says so,
 * otherwise it shows the badge, codec and bitrate; a miss just keeps the original quality.
 */
@Composable
private fun QualityTag(onClick: () -> Unit) {
    val searching = AudioInfo.upgrade == "searching"
    val pulse by rememberInfiniteTransition(label = "upgrade").animateFloat(0.35f, 0.85f, infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    // the version playing now, always: badge + format only ("HQ AAC", "LOSSLESS FLAC"; details are in the Audio sheet);
    // while a better copy is being looked for, "· Upgrading…" after it
    val text = AudioInfo.hifiNow?.first ?: AudioInfo.codecName
    val badge = AudioInfo.hifiNow?.let { (_, bits, rate) -> if (bits > 16 || rate > 48_000) "HI-RES" else "LOSSLESS" }
        ?: AudioInfo.kbps?.let { if (it >= 256) "HQ" else "SQ" }
    if (text == null && !searching) return
    AnimatedContent(text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "quality") { s ->
        Row(
            Modifier.clip(CircleShape).pressable(scale = 0.96f, onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (badge != null && s != null) Txt(
                badge, Modifier.padding(end = 6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.75f)).padding(horizontal = 3.dp),
                style = Type.overline.copy(fontSize = 8.sp, letterSpacing = 0.3.sp, lineHeight = 11.sp), color = Color(0xFF1A1210), weight = FontWeight.Bold,
            )
            val style = Type.caption.copy(fontSize = 11.sp, fontFeatureSettings = "tnum")
            if (s != null) Txt(s, style = style, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
            androidx.compose.animation.AnimatedVisibility(searching, enter = fadeIn(), exit = fadeOut()) {
                Txt(if (s != null) " · Upgrading…" else "Upgrading…", Modifier.graphicsLayer { alpha = pulse }, style = style, color = Color.White, maxLines = 1)
            }
        }
    }
}

/**
 * Lyrics or queue, immersive: the panel fills the screen; at the bottom the now bar (cover ring play/pause,
 * title capsule back to the cover, and the panel's options). Everything else steps aside.
 */
@Composable
private fun ImmersivePane(ui: PlayerUi) {
    val lyrics = ui.pane == "lyrics"
    var tray by remember { mutableStateOf(false) }
    // the content fades out above the now bar (76 dp) and, while it's open, its pill tray; only the fade moves, never the layout
    val clear by animateDpAsState(if (tray) 176.dp else 124.dp, tween(250), label = "clear")
    Box(Modifier.fillMaxSize().statusPad().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            // back · "Queue" over "Playing from …" / "Lyrics"
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ic.Back, "Back to Now Playing", { ui.setPane("art") }, size = 48.dp, iconSize = 22.dp, tint = Color.White)
                Column(Modifier.padding(start = 2.dp).semantics(mergeDescendants = true) { heading() }) {
                    Txt(if (lyrics) "Lyrics" else "Queue", style = Type.bodyStrong.copy(fontSize = 17.sp, lineHeight = 22.sp), color = Color.White, weight = FontWeight.SemiBold, maxLines = 1)
                    if (!lyrics) Playback.from?.let { (_, name) ->
                        Txt("Playing from $name", style = Type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp), color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                    }
                }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val h = size.height
                        val top = (if (lyrics) 72.dp else 16.dp).toPx() / h
                        val end = 1f - clear.toPx() / h
                        drawRect(Brush.verticalGradient(0f to Color.Transparent, top to Color.Black, (end - 110.dp.toPx() / h).coerceAtLeast(top) to Color.Black, end to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                    }
                    .padding(start = if (lyrics) 28.dp else 16.dp, end = if (lyrics) 32.dp else 16.dp),
            ) {
                if (lyrics) LyricsView(ui.t, ui.pos, tools = false) else QueueView(Modifier.fillMaxSize(), tools = false)
            }
        }
        // the now bar floats over the panel's faded bottom; its options tray opens upward over it, so nothing moves
        NowBar(ui.t, ui.pane, onOpen = { ui.setPane("art") }, modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 12.dp), onTray = { tray = it })
    }
}

/** The scopes the normal and immersive layouts share, so tagged parts can glide between them. */
@OptIn(ExperimentalSharedTransitionApi::class)
private class SharedScopes(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

private val LocalShared = compositionLocalOf<SharedScopes?> { null }

/** Marks a part that exists in both layouts (title, artist, play, seek); it morphs instead of fading. No-op elsewhere. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.shared(key: String, text: Boolean = false): Modifier {
    val s = LocalShared.current ?: return this
    return with(s.transition) {
        this@shared.sharedBounds(
            rememberSharedContentState(key), s.visibility,
            resizeMode = if (text) SharedTransitionScope.ResizeMode.ScaleToBounds() else SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/** The one-line lyric above the seek bar, filling in letter by letter when the lyrics are timed per word. */
@Composable
private fun InlineSung(line: Lyrics.Line, shown: String, pos: Long, style: androidx.compose.ui.text.TextStyle, modifier: Modifier, tint: Color = Color.White) {
    val words = remember(line, shown) { wordsOf(line, shown) }
        ?: return Txt(shown, modifier, style = style, color = tint, maxLines = 2)
    KaraokeText(words, smoothPosition(pos + 150), style, lit = tint, dim = Color.White.copy(alpha = 0.45f), modifier = modifier, maxLines = 2)
}

/**
 * Three-line inline lyrics: the line just sung and the next one dimmed around the current one, centered; each new
 * line slides the stack up. Tap to open the full lyrics.
 */
@Composable
private fun StackLines(ui: PlayerUi, lines: List<Lyrics.Line>, previewMs: Long?) {
    val i = currentLine(lines, previewMs ?: ui.pos, Lyrics.offset(ui.t.id))
    fun text(k: Int) = lines.getOrNull(k)?.text?.takeIf { it.isNotBlank() }?.let { if (Settings.romanInline) romanize(it) ?: it else it }
    val dim = Type.body.copy(fontFamily = LyricFont, fontSize = 17.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
    val main = Type.body.copy(fontFamily = LyricFont, fontSize = 21.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    AnimatedContent(
        i, Modifier.fillMaxWidth().padding(top = 6.dp).pressable(scale = 0.98f) { ui.toggle("lyrics") },
        transitionSpec = {
            (androidx.compose.animation.slideInVertically(tween(380)) { it / 3 } + fadeIn(tween(380))) togetherWith
                (androidx.compose.animation.slideOutVertically(tween(300)) { -it / 3 } + fadeOut(tween(220)))
        },
        label = "stack",
    ) { k ->
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(text(k - 1).orEmpty(), Modifier.fillMaxWidth(), style = dim, color = Color.White.copy(alpha = 0.4f), maxLines = 1)
            val cur = text(k); val sung = lines.getOrNull(k)
            if (cur != null && sung != null && Settings.inlineWords && previewMs == null)
                InlineSung(sung, cur, ui.pos + Lyrics.offset(ui.t.id), main, Modifier.fillMaxWidth().padding(vertical = 4.dp))
            else if (cur != null) Txt(cur, Modifier.fillMaxWidth().padding(vertical = 4.dp), style = main, color = Color.White, maxLines = 2)
            else if (lines.isEmpty()) Txt("", Modifier.fillMaxWidth().padding(vertical = 4.dp), style = main, maxLines = 1) // loading: same height, nothing shown
            else Box(Modifier.fillMaxWidth().heightIn(min = 31.dp).padding(vertical = 4.dp), Alignment.Center) { MusicBreak() } // a full line tall, like the text
            Txt(text(k + 1).orEmpty(), Modifier.fillMaxWidth(), style = dim, color = Color.White.copy(alpha = 0.4f), maxLines = 1)
        }
    }
}

/**
 * "Go to <artist>" for each linked artist and "Go to album", opening their real pages (looked up when the menu opens);
 * a search stands in while they load, or for songs without pages.
 */
@Composable
private fun GoToItems(ui: PlayerUi, menuOpen: Boolean, run: (() -> Unit) -> Unit) {
    val links by produceState<List<com.wetune.sources.Card>?>(null, menuOpen, ui.t.id) {
        if (menuOpen && ui.t.source == "yt") value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { com.wetune.youtube.YouTube.links(ui.t.ref) }.getOrNull() }
    }
    val artists = links.orEmpty().filter { it.subtitle == "Artist" }
    val album = links.orEmpty().firstOrNull { it.subtitle == "Album" }
    if (artists.isNotEmpty()) artists.forEach { a -> MenuItem("Go to ${a.title}", { run { ui.open(a) } }, Ic.Person) }
    else MenuItem("Go to artist", { run { ui.search(ui.t.artist.substringBefore(",").trim()) } }, Ic.Person)
    if (album != null) MenuItem("Go to album", { run { ui.open(album) } }, Ic.Library)
    else if (ui.t.album.isNotBlank()) MenuItem("Go to album", { run { ui.search(ui.t.album) } }, Ic.Library)
}
