package com.wetune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.nestedscroll.nestedScroll
import coil3.compose.AsyncImage
import com.wetune.ui.player.fmt
import com.wetune.Playback
import com.wetune.Settings
import com.wetune.Video
import com.wetune.recentKey
import com.wetune.sources.Card
import com.wetune.sources.Track
import com.wetune.youtube.Items
import com.wetune.youtube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val KINDS = listOf("all" to "All", "songs" to "Songs", "albums" to "Albums", "artists" to "Artists", "playlists" to "Playlists", "videos" to "Videos")

/**
 * One filter's results. A filter: its [items]. All: YouTube Music's own [top] result plus each filter's results in
 * [parts] (the very lists those tabs then show). [corrected]: the query YouTube searched instead (spelling fixed);
 * [exact]: searched as typed, spelling fix off.
 */
private data class Results(
    val query: String, val kind: String, val exact: Boolean, val items: Items = Items(emptyList(), emptyList()),
    val top: Any? = null, val corrected: String? = null, val parts: Map<String, Items> = emptyMap(),
)

/** Results kept for the session, so coming back to the tab shows the last search at once. */
private object SearchState {
    var results by mutableStateOf<Results?>(null)
    val byKind = HashMap<String, Results>() // each filter's results for the current query; cleared when the query changes
    val totals = HashMap<String, String>() // album / playlist browseId → "14 songs · 56 min" (see CountedRow)
}

@Composable
fun SearchScreen() {
    val c = WT.c
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf(SearchState.results?.query.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf("all") }
    var focused by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var attempt by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var exact by rememberSaveable(query) { mutableStateOf(false) } // "Search instead for …": spelling fix off for this query
    val state = rememberLazyListState()
    ReselectToTop(state)

    // typing is debounced and keeps the previous results until the new ones arrive; a filter already fetched for this
    // query comes back at once, a new one shows placeholders (never another filter's results)
    LaunchedEffect(query, kind, attempt, exact) {
        val q = query.trim()
        if (q.isEmpty()) { error = null; return@LaunchedEffect }
        val r = SearchState.results
        if (r != null && r.query == q && r.kind == kind && r.exact == exact) return@LaunchedEffect
        if (SearchState.byKind.values.firstOrNull()?.let { it.query != q || it.exact != exact } == true) SearchState.byKind.clear()
        SearchState.byKind[kind]?.let { SearchState.results = it; error = null; state.scrollToItem(0); return@LaunchedEffect }
        if (r != null && r.query == q) SearchState.results = null else delay(350)
        loading = true
        runCatching { search(q, kind, exact) }
            .onSuccess {
                SearchState.byKind[kind] = it; SearchState.results = it; error = null; state.scrollToItem(0)
                // All fetched every filter's results: each tab then opens at once, the same list in the same order
                it.parts.forEach { (k, items) -> SearchState.byKind.getOrPut(k) { Results(q, k, exact, items, corrected = it.corrected) } }
            }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: "Search failed" }
        loading = false
    }
    fun commit() { query.trim().takeIf { it.isNotEmpty() }?.let(Settings::addRecentSearch); focus.clearFocus() }

    Column(Modifier.fillMaxSize().statusPad()) {
        if (!focused && query.isEmpty()) PageTitle("Search")
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(40.dp).glass(RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Search, c.onSecondary, 18.dp)
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = Type.body.copy(color = c.on, fontSize = Type.body.fontSize * 1.07f), cursorBrush = SolidColor(c.on),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { commit() }),
                    modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (query.isEmpty()) Txt("Songs, albums, artists", Type.body, color = c.onSecondary); inner() } },
                )
                if (query.isNotEmpty()) Box(Modifier.size(24.dp).clip(CircleShape).background(c.onTertiary).press("Clear search") { query = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Close, c.bg, 12.dp)
                }
            }
            if (focused || query.isNotEmpty()) Box(Modifier.height(44.dp).padding(horizontal = 8.dp).press("Cancel") { query = ""; focus.clearFocus() }, contentAlignment = Alignment.Center) {
                Txt("Cancel", Type.body.copy(fontSize = Type.body.fontSize * 1.07f))
            }
        }
        val r = SearchState.results
        LazyColumn(
            state = state, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Dock.bottomPadding(Playback.current != null)),
        ) {
            if (query.isBlank()) recents { query = it; Settings.addRecentSearch(it) }
            else {
                item("kinds", "kinds") {
                    val chips = rememberLazyListState()
                    // a filter picked from an All group's header: bring its chip into view if it's off the edge
                    LaunchedEffect(kind) {
                        val i = KINDS.indexOfFirst { it.first == kind }
                        val vis = chips.layoutInfo.visibleItemsInfo.find { it.index == i }
                        if (vis == null || vis.offset < 0 || vis.offset + vis.size > chips.layoutInfo.viewportEndOffset) chips.animateScrollToItem(i)
                    }
                    LazyRow(state = chips, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(KINDS, key = { it.first }) { (k, label) -> Pill(label, k == kind) { kind = k } }
                    }
                }
                val e = error
                when {
                    e != null && r == null -> item("err", "err") { Failed(e) { error = null; attempt++ } }
                    r == null -> items(8, key = { "s$it" }, contentType = { "skeleton" }) { SkeletonRow() }
                    else -> results(r, ::commit, { exact = true }) { kind = it }
                }
            }
        }
    }
}

