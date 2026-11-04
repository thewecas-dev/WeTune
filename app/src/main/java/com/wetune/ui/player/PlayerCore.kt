package com.wetune.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.wetune.Settings
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.wetune.Lyrics
import com.wetune.Playback
import com.wetune.rememberPosition
import com.wetune.sources.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- Colors from the cover ----

/** [band] is the cover's vibrant color (app accent); [sea] is a dark muted one (player backdrop). */
data class ArtPalette(val band: Color, val sea: Color)

val SunsetPalette = ArtPalette(Color(0xFFE0613F), Color(0xFF123445))

/** The current song's cover colors, shared by the player and the app-wide accent. */
object NowColors {
    var palette by mutableStateOf<ArtPalette?>(null)
}

internal suspend fun artPalette(ctx: Context, url: String?): ArtPalette? = withContext(Dispatchers.IO) {
    if (url == null) return@withContext null
    val result = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).size(160).build())
    val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
    val p = Palette.from(bitmap).generate()
    fun c(s: Palette.Swatch?) = s?.rgb?.let { Color(it) }
    val band = c(p.vibrantSwatch) ?: c(p.dominantSwatch) ?: return@withContext null
    val deep = c(p.darkVibrantSwatch) ?: lerp(band, Color.Black, 0.35f)
    var sea = c(p.darkMutedSwatch) ?: lerp(deep, Color.Black, 0.45f)
    if (sea.luminance() > 0.12f) sea = lerp(sea, Color.Black, 0.45f) // backdrops must stay dark enough for white text
    ArtPalette(band, sea)
}

// ---- Player state ----

class PlayerUi(
    val t: Track,
    val pal: ArtPalette,
    val pos: Long,
    val duration: Long,
    val pane: String,
    val setPane: (String) -> Unit,
    val close: () -> Unit,
    val openSleep: () -> Unit,
    /** Closes the player and searches for [query] (tap on the artist or album). */
    val search: (String) -> Unit,
    /** Closes the player and opens an artist or album page. */
    val open: (com.wetune.sources.Card) -> Unit = {},
) {
    val fraction: Float get() = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
    fun seekTo(f: Float) = Playback.seek((f.coerceIn(0f, 1f) * duration).toLong())
    fun toggle(p: String) = setPane(if (pane == p) "art" else p)
}

@Composable
fun PlayerScreen(onClose: () -> Unit, onSearch: (String) -> Unit, onOpen: (com.wetune.sources.Card) -> Unit = {}) {
    // the status bar (clock, notifications) steps away while the player is open; a swipe from the top shows it for a moment
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Settings.npHideStatus) {
        val window = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()?.window
        val bars = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        if (Settings.npHideStatus && bars != null) {
            bars.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
        onDispose { bars?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars()) }
    }
    val t = Playback.current ?: return
    val pal = NowColors.palette ?: SunsetPalette // computed once per song in AppUi
    var pane by rememberSaveable { mutableStateOf("art") }
    var sleep by remember { mutableStateOf(false) }
    val pos by rememberPosition()
    val ui = PlayerUi(t, pal, pos, Playback.duration().takeIf { it > 0 } ?: t.durationMs, pane, { pane = it }, onClose, { sleep = true }, onSearch, onOpen)

    // Swipe down: the player follows the finger and closes after a short pull (~110 dp) or any quick downward flick;
    // otherwise it springs back.
    val drag = remember { Animatable(0f) }
    val closeAfter = with(LocalDensity.current) { 110.dp.toPx() }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.fillMaxSize().graphicsLayer { translationY = drag.value; alpha = 1f - (drag.value / size.height * 0.6f).coerceIn(0f, 0.6f) }
            .background(Color.Black)
            .draggable(
                rememberDraggableState { dy -> scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) } },
                Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (drag.value > closeAfter || velocity > 900f) onClose() else drag.animateTo(0f, spring(dampingRatio = 0.8f))
                },
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) { GlassPlayer(ui) }
    }
    if (sleep) SleepSheet { sleep = false }
}

// ---- Shared parts ----

