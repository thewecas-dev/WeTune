package com.wetune.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wetune.Playback
import com.wetune.R
import com.wetune.Settings
import com.wetune.appScope
import com.wetune.sources.Card
import com.wetune.sources.Track
import com.wetune.youtube.Section
import com.wetune.youtube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home feed kept for the whole session: the All feed and each mood chip's feed, each fetched once and kept, so
 * switching tabs or chips never reloads. Pull to refresh reloads the shown one.
 */
object HomeFeed {
    private class Feed(val sections: List<Section>, val token: String?)

    var sections by mutableStateOf<List<Section>>(emptyList()); private set
    /** The mood / genre chips YouTube Music offers above Home ("Energize", "Relax"…); kept from the All feed. */
    var chips by mutableStateOf<List<YouTube.Chip>>(emptyList()); private set
    /** The chip whose feed is shown; null = All. */
    var chip by mutableStateOf<YouTube.Chip?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var refreshing by mutableStateOf(false); private set
    /** Bumped each time a feed is shown: its rows animate in once, keyed by it. */
    var generation by mutableIntStateOf(0); private set
    /** When the current feed was shown (ms): rows only animate in shortly after, never when scrolled back into view. */
    var shownAt by mutableLongStateOf(0L); private set
    private val cache = HashMap<String, Feed>() // by chip params ("" = All)
    private var cacheFor: String? = null // the account the cache belongs to
    private var job: Job? = null
    private var paging = false

    private fun key(c: YouTube.Chip?) = c?.params ?: ""

    private fun show(f: Feed) { sections = f.sections; generation++; shownAt = System.currentTimeMillis() }

    /** The shown feed: from memory unless [force] (or another account signed in), else fetched; placeholders meanwhile. */
    fun load(force: Boolean = false): Job {
        if (cacheFor != Settings.cookie) { cache.clear(); cacheFor = Settings.cookie }
        val c = chip
        val kept = cache[key(c)]
        if (kept != null && !force) { if (sections !== kept.sections) show(kept); return Job().apply { complete() } }
        job?.cancel()
        return appScope.launch {
            runCatching { withContext(Dispatchers.IO) { YouTube.home(c?.params) } }
                .onSuccess { h ->
                    if (chip != c) return@onSuccess // the user picked another chip meanwhile
                    val f = Feed(h.sections, h.token)
                    cache[key(c)] = f
                    if (c == null && h.chips.isNotEmpty()) chips = h.chips
                    error = null
                    show(f)
                    // YouTube sends Home a couple of sections at a time: fetch the next few pages straight away, so it opens
                    // full (loading more on scroll alone stalls when the first page already fits on screen)
                    launch { repeat(4) { if (chip == c) more().join() } }
                }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException && sections.isEmpty()) error = it.message ?: "Something went wrong" }
        }.also { job = it }
    }

    /** Show [c]'s feed (null = All): at once if it was opened before, else placeholders until it arrives. */
    fun pick(c: YouTube.Chip?) {
        if (c == chip) return
        chip = c; error = null
        if (cache[key(c)] == null) sections = emptyList() // placeholders while it loads, never the previous mood's rows
        load()
    }

    /** Pull to refresh: the shown feed, fresh; the spinner shows at least briefly so the gesture always feels answered. */
    fun refresh() = appScope.launch {
        if (refreshing) return@launch
        refreshing = true
        val started = System.currentTimeMillis()
        load(force = true).join()
        delay((700 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
        refreshing = false
    }

    fun more() = appScope.launch {
        val c = chip
        val f = cache[key(c)] ?: return@launch
        val t = f.token ?: return@launch
        if (paging) return@launch
        paging = true
        runCatching { withContext(Dispatchers.IO) { YouTube.homeMore(t) } }.onSuccess { (s, next) ->
            val grown = Feed(f.sections + s, next)
            cache[key(c)] = grown
            if (chip == c) sections = grown.sections
        }
        paging = false
    }

    fun retry() { error = null; load(force = true) }
}

private fun isArtist(c: Card) = c.subtitle.startsWith("Artist") || c.browseId.startsWith("UC")
private fun isMix(title: String) = Regex("mix|for you|radio|supermix", RegexOption.IGNORE_CASE).containsMatchIn(title)


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val c = WT.c
    val state = rememberLazyListState()
    LaunchedEffect(Settings.cookie) { HomeFeed.load() }
    ReselectToTop(state)
    // next page when the last few rows come into view
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.let { it.totalItemsCount > 0 && (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 4 } }
            .collect { if (it) HomeFeed.more() }
    }
    val sections = HomeFeed.sections
    val gen = HomeFeed.generation
    Box(Modifier.fillMaxSize()) {
        PullRefresh(HomeFeed.refreshing, onRefresh = { HomeFeed.refresh() }) {
            LazyColumn(
                state = state, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Dock.bottomPadding(Playback.current != null)),
            ) {
                item("top", "top") { TopBar() }
                if (HomeFeed.chips.isNotEmpty()) item("chips", "chips") { Chips() }
                if (sections.isEmpty()) item("state", "state") {
                    Crossfade(HomeFeed.error, label = "home") { e ->
                        if (e != null) Failed(e) { HomeFeed.retry() }
                        else Column {
                            SectionHeader("Listen again"); SkeletonTiles(); SectionHeader("Quick picks"); repeat(5) { SkeletonRow() }
                        }
                    }
                }
                var order = 0 // place on screen, for the arrival stagger
                sections.forEachIndexed { i, s ->
                    val cards = s.cards
                    if (cards.isEmpty() && s.tracks.isEmpty()) return@forEachIndexed
                    val ho = order++
                    item("$gen:h$i", "header") {
                        Box(Modifier.arrive(ho)) {
                            SectionHeader(s.title, onMore = if (s.tracks.size > 4 || cards.size > 6) ({ Nav.open(Route.Shelf(s.title, s.tracks, s.cards)) }) else null)
                        }
                    }
                    val ro = order++
                    when {
                        // songs only: pages of four, swiped sideways (like Quick picks)
                        cards.isEmpty() -> item("$gen:p$i", "pages") { Box(Modifier.arrive(ro)) { SongPages(s) } }
                        // artists: round portraits
                        cards.all(::isArtist) -> item("$gen:a$i", "artists") { Box(Modifier.arrive(ro)) { TileRow(cards, emptyList(), 120.dp, s.title) } }
                        // mixes: large tiles; everything else: regular ones (songs in the row play in place)
                        else -> item("$gen:c$i", "tiles") { Box(Modifier.arrive(ro)) { TileRow(cards, s.tracks, if (isMix(s.title)) 172.dp else 144.dp, s.title) } }
                    }
                }
            }
        }
        CollapsedBar("Home", state) // a soft fade behind the status bar once content scrolls under it
    }
}