/** A filter: its own request. All: the unfiltered request (only for YouTube's top result) plus every filter's, at once. */
private suspend fun search(q: String, kind: String, exact: Boolean): Results = withContext(Dispatchers.IO) {
    if (kind != "all") return@withContext Results(q, kind, exact, YouTube.searchKind(q, kind, exact))
    val top = async { YouTube.searchTop(q, exact) }
    val parts = KINDS.drop(1).map { (k, _) -> k to async { runCatching { YouTube.searchKind(q, k, exact) }.getOrNull() } }
    val (best, fixed) = top.await()
    Results(q, kind, exact, top = best, corrected = fixed, parts = parts.mapNotNull { (k, d) -> d.await()?.let { k to it } }.toMap())
}

private fun androidx.compose.foundation.lazy.LazyListScope.recents(pick: (String) -> Unit) {
    val recent = Settings.recentSearches.lines().filter { it.isNotBlank() }.take(6)
    val played = Settings.picks()
    if (recent.isEmpty() && played.isEmpty()) {
        item("hint", "hint") { EmptyState(Icons.Search, "Find any song", "Search YouTube Music — songs, albums, artists and playlists.") }
        return
    }
    if (recent.isNotEmpty()) {
        item("rh", "header") { RecentHeader("Recent searches") { Settings.recentSearches = "" } }
        items(recent, key = { "r:$it" }, contentType = { "recent" }) { q ->
            Row(
                Modifier.animateItem().fillMaxWidth().height(52.dp).press(q, scale = 0.985f) { pick(q) }.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Search, WT.c.onSecondary, 18.dp)
                Txt(q, Type.row, Modifier.weight(1f))
                IconBtn(Icons.Close, "Remove $q", tint = WT.c.onSecondary, size = 14.dp) { Settings.removeRecentSearch(q) }
            }
        }
    }
    if (played.isNotEmpty()) {
        item("ph", "header") { RecentHeader("From your searches") { Settings.searchPicks = "" } }
        items(played, key = { "p:" + recentKey(it) }, contentType = { it.javaClass.simpleName }) { x ->
            Box(Modifier.animateItem()) {
                when (x) {
                    is Track -> TrackRow(x) { Settings.addPick(x); Playback.startRadio(x) }
                    is Video -> VideoRow(x.track) { Settings.addPick(x); Playback.startRadio(x.track) }
                    is Card -> CardRow(x) { Settings.addPick(x); Nav.open(Route.Collection(x)) }
                }
            }
        }
    }
}

