package com.wetune.ui

import android.text.format.DateUtils
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wetune.Library
import com.wetune.Playback
import com.wetune.sources.Card
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Sync

private val SEGMENTS = listOf("Playlists", "Albums", "Artists", "Songs", "Downloads")

@Composable
fun LibraryScreen() {
    val c = WT.c
    val state = rememberLazyListState()
    ReselectToTop(state)
    var seg by rememberSaveable { mutableIntStateOf(0) }
    val signedIn = InnerTube.signedIn
    val liked = if (signedIn) Sync.liked + Library.liked.filter { it.source != "yt" } else Library.liked
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = statusTop(), bottom = Dock.bottomPadding(Playback.current != null))) {
            item("title", "title") {
                PageTitle("Library") {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c.chip).press("New playlist") { newPlaylist() }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Plus, c.on, 18.dp)
                        }
                    }
                }
            }
            item("status", "status") { SyncStatus(Modifier.padding(start = 16.dp, top = 4.dp)) }
            if (!signedIn) item("signin", "signin") { SignInCard() }
            item("segments", "segments") { Segments(SEGMENTS, seg, { seg = it }, Modifier.padding(top = 16.dp, bottom = 8.dp)) }
            when (seg) {
                0 -> {
                    item("liked", "liked") {
                        Group(Modifier.padding(top = 8.dp)) {
                            GroupRow("Liked songs", ic = Icons.Heart, value = "${liked.size}", last = true) { seg = 3 }
                        }
                    }
                    val yt = Sync.playlists
                    val local = Library.playlists
                    if (yt.isEmpty() && local.isEmpty()) item("empty", "empty") {
                        EmptyState(Icons.Library, "No playlists yet", "Make one here, or on YouTube Music — it shows up in both.", "New playlist") { newPlaylist() }
                    } else {
                        item("ph", "header") { SectionHeader("Your playlists") }
                        // YouTube playlists and the ones on this device, in the chosen layout (list or grid)
                        val all = yt.map { it to { Nav.open(Route.Collection(it)) } } + local.map { p ->
                            Card("local:" + p.name, p.name, "On this device · ${p.tracks.size} songs", p.tracks.firstOrNull()?.art) to { Nav.open(Route.Shelf(p.name, p.tracks, emptyList())) }
                        }
                        laidOut(all)
                    }
                }
                1 -> cards(Sync.albums, Icons.Album, "No saved albums", "Albums you save here or on YouTube Music show up here.")
                2 -> cards(Sync.artists, Icons.Artist, "No artists yet", "Artists from songs you like and subscribe to show up here.")
                3 -> if (liked.isEmpty()) item("empty", "empty") {
                    EmptyState(Icons.HeartOutline, "No liked songs", "Tap the heart on any song. Likes sync with YouTube Music both ways.")
                } else itemsIndexed(liked, key = { _, t -> "l:" + t.id }, contentType = { _, _ -> "track" }) { i, t ->
                    TrackRow(t) { Playback.play(liked, i, from = "Playlist" to "Liked songs") }
                }
                else -> {
                    val d = Library.downloads
                    if (d.isEmpty()) item("empty", "empty") {
                        EmptyState(Icons.Download, "Nothing downloaded yet", "Download songs, albums or playlists to listen when you're offline.", "Browse music") { Nav.select(Tab.Home) }
                    } else itemsIndexed(d, key = { _, t -> "d:" + t.id }, contentType = { _, _ -> "track" }) { i, t ->
                        TrackRow(t) { Playback.play(d, i, from = "Playlist" to "Downloads") }
                    }
                }
            }
        }
        CollapsedBar("Library", state)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.cards(list: List<Card>, ic: Ic, title: String, text: String) =
    if (list.isEmpty()) item("empty", "empty") { EmptyState(ic, title, text) }
    else laidOut(list.map { it to { Nav.open(Route.Collection(it)) } })

/**
 * Albums, artists and playlists in the layout chosen in Settings › General › Library layout: rows (cover, title,
 * subtitle), or a grid of covers two or three across. Grid rows are lazy like list rows.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.laidOut(list: List<Pair<Card, () -> Unit>>) {
    val across = when (com.wetune.Settings.libraryLayout) { "grid" -> 2; "grid3" -> 3; else -> 0 }
    if (across == 0) items(list, key = { it.first.browseId }, contentType = { "card" }) { (c, open) -> CardRow(c, open) }
    else items(list.chunked(across), key = { row -> "g:" + row.first().first.browseId }, contentType = { "grid" }) { row ->
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val gap = 12.dp
            val size = (maxWidth - gap * (across - 1)) / across
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { (c, open) -> Tile(c, size = size, onClick = open) }
            }
        }
    }
}

private fun newPlaylist() = Sheet.show {
    NewPlaylistBody { name -> if (InnerTube.signedIn) Sync.createPlaylist(name) else Library.createPlaylist(name) }
}

/** "In sync with YouTube Music · 2 min ago" / "3 changes waiting to sync" / "Syncing…" (R11-A03). */
@Composable
fun SyncStatus(modifier: Modifier = Modifier) {
    val c = WT.c
    val (ic, text) = when {
        !InnerTube.signedIn -> Icons.Phone to "Saved on this device"
        Sync.expired -> Icons.Offline to "Sign-in expired — sign in again from You"
        Sync.pending > 0 -> Icons.Offline to "${Sync.pending} change${if (Sync.pending == 1) "" else "s"} waiting to sync"
        Sync.pulling -> Icons.Synced to "Syncing with YouTube Music…"
        Sync.lastPull > 0 -> Icons.Synced to "In sync with YouTube Music · ${DateUtils.getRelativeTimeSpanString(Sync.lastPull, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}"
        else -> Icons.Synced to "Syncing with YouTube Music…"
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(ic, if (Sync.pending > 0 || Sync.expired) c.onSecondary else c.success, 16.dp)
        Txt(text, Type.sub, color = c.onSecondary)
    }
}

/** Signed-out prompt: everything works on the device; signing in syncs it with YouTube Music. */
@Composable
fun SignInCard() {
    val c = WT.c
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().glass(RoundedCornerShape(12.dp)).padding(14.dp)) {
        Txt("Sync with YouTube Music", Type.button)
        Spacer(Modifier.height(4.dp))
        Txt("Sign in and your likes, playlists and albums stay the same here and on YouTube Music.", Type.sub, color = c.onSecondary, lines = 3)
        Spacer(Modifier.height(12.dp))
        BigButton("Sign in", Icons.You, primary = true, modifier = Modifier.fillMaxWidth().height(44.dp)) { Nav.open(Route.Login) }
    }
}
