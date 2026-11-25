package com.wetune.ui.player

import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wetune.Lyrics
import com.wetune.Playback
import com.wetune.Roman
import com.wetune.Settings
import com.wetune.rememberPosition
import com.wetune.sources.Track
import com.wetune.ui.Icons
import com.wetune.ui.MenuRow
import com.wetune.ui.Motion
import com.wetune.ui.Pill
import com.wetune.ui.Sheet
import com.wetune.ui.SheetTitle
import com.wetune.ui.Txt
import com.wetune.ui.Type
import com.wetune.ui.WT
import com.wetune.ui.press
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lyrics for the sheet ([big] = full screen). Synced lyrics follow the song: the sung line is bright, its neighbours dim,
 * the rest dimmer; the list glides to keep it near the top third. Only the line index is state the list recomposes on;
 * the sung line alone updates per frame (word-by-word fill).
 */
@Composable
fun LyricsPanel(t: Track, big: Boolean, modifier: Modifier = Modifier) {
    val c = WT.c
    val v = inlineLyricsFor(t)
    val style = if (material) pt(Type.title, if (big) 27 else 19, 600).copy(lineHeight = 1.2.em)
    else Type.title.copy(fontSize = if (big) 27.sp else 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(if (big) 800 else 700), letterSpacing = (if (big) -0.02 else -0.01).em, lineHeight = 1.22.em)
    Column(modifier.fillMaxSize()) {
        LyricsTools(t, v.orEmpty())
        if (v == null || v.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Txt(if (v == null) "Finding lyrics…" else "No lyrics for this song", Type.body, color = c.onSecondary)
        } else Lines(t, v, big, style, Modifier.weight(1f))
    }
}

@Composable
private fun Lines(t: Track, v: List<Lyrics.Version>, big: Boolean, style: TextStyle, modifier: Modifier) {
    val c = WT.c
    val lines = (v.getOrNull(Lyrics.choice[t.id] ?: 0) ?: v.first()).lines
    val synced = lines.firstOrNull()?.ms?.let { it >= 0 } == true
    val pos = rememberPosition()
    val current by remember(lines) { derivedStateOf { if (synced) lines.indexOfLast { it.ms <= pos.value + 300 + Lyrics.offset(t.id) } else -1 } }
    // opens at the sung line (animating there from the top built every line on the way: a stutter on opening)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - if (big) 2 else 1).coerceAtLeast(0))
    LaunchedEffect(current, big) { if (current >= 0) state.animateScrollToItem((current - if (big) 2 else 1).coerceAtLeast(0)) }
    val roman = Settings.lyricsRoman

    LazyColumn(
        modifier.fillMaxSize()
            // soft top/bottom edges so lines drift in and out instead of being cut
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val fade = Brush.verticalGradient(0f to Color.Transparent, 0.08f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent)
                onDrawWithContent { drawContent(); drawRect(fade, blendMode = BlendMode.DstIn) }
            },
        state = state,
        contentPadding = PaddingValues(top = 8.dp, bottom = if (big) 200.dp else 120.dp),
    ) {
        itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
            val target = when { !synced -> 1f; i == current -> 1f; i == current - 1 || i == current + 1 -> 0.32f; else -> 0.2f }
            val alpha by animateFloatAsState(target, Motion.push(), label = "line")
            Column(
                Modifier.fillMaxWidth().padding(vertical = if (big) 9.dp else 5.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .then(if (synced) Modifier.press("Play from this line", scale = 0.98f) { Playback.seek((line.ms - Lyrics.offset(t.id)).coerceAtLeast(0)) } else Modifier),
            ) {
                val words = line.words
                if (synced && i == current && Settings.lyricsWords && words != null) Karaoke(words, t.id, style)
                else BasicText(line.text, style = style.copy(color = c.on))
                if (roman) remember(line.text, Settings.romanKeep) { romanize(line.text) }?.let { Txt(it, Type.body, Modifier.padding(top = 2.dp), color = c.onSecondary, lines = 3) }
            }
        }
    }
}

/**
 * The sung line filling word by word, smoothly, from a position advanced every frame between the player's 250 ms
 * updates. Drawn twice, dim and bright, with the bright copy revealed by a clip that sweeps along the line: colouring
 * single letters would split syllables (a Hindi vowel sign drawn alone shows as a dotted circle). Only the draw phase
 * reads the position, so the fill never recomposes.
 * shortcut: the sweep runs left to right; right-to-left scripts fill in the wrong direction (they show plain lines today).
 */