@Composable
private fun RecentHeader(title: String, clear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, Type.section, Modifier.weight(1f))
        Box(Modifier.height(44.dp).padding(horizontal = 8.dp).press("Clear ${title.lowercase()}", onClick = clear), contentAlignment = Alignment.Center) {
            Txt("Clear", Type.body, color = WT.c.onSecondary)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.results(r: Results, commit: () -> Unit, exact: () -> Unit, more: (String) -> Unit) {
    val all = r.kind == "all"
    // All shows the start of each filter's own results, so a group and its tab always agree
    fun part(kind: String) = if (all) r.parts[kind] else r.items.takeIf { r.kind == kind }
    val songs = part("songs")?.tracks.orEmpty()
    val albums = part("albums")?.cards.orEmpty()
    val artists = part("artists")?.cards.orEmpty()
    val playlists = part("playlists")?.cards.orEmpty()
    val videos = part("videos")?.tracks.orEmpty()
    val fixed = r.corrected
    if (fixed != null) item("fixed", "fixed") {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
            Txt("Showing results for “$fixed”", Type.sub, color = WT.c.onSecondary)
            Box(Modifier.height(36.dp).press("Search instead for ${r.query}", onClick = exact), contentAlignment = Alignment.CenterStart) {
                Txt("Search instead for “${r.query}”", Type.sub.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
    // what's played or opened here is kept for the empty Search page ("From your searches")
    val open = { card: Card -> commit(); Settings.addPick(card); Nav.open(Route.Collection(card)) }
    val play = { t: Track, video: Boolean -> commit(); Settings.addPick(if (video) Video(t) else t); Playback.startRadio(t) }
    val top = r.top
    if (top == null && listOf(songs, albums, artists, playlists, videos).all { it.isEmpty() }) {
        item("none", "none") { EmptyState(Icons.Search, "No results", "Nothing found for “${r.query}”. Check the spelling or try other words.") }
        return
    }
    if (top != null) {
        item("toph", "header") { Txt("Top result", Type.section.copy(fontSize = Type.section.fontSize * 0.95f), Modifier.padding(start = 16.dp, top = 6.dp, bottom = 10.dp)) }
        item("top", "top") {
            if (top is Track) TopResult(top.title, listOf(top.artist, top.views).filter { it.isNotEmpty() }.joinToString(" · "), top.art, round = false, playable = true) { play(top, top.views.isNotEmpty()) } // a video's line counts views, a song's plays
            else if (top is Card) TopResult(top.title, top.subtitle, top.art, top.subtitle.startsWith("Artist") || top.browseId.startsWith("UC"), playable = false) { open(top) }
        }
    }
    // All: each group under a header that opens its filter, in its own look; a filter is a plain list of everything
    fun <T> group(title: String, kind: String, list: List<T>, cap: Int = Int.MAX_VALUE): List<T> {
        if (all && list.isNotEmpty()) item("h:$kind", "header") { SectionHeader(title, onMore = { more(kind) }) }
        return if (all) list.take(cap) else list
    }

    items(group("Songs", "songs", songs, ALL_CAP), key = { "songs:" + it.id }, contentType = { "track" }) { TrackRow(it) { play(it, false) } }

    val shownAlbums = group("Albums", "albums", albums, ALL_CAP)
    if (!all) items(shownAlbums, key = { "albums:" + it.browseId }, contentType = { "meta" }) { MetaRow(it) { open(it) } }
    else if (shownAlbums.isNotEmpty()) item("albums", "albumrow") {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shownAlbums, key = { it.browseId }, contentType = { "tile" }) { card -> Tile(card, size = 116.dp) { open(card) } }
        }
    }

    val shownArtists = group("Artists", "artists", artists, ALL_CAP)
    if (!all) items(shownArtists, key = { "artists:" + it.browseId }, contentType = { "card" }) { CardRow(it) { open(it) } }
    else if (shownArtists.isNotEmpty()) item("artists", "portraits") { Portraits(shownArtists, open) { more("artists") } }

    val shownPlaylists = group("Playlists", "playlists", playlists, ALL_CAP)
    if (!all) items(shownPlaylists, key = { "playlists:" + it.browseId }, contentType = { "meta" }) { MetaRow(it) { open(it) } }
    else if (shownPlaylists.isNotEmpty()) item("playlists", "tilerow") {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shownPlaylists, key = { it.browseId }, contentType = { "tile" }) { card -> Tile(card.copy(subtitle = ""), size = 140.dp) { open(card) } }
        }
    }

    items(group("Videos", "videos", videos, ALL_CAP), key = { "videos:" + it.id }, contentType = { "video" }) { VideoRow(it) { play(it, true) } }
}

/**
 * Artists as round portraits scrolling sideways, ending in a small "See all" circle. Dragging on past the end grows
 * the circle and, far enough, opens the Artists filter ([seeAll]); tapping the circle does the same.
 */
