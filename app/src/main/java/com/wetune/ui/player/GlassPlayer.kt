@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.wetune.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wetune.Downloads
import com.wetune.Library
import com.wetune.Lyrics
import com.wetune.Playback
import com.wetune.Settings
import com.wetune.io
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Now Playing 2.0: the screen that stacks the layers (NpLayers.kt) where [npGeometry] puts them, plus what isn't
// styled: the header, gestures, menus, the lyric line, the immersive lyrics / queue pane and the landscape player.

/**
 * The player for [style] (the applied one, or the editor's draft when [ui] is a preview). Back to front: background
 * (with dim) · cover · scrim · header, lyric line and controls. Lyrics / queue open: the background softens and dims
 * and title, artist, play and seek glide into the pane.
 */
@Composable
fun GlassPlayer(ui: PlayerUi, style: NowPlayingStyle = NpStyleStore.style) {
    val colors = remember(style, ui.art) { resolveColors(style, ui.art) }
    val paneOpen = ui.pane != "art"
    val paneDim by animateFloatAsState(if (paneOpen) 0.45f else 0f, tween(400), label = "paneDim")
    val still = rememberReduceMotion()
    val ambient = style.motion.ambient && !still
    var seekDrag by remember { mutableStateOf<Float?>(null) } // while dragging the seek bar: where you'd land
    var coverMenu by remember { mutableStateOf(false) }
    var pipeline by remember { mutableStateOf(false) }
    var audioOptions by remember { mutableStateOf(false) }
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // depth: tilting the phone moves the background against the tilt and the cover with it (read in graphicsLayer only)
    val depthOn = style.motion.depth && !still && !ui.preview
    val tilt = rememberTilt(depthOn, style.motion.depthInvert)
    val depthK = 0.25f + style.motion.depthIntensity / 100f * 1.5f
    // the background (and, in the lyrics / queue pane, the panel) is recorded for the now bar's frosted glass
    val npHaze = remember { HazeState() }
    // menus and sheets opened here use the dark palette, like the player itself
    CompositionLocalProvider(com.wetune.ui.LocalOverlayPalette provides WT.c.asDark()) {
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.base)) {
        if (maxWidth > maxHeight && !ui.preview) return@BoxWithConstraints LandscapePlayer(ui, style, colors, seekDrag) { seekDrag = it }
        // the status bar's height even while it's hidden, so hiding or showing it never moves anything
        val top = if (ui.preview) 36.dp else maxOf(statusBarHeight(), WindowInsets.displayCutout.asPaddingValues().calculateTopPadding())
        val bottom = if (ui.preview) 16.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val geo = npGeometry(style, maxWidth.value, maxHeight.value, top.value, bottom.value)

        Box(Modifier.fillMaxSize().hazeSource(npHaze).background(colors.base)) {
            BackgroundLayer(
                if (paneOpen && style.background.kind != BgKind.Solid) style.background.copy(kind = BgKind.Blur, blur = 100, zoom = 1.4f) else style.background,
                colors, ui.art, geo, ui.playing, still, ambient,
                Modifier.graphicsLayer {
                    if (depthOn) {
                        scaleX = 1.08f; scaleY = 1.08f // zoomed a little so its edges never show while it shifts
                        translationX = -tilt.value.x * 14.dp.toPx() * depthK; translationY = tilt.value.y * 14.dp.toPx() * depthK
                    }
                },
            )
            if (paneDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = paneDim)))
        }

        BackHandler(paneOpen && !ui.preview) { ui.setPane("art") }
        SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(
            paneOpen, Modifier.fillMaxSize(), label = "immersive",
            transitionSpec = { (fadeIn(tween(380, delayMillis = 60)) togetherWith fadeOut(tween(220))).using(SizeTransform(clip = false)) },
        ) { open ->
        CompositionLocalProvider(LocalShared provides SharedScopes(this@SharedTransitionLayout, this@AnimatedContent)) {
        if (open) ImmersivePane(ui, npHaze.takeIf { !ui.preview }) else Box(Modifier.fillMaxSize()) {
            // the scrim lies under a card / disc / record (as in the mockups) and over a full-width cover, whose top must darken under the header
            val scrimOver = style.cover.kind == CoverKind.FullWidth
            val vignette = if (depthOn) (0.55f * depthK).coerceAtMost(0.8f) else 0f
            if (!scrimOver) ScrimLayer(geo, vignette)
            CoverLayer(
                style.cover, colors, ui, geo, still, ambient, { swipe.value * 0.5f }, { coverMenu = true },
                Modifier.graphicsLayer { if (depthOn) { translationX = tilt.value.x * 7.dp.toPx() * depthK; translationY = -tilt.value.y * 7.dp.toPx() * depthK } },
            )
            if (scrimOver) ScrimLayer(geo, vignette)
            // on the cover: swipe sideways to skip (the pager swipes itself), long-press for the song's menu
            if (!ui.preview) Box(
                Modifier.offset(y = (geo.headerTop + geo.headerH).dp).fillMaxWidth().height((geo.coverBottom - geo.headerTop - geo.headerH).coerceAtLeast(0f).dp)
                    .then(
                        if (style.cover.kind == CoverKind.Pager) Modifier
                        else Modifier.pointerInput(ui.t.id) {
                            val limit = 90.dp.toPx()
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        swipe.value < -limit -> ui.next()
                                        swipe.value > limit -> ui.prev()
                                    }
                                    scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.8f)) }
                                },
                                onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                            ) { change, dx -> change.consume(); scope.launch { swipe.snapTo(swipe.value + dx) } }
                        }.pointerInput(Unit) { detectTapGestures(onLongPress = { coverMenu = true }) },
                    )
                    // TalkBack can't swipe the cover: the same things as actions
                    .semantics {
                        contentDescription = "Cover of ${ui.t.title}"
                        customActions = listOf(
                            CustomAccessibilityAction("Next song") { ui.next(); true },
                            CustomAccessibilityAction("Previous song") { ui.prev(); true },
                            CustomAccessibilityAction("Song options") { coverMenu = true; true },
                            CustomAccessibilityAction("Close player") { ui.close(); true },
                        )
                    },
            ) { Box(Modifier.align(Alignment.Center)) { CoverMenu(ui, coverMenu) { coverMenu = false } } }
            if (style.layout.header) Header(ui, geo)
            SleepBadge(ui, Modifier.align(Alignment.TopCenter).offset(y = (geo.headerTop + geo.headerH + 4).dp))
            // the lyric line, centred between the cover and the title; it opens and closes inside this space
            if (style.layout.lyrics != InlineLyrics.Off) Box(
                Modifier.offset(y = geo.coverBottom.dp).fillMaxWidth().height((geo.controlsTop - geo.coverBottom).coerceAtLeast(0f).dp).padding(horizontal = 24.dp),
                Alignment.Center,
            ) { LyricLine(ui, seekDrag?.let { (it * ui.duration).toLong() }, style.layout.lyrics) }
            ControlsLayer(
                style.controls, colors, ui,
                NpActions({ audioOptions = true }, { pipeline = true }, { seekDrag = it }) { PlayerMenu(ui) { pipeline = true } },
                Modifier.offset(y = geo.controlsTop.dp).height(geo.controlsH.dp).padding(horizontal = 24.dp),
            )
        }
        }
        }
        }
    }
    if (audioOptions) AudioOptionsSheet(ui.t, onPipeline = { audioOptions = false; pipeline = true }) { audioOptions = false }
    if (pipeline) PipelineSheet(ui.t) { pipeline = false }
    }
}

