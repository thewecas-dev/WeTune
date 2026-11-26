package com.wetune.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.wetune.ui.player.StackedCovers
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.wetune.Downloads
import com.wetune.Playback
import com.wetune.appScope
import com.wetune.sources.Card
import com.wetune.sources.Track
import com.wetune.toast
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Sync
import com.wetune.youtube.YouTube
import com.wetune.youtube.subtitleArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Opened pages kept for the session: back navigation and reopening are instant, then refreshed in the background. */
private object PageCache {
    private val pages = object : LinkedHashMap<String, YouTube.Page>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, YouTube.Page>) = size > 30
    }
    @Synchronized fun get(id: String) = pages[id]
    @Synchronized fun put(id: String, p: YouTube.Page) { pages[id] = p }
}

private val YEAR = Regex("\\b(19|20)\\d{2}\\b")

private fun time(ms: Long) = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

private val BAR = 56.dp // the top bar (Back · title · More) below the status bar
private val BUTTONS = 76.dp // the sheet's top: 16 above the buttons, 48 of buttons, 12 below
private val SIDE = 12.dp // the sheet's side gap at rest; none once expanded
private val EXPAND = 96.dp // how much scrolling turns the page from resting to expanded

/**
 * Album, playlist and artist pages. At rest: the art, title and one frosted sheet holding Play · Shuffle · Download
 * and the songs. Scrolling up expands it like Now Playing's queue: the header shrinks into the top bar, the buttons
 * stop under it and stay put, and the sheet goes edge to edge with the list running beneath the buttons.
 */