val Dim: Color @Composable get() = LocalContentColor.current.copy(alpha = 0.65f)

@Composable
fun Cover(t: Track, modifier: Modifier, alpha: Float = 1f) {
    AsyncImage(t.art, null, modifier, contentScale = ContentScale.Crop, alpha = alpha)
}

/** All lyrics versions for [t]: from the session cache when already fetched, so reopening never refetches. */
@Composable
fun rememberVersions(t: Track): List<Lyrics.Version>? {
    // tagged with the song id, so after a song change the previous song's lyrics are never shown while the new ones load
    val s = produceState(Lyrics.cached(t.id)?.let { t.id to it }, t.id) {
        value = t.id to (Lyrics.cached(t.id) ?: withContext(Dispatchers.IO) { runCatching { Lyrics.versions(t) }.getOrDefault(emptyList()) })
    }.value
    return s?.takeIf { it.first == t.id }?.second ?: Lyrics.cached(t.id)
}

/** The chosen version's lines (empty when none were found; null while loading). */
@Composable
fun rememberLyrics(t: Track): List<Lyrics.Line>? =
    rememberVersions(t)?.let { v -> (v.getOrNull(Lyrics.choice[t.id] ?: 0) ?: v.firstOrNull())?.lines.orEmpty() }

/** Index of the line being sung at [pos]; [offsetMs] shifts the lyrics earlier (+) or later (−). */
fun currentLine(lines: List<Lyrics.Line>, pos: Long, offsetMs: Long = 0) =
    if (lines.firstOrNull()?.let { it.ms >= 0 } == true) lines.indexOfLast { it.ms <= pos + 300 + offsetMs } else -1

/** Latin letters for a lyric in another script (Tamil, Hindi, Telugu…); null when it's already Latin or the phone can't. */
private val romanizer by lazy {
    if (android.os.Build.VERSION.SDK_INT >= 29) runCatching { android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII") }.getOrNull() else null
}

fun romanize(text: String): String? {
    if (text.all { it.code < 0x250 }) return null
    // Hindi and Tamil the way lyrics are usually typed; any other script through ICU
    val pre = com.wetune.Roman.convert(text)
    val out = if (pre.all { it.code < 0x250 }) pre else runCatching { romanizer?.transliterate(pre) }.getOrNull() ?: pre
    return out.takeIf { it.isNotBlank() && it != text }
}

@Composable
fun LyricsView(t: Track, pos: Long, modifier: Modifier = Modifier.fillMaxSize()) {
    val lines = rememberLyrics(t) ?: return Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { Spinner(color = LocalContentColor.current) }
    if (lines.isEmpty()) return Txt("No lyrics found for this song", Modifier.padding(24.dp), color = Dim)
    val synced = lines.first().ms >= 0
    val current = currentLine(lines, pos, Lyrics.offset(t.id))
    val state = rememberLazyListState()
    // how the panel moves between lines (Settings → Appearance → Lyrics panel): glide | fade | instant
    val motion = Settings.lyricsMotion
    val align = when (Settings.lyricsAlign) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start }
    val originX = when (Settings.lyricsAlign) { "center" -> 0.5f; "end" -> 1f; else -> 0f }
    val size = Settings.lyricsSize
    val lineStyle = TextStyle(fontSize = size.sp, lineHeight = (size * Settings.lyricsLeading / 100f).sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, textAlign = align)
    LaunchedEffect(current) {
        if (current < 0) return@LaunchedEffect
        val target = (current - 2).coerceAtLeast(0)
        if (motion == "instant") state.scrollToItem(target) else state.animateScrollToItem(target)
    }
    val c = LocalContentColor.current
    val canRomanize = remember(lines) { lines.any { romanize(it.text) != null } }
    Box(modifier) {
        // soft fade at the top and bottom edges so lines drift in and out instead of being cut
        LazyColumn(
            Modifier.fillMaxSize().graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                },
            state, contentPadding = PaddingValues(top = 60.dp, bottom = 120.dp),
        ) {
            itemsIndexed(lines) { i, line ->
                val active = !synced || i == current
                // glide: the sung line grows a little; fade: lines only brighten and dim; instant: no animation at all
                val grow by animateFloatAsState(if (motion != "glide" || (active && synced)) 1f else 0.94f, spring(dampingRatio = 0.8f), label = "grow")
                val shade = if (active) 1f else (0.42f - 0.06f * kotlin.math.abs(i - current).coerceAtMost(4)).coerceAtLeast(0.18f)
                val fade by animateFloatAsState(shade, if (motion == "instant") androidx.compose.animation.core.snap() else tween(320), label = "fade")
                Column(
                    Modifier.fillMaxWidth().then(if (synced) Modifier.pressable(scale = 0.98f) { Playback.seek(line.ms) } else Modifier)
                        .graphicsLayer { scaleX = grow; scaleY = grow; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(originX, 0.5f) }
                        .padding(vertical = 10.dp),
                ) {
                    // the line being sung fills in letter by letter (only when the lyrics provider timed it word by word)
                    if (synced && i == current && Settings.lyricsWords && line.text.isNotBlank()) SungLine(line, pos + Lyrics.offset(t.id), lineStyle, c)
                    else Txt(
                        line.text.ifBlank { "♪" }, Modifier.fillMaxWidth(),
                        style = lineStyle,
                        // lines further from the current one fade more (a per-line blur was too heavy to scroll smoothly)
                        color = c.copy(alpha = fade),
                    )
                    if (Settings.lyricsRoman) romanize(line.text)?.let {
                        Txt(it, Modifier.fillMaxWidth().padding(top = 4.dp), style = Type.body.copy(fontSize = (size * 0.57f).sp, lineHeight = (size * 0.75f).sp, textAlign = align), color = c.copy(alpha = if (active) 0.75f else 0.3f))
                    }
                }
            }
        }
        // tools: which lyrics version, timing offset, and Latin letters
        Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LyricsSourceButton(t)
            if (synced) LyricsOffsetButton(t)
            if (canRomanize) ToolPill(Ic.Latin, "Show lyrics in Latin letters", Settings.lyricsRoman) { Settings.lyricsRoman = !Settings.lyricsRoman }
        }
    }
}