/**
 * "Playing from" what the queue was started from. No close button: swipe down or Back closes the player, and for
 * TalkBack this line (like the cover) has a "Close player" action.
 */
@Composable
private fun Header(ui: PlayerUi, geo: NpGeometry) {
    Row(Modifier.offset(y = geo.headerTop.dp).fillMaxWidth().height(geo.headerH.dp).padding(horizontal = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) { customActions = listOf(CustomAccessibilityAction("Close player") { ui.close(); true }) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Playback.from?.let { (kind, name) ->
                Txt("PLAYING FROM ${kind.uppercase()}", style = Type.overline.copy(fontSize = 11.sp, letterSpacing = 0.9.sp), color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                Txt(name, Modifier.basicMarquee(iterations = Int.MAX_VALUE), style = Type.caption.copy(fontSize = 14.sp, lineHeight = 19.sp), color = Color.White, weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
            }
        }
    }
}

/** "Ends in 23 min" while a sleep timer runs; tap to cancel it. */
@Composable
private fun SleepBadge(ui: PlayerUi, modifier: Modifier) {
    val end = Playback.sleepEndsAt
    val minutes by produceState(0L, end) {
        while (end > 0) { value = ((end - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(0); delay(20_000) }
    }
    AnimatedVisibility(ui.sleepOn, modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                .pressable(scale = 0.96f) { Playback.sleep(0) }.semantics(mergeDescendants = true) { contentDescription = "Sleep timer ends in $minutes minutes. Tap to cancel" }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.Moon, null, size = 14.dp, tint = Color.White)
            Txt("Ends in $minutes min", Modifier.padding(start = 6.dp), style = Type.caption.copy(fontSize = 12.sp), color = Color.White)
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

/** Round 48 dp button: a plain glyph, a soft wash while active. */
@Composable
private fun SoftCircle(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val bg by animateColorAsState(Color.White.copy(alpha = if (active) 0.16f else 0f), tween(200), label = "soft")
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(bg)
            .semantics { contentDescription = label; selected = active }
            .pressable(scale = 0.9f, onClick = onClick),
        Alignment.Center,
    ) { Icon(icon, null, size = 22.dp, tint = Color.White) }
}

/** ⋯ in the player: everything that isn't needed every second lives here. */
@Composable
private fun PlayerMenu(ui: PlayerUi, onPipeline: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    fun close(a: () -> Unit): () -> Unit = { open = false; a() }
    Box {
        SoftCircle(Ic.More, "More options") { if (!ui.preview) open = true }
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
            MenuItem("Customize player", close { NpEditor.open = true }, Ic.Palette)
        }
    }
    if (pick) PlaylistPicker(ui.t) { pick = false }
}

/**
 * Lyrics or queue, immersive: the panel fills the screen; at the bottom the now bar (cover ring play/pause,
 * title capsule back to the cover, and the panel's options). Everything else steps aside.
 */
@Composable
private fun ImmersivePane(ui: PlayerUi, haze: HazeState?) {
    val lyrics = ui.pane == "lyrics"
    var tray by remember { mutableStateOf(NowBarTray.open) } // open by default; the now bar reports the real state
    // the content fades out above the now bar (76 dp) and, while it's open, its pill tray; only the fade moves, never the layout
    val clear by animateDpAsState(if (tray) 176.dp else 124.dp, tween(250), label = "clear")
    Box(Modifier.fillMaxSize().statusPad().navigationBarsPadding()) {
        // the now bar frosts the panel scrolling under it, over the player background
        Column(Modifier.fillMaxSize().then(if (haze != null) Modifier.hazeSource(haze, zIndex = 1f) else Modifier)) {
            // back · "Queue" over "Playing from …" / "Lyrics"
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ic.Back, "Back to Now Playing", { ui.setPane("art") }, size = 48.dp, iconSize = 22.dp, tint = Color.White)
                Column(Modifier.padding(start = 2.dp).semantics(mergeDescendants = true) { heading() }) {
                    Txt(if (lyrics) "Lyrics" else "Queue", style = Type.bodyStrong.copy(fontSize = 17.sp, lineHeight = 22.sp), color = Color.White, weight = FontWeight.SemiBold, maxLines = 1)
                    if (!lyrics) Playback.from?.let { (_, name) ->
                        Txt("Playing from $name", style = Type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp), color = Color.White.copy(alpha = MutedText), maxLines = 1)
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
        // dark frost, like the player
        CompositionLocalProvider(com.wetune.ui.LocalPalette provides WT.c.asDark()) {
            NowBar(ui.t, ui.pane, onOpen = { ui.setPane("art") }, modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 12.dp), haze = haze, onTray = { tray = it })
        }
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

/** Sideways phone: the cover (or lyrics / queue) on the left, the lyric line and the controls on the right. */
@Composable
private fun LandscapePlayer(ui: PlayerUi, style: NowPlayingStyle, colors: NpColors, seekDrag: Float?, onSeekDrag: (Float?) -> Unit) {
    var audioOptions by remember { mutableStateOf(false) }
    var pipeline by remember { mutableStateOf(false) }
    val still = rememberReduceMotion()
    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val geo = NpGeometry(maxWidth.value, maxHeight.value, 0f, 0f, 0f, 0f, maxHeight.value, maxHeight.value, maxHeight.value, 0f)
            BackgroundLayer(if (style.background.kind == BgKind.Extend) style.background.copy(kind = BgKind.Blur) else style.background, colors, ui.art, geo, ui.playing, still, style.motion.ambient && !still)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = TextScrim))) // the scrim the contrast solver counts on
        }
        Row(Modifier.fillMaxSize().statusPad().navigationBarsPadding().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                when (ui.pane) {
                    "lyrics" -> LyricsView(ui.t, ui.pos)
                    "queue" -> QueueView()
                    else -> CoverImage(ui, ui.t, Modifier.fillMaxSize().shadow(24.dp, RoundedCornerShape(18.dp), ambientColor = colors.shadow, spotColor = colors.shadow).clip(RoundedCornerShape(18.dp)))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (ui.pane != "lyrics" && style.layout.lyrics != InlineLyrics.Off) LyricLine(ui, seekDrag?.let { (it * ui.duration).toLong() }, InlineLyrics.Line)
                ControlsLayer(
                    style.controls.copy(kind = ControlsKind.Classic, volume = false, playSize = minOf(style.controls.playSize, 64)), colors, ui,
                    NpActions({ audioOptions = true }, { pipeline = true }, onSeekDrag) { PlayerMenu(ui) { pipeline = true } },
                    Modifier.weight(1f),
                )
            }
        }
    }
    if (audioOptions) AudioOptionsSheet(ui.t, onPipeline = { audioOptions = false; pipeline = true }) { audioOptions = false }
    if (pipeline) PipelineSheet(ui.t) { pipeline = false }
}