@Composable
fun CollectionScreen(card: Card, ready: YouTube.Page? = null) {
    val c = WT.c
    val id = card.browseId
    val artist = id.startsWith("UC")
    val album = id.startsWith("MPRE")
    val shelf = ready != null // a "see all" shelf: its songs and / or albums & playlists are already here
    val page = load(id, ready ?: PageCache.get(id)) {
        if (ready != null) ready
        else if (id.startsWith("local-")) YouTube.Page(emptyList(), emptyList()) // made offline, not on YouTube yet
        else YouTube.page(card).also { PageCache.put(id, it) }
    }
    val p = page.value
    val owned = id.startsWith("local-") || (p != null && p.saveId == null && Sync.playlists.any { it.browseId == id })
    // the user's own playlist shows this device's unsent adds / removes (Sync.pending changes when they do)
    val tracks = remember(p, Sync.pending) { p?.tracks?.let { if (owned) Sync.overlay(id, it) else it }.orEmpty() }
    val cards = if (shelf) ready!!.sections.flatMap { it.cards } else emptyList()
    val noun = when { cards.all { it.browseId.startsWith("MPRE") } -> "albums"; cards.all { it.browseId.startsWith("UC") } -> "artists"; else -> "playlists" }
    val count = if (tracks.isEmpty() && cards.isNotEmpty()) "${cards.size} $noun" else totals(tracks)
    val label = if (!shelf) kind(card, artist, album) else if (tracks.isNotEmpty()) "Songs" else noun.replaceFirstChar { it.uppercase() }
    val buttons = !shelf || tracks.isNotEmpty() // a shelf of albums / playlists only has nothing to play itself
    val room = if (buttons) BUTTONS else 16.dp
    val from = (if (artist) "Artist" else if (album) "Album" else "Playlist") to card.title
    val art = p?.art ?: card.art
    val state = rememberLazyListState()

    // Scroll geometry, read only in layout and draw (scrolling never recomposes): where the sheet's top sits in the
    // list, held under the top bar once it gets there, and how expanded the page is (0..1).
    val d = LocalDensity.current
    val pin = with(d) { (statusTop() + BAR).toPx() }
    val span = with(d) { EXPAND.toPx() }
    val natural = { state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "sheet" }?.offset?.toFloat() ?: -1e6f }
    val sheetTop = { natural().coerceAtLeast(pin) }
    val expanded = { ((pin + span - natural()) / span).coerceIn(0f, 1f) }

    val tailPx = remember { intArrayOf(0) } // the end spacer's height, kept while the sheet's top is scrolled out of view
    val fill = glassFill()
    val grain = remember { grainBrush() }
    val grainA = grainAlpha()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val reveal = remember { Reveal(scope) } // the artist's info, pulled down at the top of the page
    // the top buttons sit inside the app's blur source, so they frost this page's own layer instead
    val haze = remember { HazeState() }
    Box(Modifier.fillMaxSize().background(c.bg)) {
      // the colour field and the list together are what Back / More frost
      Box(Modifier.fillMaxSize().hazeSource(haze)) {
        // the colour field from the page's own art (A1-c / D1): still while the list moves over it
        Aura(Modifier.fillMaxHeight(0.9f), art, if (c.dark) 0.55f else 0.32f, brand = false)
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize()
                .then(if (artist) Modifier.nestedScroll(reveal.scroll) else Modifier)
                // the sheet: one glass surface from the buttons down, its side gaps closing as the page expands
                .drawBehind {
                    val top = sheetTop()
                    val side = lerp(SIDE.toPx(), 0f, expanded())
                    val r = CornerRadius(24.dp.toPx())
                    val at = Offset(side, top); val box = Size(size.width - side * 2, size.height - top + r.x) // bottom corners off screen
                    drawRoundRect(fill, at, box, r)
                    drawRoundRect(grain, at, box, r, alpha = grainA)
                }
                // expanded, the songs pass under the buttons and end there, like the queue under its pills
                .drawWithContent { if (natural() >= pin) drawContent() else clipRect(top = pin + room.toPx()) { this@drawWithContent.drawContent() } },
            contentPadding = PaddingValues(bottom = Dock.bottomPadding(Playback.current != null)),
        ) {
            item("header", "header") {
                Header(
                    card, artist, album, art, tracks, cards,
                    if (artist) card.subtitle.replace(" • ", " · ") else count, p?.about.takeIf { artist }, reveal,
                    Modifier.graphicsLayer { alpha = 1f - expanded() },
                )
            }
            item("sheet", "sheet") { Spacer(Modifier.height(room)) } // the buttons' room; they're drawn over it
            val row = Modifier.padding(horizontal = SIDE)
            when {
                p == null && page.error != null -> item("err", "err") { Failed(page.error!!) { page.retry() } }
                p == null -> items(8, key = { "s$it" }, contentType = { "skeleton" }) { Box(row) { SkeletonRow() } }
                artist -> {
                    if (tracks.isNotEmpty()) {
                        item("top", "header") {
                            SectionHeader("Top songs", row, onMore = if (tracks.size > 5) ({ Nav.open(Route.Shelf("${card.title} · Songs", tracks, emptyList())) }) else null)
                        }
                        itemsIndexed(tracks.take(5), key = { _, t -> "t:" + t.id }, contentType = { _, _ -> "track" }) { i, t -> TrackRow(t, row, ring = true) { Playback.play(tracks, i, from = from) } }
                    }
                    p.sections.forEachIndexed { i, s ->
                        item("sh$i", "header") { SectionHeader(s.title, row) }
                        item("sc$i", "tiles") {
                            LazyRow(row, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(s.cards, key = { it.browseId }, contentType = { "tile" }) { x -> Tile(x) { Nav.open(Route.Collection(x)) } }
                            }
                        }
                    }
                }
                shelf && cards.isNotEmpty() -> {
                    itemsIndexed(tracks, key = { i, t -> "t:$i:" + t.id }, contentType = { _, _ -> "track" }) { i, t -> TrackRow(t, row, ring = true) { Playback.play(tracks, i, from = from) } }
                    itemsIndexed(cards, key = { i, x -> "c:$i:" + x.browseId }, contentType = { _, _ -> "card" }) { _, x -> CardRow(x, row) }
                }
                tracks.isEmpty() -> item("empty", "empty") { EmptyState(Icons.Library, "No songs yet", "Add songs from any song's menu. They sync with YouTube Music.") }
                else -> itemsIndexed(tracks, key = { i, t -> "t:$i:" + t.id }, contentType = { _, _ -> "track" }) { i, t ->
                    val play = { Playback.play(tracks, i, from = from) }
                    if (album) TrackRow(t, row, index = i + 1, ring = true, trailing = { Txt(time(t.durationMs), Type.sub, color = c.onSecondary) }, onClick = play)
                    else TrackRow(t, row, ring = true, trailing = {
                        IconBtn(Icons.More, "More for ${t.title}", tint = c.onSecondary, size = 18.dp) { Sheet.show { TrackMenuBody(t, id.takeIf { owned }) } }
                    }, onClick = play)
                }
            }
            // Room for a short list to still expand fully: just enough that, scrolled to its end, the sheet's top reaches
            // the top bar (nothing for a list long enough). Measured from the last layout: what lies between the sheet's
            // top and this spacer; its own height doesn't change that, so it settles in one frame.
            item("tail", "tail") {
                Spacer(Modifier.fillMaxWidth().layout { m, cs ->
                    val info = state.layoutInfo
                    val sheet = info.visibleItemsInfo.firstOrNull { it.key == "sheet" }
                    val tail = info.visibleItemsInfo.firstOrNull { it.key == "tail" }
                    if (sheet != null && tail != null) tailPx[0] = (info.viewportEndOffset - info.afterContentPadding - pin - (tail.offset - sheet.offset)).roundToInt().coerceAtLeast(0)
                    val s = m.measure(Constraints.fixed(cs.maxWidth, tailPx[0]))
                    layout(s.width, s.height) { s.place(0, 0) }
                })
            }
        }
      }
        // Play · Shuffle · Download (artists: Follow): on the sheet's top, then held under the top bar, never moving otherwise
        if (buttons) Buttons(
            card, artist, tracks, p, from,
            Modifier.offset { IntOffset(0, (sheetTop() + 16.dp.toPx()).roundToInt()) }.padding(horizontal = SIDE * 2),
        )
        // fixed Back / More over the page; between them the kind at rest, the small art and title once expanded
        CompositionLocalProvider(LocalHaze provides haze) {
            Row(Modifier.fillMaxWidth().statusPad().height(BAR).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Back, "Back", circle = true, frosted = true, size = 16.dp) { Nav.back() }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Txt(label.uppercase(), Type.caption, Modifier.graphicsLayer { alpha = 1f - expanded() }, color = c.onSecondary)
                    Row(
                        Modifier.fillMaxWidth().graphicsLayer { alpha = ((expanded() - 0.5f) * 2f).coerceIn(0f, 1f) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Cover(art, 36.dp, if (artist) 18.dp else 6.dp)
                        Column(Modifier.weight(1f)) {
                            Txt(card.title, Type.row.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(700)))
                            Txt(if (artist) "Artist" else count, Type.sub, color = c.onSecondary)
                        }
                    }
                }
                IconBtn(Icons.More, "More", circle = true, frosted = true, size = 16.dp) { Sheet.show { PageMenu(card, p, tracks, owned, from) } }
            }
        }
    }
}