@Composable
private fun Portraits(cards: List<Card>, open: (Card) -> Unit, seeAll: () -> Unit) {
    val reach = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    val pull = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val pullEnd = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                // what the row couldn't scroll past its end, while the finger is down
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.x < 0) {
                    pull.floatValue -= available.x
                    if (pull.floatValue >= reach) { pull.floatValue = 0f; seeAll() }
                } else if (consumed.x != 0f) pull.floatValue = 0f
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity { pull.floatValue = 0f; return androidx.compose.ui.unit.Velocity.Zero }
        }
    }
    LazyRow(
        Modifier.nestedScroll(pullEnd),
        contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(cards, key = { it.browseId }, contentType = { "artist" }) { a ->
            Column(Modifier.width(88.dp).press(a.title) { open(a) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(a.art, 88.dp, 44.dp)
                Spacer(Modifier.height(8.dp))
                Txt(a.title, Type.tile.copy(textAlign = TextAlign.Center), Modifier.fillMaxWidth())
            }
        }
        item("all", "seeall") {
            Column(Modifier.width(88.dp).press("See all artists", onClick = seeAll), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(48.dp).graphicsLayer { val s = 1f + 0.35f * (pull.floatValue / reach).coerceIn(0f, 1f); scaleX = s; scaleY = s }
                            .background(WT.c.chip, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Chevron, WT.c.on, 18.dp) }
                }
                Spacer(Modifier.height(8.dp))
                Txt("See all", Type.tile.copy(textAlign = TextAlign.Center), Modifier.fillMaxWidth(), color = WT.c.onSecondary)
            }
        }
    }
}

/**
 * An album or playlist in its filter's list: cover, title, and one short line: who, year and song count, without
 * the "Album" / "Playlist" label the tab already gives. Albums' search results carry no song count, so once the row
 * is on screen it comes from the page's header ([YouTube.totals], one request, kept for the session).
 */
@Composable
private fun MetaRow(card: Card, onClick: () -> Unit) {
    val segs = card.subtitle.split(" • ").filter { it !in LABELS && !it.endsWith(" views") }
    val given = segs.firstOrNull { it.matches(SONGS) }
    var count by remember(card.browseId) { mutableStateOf(given ?: SearchState.totals[card.browseId]) }
    LaunchedEffect(card.browseId) {
        if (count != null) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { YouTube.totals(card) } }.getOrNull()
            ?.split(" · ")?.firstOrNull { it.matches(SONGS) }?.let { SearchState.totals[card.browseId] = it; count = it }
    }
    Row(
        Modifier.fillMaxWidth().height(66.dp).press(card.title, scale = 0.985f, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(card.art, 56.dp, 6.dp)
        Column(Modifier.weight(1f)) {
            Txt(card.title, Type.row.copy(fontSize = Type.row.fontSize * 1.07f))
            Txt((segs.filter { it != given } + listOfNotNull(count)).joinToString(" · "), Type.sub, color = WT.c.onSecondary)
        }
    }
}

private val LABELS = setOf("Album", "Playlist")
private val SONGS = Regex("[\\d,.]+[KM]?\\+? songs?", RegexOption.IGNORE_CASE)

/** A video as a row: its 16:9 frame with the length on its corner, then title and "channel · views" (search gives no like counts). */
@Composable
private fun VideoRow(t: Track, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(80.dp).press(t.title, onLongClick = { TrackMenu.show(t) }, scale = 0.985f, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AsyncImage(t.art, null, Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(8.dp)).background(WT.c.surface), contentScale = ContentScale.Crop)
            if (t.durationMs > 0) Txt(
                fmt(t.durationMs), Type.caption, Modifier.padding(4.dp).background(androidx.compose.ui.graphics.Color(0xB3000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
        Column(Modifier.weight(1f)) {
            Txt(t.title, Type.row)
            Txt(listOf(t.artist, t.views).filter { it.isNotEmpty() }.joinToString(" · "), Type.sub, color = WT.c.onSecondary)
        }
    }
}

private const val ALL_CAP = 7 // items per group in All; the group's header (and its tab) has the rest

@Composable
private fun TopResult(title: String, subtitle: String, art: String?, round: Boolean, playable: Boolean, onClick: () -> Unit) {
    val c = WT.c
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(14.dp)).press(title, scale = 0.98f, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cover(art, 72.dp, if (round) 36.dp else 8.dp)
        Column(Modifier.weight(1f)) {
            Txt(title, Type.title.copy(fontSize = Type.title.fontSize * 0.9f, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(2.dp))
            Txt(subtitle, Type.sub, color = c.onSecondary)
        }
        Box(Modifier.size(44.dp).background(c.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(if (playable) Icons.Play else Icons.Chevron, c.onPrimary, 18.dp) }
    }
}
