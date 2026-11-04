@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.wetune.ui

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.semantics.contentDescription
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
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // where the cover may start: under the status bar or camera cutout, the grabber and "Playing from", plus a gap
    var headerH by remember { mutableStateOf(64.dp) }
    val cutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val clearTop = maxOf(statusTop, cutTop) + headerH + 8.dp
    var seekDrag by remember { mutableStateOf<Float?>(null) } // while dragging the seek bar: where you'd land
    var coverMenu by remember { mutableStateOf(false) }
    var pipeline by remember { mutableStateOf(false) }
    var audioOptions by remember { mutableStateOf(false) } // tap the quality tag: versions + sources, then the pipeline
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(lerp(ui.pal.sea, Color.Black, 0.35f))) {
        if (maxWidth > maxHeight) return@BoxWithConstraints LandscapePlayer(ui, seekDrag) { seekDrag = it }
        val w = maxWidth
        // Title, controls and corner buttons live in the bottom 40% (never less than they need); the cover owns the rest.
        // The controls never move. With no lyric line (none for this song, or switched off) only the cover and background
        // slide down into the empty lyrics space.
        val lyricLines = rememberLyrics(ui.t)
        var hasLyric by remember { mutableStateOf(true) }
        if (lyricLines != null) hasLyric = lyricLines.isNotEmpty() && lyricLines.first().ms >= 0
        val lower by animateDpAsState(if (hasLyric && Settings.showInline) 0.dp else 56.dp, tween(450), label = "lower")
        val controlsH = maxOf(maxHeight * 0.45f, 400.dp)
        // with lyrics showing the cover has less room, so Bleeding edge shows it whole (no side crop); without, the usual slight trim
        val sideScale by animateFloatAsState(if (hasLyric && Settings.showInline) 1f else SideScale, tween(450), label = "crop")
        val split = maxHeight - controlsH + lower // where the cover area ends (the controls block itself stays fixed)

        // A new song crossfades in; dragging sideways moves only the cover (not the background) to show the skip coming.
        Crossfade(ui.t, Modifier.blur(paneBlur, BlurredEdgeTreatment.Rectangle), animationSpec = tween(650), label = "song") { t ->
            Box(Modifier.fillMaxSize()) {
                if (Settings.npStyle == "bleed") CoverBackdrop(ui.pal, w, split, clearTop, coverAlpha, t, sideScale) { swipe.value * 0.5f }
                else StyledBackdrop(Settings.npStyle, ui.pal, t, split)
            }
        }
        if (paneDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = paneDim)))

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
        if (open) ImmersivePane(ui, onQuality = { audioOptions = true }) { seekDrag = it } else Box(Modifier.fillMaxSize()) {

        // Top 60%: the corner buttons. On the cover: swipe to skip, long-press for a menu.
        Column(Modifier.fillMaxWidth().height(split).statusBarsPadding().displayCutoutPadding().padding(start = 16.dp, end = 16.dp)) {
          // the header (grabber + Playing from) is measured so the cover never starts underneath it
          Column(Modifier.onSizeChanged { headerH = with(density) { it.height.toDp() } }) {
            // grabber: tap to close, or drag down (the whole player follows a downward drag)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(scale = 0.95f, onClick = ui.close)
                    .semantics { contentDescription = "Close player" }.padding(top = 0.dp, bottom = 10.dp),
                Alignment.Center,
            ) { Box(Modifier.size(40.dp, 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f))) }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                // (no close button: swipe down or Back closes the player)
                // what the queue was started from: "PLAYING FROM PLAYLIST" over "Tamil Hits 2025"
                Column(Modifier.weight(1f).heightIn(min = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Playback.from?.let { (kind, name) ->
                        Txt("PLAYING FROM ${kind.uppercase()}", style = Type.overline.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.65f), maxLines = 1)
                        Txt(name, Modifier.basicMarquee(iterations = Int.MAX_VALUE), style = Type.caption.copy(fontSize = 13.sp), color = Color.White, weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
                    }
                }
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
                // classic, strips and disc frame the cover here (Bleeding edge draws it as part of the background)
                if (Settings.npStyle != "bleed") Crossfade(ui.t, animationSpec = tween(500), label = "cover") { StyledCover(Settings.npStyle, it, { swipe.value * 0.5f }) }
                Box(Modifier.align(Alignment.Center)) { CoverMenu(ui, coverMenu) { coverMenu = false } }
            }
        }

        // Bottom: title · lyric line · seek · times and quality · transport · volume · lyrics | sleep+output | queue · device.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(controlsH).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) {
            // lyrics (one line or three) centered in the free space between where the blur begins and the title;
            // they open and close inside this space, so nothing below them moves
            Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp), Alignment.Center) { LyricLine(ui, seekDrag?.let { (it * ui.duration).toLong() }) }
            // the controls: a fixed block pinned to the bottom
            TitleRow(ui) { pipeline = true }
            Spacer(Modifier.height(18.dp))
            GlassSeek(ui, onQuality = { audioOptions = true }) { seekDrag = it }
            Spacer(Modifier.height(22.dp))
            Transport()
            Spacer(Modifier.height(22.dp))
            if (Settings.showVolume) {
                VolumeRow()
                Spacer(Modifier.height(26.dp))
            }
            BottomBar(ui)
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
private fun TitleRow(ui: PlayerUi, onPipeline: (() -> Unit)? = null) {
    val lift = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 12f)
    val liked = Library.isLiked(ui.t)
    val view = androidx.compose.ui.platform.LocalView.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            // long titles scroll slowly; tap the title for its album, the artist for the artist
            Txt(
                ui.t.title, Modifier.shared("title", text = true).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000).pressable(scale = 0.98f) { ui.search(ui.t.album.ifBlank { ui.t.title }) },
                style = Type.title.copy(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, shadow = lift), maxLines = 1,
            )
            Txt(
                ui.t.artist, Modifier.shared("artist", text = true).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000).pressable(scale = 0.98f) { ui.search(ui.t.artist.substringBefore(",").trim()) },
                style = Type.body.copy(fontSize = 15.sp, shadow = lift), color = Color.White.copy(alpha = 0.7f), maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        SoftCircle(if (liked) Ic.Heart else Ic.HeartOutline, if (liked) "Remove from Liked" else "Like", size = 44) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            io { Library.toggleLike(ui.t) }
        }
        Spacer(Modifier.width(10.dp))
        PlayerMenu(ui, onPipeline)
    }
}

