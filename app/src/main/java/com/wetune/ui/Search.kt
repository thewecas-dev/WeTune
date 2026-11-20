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
import androidx.compose.ui.unit.dp
import com.wetune.Playback
import com.wetune.Settings
import com.wetune.sources.Card
import com.wetune.sources.Track
import com.wetune.youtube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val KINDS = listOf("all" to "All", "songs" to "Songs", "albums" to "Albums", "artists" to "Artists", "playlists" to "Playlists", "videos" to "Videos")

private data class Results(val query: String, val kind: String, val tracks: List<Track>, val cards: List<Card>)

/** Results kept for the session, so coming back to the tab shows the last search at once. */
private object SearchState {
    var results by mutableStateOf<Results?>(null)
    val byKind = HashMap<String, Results>() // each filter's results for the current query; cleared when the query changes
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
    val state = rememberLazyListState()
    ReselectToTop(state)

    // typing is debounced and keeps the previous results until the new ones arrive; a filter already fetched for this
    // query comes back at once, a new one shows placeholders (never another filter's results)
    LaunchedEffect(query, kind, attempt) {
        val q = query.trim()
        if (q.isEmpty()) { error = null; return@LaunchedEffect }
        val r = SearchState.results
        if (r != null && r.query == q && r.kind == kind) return@LaunchedEffect
        if (SearchState.byKind.values.firstOrNull()?.query != q) SearchState.byKind.clear()
        SearchState.byKind[kind]?.let { SearchState.results = it; error = null; state.scrollToItem(0); return@LaunchedEffect }
        if (r != null && r.query == q) SearchState.results = null else delay(350)
        loading = true
        runCatching { search(q, kind) }
            .onSuccess { SearchState.byKind[kind] = it; SearchState.results = it; error = null; state.scrollToItem(0) }
            .onFailure { error = it.message ?: "Search failed" }
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
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(KINDS, key = { it.first }) { (k, label) -> Pill(label, k == kind) { kind = k } }
                    }
                }
                val e = error
                when {
                    e != null && r == null -> item("err", "err") { Failed(e) { error = null; attempt++ } }
                    r == null -> items(8, key = { "s$it" }, contentType = { "skeleton" }) { SkeletonRow() }
                    else -> results(r, ::commit)
                }
            }
        }
    }
}

private suspend fun search(q: String, kind: String): Results = if (kind == "all") coroutineScope {
    val cards = async(Dispatchers.IO) { YouTube.searchCards(q) }
    val songs = async(Dispatchers.IO) { YouTube.search(q) }
    Results(q, kind, songs.await(), cards.await())
} else withContext(Dispatchers.IO) { YouTube.searchKind(q, kind).let { Results(q, kind, it.tracks, it.cards) } }

private fun androidx.compose.foundation.lazy.LazyListScope.recents(pick: (String) -> Unit) {
    val recent = Settings.recentSearches.lines().filter { it.isNotBlank() }
    if (recent.isEmpty()) {
        item("hint", "hint") { EmptyState(Icons.Search, "Find any song", "Search YouTube Music — songs, albums, artists and playlists.") }
        return
    }
    item("rh", "header") {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Recent", Type.section, Modifier.weight(1f))
            Box(Modifier.height(44.dp).padding(horizontal = 8.dp).press("Clear recent searches") { Settings.recentSearches = "" }, contentAlignment = Alignment.Center) {
                Txt("Clear", Type.body, color = WT.c.onSecondary)
            }
        }
    }
    items(recent, key = { "r:$it" }, contentType = { "recent" }) { q ->
        Row(
            Modifier.fillMaxWidth().height(52.dp).press(q, scale = 0.985f) { pick(q) }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Search, WT.c.onSecondary, 18.dp)
            Txt(q, Type.row)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.results(r: Results, commit: () -> Unit) {
    if (r.tracks.isEmpty() && r.cards.isEmpty()) {
        item("none", "none") { EmptyState(Icons.Search, "No results", "Nothing found for “${r.query}”. Check the spelling or try other words.") }
        return
    }
    val top = if (r.kind == "all") r.cards.firstOrNull() else null
    if (top != null) {
        item("toph", "header") { Txt("Top result", Type.section.copy(fontSize = Type.section.fontSize * 0.95f), Modifier.padding(start = 16.dp, top = 6.dp, bottom = 10.dp)) }
        item("top", "top") { TopResult(top) { commit(); Nav.open(Route.Collection(top)) } }
    }
    if (r.tracks.isNotEmpty()) {
        if (r.kind == "all") item("sh", "header") { SectionHeader("Songs") }
        items(r.tracks, key = { "t:" + it.id }, contentType = { "track" }) { t ->
            TrackRow(t) { commit(); Playback.startRadio(t) }
        }
    }
    val rest = r.cards.filter { it != top }
    if (rest.isNotEmpty()) {
        if (r.kind == "all") item("ch", "header") { SectionHeader("Albums, artists & playlists") }
        items(rest, key = { "c:" + it.browseId }, contentType = { "card" }) { card -> CardRow(card) { commit(); Nav.open(Route.Collection(card)) } }
    }
}

@Composable
private fun TopResult(card: Card, onClick: () -> Unit) {
    val c = WT.c
    val round = card.subtitle.startsWith("Artist") || card.browseId.startsWith("UC")
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(14.dp)).press(card.title, scale = 0.98f, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cover(card.art, 72.dp, if (round) 36.dp else 8.dp)
        Column(Modifier.weight(1f)) {
            Txt(card.title, Type.title.copy(fontSize = Type.title.fontSize * 0.9f, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(2.dp))
            Txt(card.subtitle, Type.sub, color = c.onSecondary)
        }
        Box(Modifier.size(44.dp).background(c.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Chevron, c.onPrimary, 18.dp) }
    }
}
