package com.wetune.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.wetune.Library
import com.wetune.Playback
import com.wetune.Resolver
import com.wetune.Settings
import com.wetune.io
import com.wetune.sources.Card
import com.wetune.sources.Local
import com.wetune.sources.Track
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Section
import com.wetune.youtube.Sync
import com.wetune.youtube.YouTube
import java.util.Calendar

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data object Settings : Screen
    data object Login : Screen
    data class SettingsPage(val id: String) : Screen
    data class Yt(val card: Card) : Screen
    data class LocalPlaylist(val name: String) : Screen
}

/** A search to run when the Search tab opens (set from the player when you tap an artist or album). */
object SearchRequest {
    var query by mutableStateOf<String?>(null)
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Late night"
}

@Composable
private fun HomeHeader(open: (Screen) -> Unit, reload: (() -> Unit)?) {
    val account = Sync.account
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(greeting(), style = Type.display, maxLines = 1)
            Txt(account?.let { "Welcome back, ${it.name.substringBefore(' ')}" } ?: "Your music, all in one place", style = Type.body, color = WT.c.muted, maxLines = 1)
        }
        if (reload != null) IconBtn(Ic.Refresh, "Refresh", reload)
        if (account != null) {
            Art(account.photo, Modifier.size(40.dp).pressable { open(Screen.SettingsPage("account")) }, CircleShape)
            Spacer(Modifier.width(8.dp))
        } else IconBtn(Ic.Person, "Sign in", { open(Screen.SettingsPage("account")) })
    }
}

/** The Home feed and its scroll position, kept for the whole app session so switching tabs doesn't reload it. */
private object HomeFeed {
    var sections by mutableStateOf<List<Section>?>(null)
    var account: String? = null
    val scroll = androidx.compose.foundation.lazy.LazyListState()

    /** Further pages (trending, new releases…), fetched in the background after the first page is on screen. */
    suspend fun loadMore(token: String?, account: String?) {
        var next = token
        repeat(3) {
            val t = next ?: return
            val (more, after) = runCatching { withContext(Dispatchers.IO) { YouTube.homeMore(t) } }.getOrNull() ?: return
            if (HomeFeed.account != account) return
            sections = (sections.orEmpty() + more).unique()
            next = after
        }
    }
}

// section titles are list keys, so they must be unique
private fun List<Section>.unique() = distinctBy { it.title.ifBlank { it.hashCode().toString() } }

/** Loads once per app start (or per account); after that only the refresh button reloads it. */
@Composable
fun HomeScreen(open: (Screen) -> Unit) {
    val account = Sync.account?.name
    val cached = HomeFeed.sections.takeIf { HomeFeed.account == account }
    if (cached != null) return HomeList(cached, open) { HomeFeed.sections = null }
    Load(account, {
        val (first, token) = YouTube.home()
        HomeFeed.account = account; HomeFeed.sections = first.unique()
        com.wetune.appScope.launch { HomeFeed.loadMore(token, account) }
        first.unique()
    }, placeholder = {
        Column(Modifier.padding(listPadding())) { HomeHeader(open, null); SkeletonCarousels() }
    }) { sections, _ -> HomeList(sections, open) { HomeFeed.sections = null } }
}

@Composable
private fun HomeList(sections: List<Section>, open: (Screen) -> Unit, reload: () -> Unit) {
    // which layout each section gets is worked out once per feed, not on every frame
    val arranged = remember(sections) { arrangeHome(sections) }
    LazyColumn(state = HomeFeed.scroll, contentPadding = listPadding()) {
        item(key = "header") { HomeHeader(open, reload) }
        item(key = "expired") { ExpiredBanner() }
        if (sections.isEmpty()) item { EmptyState(Ic.Search, "Nothing to show yet", "Search for a song to get started.") }
        // each kind of section gets its own layout (quick picks rows, listen-again grid, charts, banners…);
        // keys + content types let scrolling reuse already-built sections of the same kind
        items(arranged, key = { it.first.title }, contentType = { it.second }) { (s, kind) -> HomeSection(s, kind) { c -> open(Screen.Yt(c)) } }
    }
}