/** Previous · play/pause · next as large plain glyphs, no circle behind them. */
@Composable
private fun Transport(compact: Boolean = false) {
    val playing = Playback.playing
    val pulse by rememberInfiniteTransition(label = "buffer").animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "p")
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
        IconBtn(Ic.Rewind, "Previous", { Playback.prev() }, size = 64.dp, iconSize = if (compact) 34.dp else 40.dp, haptic = true)
        // while a song loads, the play glyph breathes
        IconBtn(
            if (playing) Ic.Pause else Ic.Play, if (Playback.buffering) "Loading" else if (playing) "Pause" else "Play", Playback::toggle,
            Modifier.shared("play").graphicsLayer { alpha = if (Playback.buffering) pulse else 1f }, size = 76.dp, iconSize = if (compact) 46.dp else 54.dp, haptic = true,
        )
        IconBtn(Ic.Forward, "Next", { Playback.next() }, size = 64.dp, iconSize = if (compact) 34.dp else 40.dp, haptic = true)
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

/** Lyrics · a pill with sleep timer and output device · queue. The output button shows the current device on tap. */
@Composable
private fun BottomBar(ui: PlayerUi) {
    val ctx = LocalContext.current
    var outputMenu by remember { mutableStateOf(false) }
    val sleeping = Playback.sleepEndsAt > 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconBtn(Ic.LyricsQuote, "Lyrics", { ui.toggle("lyrics") }, tint = Color.White.copy(alpha = if (ui.pane == "lyrics") 1f else 0.8f), background = if (ui.pane == "lyrics") Color.White.copy(alpha = 0.18f) else Color.Transparent)
            Row(Modifier.height(46.dp).clip(CircleShape).background(SoftBase).background(Color.White.copy(alpha = 0.12f)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp, 46.dp).background(if (sleeping) Color.White.copy(alpha = 0.14f) else Color.Transparent).pressable(scale = 0.92f, onClick = ui.openSleep).semantics { contentDescription = if (sleeping) "Sleep timer on" else "Sleep timer" }, Alignment.Center) {
                    Icon(Ic.Sleep, null, size = 21.dp, tint = Color.White)
                }
                Box(Modifier.size(1.dp, 22.dp).background(Color.White.copy(alpha = 0.18f)))
                Box {
                    Box(Modifier.size(56.dp, 46.dp).pressable(scale = 0.92f) { outputMenu = true }.semantics { contentDescription = "Where audio plays" }, Alignment.Center) {
                        Icon(Ic.Output, null, size = 21.dp, tint = Color.White)
                    }
                    Menu(outputMenu, { outputMenu = false }, title = "Playing on") {
                        val device = outputDevice(ctx).first
                        Txt(if (device == "Phone speaker") "This phone" else device.substringAfter(" · "), Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp), style = Type.bodyStrong)
                        MenuItem("Choose another device…", { outputMenu = false; openOutputSwitcher(ctx) }, Ic.Output)
                    }
                }
            }
            IconBtn(Ic.QueueList, "Queue", { ui.toggle("queue") }, tint = Color.White.copy(alpha = if (ui.pane == "queue") 1f else 0.8f), background = if (ui.pane == "queue") Color.White.copy(alpha = 0.18f) else Color.Transparent)
        }
    }
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
        Cover(ui.t, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }.blur(60.dp, BlurredEdgeTreatment.Unbounded), alpha = 0.8f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Transport(compact = true)
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
        if (Settings.inlineStyle == "stack" && lines.isNotEmpty()) StackLines(ui, lines, previewMs) else LyricLineContent(ui, lines, previewMs)
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
            Modifier.graphicsLayer {
                rotationX = 80f * roll // top tips back as it rises, bottom tips back as it arrives
                translationY = -roll * size.height * 0.6f
                cameraDistance = 10f * density
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (line == "") return@Row Spacer(Modifier.height(22.dp))
            // optional: the inline line in Latin letters for Tamil, Hindi, Telugu… (the full lyrics panel is unchanged)
        val shownLine = line?.let { if (Settings.romanInline) romanize(it) ?: it else it }
        val sung = lines.getOrNull(i)?.takeIf { it.text.trim() == line?.trim() } // this line's timing (not for one that's leaving)
        val inlineStyle = Type.body.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
        if (shownLine != null && sung != null && Settings.inlineWords && previewMs == null)
            InlineSung(sung, shownLine, ui.pos + Lyrics.offset(ui.t.id), inlineStyle, Modifier.weight(1f, fill = false))
        else if (shownLine != null) Txt(shownLine, Modifier.weight(1f, fill = false), style = inlineStyle, color = Color.White.copy(alpha = if (previewMs != null) 1f else 0.9f), maxLines = 2)
            else MusicBreak()
            Icon(Ic.Chevron, null, Modifier.padding(start = 6.dp), size = 14.dp, tint = Color.White.copy(alpha = 0.7f))
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
    Menu(open, onDismiss) {
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
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(SoftBase).background(Color.White.copy(alpha = if (active) 0.24f else 0.1f))
            .semantics { contentDescription = label; selected = active }
            .pressable(scale = 0.9f, onClick = onClick),
        Alignment.Center,
    ) { Icon(icon, null, size = 20.dp, tint = Color.White.copy(alpha = if (active) 1f else 0.85f)) }
}