/** Up next: tap to jump, long-press and drag to reorder, swipe sideways to remove. */
@Composable
fun QueueView(modifier: Modifier = Modifier.fillMaxSize()) {
    val c = LocalContentColor.current
    val state = rememberLazyListState(Playback.index)
    val rowH = 62.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragging by remember { mutableStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }
    Column(modifier) {
        // Up next · shuffle · repeat (off → all → one, one button) · infinite (keeps adding similar songs)
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Up next", style = Type.headline)
                if (Settings.autoplay) Txt("Similar songs keep coming", style = Type.caption.copy(fontSize = 12.sp), color = c.copy(alpha = 0.6f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolPill(Ic.Shuffle, if (Playback.shuffle) "Shuffle on" else "Shuffle off", Playback.shuffle) { Playback.toggleShuffle() }
                val rep = Playback.repeat
                ToolPill(
                    if (rep == Player.REPEAT_MODE_ONE) Ic.RepeatOne else Ic.Repeat,
                    when (rep) { Player.REPEAT_MODE_ALL -> "Repeat all"; Player.REPEAT_MODE_ONE -> "Repeat this song"; else -> "Repeat off" },
                    rep != Player.REPEAT_MODE_OFF,
                ) { Playback.cycleRepeat() }
                ToolPill(Ic.Infinite, if (Settings.autoplay) "Infinite on: similar songs keep coming" else "Infinite off", Settings.autoplay) { Settings.autoplay = !Settings.autoplay }
            }
        }
        // stable keys (a song's id, numbered when it's queued twice) so moves animate instead of redrawing in place
        val keys = remember(Playback.queue) { val seen = HashMap<String, Int>(); Playback.queue.map { t -> val n = seen.merge(t.id, 1, Int::plus)!!; "${t.id}#$n" } }
        val target = if (dragging >= 0) (dragging + (dragY / rowPx).roundToInt()).coerceIn(0, Playback.queue.lastIndex) else -1
        LazyColumn(Modifier.fillMaxSize(), state) {
            itemsIndexed(Playback.queue, key = { i, _ -> keys.getOrElse(i) { "$i" } }) { i, t ->
                val current = i == Playback.index
                var swipe by remember { mutableFloatStateOf(0f) }
                val lifted = dragging == i
                // while a song is dragged, the ones it passes slide out of its way
                val make = when {
                    dragging < 0 || lifted -> 0f
                    i in (dragging + 1)..target -> -rowPx
                    i in target until dragging -> rowPx
                    else -> 0f
                }
                val shift by animateFloatAsState(make, spring(dampingRatio = 0.85f, stiffness = 500f), label = "shift")
                val lift by animateFloatAsState(if (lifted) 1f else 0f, spring(dampingRatio = 0.7f), label = "lift")
                Row(
                    Modifier.fillMaxWidth().height(rowH)
                        .then(if (lifted || dragging >= 0) Modifier else Modifier.animateItem())
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            translationX = swipe
                            translationY = if (lifted) dragY else shift
                            alpha = 1f - (kotlin.math.abs(swipe) / size.width).coerceIn(0f, 0.7f)
                            scaleX = 1f + 0.03f * lift; scaleY = 1f + 0.03f * lift
                            shadowElevation = 24f * lift
                            shape = RoundedCornerShape(18.dp); clip = true
                        }
                        .background(if (current || lifted) c.copy(alpha = if (lifted) 0.16f else 0.12f) else Color.Transparent)
                        .pointerInput(i) {
                            detectHorizontalDragGestures(
                                onDragEnd = { if (!current && kotlin.math.abs(swipe) > size.width * 0.35f) Playback.remove(i); swipe = 0f },
                                onDragCancel = { swipe = 0f },
                            ) { ch, dx -> if (!current) { ch.consume(); swipe += dx } }
                        }
                        .pressable(scale = 0.98f) { Playback.skipTo(i) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // drag handle, left of the cover: grab and move up or down
                    Box(
                        Modifier.size(40.dp, rowH).pointerInput(i) {
                            detectVerticalDragGestures(
                                onDragStart = { dragging = i; dragY = 0f },
                                onDragEnd = {
                                    val to = (i + (dragY / rowPx).roundToInt()).coerceIn(0, Playback.queue.size - 1)
                                    if (to != i) Playback.move(i, to)
                                    dragging = -1; dragY = 0f
                                },
                                onDragCancel = { dragging = -1; dragY = 0f },
                            ) { ch, dy -> ch.consume(); dragY += dy }
                        }.semantics { contentDescription = "Drag to reorder ${t.title}" },
                        Alignment.Center,
                    ) { Icon(Ic.Grip, null, size = 20.dp, tint = c.copy(alpha = if (lifted) 0.9f else 0.45f)) }
                    Art(t.art, Modifier.size(46.dp), RoundedCornerShape(10.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Txt(t.title, style = Type.bodyStrong, maxLines = 1)
                        Txt(t.artist, style = Type.caption, color = c.copy(alpha = 0.6f), maxLines = 1)
                    }
                    if (current) Equalizer(Playback.playing, c, Modifier.padding(end = 4.dp))
                    // options for this song: play next, radio, like, playlist, download… and remove from the queue
                    TrackMenu(t, onRemove = if (current) null else ({ Playback.remove(i) }), tint = c, removeLabel = "Remove from queue")
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun SleepSheet(onDismiss: () -> Unit) {
    Sheet(
        "Sleep timer", onDismiss,
        actions = {
            Btn("Turn off", { Playback.sleep(0); onDismiss() }, kind = BtnKind.Ghost)
            Btn("Cancel", onDismiss, kind = BtnKind.Secondary)
        },
    ) {
        listOf(15, 30, 45, 60, 90).forEach { m ->
            Txt("$m minutes", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressable(scale = 0.98f) { Playback.sleep(m); onDismiss() }.padding(14.dp), style = Type.bodyStrong)
        }
    }
}

/** A small round tool button for the lyrics panel. */
@Composable
private fun ToolPill(icon: ImageVector, label: String, active: Boolean = false, text: String? = null, onClick: () -> Unit) {
    val c = LocalContentColor.current
    Row(
        Modifier.height(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.16f)).background(c.copy(alpha = if (active) 0.26f else 0.12f))
            .semantics { contentDescription = label; selected = active }.pressable(scale = 0.92f, onClick = onClick)
            .padding(horizontal = if (text == null) 9.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, size = 18.dp, tint = c)
        if (text != null) Txt(text, Modifier.padding(start = 6.dp), style = Type.caption.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"), weight = FontWeight.SemiBold)
    }
}

/** Pick another lyrics version LRCLIB has for this song (synced or plain, other uploads). */
@Composable
private fun LyricsSourceButton(t: Track) {
    val versions = rememberVersions(t).orEmpty()
    if (versions.size < 2) return
    var open by remember { mutableStateOf(false) }
    val chosen = Lyrics.choice[t.id] ?: 0
    Box {
        ToolPill(Ic.Versions, "Choose lyrics version", open) { open = true }
        Menu(open, { open = false }, title = "Lyrics versions") {
            versions.forEachIndexed { i, v -> LyricsVersionRow(v, i == chosen) { Lyrics.choice[t.id] = i; open = false } }
        }
    }
}

/** Shift the lyrics earlier or later in half-second steps; remembered per song. */
@Composable
private fun LyricsOffsetButton(t: Track) {
    var open by remember { mutableStateOf(false) }
    val off = Lyrics.offset(t.id)
    fun fmt(ms: Long) = (if (ms > 0) "+" else if (ms < 0) "−" else "") + "%.1f s".format(kotlin.math.abs(ms) / 1000f)
    Box {
        ToolPill(Ic.Clock, "Lyrics timing", off != 0L, if (off != 0L) fmt(off) else null) { open = true }
        Menu(open, { open = false }, title = "Lyrics timing · ${fmt(off)}") {
            MenuItem("Earlier by 0.5 s", { Lyrics.setOffset(t.id, off + 500) }, Ic.ArrowUp)
            MenuItem("Later by 0.5 s", { Lyrics.setOffset(t.id, off - 500) }, Ic.ArrowDown)
            if (off != 0L) MenuItem("Reset", { Lyrics.setOffset(t.id, 0); open = false }, Ic.Refresh)
        }
    }
}

/** The line being sung in the lyrics panel, lit letter by letter as the words are sung. */
@Composable
private fun SungLine(line: Lyrics.Line, pos: Long, style: TextStyle, c: Color) {
    val words = remember(line) { wordsOf(line) }
        ?: return Txt(line.text, Modifier.fillMaxWidth(), style = style, color = c)
    KaraokeText(words, smoothPosition(pos + 150), style.copy(color = c), lit = c, dim = c.copy(alpha = 0.32f), modifier = Modifier.fillMaxWidth())
}

/** One lyrics version in the menu: the source first, then how it's timed (word by word, by line, or not), then the song. */
@Composable
private fun LyricsVersionRow(v: Lyrics.Version, chosen: Boolean, onClick: () -> Unit) {
    val c = WT.c
    val (icon, timing) = when {
        v.words -> Ic.Karaoke to "Word by word"
        v.synced -> Ic.Clock to "Synced by line"
        else -> Ic.Lyrics to "Not synced"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).pressable(scale = 0.98f, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(v.source, style = Type.bodyStrong, color = c.text, maxLines = 1)
                Icon(icon, timing, Modifier.padding(start = 8.dp), size = 16.dp, tint = if (v.synced) c.accent else c.muted)
            }
            Txt("$timing · ${v.title} · ${v.artist}", style = Type.caption, color = c.muted, maxLines = 1)
        }
        if (chosen) Icon(Ic.Check, "Chosen", Modifier.padding(start = 8.dp), size = 18.dp, tint = c.accent)
    }
}