@Composable
private fun Pills(options: List<Triple<String, String, ImageVector?>>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (key, label, icon) -> Pill(label, selected == key, { onSelect(key) }, icon = icon) }
    }
}

@Composable
fun SearchScreen(open: (Screen) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("yt") }
    val focus = LocalFocusManager.current
    fun submit(q: String) {
        query = q; submitted = q.trim(); focus.clearFocus()
        if (submitted.isNotBlank()) Settings.addRecentSearch(submitted)
    }
    androidx.compose.runtime.LaunchedEffect(SearchRequest.query) { SearchRequest.query?.let { submit(it); SearchRequest.query = null } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header("Search")
        Field(
            query, { query = it }, "Songs, artists, albums",
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            leading = Ic.Search,
            keyboard = KeyboardOptions(imeAction = ImeAction.Search),
            actions = KeyboardActions(onSearch = { submit(query) }),
            onClear = { query = ""; submitted = "" }, // back to recent searches
        )
        Pills(
            listOf(
                Triple("yt", "Music", Ic.Play), Triple("local", "Device", Ic.Library), Triple("subsonic", "My server", Ic.Server),
            ), source,
        ) { source = it }
        if (submitted.isBlank()) return@Column RecentSearches(::submit)
        Load(source to submitted, {
            val tracks = Resolver.source(source).search(submitted)
            val cards = if (source == "yt") YouTube.searchCards(submitted) else emptyList()
            tracks to cards
        }, placeholder = { SkeletonRows() }) { (tracks, cards), _ ->
            LazyColumn(contentPadding = PaddingValues(bottom = BottomSpace)) {
                tracks.firstOrNull()?.let { top -> item { TopResult(top) { if (source == "yt") Playback.play(listOf(top), from = "Search" to submitted) else Playback.play(tracks, 0, "Search" to submitted) } } }
                if (cards.isNotEmpty()) item { SectionView(Section("Albums, artists & playlists", emptyList(), cards), { open(Screen.Yt(it)) }) }
                if (tracks.isEmpty() && cards.isEmpty()) item { EmptyState(Ic.Search, "No results for \"$submitted\"", "Try different words, or another source above.") }
                if (tracks.isNotEmpty()) item { SectionTitle("Songs") }
                itemsIndexed(tracks, key = { i, t -> "$i:${t.id}" }) { i, t ->
                    // YouTube: play the song and let autoplay fill the queue with its radio.
                    TrackRow(t, { if (source == "yt") Playback.play(listOf(t), from = "Search" to submitted) else Playback.play(tracks, i, "Search" to submitted) })
                }
            }
        }
    }
}

/** The best match as a glass card: cover, title, source, and a play button. */
@Composable
private fun TopResult(t: Track, onPlay: () -> Unit) {
    val c = WT.c
    SectionTitle("Top result")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).glassChip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .pressable(scale = 0.98f, onClick = onPlay).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(t.art, Modifier.size(78.dp), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Txt(t.title, style = Type.title.copy(fontSize = Type.title.fontSize * 0.95f), maxLines = 2)
            Txt("Song · ${t.artist}", Modifier.padding(top = 3.dp), style = Type.caption, color = c.muted, maxLines = 1)
            when (t.source) { "local" -> "On this device"; "subsonic" -> "On your server"; else -> null }?.let { Txt(it, Modifier.padding(top = 8.dp).glassChip(CircleShape).padding(horizontal = 9.dp, vertical = 3.dp), style = Type.overline.copy(letterSpacing = 0.2.sp), color = c.text) }
        }
        Box(Modifier.size(50.dp).clip(CircleShape).background(c.text).pressable(onClick = onPlay).semantics { contentDescription = "Play ${t.title}" }, Alignment.Center) {
            Icon(Ic.Play, null, size = 20.dp, tint = c.bg)
        }
    }
}