@Composable
internal fun Karaoke(words: List<Lyrics.Word>, id: String, style: TextStyle, modifier: Modifier = Modifier, lines: Int = Int.MAX_VALUE) {
    val c = WT.c
    val pos = rememberPosition()
    val now = produceState(pos.value, Playback.playing) {
        var base = pos.value; var at = System.nanoTime()
        while (true) withFrameNanos { f ->
            if (pos.value != base) { base = pos.value; at = f }
            value = base + if (Playback.playing) ((f - at) / 1_000_000).coerceIn(0, 500) else 0
        }
    }
    val text = remember(words) { words.joinToString("") { it.text } }
    val starts = remember(words) { words.runningFold(0) { acc, w -> acc + w.text.length } }
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val clip = remember { androidx.compose.ui.graphics.Path() }
    val overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    Box(modifier) {
        BasicText(text, style = style.copy(color = c.on.copy(alpha = 0.4f)), onTextLayout = { layout = it }, overflow = overflow, maxLines = lines, softWrap = lines > 1)
        BasicText(
            text, style = style.copy(color = c.on), overflow = overflow, maxLines = lines, softWrap = lines > 1,
            modifier = Modifier.drawWithContent {
                val l = layout ?: return@drawWithContent
                val t = now.value + Lyrics.offset(id)
                val wi = words.indexOfLast { it.ms <= t }
                if (wi < 0 || text.isEmpty()) return@drawWithContent
                val w = words[wi]
                val s = starts[wi].coerceAtMost(text.length - 1)
                val e = (starts[wi] + w.text.trimEnd().length).coerceIn(s, text.length)
                val frac = ((t - w.ms).toFloat() / (w.endMs - w.ms).coerceAtLeast(1)).coerceIn(0f, 1f)
                val line = l.getLineForOffset(s)
                val xs = l.getHorizontalPosition(s, true)
                val xe = if (l.getLineForOffset((e - 1).coerceAtLeast(s)) == line) l.getHorizontalPosition(e, true) else l.getLineRight(line)
                val top = l.getLineTop(line)
                clip.reset()
                if (top > 0f) clip.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, top)) // lines already sung
                clip.addRect(androidx.compose.ui.geometry.Rect(0f, top, xs + (xe - xs) * frac, l.getLineBottom(line)))
                clipPath(clip) { this@drawWithContent.drawContent() }
            },
        )
    }
}

private val romanizer by lazy {
    if (android.os.Build.VERSION.SDK_INT >= 29) runCatching { android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII") }.getOrNull() else null
}

/** The scripts offered in Settings › Lyrics › "Never romanize" (key = [lyricScript]'s answer). */
val RomanScripts = listOf(
    "TAMIL" to "Tamil", "DEVANAGARI" to "Hindi, Marathi (Devanagari)", "TELUGU" to "Telugu", "KANNADA" to "Kannada",
    "MALAYALAM" to "Malayalam", "BENGALI" to "Bengali", "GURMUKHI" to "Punjabi (Gurmukhi)", "GUJARATI" to "Gujarati",
    "ARABIC" to "Arabic, Urdu", "HANGUL" to "Korean", "JAPANESE" to "Japanese", "CHINESE" to "Chinese",
    "CYRILLIC" to "Russian, Ukrainian (Cyrillic)", "GREEK" to "Greek", "HEBREW" to "Hebrew", "THAI" to "Thai",
)

/** The writing system of a lyric line: its first non-Latin letter's script; kana anywhere = Japanese, other Han = Chinese. */
fun lyricScript(text: String): String? {
    var han = false
    var first: String? = null
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i); i += Character.charCount(cp)
        if (cp < 0x250 || !Character.isLetter(cp)) continue
        when (val s = Character.UnicodeScript.of(cp)) {
            Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> return "JAPANESE"
            Character.UnicodeScript.HAN -> han = true
            else -> if (first == null) first = s.name
        }
    }
    return first ?: if (han) "CHINESE" else null
}

/** Latin letters for a lyric in another script; null when it's already Latin. Hindi and Tamil the way lyrics are typed, others via ICU. */
fun romanize(text: String): String? {
    if (text.all { it.code < 0x250 }) return null
    if (lyricScript(text) in Settings.romanKeep.split(',')) return null // a script the user reads: leave it as written
    val pre = Roman.convert(text)
    val out = if (pre.all { it.code < 0x250 }) pre else runCatching { romanizer?.transliterate(pre) }.getOrNull() ?: pre
    return out.takeIf { it.isNotBlank() && it != text }
}