/**
 * The line being sung right now, above the seek bar; tap for the full lyrics. Between sung lines it shows pulsing
 * notes. While you drag the seek bar ([previewMs]) it shows the line you'd land on.
 */
@Composable
private fun LyricLine(ui: PlayerUi, previewMs: Long?, mode: InlineLyrics) {
    val loaded = rememberLyrics(ui.t) // null while the new song's lyrics load
    val lines = loaded.orEmpty()
    // while loading keep the line's space as it was, then open or close it once we know; on every song change the
    // title, heart and ⋯ above it glide instead of jumping
    var synced by remember { mutableStateOf(lines.isNotEmpty() && lines.first().ms >= 0) } // no animation when the player opens
    if (loaded != null) synced = lines.isNotEmpty() && lines.first().ms >= 0
    if (mode == InlineLyrics.Off) synced = false
    AnimatedVisibility(
        synced,
        enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 300f), expandFrom = Alignment.Top) + fadeIn(tween(300, delayMillis = 120)),
        exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 300f), shrinkTowards = Alignment.Top) + fadeOut(tween(180)),
    ) {
        // empty while loading or closing: a blank of the same height, so the line has something to shrink
        // (the three-line style keeps its full height while the next song's lyrics load, so nothing shrinks and regrows)
        if (mode == InlineLyrics.Stack) StackLines(ui, lines, previewMs) else LyricLineContent(ui, lines, previewMs)
    }
}