@Composable
private fun RecentSearches(onPick: (String) -> Unit) {
    val recent = Settings.recentSearches.lines().filter { it.isNotBlank() }
    if (recent.isEmpty()) return EmptyState(Ic.Search, "Find something to play", "Songs, albums, artists, and music on your device or your own server.")
    LazyColumn(contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Recent searches", Modifier.weight(1f), style = Type.headline)
                Btn("Clear", { Settings.recentSearches = "" }, kind = BtnKind.Ghost)
            }
        }
        items(recent) { q ->
            Row(Modifier.fillMaxWidth().pressable(scale = 0.98f) { onPick(q) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Search, null, size = 18.dp, tint = WT.c.muted)
                Txt(q, Modifier.weight(1f).padding(start = 14.dp), maxLines = 1)
                Icon(Ic.Chevron, null, size = 18.dp, tint = WT.c.muted)
            }
        }
    }
}

@Composable
fun LibraryScreen(open: (Screen) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("liked") }
    var version by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    val signedIn = InnerTube.signedIn
    val openCard: (Card) -> Unit = { open(Screen.Yt(it)) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header("Library") {
            if (tab == "playlists") IconBtn(Ic.Add, "New playlist", { creating = true })
            // refresh this tab (lists are otherwise kept for the session, and fetched fresh when the app starts)
            IconBtn(Ic.Refresh, "Refresh", { LibraryCache.map.keys.removeAll { it.startsWith("$tab:") }; version++ })
        }
        ExpiredBanner()
        Pills(
            listOf(
                Triple("liked", "Liked", Ic.Heart), Triple("playlists", "Playlists", Ic.Queue), Triple("albums", "Albums", Ic.Library),
                Triple("artists", "Artists", Ic.Person), Triple("history", "History", Ic.Timer), Triple("downloads", "Downloads", Ic.Download),
                Triple("device", "Device", Ic.Note),
            ), tab,
        ) { tab = it }
        val needsYt = tab in setOf("albums", "artists", "history")
        when {
            needsYt && !signedIn -> EmptyState(Ic.Person, "Sign in to see this", "Your albums, artists, playlists and history show up here.") {
                Btn("Sign in", { open(Screen.SettingsPage("account")) })
            }
            tab == "liked" -> Cached("liked:$signedIn", { if (signedIn) Sync.likedSongs() else emptyList() }, { SkeletonRows() }) { yt -> TrackList(yt + Library.liked, "Library" to "Liked songs") }
            tab == "history" -> Cached("history:", { Sync.history() }, { SkeletonRows() }) { list -> TrackList(list, "Library" to "Recently played") }
            tab == "downloads" -> if (Library.downloads.isEmpty()) EmptyState(Ic.Download, "No downloads yet", "Use Download in any song's menu to keep it for offline listening.") else TrackList(Library.downloads, "Library" to "Downloads")
            tab == "device" -> Cached("device:", { Local.all() }, { SkeletonRows() }) { list -> TrackList(list, "Library" to "On this device") }
            tab == "albums" -> Cached("albums:", { Sync.albums() }, { SkeletonGrid() }) { list -> CardGrid(list.map { it.entry(openCard) }, "No saved albums") }
            tab == "artists" -> Cached("artists:", { Sync.artists() }, { SkeletonGrid() }) { list -> CardGrid(list.map { it.entry(openCard) }, "No followed artists") }
            tab == "playlists" -> Cached("playlists:$signedIn", { if (signedIn) Sync.playlists() else emptyList() }, { SkeletonGrid() }) { yt ->
                val local = Library.playlists.map { p ->
                    GridEntry("local:${p.name}", p.name, "On this device • ${p.tracks.size} songs", p.tracks.firstOrNull()?.art, false) { open(Screen.LocalPlaylist(p.name)) }
                }
                CardGrid(yt.map { it.entry(openCard) } + local, "No playlists yet. Tap + to create one.")
            }
        }
    }
    if (creating) NewPlaylistSheet(signedIn, onDismiss = { creating = false }) { name, onYouTube ->
        creating = false
        if (onYouTube) io("Created $name", then = { LibraryCache.map.keys.removeAll { it.startsWith("playlists:") }; version++ }) { Sync.createPlaylist(name) } else Library.createPlaylist(name)
    }
}