/** ⋯ in the player: everything that isn't needed every second lives here. */
@Composable
private fun PlayerMenu(ui: PlayerUi, onPipeline: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    fun close(a: () -> Unit): () -> Unit = { open = false; a() }
    Box {
        SoftCircle(Ic.More, "More options") { open = true }
        Menu(open, { open = false }) {
            MenuItem("Add to playlist…", close { pick = true }, Ic.AddPlaylist)
            if (ui.t.source != "local") {
                val done = Library.downloads.any { it.id == ui.t.id }
                if (done) MenuItem("Remove download", close { Downloads.delete(ui.t) }, Ic.Delete)
                else MenuItem(if (ui.t.id in Downloads.active) "Downloading…" else "Download", close { Downloads.download(ui.t) }, Ic.Download, enabled = ui.t.id !in Downloads.active)
            }
            MenuItem("Play next", close { Playback.playNext(ui.t) }, Ic.PlayNext)
            if (ui.t.source == "yt") MenuItem("Start radio", close { Playback.startRadio(ui.t) }, Ic.Radio)
            MenuItem(if (Playback.shuffle) "Shuffle: on" else "Shuffle: off", { Playback.toggleShuffle() }, Ic.Shuffle)
            MenuItem(
                when (Playback.repeat) { androidx.media3.common.Player.REPEAT_MODE_ALL -> "Repeat: all"; androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repeat: this song"; else -> "Repeat: off" },
                { Playback.cycleRepeat() }, if (Playback.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) Ic.RepeatOne else Ic.Repeat,
            )
            GoToItems(ui, open) { a -> close(a)() }
            if (onPipeline != null) MenuItem("Audio pipeline", close(onPipeline), Ic.Pipeline)
        }
    }
    if (pick) PlaylistPicker(ui.t) { pick = false }
}

/** Thin track with a small dot; while dragging, a bubble shows the time you'd land on. Elapsed left, time left right. */
@Composable
private fun GlassSeek(ui: PlayerUi, onQuality: () -> Unit = {}, onSeekDrag: (Float?) -> Unit) {
    val onDrag = onSeekDrag
    var dragging by remember { mutableStateOf<Float?>(null) }
    var width by remember { mutableFloatStateOf(1f) }
    val shown = dragging ?: ui.fraction
    val density = LocalDensity.current
    // faint dots where each verse starts (a pause of 4 s+ in the synced lyrics), like chapter marks
    val lines = rememberLyrics(ui.t).orEmpty()
    val marks = remember(lines, ui.duration) {
        if (ui.duration <= 0 || lines.firstOrNull()?.ms?.let { it < 0 } != false) emptyList()
        else lines.zipWithNext().filter { (a, b) -> b.ms - a.ms >= 4_000 && b.text.isNotBlank() }.map { it.second.ms.toFloat() / ui.duration }.filter { it in 0.02f..0.98f }
    }
    Column(Modifier.fillMaxWidth().shared("seek")) {
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat() }) {
        Canvas(Modifier.matchParentSize()) { marks.forEach { drawCircle(Color.White.copy(alpha = 0.55f), 1.6.dp.toPx(), androidx.compose.ui.geometry.Offset(it * size.width, size.height / 2)) } }
        SeekSlider(
            shown, { dragging = it; onDrag(it) }, { dragging?.let(ui::seekTo); dragging = null; onDrag(null) },
            color = Color.White, thickness = 3.dp, thumb = true,
        )
        dragging?.let { f ->
            val bubbleW = with(density) { 60.dp.toPx() }
            Box(
                Modifier.offset { IntOffset((f * width - bubbleW / 2).coerceIn(0f, width - bubbleW).roundToInt(), with(density) { (-30).dp.roundToPx() }) }
                    .width(60.dp).clip(CircleShape).background(Color.White).padding(vertical = 4.dp),
                Alignment.Center,
            ) { Txt(formatTime((f * ui.duration).toLong()), style = TimeStyle, color = Color(0xFF1A1210), weight = FontWeight.SemiBold) }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        val at = (shown * ui.duration).toLong()
        Txt(formatTime(at), style = TimeStyle, color = Color.White.copy(alpha = 0.6f))
        // the stream quality, tiny, between the times; tap it for the whole audio pipeline
        Box(Modifier.weight(1f), Alignment.Center) { QualityTag(onQuality) }
        Txt("-" + formatTime(ui.duration - at), style = TimeStyle, color = Color.White.copy(alpha = 0.6f))
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
 * Lyrics or queue, immersive: the panel fills the screen; at the bottom a slim bar with title and artist,
 * play/pause and a button to close the panel, then the seek bar. Everything else steps aside.
 */
@Composable
private fun ImmersivePane(ui: PlayerUi, onQuality: () -> Unit, onSeekDrag: (Float?) -> Unit) {
    val playing = Playback.playing
    // swipe the bottom bar sideways to change song (the panel above keeps its own scrolling and swipes)
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 80.dp.toPx() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (ui.pane == "lyrics") LyricsView(ui.t, ui.pos) else QueueView(Modifier.fillMaxSize().padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.graphicsLayer { translationX = swipe.value * 0.6f; alpha = 1f - (kotlin.math.abs(swipe.value) / (limit * 4)).coerceIn(0f, 0.5f) }
                .pointerInput(ui.t.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when { swipe.value < -limit -> Playback.next(); swipe.value > limit -> Playback.prev() }
                            scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.8f)) }
                        },
                        onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                    ) { change, dx -> change.consume(); scope.launch { swipe.snapTo(swipe.value + dx) } }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(ui.t, Modifier.size(46.dp).shadow(8.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(ui.t.title, Modifier.shared("title", text = true).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000), style = Type.headline, weight = FontWeight.Bold, maxLines = 1)
                Txt(ui.t.artist, Modifier.shared("artist", text = true), style = Type.caption, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
            }
            IconBtn(if (playing) Ic.Pause else Ic.Play, if (playing) "Pause" else "Play", Playback::toggle, Modifier.shared("play"), size = 52.dp, iconSize = 30.dp, haptic = true)
            Spacer(Modifier.width(6.dp))
            SoftCircle(Ic.Collapse, if (ui.pane == "lyrics") "Close lyrics" else "Close queue", size = 44) { ui.setPane("art") }
        }
        Spacer(Modifier.height(10.dp))
        GlassSeek(ui, onQuality = onQuality, onSeekDrag = onSeekDrag)
    }
}