private fun kind(card: Card, artist: Boolean, album: Boolean): String = if (artist) "Artist" else listOfNotNull(
    card.subtitle.substringBefore(" • ").takeIf { it in setOf("Album", "Single", "EP", "Playlist") } ?: if (album) "Album" else "Playlist",
    YEAR.find(card.subtitle)?.value,
).joinToString(" · ")

private fun totals(tracks: List<Track>): String {
    if (tracks.isEmpty()) return ""
    val m = tracks.sumOf { it.durationMs } / 60_000
    return "${tracks.size} song${if (tracks.size == 1) "" else "s"}" + when { m >= 60 -> " · ${m / 60} h ${m % 60} min"; m > 0 -> " · $m min"; else -> "" }
}

@Composable
private fun Header(card: Card, artist: Boolean, album: Boolean, art: String?, tracks: List<Track>, cards: List<Card>, rest: String, about: YouTube.About?, reveal: Reveal, modifier: Modifier) {
    val c = WT.c
    Column(modifier.fillMaxWidth().statusPad().padding(start = 16.dp, end = 16.dp, top = BAR + 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            artist -> Cover(art, 200.dp, 100.dp, description = "Photo: ${card.title}")
            album -> Cover(art, 240.dp, 12.dp, description = "Artwork: ${card.title}")
            // a playlist (or shelf): Now Playing's Stack cover style, two more of its covers fanned out behind, which
            // slide out from behind the front one once when the page opens
            else -> {
                val behind = remember(tracks, cards, art) { (tracks.mapNotNull { it.art } + cards.mapNotNull { it.art }).distinct().filter { it != art }.take(2) }
                var fanned by rememberSaveable { mutableStateOf(false) }
                val fan = remember { Animatable(if (fanned) 1f else 0f) }
                LaunchedEffect(behind.isNotEmpty()) {
                    if (behind.isNotEmpty() && !fanned) { fan.animateTo(1f, spring(dampingRatio = 1f, stiffness = 260f)); fanned = true }
                }
                Box(Modifier.size(240.dp)) {
                    StackedCovers(behind.getOrNull(0), behind.getOrNull(1), RoundedCornerShape(12.dp), { fan.value }) {
                        Cover(art, 240.dp, 12.dp, description = "Artwork: ${card.title}")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Txt(card.title, Type.title.copy(fontSize = Type.title.fontSize * 1.05f, textAlign = TextAlign.Center), lines = 2)
        val by = if (artist || card.subtitle.isEmpty()) "" else subtitleArtist(card.subtitle)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (by.isNotEmpty()) Txt(
                by, Type.sub, Modifier.weight(1f, fill = false)
                    .then(if (tracks.isNotEmpty() && tracks[0].source == "yt") Modifier.press("Go to artist $by") { openArtist(tracks[0]) } else Modifier),
                color = c.on,
            )
            Txt(if (by.isNotEmpty() && rest.isNotEmpty()) " · $rest" else rest, Type.sub, color = c.onSecondary)
        }
        if (about != null) Box(
            // nothing at rest: as tall as it's been pulled open (all of it once open), clipped, fading in as it opens
            Modifier.fillMaxWidth().clipToBounds()
                .layout { m, cs ->
                    val s = m.measure(cs.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                    if (reveal.full > 0 && reveal.shown >= reveal.full - 0.5f && s.height != reveal.full) reveal.shown = s.height.toFloat() // open: follows More / Less
                    reveal.full = s.height
                    val h = reveal.shown.roundToInt().coerceIn(0, s.height)
                    layout(s.width, h) { s.place(0, 0) }
                }
                .graphicsLayer { alpha = if (reveal.full == 0) 0f else (reveal.shown / reveal.full).coerceIn(0f, 1f) }
                .pointerInput(Unit) { detectTapGestures { reveal.settle(false) } },
        ) { Bio(about) }
    }
}

/**
 * An artist's info, hidden until pulled down at the top of the page: overscroll there drags it open, letting go past
 * a third keeps it open (less springs back), scrolling up closes it first, a tap on it closes it. [shown] px of it show.
 */
private class Reveal(private val scope: kotlinx.coroutines.CoroutineScope) {
    var shown by androidx.compose.runtime.mutableFloatStateOf(0f)
    var full = 0 // its whole height, as last measured
    private var job: kotlinx.coroutines.Job? = null

    fun settle(open: Boolean) {
        job?.cancel()
        job = scope.launch { androidx.compose.animation.core.animate(shown, if (open) full.toFloat() else 0f, animationSpec = Motion.push()) { v, _ -> shown = v } }
    }

    val scroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f || shown <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
            job?.cancel()
            val take = maxOf(available.y, -shown) // scrolling up closes it before the list moves
            shown += take
            return Offset(0f, take)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f || full == 0 || shown >= full || source != NestedScrollSource.UserInput) return Offset.Zero
            job?.cancel()
            val add = minOf(available.y * 0.6f, full - shown) // pulled with a little resistance
            shown += add
            return Offset(0f, add / 0.6f)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (shown <= 0f || shown >= full) return Velocity.Zero
            settle(shown > full / 3f)
            return available
        }
    }
}

/** An artist's audience line and bio: three lines, "More" opens it in place (and "Less" closes it), then where it's from. */
@Composable
private fun Bio(about: YouTube.About) {
    val c = WT.c
    var open by rememberSaveable { mutableStateOf(false) }
    var long by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 12.dp).animateContentSize(Motion.small()), horizontalAlignment = Alignment.CenterHorizontally) {
        if (about.stat.isNotEmpty()) Txt(about.stat, Type.sub.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), Modifier.padding(bottom = 6.dp), color = c.on)
        if (about.bio.isNotEmpty()) androidx.compose.foundation.text.BasicText(
            about.bio, Modifier.fillMaxWidth(), Type.sub.copy(color = c.onSecondary, textAlign = TextAlign.Center),
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, maxLines = if (open) Int.MAX_VALUE else 3,
            onTextLayout = { if (!open) long = it.hasVisualOverflow },
        )
        if (long || open) Txt(if (open) "Less" else "More", Type.sub.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)),
            Modifier.padding(top = 4.dp).press(if (open) "Show less" else "Show more") { open = !open })
        if (about.source.isNotEmpty()) Txt(about.source, Type.caption, Modifier.padding(top = 4.dp), color = c.onTertiary)
    }
}