@Composable
private fun NewPlaylistSheet(canUseYouTube: Boolean, onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var onYouTube by remember { mutableStateOf(canUseYouTube) }
    Sheet(
        "New playlist", onDismiss,
        actions = {
            Btn("Cancel", onDismiss, kind = BtnKind.Ghost)
            Btn("Create", { if (name.isNotBlank()) onCreate(name.trim(), onYouTube) }, enabled = name.isNotBlank())
        },
    ) {
        Field(name, { name = it }, "Name")
        if (canUseYouTube) Row(
            Modifier.fillMaxWidth().padding(top = 12.dp)
                .toggleable(onYouTube, role = Role.Checkbox) { onYouTube = it }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckMark(onYouTube)
            Spacer(Modifier.width(12.dp))
            Txt("Sync to your account")
        }
    }
}

@Composable
fun YtPageScreen(card: Card, open: (Screen) -> Unit, back: () -> Unit) {
    Load(card.browseId, { YouTube.page(card) }, placeholder = {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.padding(horizontal = 4.dp)) { IconBtn(Ic.Back, "Back", back) }
            SkeletonRows()
        }
    }) { page, reload ->
        val editable = card.browseId.startsWith("VLPL") && InnerTube.signedIn
        TrackList(
            page.tracks,
            from = (when { card.browseId.startsWith("MPRE") -> "Album"; card.browseId.startsWith("VL") -> "Playlist"; card.isArtist() -> "Artist"; else -> "Collection" }) to card.title,
            header = {
                PageHeader(card.title, card.subtitle, card.art, card.isArtist(), back, onDelete = if (editable) {
                    { io("Playlist deleted") { Sync.deletePlaylist(card.browseId) }; back() }
                } else null, count = page.tracks.size.takeIf { !card.isArtist() })
            },
            onRemove = if (editable) { t: Track ->
                t.setVideoId?.let { sv -> io("Removed from playlist", then = reload) { Sync.removeFromPlaylist(card.browseId, t.ref, sv) } }
                Unit
            } else null,
            numbered = card.browseId.startsWith("MPRE"),
            extra = { items(page.sections) { s -> SectionView(s, { open(Screen.Yt(it)) }) } },
        )
    }
}

@Composable
fun LocalPlaylistScreen(name: String, back: () -> Unit) {
    val p = Library.playlists.firstOrNull { it.name == name } ?: return EmptyState(Ic.Queue, "This playlist was deleted")
    TrackList(
        p.tracks,
        from = "Playlist" to p.name,
        header = { PageHeader(p.name, "On this device", p.tracks.firstOrNull()?.art, false, back, { Library.deletePlaylist(name); back() }, p.tracks.size) },
        onRemove = { Library.removeFrom(name, it) },
    )
}

/** Library lists already fetched this session, by tab (+ sign-in state); cleared by the tab's refresh button. */
private object LibraryCache { val map = androidx.compose.runtime.mutableStateMapOf<String, Any>() }

/** Shows [key]'s cached list at once; the first time, loads it (with [placeholder]) and keeps it for the session. */
@Composable
private fun <T : Any> Cached(key: String, load: suspend () -> T, placeholder: @Composable () -> Unit, content: @Composable (T) -> Unit) {
    @Suppress("UNCHECKED_CAST") val hit = LibraryCache.map[key] as T?
    if (hit != null) return content(hit)
    Load(key, { load().also { LibraryCache.map[key] = it } }, placeholder = placeholder) { v, _ -> content(v) }
}
