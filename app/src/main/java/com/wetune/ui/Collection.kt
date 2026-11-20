package com.wetune.ui

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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

@Composable
fun CollectionScreen(card: Card) {
    val c = WT.c
    val id = card.browseId
    val artist = id.startsWith("UC")
    val album = id.startsWith("MPRE")
    val page = load(id, PageCache.get(id)) {
        if (id.startsWith("local-")) YouTube.Page(emptyList(), emptyList()) // made offline, not on YouTube yet
        else YouTube.page(card).also { PageCache.put(id, it) }
    }
    val p = page.value
    val owned = id.startsWith("local-") || (p != null && p.saveId == null && Sync.playlists.any { it.browseId == id })
    // the user's own playlist shows this device's unsent adds / removes (Sync.pending changes when they do)
    val tracks = remember(p, Sync.pending) { p?.tracks?.let { if (owned) Sync.overlay(id, it) else it }.orEmpty() }
    val from = (if (artist) "Artist" else if (album) "Album" else "Playlist") to card.title
    val state = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Dock.bottomPadding(Playback.current != null))) {
            item("header", "header") {
                Header(card, artist, tracks, p, from)
            }
            when {
                p == null && page.error != null -> item("err", "err") { Failed(page.error!!) { page.retry() } }
                p == null -> items(8, key = { "s$it" }, contentType = { "skeleton" }) { SkeletonRow() }
                artist -> {
                    if (tracks.isNotEmpty()) {
                        item("top", "header") {
                            SectionHeader("Top songs", onMore = if (tracks.size > 5) ({ Nav.open(Route.Shelf("${card.title} · Songs", tracks, emptyList())) }) else null)
                        }
                        itemsIndexed(tracks.take(5), key = { _, t -> "t:" + t.id }, contentType = { _, _ -> "track" }) { i, t -> TrackRow(t) { Playback.play(tracks, i, from = from) } }
                    }
                    p.sections.forEachIndexed { i, s ->
                        item("sh$i", "header") { SectionHeader(s.title) }
                        item("sc$i", "tiles") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(s.cards, key = { it.browseId }, contentType = { "tile" }) { x -> Tile(x) { Nav.open(Route.Collection(x)) } }
                            }
                        }
                    }
                }
                tracks.isEmpty() -> item("empty", "empty") { EmptyState(Icons.Library, "No songs yet", "Add songs from any song's menu. They sync with YouTube Music.") }
                else -> itemsIndexed(tracks, key = { i, t -> "t:$i:" + t.id }, contentType = { _, _ -> "track" }) { i, t ->
                    val play = { Playback.play(tracks, i, from = from) }
                    if (album) TrackRow(t, index = i + 1, trailing = { Txt(time(t.durationMs), Type.sub, color = c.onSecondary) }, onClick = play)
                    else TrackRow(t, trailing = {
                        IconBtn(Icons.More, "More for ${t.title}", tint = c.onSecondary, size = 18.dp) { Sheet.show { TrackMenuBody(t, id.takeIf { owned }) } }
                    }, onClick = play)
                }
            }
        }
        // fixed Back / More over the page
        Row(Modifier.fillMaxWidth().statusPad().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconBtn(Icons.Back, "Back", circle = true, size = 16.dp) { Nav.back() }
            IconBtn(Icons.More, "More", circle = true, size = 16.dp) { Sheet.show { PageMenu(card, tracks, owned, from) } }
        }
    }
}