@Composable
private fun Buttons(card: Card, artist: Boolean, tracks: List<Track>, p: YouTube.Page?, from: Pair<String, String>, modifier: Modifier) {
    val c = WT.c
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigButton("Play", Icons.Play, primary = true, modifier = Modifier.weight(1.15f)) { if (tracks.isNotEmpty()) Playback.play(tracks, 0, from = from) }
        BigButton("Shuffle", Icons.Shuffle, primary = false, modifier = Modifier.weight(1f)) { if (tracks.isNotEmpty()) Playback.play(tracks.shuffled(), 0, from = from) }
        if (artist) {
            // follow state as YouTube reported it with the page; flips at once, back if YouTube refuses
            var following by remember(p) { mutableStateOf(p?.let { if (it.channel != null) it.subscribed else null }) }
            val can = InnerTube.signedIn && following != null
            BigButton(if (following == true) "Following" else "Follow", if (following == true) Icons.Check else Icons.Plus, primary = false,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = if (can) 1f else 0.45f }) {
                val was = following ?: return@BigButton
                if (!InnerTube.signedIn) return@BigButton
                following = !was
                appScope.launch {
                    runCatching { withContext(Dispatchers.IO) { InnerTube.post(if (was) "subscription/unsubscribe" else "subscription/subscribe", JSONObject().put("channelIds", JSONArray().put(p!!.channel))) } } // the follow button's channel, not the page's id
                        .onSuccess { p?.let { PageCache.put(card.browseId, it.copy(subscribed = !was)) } }
                        .onFailure { following = was; toast(it.message ?: "Couldn't update") }
                }
            }
        } else {
            val done = tracks.isNotEmpty() && tracks.all { Downloads.file(it).exists() }
            BigButton(if (done) "Downloaded" else "Download", if (done) Icons.Downloaded else Icons.Download, primary = false, modifier = Modifier.weight(1f)) {
                if (tracks.isNotEmpty() && !done) { tracks.forEach { Downloads.download(it) }; toast("Downloading ${tracks.size} songs") }
            }
        }
    }
}