@Composable
private fun LyricLineContent(ui: PlayerUi, lines: List<Lyrics.Line>, previewMs: Long?) {
    // the line index changes a few times a minute: only that recomposes, not every position tick
    val i by remember(lines, previewMs, ui.t.id) { derivedStateOf { currentLine(lines, previewMs ?: ui.pos, Lyrics.offset(ui.t.id)) } }
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
            InlineSung(sung, shownLine, { ui.pos + Lyrics.offset(ui.t.id) }, inlineStyle, Modifier.weight(1f, fill = false))
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

/** The one-line lyric above the seek bar, filling in letter by letter when the lyrics are timed per word. */
@Composable
private fun InlineSung(line: Lyrics.Line, shown: String, pos: () -> Long, style: androidx.compose.ui.text.TextStyle, modifier: Modifier, tint: Color = Color.White) {
    val words = remember(line, shown) { wordsOf(line, shown) }
        ?: return Txt(shown, modifier, style = style, color = tint, maxLines = 2)
    KaraokeText(words, smoothPosition(pos() + 150), style, lit = tint, dim = Color.White.copy(alpha = 0.45f), modifier = modifier, maxLines = 2)
}

/**
 * Three-line inline lyrics: the line just sung and the next one dimmed around the current one, centered; each new
 * line slides the stack up. Tap to open the full lyrics.
 */
@Composable
private fun StackLines(ui: PlayerUi, lines: List<Lyrics.Line>, previewMs: Long?) {
    // the line index changes a few times a minute: only that recomposes, not every position tick
    val i by remember(lines, previewMs, ui.t.id) { derivedStateOf { currentLine(lines, previewMs ?: ui.pos, Lyrics.offset(ui.t.id)) } }
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
                InlineSung(sung, cur, { ui.pos + Lyrics.offset(ui.t.id) }, main, Modifier.fillMaxWidth().padding(vertical = 4.dp))
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
