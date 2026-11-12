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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
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
/** [hue]: the cover's main hue (null for a black-and-white cover); accents use only this, at a fixed saturation and lightness. */
data class ArtPalette(val band: Color, val sea: Color, val hue: Float? = null)

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
    // the hue of the colour covering most of the cover (weighted by how colourful it is), so the accent clearly belongs to it
    val hue = p.swatches.filter { it.hsl[1] > 0.18f && it.hsl[2] in 0.12f..0.92f }.maxByOrNull { it.population * it.hsl[1] }?.hsl?.get(0)
    ArtPalette(band, sea, hue)
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
        suspend fun look() = Lyrics.cached(t.id) ?: withContext(Dispatchers.IO) { runCatching { Lyrics.versions(t) }.getOrDefault(emptyList()) }
        var found = look()
        // a lookup that hit a busy server isn't remembered; try twice more (the lyrics area stays as it was meanwhile)
        for (wait in listOf(3_000L, 6_000L)) {
            if (found.isNotEmpty() || Lyrics.cached(t.id) != null) break
            kotlinx.coroutines.delay(wait)
            found = look()
        }
        value = t.id to found
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
fun LyricsView(t: Track, pos: Long, modifier: Modifier = Modifier.fillMaxSize(), tools: Boolean = true) {
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
        // soft fade at the top and bottom edges so lines drift in and out instead of being cut (the pane fades its own edges,
        // clear of the now bar and its tray, and leaves room to scroll the last line above them)
        LazyColumn(
            if (!tools) Modifier.fillMaxSize() else Modifier.fillMaxSize().graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                },
            state, contentPadding = if (tools) PaddingValues(top = 60.dp, bottom = 120.dp) else PaddingValues(top = 24.dp, bottom = 320.dp),
        ) {
            itemsIndexed(lines) { i, line ->
                val active = !synced || i == current
                // glide: the sung line grows a little; fade: lines only brighten and dim; instant: no animation at all
                val grow by animateFloatAsState(if (motion != "glide" || (active && synced)) 1f else 0.94f, spring(dampingRatio = 0.8f), label = "grow")
                val shade = if (active) 1f else if (i < current) 0.22f else 0.38f // sung lines step back further than the ones to come
                val fade by animateFloatAsState(shade, if (motion == "instant") androidx.compose.animation.core.snap() else tween(320), label = "fade")
                Column(
                    Modifier.fillMaxWidth().then(if (synced) Modifier.pressable(scale = 0.98f) { Playback.seek(line.ms) } else Modifier)
                        .graphicsLayer { scaleX = grow; scaleY = grow; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(originX, 0.5f) }
                        .padding(vertical = 11.dp),
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
        // tools: which lyrics version, timing offset, and Latin letters (the pane has them in its now bar instead)
        if (tools) Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LyricsSourceButton(t)
            if (synced) LyricsOffsetButton(t)
            if (canRomanize) ToolPill(Ic.Latin, "Show lyrics in Latin letters", Settings.lyricsRoman) { Settings.lyricsRoman = !Settings.lyricsRoman }
        }
    }
}

/** Stable list keys for the queue (a song's id, numbered when it's queued twice), so moves animate instead of redrawing. */
private fun queueKeys(q: List<Track>): List<String> { val seen = HashMap<String, Int>(); return q.map { t -> "${t.id}#${seen.merge(t.id, 1, Int::plus)}" } }

/**
 * The queue ("calm list"): the songs already played folded at the top; Up next (tap to jump, drag the handle or
 * long-press a row to reorder, swipe left to remove); then what autoplay adds after it, steered by Radio / Same artist
 * (+ or swipe right to add one, or drag it up into Up next). The song playing now is in the now bar, not listed.
 */