/** Source · Timing · Romanize, above the lines. Source and Timing open a sheet; Romanize toggles in place. */
@Composable
private fun LyricsTools(t: Track, versions: List<Lyrics.Version>) {
    val chosen = versions.getOrNull(Lyrics.choice[t.id] ?: 0) ?: versions.firstOrNull()
    val offset = Lyrics.offset(t.id)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // always all three, in place; Source and Timing sit dimmed until the song has lyrics. Romanize is a preference,
        // not an action on these lyrics: it works (and shows on / off) while they load too.
        val has = versions.isNotEmpty()
        ToolPill(chosen?.source ?: "Source", false, Icons.Lyrics, Modifier.weight(1f), enabled = has) { Sheet.show { SourceSheet(t, versions) } }
        ToolPill(if (offset == 0L) "Timing" else "%+.1f s".format(offset / 1000f), offset != 0L, modifier = Modifier.weight(1f), enabled = has && chosen?.synced == true) { Sheet.show { TimingSheet(t, versions) } }
        ToolPill("Romanize", Settings.lyricsRoman, modifier = Modifier.weight(1f)) { Settings.lyricsRoman = !Settings.lyricsRoman }
    }
}

@Composable
private fun SourceSheet(t: Track, versions: List<Lyrics.Version>) {
    val pick = Lyrics.choice[t.id] ?: 0
    SheetTitle("Lyrics source")
    versions.forEachIndexed { i, v ->
        val kind = when { v.words -> "word by word"; v.synced -> "synced"; else -> "plain" }
        MenuRow(if (i == pick) Icons.Check else Icons.Lyrics, "${v.source} · $kind") { Lyrics.choice[t.id] = i }
    }
}

/**
 * Lyrics timing: the sung line live at the top (so the user syncs by eye), a big readout, ±0.1 s / ±0.5 s steps either
 * side of it, a fine slider over ±5 s, and Reset. Changes apply instantly; the sheet stays open.
 */
@Composable
private fun TimingSheet(t: Track, versions: List<Lyrics.Version>) {
    val c = WT.c
    val id = t.id
    val offset = Lyrics.offset(id)
    val lines = (versions.getOrNull(Lyrics.choice[id] ?: 0) ?: versions.first()).lines
    val pos = rememberPosition()
    val current by remember(lines) { derivedStateOf { lines.indexOfLast { it.ms <= pos.value + 300 + Lyrics.offset(id) } } }
    fun set(ms: Long) = Lyrics.setOffset(id, ms.coerceIn(-5000L, 5000L))
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt("Lyrics timing", Type.title.copy(fontSize = Type.title.fontSize * 0.86f), Modifier.fillMaxWidth().padding(vertical = 8.dp))
        // the line being sung with this timing, sliding as it changes
        androidx.compose.animation.AnimatedContent(
            targetState = current,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically(Motion.push()) { it / 2 } + androidx.compose.animation.fadeIn(Motion.push())) togetherWith
                    (androidx.compose.animation.slideOutVertically(Motion.push()) { -it / 2 } + androidx.compose.animation.fadeOut(Motion.small()))
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp).clip(RoundedCornerShape(14.dp)).background(c.on.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
            label = "timing-line",
        ) { i ->
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Txt(lines.getOrNull(i)?.text?.ifBlank { "♪" } ?: "♪", Type.button.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), Modifier.fillMaxWidth(), color = c.on)
            }
        }
        Txt(
            when { offset == 0L -> "In sync"; else -> "%+.1f s".format(offset / 1000f) },
            Type.largeTitle.copy(fontFeatureSettings = "tnum"), Modifier.padding(top = 20.dp),
        )
        Txt(
            when { offset > 0 -> "Lines show earlier"; offset < 0 -> "Lines show later"; else -> "The lyrics' own timing" },
            Type.sub, Modifier.padding(top = 6.dp), color = c.onSecondary,
        )
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepPill("−0.5", "Half a second later", Modifier.weight(1f)) { set(offset - 500) }
            StepPill("−0.1", "A tenth of a second later", Modifier.weight(1f)) { set(offset - 100) }
            StepPill("+0.1", "A tenth of a second earlier", Modifier.weight(1f)) { set(offset + 100) }
            StepPill("+0.5", "Half a second earlier", Modifier.weight(1f)) { set(offset + 500) }
        }
        Box(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Slider((offset / 100).toInt(), -50..50, "Lyrics timing, tenths of a second", mark = 0) { set(it * 100L) } // notch, snap and double-tap: in sync
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt("Later", Type.caption, color = c.onTertiary); Txt("Earlier", Type.caption, color = c.onTertiary)
        }
        ToolPill("Reset", false, Icons.Close, Modifier.padding(top = 16.dp, bottom = 4.dp).fillMaxWidth(), enabled = offset != 0L) { set(0) }
    }
}