/** The top of Home: WeTune's mark at the start, the profile at the end. Scrolls with the page. */
@Composable
private fun TopBar() = Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = statusTop() + 6.dp), verticalAlignment = Alignment.CenterVertically) {
    AppMark()
    Spacer(Modifier.weight(1f))
    ProfileButton()
}


/** All · Energize · Relax · Workout…: YouTube Music's moods; picking one swaps the whole feed below. */
@Composable
private fun Chips() {
    val chips = HomeFeed.chips
    val on = HomeFeed.chip
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item("all") { Pill("All", on == null) { HomeFeed.pick(null) } }
        items(chips, key = { it.title }) { c -> Pill(c.title, on == c) { HomeFeed.pick(c) } }
    }
}

/** Songs four to a page, pages swiped sideways and snapping into place; the next page peeks in at the edge. */
@Composable
private fun SongPages(s: Section) {
    val pages = s.tracks.take(20).chunked(4)
    val row = rememberLazyListState()
    BoxWithConstraints {
        val pageW = maxWidth - 40.dp
        LazyRow(state = row, flingBehavior = rememberSnapFlingBehavior(row)) {
            items(pages.size, key = { it }) { p ->
                Column(Modifier.width(pageW)) {
                    // swipe off: a sideways slide here pages the shelf
                    pages[p].forEachIndexed { j, t -> TrackRow(t, swipe = false) { Playback.play(s.tracks, p * 4 + j, from = "Home" to s.title) } }
                }
            }
        }
    }
}

/** A row of tiles: [cards] open their page; [tracks] (songs mixed into the row) play in place. */
@Composable
private fun TileRow(cards: List<Card>, tracks: List<Track>, size: Dp, from: String) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cards, key = { it.browseId }, contentType = { "tile" }) { c -> Tile(c, size = size) { Nav.open(Route.Collection(c)) } }
        items(tracks.size, key = { "t" + tracks[it].id }, contentType = { "song" }) { i ->
            val t = tracks[i]
            Column(Modifier.width(size).press(t.title) { Playback.play(tracks, i, from = "Home" to from) }) {
                Cover(t.art, size, 10.dp)
                Spacer(Modifier.height(6.dp))
                Txt(t.title, Type.tile)
                Txt(t.artist, Type.sub, color = WT.c.onSecondary)
            }
        }
    }
}

/** WeTune's mark, small, at the start of the header. */
@Composable
private fun AppMark() {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        // the launcher icon's own two layers, so the header badge always matches the app icon
        Box(Modifier.size(34.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.size(34.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), "WeTune", Modifier.size(34.dp))
        }
    }
}

/**
 * A row arriving with a fresh feed: rises 18dp and fades in, staggered by its place on screen. Rows composed later
 * (scrolled into view) are simply there; only the layer animates, never the layout.
 */
@Composable
private fun Modifier.arrive(order: Int): Modifier {
    val fresh = remember { System.currentTimeMillis() - HomeFeed.shownAt < 1200 && order < 14 }
    val a = remember { Animatable(if (fresh) 0f else 1f) }
    if (fresh) LaunchedEffect(Unit) { delay(order * 55L); a.animateTo(1f, tween(460, easing = Motion.Decelerate)) }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 18.dp.toPx() }
}