@Composable
private fun Header(card: Card, artist: Boolean, tracks: List<Track>, p: YouTube.Page?, from: Pair<String, String>) {
    val c = WT.c
    val width = LocalConfiguration.current.screenWidthDp.dp
    val art = p?.art ?: card.art
    Box {
        // faint, desaturated cover glow behind the top of the page (spec: cover colour only as a glow). Its blur runs
        // past its own edges and a gradient melts it into the background, so it never ends in a line.
        if (blurSupported) Box(Modifier.matchParentSize().clipToBounds()) {
            AsyncImage(
                art, null, contentScale = ContentScale.Crop,
                colorFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.8f) }) },
                modifier = Modifier.size(width, 420.dp).graphicsLayer { alpha = if (c.dark) 0.35f else 0.25f; scaleX = 1.6f; scaleY = 1.6f }
                    .blur(80.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
            )
            Box(Modifier.matchParentSize().background(remember(c) { Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to c.bg) }))
        }
        Column(Modifier.fillMaxWidth().statusPad().padding(start = 16.dp, end = 16.dp, top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Cover(art, 240.dp, if (artist) 120.dp else 12.dp, description = "Artwork: ${card.title}")
            Spacer(Modifier.height(16.dp))
            Txt(card.title, Type.title.copy(fontSize = Type.title.fontSize * 1.05f, textAlign = TextAlign.Center), lines = 2)
            val by = subtitleArtist(card.subtitle)
            if (!artist && by.isNotEmpty()) Txt(
                by, Type.row.copy(fontSize = Type.row.fontSize * 1.2f, textDecoration = TextDecoration.Underline),
                Modifier.padding(top = 2.dp).then(if (tracks.isNotEmpty() && tracks[0].source == "yt") Modifier.press("Go to artist $by") { openArtist(tracks[0]) } else Modifier),
                color = if (c.dark) androidx.compose.ui.graphics.Color(0xFFEBEBF0) else c.on,
            )
            val meta = buildList {
                if (artist) add("Artist") else add(card.subtitle.substringBefore(" • ").takeIf { it in setOf("Album", "Single", "EP", "Playlist") } ?: "Playlist")
                YEAR.find(card.subtitle)?.let { add(it.value) }
                if (tracks.isNotEmpty() && !artist) {
                    val mins = tracks.sumOf { it.durationMs } / 60_000
                    add("${tracks.size} song${if (tracks.size == 1) "" else "s"}" + if (mins > 0) ", $mins min" else "")
                }
            }.joinToString(" · ")
            Txt(meta, Type.sub, Modifier.padding(top = 4.dp), color = c.onSecondary)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Play", Icons.Play, primary = true, modifier = Modifier.weight(1f)) { if (tracks.isNotEmpty()) Playback.play(tracks, 0, from = from) }
                BigButton("Shuffle", Icons.Shuffle, primary = false, modifier = Modifier.weight(1f)) { if (tracks.isNotEmpty()) Playback.play(tracks.shuffled(), 0, from = from) }
            }
            if (!artist && p != null) SavedRow(card, p, tracks)
        }
    }
}

/** "Saved to library · synced" with the save toggle and download-all (R11-A06). */
@Composable
private fun SavedRow(card: Card, p: YouTube.Page, tracks: List<Track>) {
    val c = WT.c
    val saved = Sync.isSaved(card)
    val canSave = InnerTube.signedIn && p.saveId != null
    val text = when {
        !InnerTube.signedIn -> null
        saved && Sync.pending > 0 -> "Saved to library · syncing"
        saved && p.saveId == null -> "In your library · synced"
        saved -> "Saved to library · synced"
        canSave -> "Not in your library"
        else -> null
    }
    val allDownloaded = tracks.isNotEmpty() && tracks.all { Downloads.file(it).exists() }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (text != null) { if (saved) Icon(Icons.Synced, c.success, 16.dp); Txt(text, Type.sub, color = c.onSecondary) }
        }
        if (canSave) IconBtn(if (saved) Icons.Check else Icons.Plus, if (saved) "Remove from library" else "Save to library", circle = true, size = 16.dp) {
            Sync.setSaved(card, p.saveId!!, !saved)
        }
        if (tracks.isNotEmpty()) IconBtn(if (allDownloaded) Icons.Downloaded else Icons.Download, if (allDownloaded) "Downloaded" else "Download all", tint = if (allDownloaded) c.on else c.onSecondary, circle = true, size = 16.dp) {
            if (!allDownloaded) { tracks.forEach { Downloads.download(it) }; toast("Downloading ${tracks.size} songs") }
        }
    }
}

@Composable
private fun PageMenu(card: Card, tracks: List<Track>, owned: Boolean, from: Pair<String, String>) {
    SheetTitle(card.title)
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

/** A "see all" list: songs, then albums / playlists as a two-column grid. */
@Composable
fun ShelfScreen(r: Route.Shelf) {
    val gap = 12.dp
    val tile = (LocalConfiguration.current.screenWidthDp.dp - 32.dp - gap) / 2
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = statusTop(), bottom = Dock.bottomPadding(Playback.current != null))) {
        item("back", "back") { BackLink("Back") }
        item("title", "title") { Txt(r.title, Type.largeTitle, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), lines = 2) }
        itemsIndexed(r.tracks, key = { i, t -> "t:$i:" + t.id }, contentType = { _, _ -> "track" }) { i, t -> TrackRow(t) { Playback.play(r.tracks, i, from = "Playlist" to r.title) } }
        items(r.cards.chunked(2), key = { it.first().browseId }, contentType = { "tiles" }) { pair ->
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                pair.forEach { x -> Tile(x, size = tile) { Nav.open(Route.Collection(x)) } }
            }
        }
        if (r.tracks.isEmpty() && r.cards.isEmpty()) item("empty", "empty") { EmptyState(Icons.Library, "Nothing here yet", "Songs you add show up here.") }
    }
}