/** The scopes the normal and immersive layouts share, so tagged parts can glide between them. */
@OptIn(ExperimentalSharedTransitionApi::class)
private class SharedScopes(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

private val LocalShared = compositionLocalOf<SharedScopes?> { null }

/** Marks a part that exists in both layouts (title, artist, play, seek); it morphs instead of fading. No-op elsewhere. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.shared(key: String, text: Boolean = false): Modifier {
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
private fun InlineSung(line: Lyrics.Line, shown: String, pos: Long, style: androidx.compose.ui.text.TextStyle, modifier: Modifier) {
    val words = remember(line, shown) { wordsOf(line, shown) }
        ?: return Txt(shown, modifier, style = style, color = Color.White.copy(alpha = 0.9f), maxLines = 2)
    KaraokeText(words, smoothPosition(pos + 150), style, lit = Color.White, dim = Color.White.copy(alpha = 0.45f), modifier = modifier, maxLines = 2)
}

/**
 * Three-line inline lyrics: the line just sung and the next one dimmed around the current one, centered; each new
 * line slides the stack up. Tap to open the full lyrics.
 */
@Composable
private fun StackLines(ui: PlayerUi, lines: List<Lyrics.Line>, previewMs: Long?) {
    val i = currentLine(lines, previewMs ?: ui.pos, Lyrics.offset(ui.t.id))
    fun text(k: Int) = lines.getOrNull(k)?.text?.takeIf { it.isNotBlank() }?.let { if (Settings.romanInline) romanize(it) ?: it else it }
    val dim = Type.body.copy(fontSize = 17.sp, lineHeight = 27.sp, textAlign = TextAlign.Center)
    val main = Type.body.copy(fontSize = 21.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
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
            else Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), Alignment.Center) { MusicBreak() }
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