@Composable
fun QueueView(modifier: Modifier = Modifier.fillMaxSize(), tools: Boolean = true) {
    val c = LocalContentColor.current
    // after a drop the list shows the new order at once; the player confirms the move a frame or more later
    var moved by remember { mutableStateOf<List<Track>?>(null) }
    val real = Playback.queue
    LaunchedEffect(real) { moved = null }
    val queue = moved ?: real
    val now = Playback.index
    val scope = rememberCoroutineScope()
    var settleKey by remember { mutableStateOf<String?>(null) } // the row gliding from where it was let go to its slot
    var settleY by remember { mutableFloatStateOf(0f) }
    val state = rememberLazyListState()
    val rowH = 60.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    val view = androidx.compose.ui.platform.LocalView.current
    var dragging by remember { mutableStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var showPlayed by rememberSaveable { mutableStateOf(false) }
    val keys = remember(queue) { queueKeys(queue) }
    // a song moves within its part (played, or to come); read fresh from Playback since gesture handlers outlive a composition
    fun part(i: Int) = if (i < Playback.index) 0..(Playback.index - 1) else (Playback.index + 1)..Playback.queue.lastIndex
    fun dropAt(i: Int) = (i + (dragY / rowPx).roundToInt()).coerceIn(part(i))
    val target = if (dragging >= 0) dropAt(dragging) else -1
    fun lift(i: Int) { dragging = i; dragY = 0f; view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) }
    fun drop(i: Int) {
        val to = dropAt(i)
        val list = if (to != i) queue.toMutableList().apply { add(to, removeAt(i)) } else queue
        // the row starts exactly where it was let go (relative to its new slot) and glides in
        settleKey = queueKeys(list)[to]
        settleY = dragY - (to - i) * rowPx
        if (to != i) { moved = list; Playback.move(i, to) }
        dragging = -1; dragY = 0f
        scope.launch {
            androidx.compose.animation.core.animate(settleY, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)) { v, _ -> settleY = v }
            settleKey = null
        }
    }
    fun cancel() { dragging = -1; dragY = 0f }

    // what autoplay will add: the same lookup the service makes when the queue runs low
    // shortcut: previews the seed's radio only; once that's used up the service switches to the playing song's radio, preview that too if it matters
    val cur = Playback.current
    val mix = Settings.autoplayMix
    val autoOn = Settings.autoplay && cur?.source == "yt"
    val seed = Settings.radioSeed.ifBlank { cur?.ref.orEmpty() }
    val found by produceState(emptyList<Track>(), autoOn, mix, if (mix == "artist") cur?.artist else seed) {
        value = emptyList()
        if (!autoOn || cur == null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { if (mix == "artist") com.wetune.youtube.YouTube.byArtist(cur.artist) else com.wetune.youtube.YouTube.radio(seed) }.getOrDefault(emptyList())
        }
    }
    val inQueue = remember(queue) { queue.mapTo(HashSet()) { it.id } }
    val auto = remember(found, inQueue) { found.filter { it.id !in inQueue }.distinctBy { it.id }.take(5) }

    /** A suggestion let go after dragging it up: lands before the Up next row under it, else at the end of Up next. */
    fun dropSuggestion(t: Track, key: String, dy: Float) {
        if (dy > -rowPx / 2) return // barely moved: stays a suggestion
        val items = state.layoutInfo.visibleItemsInfo
        val me = items.firstOrNull { it.key == key }
        val y = (me?.offset ?: 0) + rowPx / 2 + dy
        val hit = items.firstOrNull { y >= it.offset && y < it.offset + it.size }?.key
        val at = queueKeys(Playback.queue).indexOf(hit)
        when {
            at > Playback.index -> Playback.insert(at, t)
            at >= 0 || hit == "played" || hit == "upnext" -> Playback.insert(Playback.index + 1, t) // above Up next: first in it
            else -> Playback.enqueue(t)
        }
    }

    val titleStyle = Type.bodyStrong.copy(fontSize = 15.sp, lineHeight = 20.sp)
    val subStyle = Type.caption.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")
    val headStyle = Type.headline.copy(fontSize = 20.sp, letterSpacing = (-0.2).sp)

    /** A drag handle at the end of a row: 48 dp wide, as tall as the row. */
    @Composable fun Handle(desc: String, lit: Boolean, drag: Modifier) =
        Box(Modifier.size(48.dp, rowH).then(drag).semantics { contentDescription = desc }, Alignment.Center) {
            Icon(Ic.Grip, null, size = 18.dp, tint = c.copy(alpha = if (lit) 0.9f else 0.35f))
        }

    @Composable fun LazyItemScope.QueueRow(i: Int, t: Track) {
        var swipe by remember { mutableFloatStateOf(0f) }
        val lifted = dragging == i
        val settling = settleKey != null && keys.getOrNull(i) == settleKey
        // while a song is dragged, the ones it passes slide out of its way
        val make = when {
            dragging < 0 || lifted -> 0f
            i in (dragging + 1)..target -> -rowPx
            i in target until dragging -> rowPx
            else -> 0f
        }
        val shift by animateFloatAsState(make, spring(dampingRatio = 0.85f, stiffness = 500f), label = "shift")
        val up by animateFloatAsState(if (lifted) 1f else 0f, spring(dampingRatio = 0.7f), label = "lift")
        val dim = if (i < now) 0.6f else 1f // played songs step back
        Row(
            Modifier.fillMaxWidth().height(rowH)
                // no placement animation while dragging or settling: the new order lands where the rows already are
                .then(if (dragging >= 0 || settleKey != null) Modifier else Modifier.animateItem())
                .zIndex(if (lifted || settling) 1f else 0f)
                .graphicsLayer {
                    translationX = swipe
                    translationY = when {
                        lifted -> dragY
                        settling -> settleY
                        dragging >= 0 -> shift
                        else -> 0f // after a drop the others are already in their new slots
                    }
                    alpha = (if (lifted) 1f else dim) * (1f - (kotlin.math.abs(swipe) / size.width).coerceIn(0f, 0.7f))
                    scaleX = 1f + 0.03f * up; scaleY = 1f + 0.03f * up
                    shadowElevation = 24f * up
                    shape = RoundedCornerShape(14.dp); clip = true
                }
                .background(c.copy(alpha = 0.14f * up))
                // swipe left to remove
                .pointerInput(i) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (swipe < -size.width * 0.35f) Playback.remove(i); swipe = 0f },
                        onDragCancel = { swipe = 0f },
                    ) { ch, dx -> ch.consume(); swipe = (swipe + dx).coerceAtMost(0f) }
                }
                // long-press anywhere on the row and drag to reorder
                .pointerInput(i) {
                    detectDragGesturesAfterLongPress({ lift(i) }, { drop(i) }, { cancel() }) { ch, d -> ch.consume(); dragY += d.y }
                }
                .semantics {
                    val p = part(i)
                    customActions = listOfNotNull(
                        if (i - 1 in p) CustomAccessibilityAction("Move up") { Playback.move(i, i - 1); true } else null,
                        if (i + 1 in p) CustomAccessibilityAction("Move down") { Playback.move(i, i + 1); true } else null,
                        CustomAccessibilityAction("Remove from queue") { Playback.remove(i); true },
                    )
                }
                .pressable(scale = 0.98f) { Playback.skipTo(i) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(t.art, Modifier.size(44.dp), RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Txt(t.title, style = titleStyle, weight = FontWeight.SemiBold, maxLines = 1)
                Txt(if (t.durationMs > 0) "${t.artist} · ${formatTime(t.durationMs)}" else t.artist, style = subStyle, color = c.copy(alpha = 0.55f), maxLines = 1)
            }
            Handle("Reorder ${t.title}", lifted, Modifier.pointerInput(i) {
                detectVerticalDragGestures({ lift(i) }, { drop(i) }, { cancel() }) { ch, dy -> ch.consume(); dragY += dy }
            })
        }
    }

    @Composable fun LazyItemScope.AutoRow(k: Int, t: Track) {
        val key = "auto:${t.id}"
        var swipe by remember { mutableFloatStateOf(0f) }
        var dy by remember { mutableFloatStateOf(0f) }
        var held by remember { mutableStateOf(false) }
        val up by animateFloatAsState(if (held) 1f else 0f, spring(dampingRatio = 0.7f), label = "lift")
        val fade = 0.9f - 0.1f * k // suggestions trail off
        Row(
            Modifier.fillMaxWidth().height(rowH).then(if (held) Modifier else Modifier.animateItem())
                .zIndex(if (held) 1f else 0f)
                .graphicsLayer {
                    translationX = swipe; translationY = dy
                    alpha = (fade + (1f - fade) * up) * (1f - (swipe / size.width).coerceIn(0f, 0.6f))
                    scaleX = 1f + 0.03f * up; scaleY = 1f + 0.03f * up
                    shadowElevation = 24f * up
                    shape = RoundedCornerShape(14.dp); clip = true
                }
                .background(c.copy(alpha = 0.14f * up))
                // swipe right to add it to Up next
                .pointerInput(t.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (swipe > size.width * 0.35f) Playback.enqueue(t); swipe = 0f },
                        onDragCancel = { swipe = 0f },
                    ) { ch, dx -> ch.consume(); swipe = (swipe + dx).coerceAtLeast(0f) }
                }
                .semantics { customActions = listOf(CustomAccessibilityAction("Play next") { Playback.playNext(t); true }) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(t.art, Modifier.size(44.dp), RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Txt(t.title, style = titleStyle, weight = FontWeight.SemiBold, maxLines = 1)
                Txt(t.artist, style = subStyle, color = c.copy(alpha = 0.55f), maxLines = 1)
            }
            IconBtn(Ic.Add, "Add ${t.title} to Up next", { Playback.enqueue(t) }, size = 48.dp, iconSize = 20.dp, tint = c.copy(alpha = 0.75f))
            Handle("Drag ${t.title} into Up next", held, Modifier.pointerInput(t.id) {
                detectVerticalDragGestures(
                    onDragStart = { held = true; dy = 0f; view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) },
                    onDragEnd = { dropSuggestion(t, key, dy); held = false; dy = 0f },
                    onDragCancel = { held = false; dy = 0f },
                ) { ch, d -> ch.consume(); dy += d }
            })
        }
    }

    LazyColumn(modifier, state, contentPadding = PaddingValues(bottom = if (tools) 16.dp else 300.dp)) {
        if (now > 0) item("played") {
            val turn by animateFloatAsState(if (showPlayed) 180f else 0f, tween(250), label = "turn")
            Row(
                Modifier.height(48.dp)
                    .semantics { contentDescription = "${if (showPlayed) "Hide" else "Show"} $now played ${if (now == 1) "song" else "songs"}"; stateDescription = if (showPlayed) "expanded" else "collapsed" }
                    .pressable(scale = 0.97f) { showPlayed = !showPlayed },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Down, null, Modifier.graphicsLayer { rotationZ = turn }, size = 18.dp, tint = c.copy(alpha = 0.6f))
                Txt("$now played", Modifier.padding(start = 6.dp).clearAndSetSemantics {}, style = Type.caption.copy(fontSize = 13.sp), color = c.copy(alpha = 0.6f), weight = FontWeight.Medium)
            }
        }
        if (showPlayed) items(now.coerceAtMost(queue.size), key = { keys[it] }) { i -> QueueRow(i, queue[i]) }
        item("upnext") {
            val next = queue.drop(now + 1)
            val min = next.sumOf { it.durationMs } / 60_000
            Column {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("Up next", Modifier.weight(1f), style = headStyle, weight = FontWeight.SemiBold)
                    Txt(
                        "${next.size} ${if (next.size == 1) "song" else "songs"}" + (if (min > 0) " · " + (if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min") else ""),
                        style = Type.caption.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"), color = c.copy(alpha = 0.55f), maxLines = 1,
                    )
                }
                // sideways phone: no now bar, so shuffle / repeat / autoplay live here
                if (tools) Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolPill(Ic.Shuffle, if (Playback.shuffle) "Shuffle on" else "Shuffle off", Playback.shuffle) { Playback.toggleShuffle() }
                    val rep = Playback.repeat
                    ToolPill(
                        if (rep == Player.REPEAT_MODE_ONE) Ic.RepeatOne else Ic.Repeat,
                        when (rep) { Player.REPEAT_MODE_ALL -> "Repeat all"; Player.REPEAT_MODE_ONE -> "Repeat this song"; else -> "Repeat off" },
                        rep != Player.REPEAT_MODE_OFF,
                    ) { Playback.cycleRepeat() }
                    ToolPill(Ic.Infinite, if (Settings.autoplay) "Autoplay on: similar songs keep coming" else "Autoplay off", Settings.autoplay) { Settings.autoplay = !Settings.autoplay }
                }
            }
        }
        items((queue.size - now - 1).coerceAtLeast(0), key = { keys[now + 1 + it] }) { k -> QueueRow(now + 1 + k, queue[now + 1 + k]) }
        if (autoOn) {
            item("autoplay") {
                val accent by androidx.compose.animation.animateColorAsState(NowColors.palette?.hue?.let { hueAccent(it, dark = true) } ?: WT.c.accent, tween(650), label = "autoAccent")
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.Infinite, null, size = 18.dp, tint = accent)
                        Txt("Autoplay", Modifier.weight(1f).padding(start = 8.dp), style = headStyle, weight = FontWeight.SemiBold)
                        Txt("after your queue", style = Type.caption.copy(fontSize = 12.sp), color = c.copy(alpha = 0.55f), maxLines = 1)
                    }
                    // what autoplay adds: YouTube's radio for the song, or more by the same artist
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                        listOf("radio" to "Radio", "artist" to "Same artist").forEach { (k, label) ->
                            val on = mix == k
                            val bg by androidx.compose.animation.animateColorAsState(if (on) c else c.copy(alpha = 0.1f), tween(200), label = "chip")
                            Box(
                                Modifier.height(44.dp).semantics { selected = on; contentDescription = "Autoplay: $label" }
                                    .pressable(scale = 0.94f, role = androidx.compose.ui.semantics.Role.RadioButton) { Settings.autoplayMix = k },
                                Alignment.Center,
                            ) {
                                Box(
                                    Modifier.height(34.dp).clip(CircleShape).background(bg)
                                        .padding(horizontal = 14.dp),
                                    Alignment.Center,
                                ) {
                                    Txt(label, Modifier.clearAndSetSemantics {}, style = Type.caption.copy(fontSize = 13.sp), color = if (on) Color(0xFF141414) else c.copy(alpha = 0.8f), weight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            itemsIndexed(auto, key = { _, t -> "auto:${t.id}" }) { k, t -> AutoRow(k, t) }
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
        if (text != null) Txt(text, Modifier.padding(start = 6.dp), style = Type.caption.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"), color = c, weight = FontWeight.SemiBold)
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

/** How a lyrics version is timed, as an icon and words: word by word (karaoke), by line, or not at all. */
internal fun lyricsKind(v: Lyrics.Version): Pair<ImageVector, String> = when {
    v.words -> Ic.Karaoke to "Word by word"
    v.synced -> Ic.LinesSynced to "Synced by line"
    else -> Ic.Lyrics to "Not synced"
}

/** One lyrics version in the menu: the source first, then how it's timed (word by word, by line, or not), then the song. */
@Composable
internal fun LyricsVersionRow(v: Lyrics.Version, chosen: Boolean, onClick: () -> Unit) {
    val c = WT.c
    val (icon, timing) = lyricsKind(v)
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