@Composable
private fun StepPill(text: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg) = pillColors(false, WT.c)
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(bg).press(label, scale = 0.94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt("$text s", pt(Type.button, 15, 600).copy(fontFeatureSettings = "tnum"), color = fg) }
}

/**
 * The song's lyric versions, loaded off the main thread (cache-aware); null while loading. Starts from this song's
 * cache on every song change (produceState would keep showing the previous song's lyrics).
 */
@Composable
fun inlineLyricsFor(t: Track): List<Lyrics.Version>? {
    val s = remember(t.id) { mutableStateOf(Lyrics.cached(t.id)) }
    LaunchedEffect(t.id) {
        if (s.value == null) s.value = withContext(Dispatchers.IO) { runCatching { Lyrics.versions(t) }.getOrDefault(emptyList()) }
    }
    return s.value
}

/**
 * Inline lyrics in the normal sheet ([lines] = 1 or 3: previous dim, sung bright, next dim), sliding up as the song
 * moves on. Recomposes only when the sung line changes. Tapping opens full-screen lyrics.
 */
@Composable
fun InlineLyrics(t: Track, versions: List<Lyrics.Version>, lines: Int) {
    val c = WT.c
    val all = (versions.getOrNull(Lyrics.choice[t.id] ?: 0) ?: versions.first()).lines
    val pos = rememberPosition()
    val current by remember(all) { derivedStateOf { all.indexOfLast { it.ms <= pos.value + 300 + Lyrics.offset(t.id) } } }
    val style = pt(Type.row.copy(fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), 17, 600)
    // as the sung line advances every line slides up one row: one line rolls over on the way, three lines scroll calmly
    // with the middle line slightly larger. Only one float animates, read in the layer.
    val roll = remember(all) { androidx.compose.animation.core.Animatable(current.toFloat()) }
    // dragging the lines up or down scrolls through the song's lines; letting go plays from the line in the middle
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    // the line a drag let go on: held until the player's position gets there (it reports a beat later; without this the
    // lines rolled back to the old line first, then forward again), at most 1.5 s
    var landing by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lineH = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(current, dragging, landing) {
        if (dragging) return@LaunchedEffect
        if (landing >= 0) { if (current == landing) landing = -1 else return@LaunchedEffect }
        if (kotlin.math.abs(current - roll.value) > 3f) roll.snapTo(current.toFloat()) // a seek: no long spin
        else roll.animateTo(current.toFloat(), tween(520, easing = Motion.Standard))
    }
    // the lines drawn are the ones around the drum's centre (the sung line, or where a drag has scrolled to)
    val centre by remember(all) { derivedStateOf { roll.value.roundToInt() } }
    val reach = if (lines == 3) 1.6f else 1f // how far from the centre (in lines) a line stays visible
    Box(
        Modifier.fillMaxSize().clipToBounds()
            .pointerInput(all) {
                var start = 0f; var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragging = true; start = roll.value; drag = 0f },
                    onVerticalDrag = { ch, dy ->
                        ch.consume(); drag += dy
                        scope.launch { roll.snapTo((start - drag / lineH).coerceIn(0f, all.lastIndex.toFloat())) }
                    },
                    onDragEnd = {
                        val k = roll.value.roundToInt().coerceIn(0, all.lastIndex)
                        scope.launch { roll.animateTo(k.toFloat(), tween(220, easing = Motion.Standard)) }
                        all.getOrNull(k)?.let { Playback.seek((it.ms - Lyrics.offset(t.id)).coerceAtLeast(0)) } // plays from that line
                        landing = k
                        scope.launch { delay(1500); if (landing == k) landing = -1 }
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                )
            }
            .press("Lyrics. Open full screen; drag to scroll", scale = 0.98f) {
                NowPlaying.lyricsFull = true; NowPlaying.panel = NowPlaying.Panel.Lyrics
            },
        contentAlignment = Alignment.Center,
    ) {
        for (k in (centre - 2)..(centre + 2)) androidx.compose.runtime.key(k) {
            // an instrumental stretch (a blank line, or before the first line): a still music note
            val music = all.getOrNull(k)?.text.isNullOrBlank() && (k == centre || all.getOrNull(k) != null)
            val raw = all.getOrNull(k)?.text ?: ""
            val text = if (Settings.romanInline) romanize(raw) ?: raw else raw
            val line = Modifier.fillMaxWidth().height(28.dp).graphicsLayer {
                    val d = k - roll.value // lines from the centre, fractional while rolling
                    val dist = kotlin.math.abs(d)
                    translationY = d * 28.dp.toPx()
                    if (lines == 1) {
                        // one line: the lines sit on a turning drum, [step]° apart, on a radius that keeps them a line
                        // apart: the leaving one curves up and away tipping its top back, the arriving one rises from
                        // under (positive rotationX tips the top edge away), each a little smaller as it turns off the front
                        val step = 32f
                        val deg = (d * step).coerceIn(-90f, 90f)
                        val rad = Math.toRadians(deg.toDouble())
                        val radius = 28.dp.toPx() / kotlin.math.sin(Math.toRadians(step.toDouble())).toFloat()
                        translationY = radius * kotlin.math.sin(rad).toFloat()
                        rotationX = -deg
                        val s = 1f - 0.15f * (1f - kotlin.math.cos(rad).toFloat())
                        scaleX = s; scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) // lines are start-aligned
                        cameraDistance = 10f * density
                    } else {
                        // three lines: a calm scroll; the line arriving in the middle grows a little, the one leaving settles back
                        val s = 1f - 0.1f * dist.coerceAtMost(1f)
                        scaleX = s; scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) // lines are start-aligned
                    }
                    alpha = (reach - dist).coerceIn(0f, 1f) * (1f - 0.62f * dist.coerceAtMost(1f)) // fades out at the rim; neighbours dim
                }
            val words = all.getOrNull(k)?.words
            if (music) Txt("♪", style.copy(fontSize = style.fontSize * 1.25f), line.wrapContentHeight(Alignment.CenterVertically), color = c.on.copy(alpha = 0.8f))
            // the sung line fills word by word, as in full-screen lyrics (romanized text has no word timings: plain)
            else if (k == current && !dragging && Settings.lyricsWords && words != null && text == raw) Karaoke(words, t.id, style, line.wrapContentHeight(Alignment.CenterVertically), lines = 1)
            else Txt(text, style, line.wrapContentHeight(Alignment.CenterVertically), color = c.on)
        }
    }
}