@Composable
private fun PageMenu(card: Card, p: YouTube.Page?, tracks: List<Track>, owned: Boolean, from: Pair<String, String>) {
    SheetTitle(card.title)
    // save to / remove from the library (it lived under the buttons before; Download took that row's other job)
    if (InnerTube.signedIn && p?.saveId != null) {
        val saved = Sync.isSaved(card)
        MenuRow(if (saved) Icons.Check else Icons.Plus, if (saved) "Remove from library" else "Save to library") { Sync.setSaved(card, p.saveId, !saved) }
    }
    if (tracks.isNotEmpty()) {
        MenuRow(Icons.Shuffle, "Shuffle") { Playback.play(tracks.shuffled(), 0, from = from) }
        MenuRow(Icons.Queue, "Add to queue") { tracks.forEach(Playback::enqueue) }
        if (tracks[0].source == "yt") MenuRow(Icons.Radio, "Start radio") { Playback.startRadio(tracks[0]) }
        MenuRow(Icons.Download, "Download all") { tracks.forEach { Downloads.download(it) } }
    }
    if (owned && InnerTube.signedIn) MenuRow(Icons.Trash, "Delete playlist") { Sync.deletePlaylist(card.browseId); Nav.back() }
}

private fun openArtist(t: Track) = appScope.launch {
    val a = withContext(Dispatchers.IO) { runCatching { YouTube.links(t.ref) }.getOrDefault(emptyList()) }.firstOrNull { it.subtitle == "Artist" }
        ?: return@launch toast("No artist page for this song")
    Nav.open(Route.Collection(a))
}

/**
 * A "see all" shelf (Home's song shelves and mixes, an artist's songs, a playlist kept on this device) opens as a
 * playlist page of its own: its covers stacked, Play / Shuffle / Download, the expanding list. A shelf of albums or
 * playlists gets the same page without the buttons, one row per album / playlist.
 */
@Composable
fun ShelfScreen(r: Route.Shelf) = CollectionScreen(
    Card(r.key, r.title, "", r.tracks.firstOrNull()?.art ?: r.cards.firstOrNull()?.art),
    YouTube.Page(r.tracks, if (r.cards.isEmpty()) emptyList() else listOf(com.wetune.youtube.Section(r.title, emptyList(), r.cards))),
)

/** An album / playlist / artist in a shelf's list: its cover, title and line, opening its page. */
@Composable
private fun CardRow(x: Card, modifier: Modifier) {
    val c = WT.c
    val round = x.browseId.startsWith("UC") || x.subtitle.startsWith("Artist")
    Row(
        modifier.fillMaxWidth().height(72.dp).press(x.title, scale = 0.985f) { Nav.open(Route.Collection(x)) }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(x.art, 56.dp, if (round) 28.dp else 8.dp)
        Column(Modifier.weight(1f)) {
            Txt(x.title, Type.row)
            if (x.subtitle.isNotEmpty()) Txt(x.subtitle.replace(" • ", " · "), Type.sub, color = c.onSecondary)
        }
        Icon(Icons.Chevron, c.onTertiary, 14.dp)
    }
}
