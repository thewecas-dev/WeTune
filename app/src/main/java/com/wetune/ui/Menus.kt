package com.wetune.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wetune.Downloads
import com.wetune.Library
import com.wetune.Playback
import com.wetune.appScope
import com.wetune.sources.Track
import com.wetune.toast
import com.wetune.ui.player.NowPlaying
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Sync
import com.wetune.youtube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Song actions. [playlistId]: the user's YouTube playlist this row belongs to, which adds "Remove from playlist". */
@Composable
fun TrackMenuBody(t: Track, playlistId: String? = null) {
    val c = WT.c
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Cover(t.art, 48.dp, 6.dp)
        Column(Modifier.weight(1f)) {
            Txt(t.title, Type.row.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
            Txt(t.artist, Type.sub, color = c.onSecondary)
        }
    }
    val yt = t.source == "yt"
    MenuRow(Icons.PlayNext, "Play next") { Playback.playNext(t) }
    MenuRow(Icons.Queue, "Add to queue") { Playback.enqueue(t) }
    if (yt) MenuRow(Icons.Radio, "Start radio") { Playback.startRadio(t) }
    val liked = Library.isLiked(t)
    MenuRow(if (liked) Icons.Heart else Icons.HeartOutline, if (liked) "Remove from liked songs" else "Add to liked songs") { Library.toggleLike(t) }
    MenuRow(Icons.AddToPlaylist, "Add to playlist") { Sheet.show { AddToPlaylistBody(t) } }
    if (playlistId != null) MenuRow(Icons.Trash, "Remove from this playlist") { Sync.removeFromPlaylist(playlistId, t) }
    if (yt) {
        MenuRow(Icons.Artist, "Go to artist") { goTo(t, "Artist") }
        MenuRow(Icons.Album, "Go to album") { goTo(t, "Album") }
    }
    if (t.source != "local") when {
        Downloads.file(t).exists() -> MenuRow(Icons.Trash, "Remove download") { Downloads.delete(t) }
        t.id in Downloads.active -> MenuRow(Icons.Download, "Downloading…", enabled = false) {}
        else -> MenuRow(Icons.Download, "Download") { Downloads.download(t) }
    }
}

/** Opens the artist or album page the song links to (looked up on YouTube), closing Now Playing first. */
private fun goTo(t: Track, kind: String) = appScope.launch {
    val card = withContext(Dispatchers.IO) { runCatching { YouTube.links(t.ref) }.getOrDefault(emptyList()) }.firstOrNull { it.subtitle == kind }
        ?: return@launch toast("No ${kind.lowercase()} page for this song")
    if (NowPlaying.isOpen) NowPlaying.close()
    Nav.open(Route.Collection(card))
}

/** YouTube playlists (signed in, YouTube songs) and on-device playlists, plus a new one. */
@Composable
fun AddToPlaylistBody(t: Track) {
    val toYouTube = InnerTube.signedIn && t.source == "yt"
    SheetTitle("Add to playlist")
    MenuRow(Icons.Plus, "New playlist") {
        Sheet.show {
            NewPlaylistBody { name ->
                if (toYouTube) Sync.addToPlaylist(Sync.createPlaylist(name), t) else { Library.createPlaylist(name); Library.addTo(name, t) }
                toast("Added to $name")
            }
        }
    }
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        if (toYouTube) items(Sync.playlists, key = { it.browseId }) { p ->
            MenuRow(Icons.Library, p.title) { Sync.addToPlaylist(p.browseId, t); toast("Added to ${p.title}") }
        }
        items(Library.playlists, key = { "local:" + it.name }) { p ->
            MenuRow(Icons.Library, p.name) { Library.addTo(p.name, t); toast("Added to ${p.name}") }
        }
    }
}

/** Name field + Create; [onCreate] gets the trimmed name. */
@Composable
fun NewPlaylistBody(onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun done() { name.trim().takeIf { it.isNotEmpty() }?.let { Sheet.hide(); onCreate(it) } }
    Column(Modifier.imePadding()) {
        SheetTitle("New playlist")
        Field(
            name, { name = it }, "Playlist name", Modifier.padding(horizontal = 20.dp).focusRequester(focus),
            keyboard = KeyboardOptions(imeAction = ImeAction.Done), actions = KeyboardActions(onDone = { done() }),
        )
        Spacer(Modifier.height(12.dp))
        BigButton("Create", null, primary = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { done() }
    }
}