/** While a song's lyrics load: "Loading lyrics…", gently pulsing, where the lines will appear. */
@Composable
fun InlineLoading() {
    val c = WT.c
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "loading")
        .animateFloat(0.35f, 0.75f, androidx.compose.animation.core.infiniteRepeatable(tween(900, easing = Motion.Standard), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Txt("Loading lyrics…", pt(Type.row.copy(fontSize = 15.sp), 15, 500), Modifier.graphicsLayer { alpha = pulse }, color = c.on)
    }
}

/** Off pills: near-white on a translucent fill; on: solid white, black text. Shared by the lyrics tools and the queue. */
fun pillColors(on: Boolean, c: com.wetune.ui.WeColors): Pair<Color, Color> =
    if (on) Color.White to Color.Black else c.on.copy(alpha = 0.1f) to c.on.copy(alpha = if (c.dark) 0.9f else 1f)

/** One lyrics tool pill, same look as the queue's Shuffle / Repeat / Automix. */
@Composable
internal fun ToolPill(text: String, on: Boolean, ic: com.wetune.ui.Ic? = null, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val (bg, fg) = pillColors(on, WT.c)
    // unavailable dims only the label and icon: the pill keeps its shape and fill, so off, on and unavailable pills
    // still read as one row (fading the whole pill left an off pill as bare text and an on pill as dark text on nothing)
    val dim by androidx.compose.animation.core.animateFloatAsState(if (enabled) 1f else 0.4f, Motion.small(), label = "pill")
    Row(
        modifier.height(36.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(bg)
            .then(if (enabled) Modifier.press(text, scale = 0.95f, onClick = onClick) else Modifier.semantics { contentDescription = "$text, unavailable"; disabled() })
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.graphicsLayer { alpha = dim }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (ic != null) com.wetune.ui.Icon(ic, fg, 16.dp)
            Txt(text, pt(Type.sub.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), 13, 600), color = fg)
        }
    }
}
