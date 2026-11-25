package com.wetune.ui

import androidx.compose.foundation.gestures.detectDragGestures
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
import com.wetune.Settings
import com.wetune.Playback
import com.wetune.appScope
import com.wetune.sources.Track
import com.wetune.toast
import com.wetune.ui.player.NowPlaying
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Sync
import com.wetune.youtube.YouTube
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val acts = songActions(t, playlistId, ctx).associateBy { it.id }
    // the top buttons (Settings › Song menu; Play next · Like · Playlist by default), like the queue's Shuffle · Repeat ·
    // Automix: actions close the menu as its rows do; ones with a state (Like, Download) flip in place and show it
    val pills = Settings.songMenuPills.split(',').mapNotNull { acts[it] }.take(3)
    if (pills.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pills.forEach { a -> com.wetune.ui.player.ToolPill(a.pill, a.on, a.icon, Modifier.weight(1f), enabled = a.enabled) { if (!a.keepOpen) Sheet.hide(); a.run() } }
    }
    // the rest, in the user's order
    songMenuOrder().mapNotNull { acts[it] }.filter { it !in pills }.forEach { a -> MenuRow(a.icon, a.row, a.enabled) { a.run() } }
}

/** One song-menu action. [id] is what Settings stores; [pill] is the short label for a top button; [on] = its state is set. */
class SongAction(
    val id: String, val pill: String, val row: String, val icon: Ic,
    val enabled: Boolean = true, val on: Boolean = false, val keepOpen: Boolean = false, val run: () -> Unit,
)

/** Every action, in the default order: play · keep · go to · share and info · removals last. Names for Settings. */
val SongActionNames = listOf(
    "next" to "Play next", "queue" to "Add to queue", "radio" to "Start radio", "like" to "Like",
    "playlist" to "Add to playlist", "download" to "Download", "artist" to "Go to artist", "album" to "Go to album",
    "share" to "Share", "info" to "Song info", "remove" to "Remove from this playlist",
)

/** The rows' order: the user's (Settings.songMenuOrder), with any action it doesn't list yet in its default place. */
fun songMenuOrder(): List<String> {
    val saved = Settings.songMenuOrder.split(',').filter { id -> SongActionNames.any { it.first == id } }
    return saved + SongActionNames.map { it.first }.filter { it !in saved }
}

/** The actions that apply to [t] (YouTube-only ones, downloads, "remove from this playlist" only inside one). */
@Composable
private fun songActions(t: Track, playlistId: String?, ctx: android.content.Context): List<SongAction> {
    val yt = t.source == "yt"
    val liked = Library.isLiked(t)
    return buildList {
        add(SongAction("next", "Play next", "Play next", Icons.PlayNext) { Playback.playNext(t) })
        add(SongAction("queue", "Queue", "Add to queue", Icons.Queue) { Playback.enqueue(t) })
        if (yt) add(SongAction("radio", "Radio", "Start radio", Icons.Radio) { Playback.startRadio(t) })
        add(SongAction("like", if (liked) "Liked" else "Like", if (liked) "Remove from liked songs" else "Add to liked songs",
            if (liked) Icons.Heart else Icons.HeartOutline, on = liked, keepOpen = true) { Library.toggleLike(t) })
        add(SongAction("playlist", "Playlist", "Add to playlist", Icons.AddToPlaylist, keepOpen = true) { Sheet.show { AddToPlaylistBody(t) } })
        if (t.source != "local") add(when {
            Downloads.file(t).exists() -> SongAction("download", "Saved", "Remove download", Icons.Trash, on = true, keepOpen = true) { Downloads.delete(t) }
            t.id in Downloads.active -> SongAction("download", "Saving…", "Downloading…", Icons.Download, enabled = false) {}
            else -> SongAction("download", "Download", "Download", Icons.Download) { Downloads.download(t) }
        })
        if (yt) {
            add(SongAction("artist", "Artist", "Go to artist", Icons.Artist) { goTo(t, "Artist") })
            add(SongAction("album", "Album", "Go to album", Icons.Album) { goTo(t, "Album") })
        }
        add(SongAction("share", "Share", "Share", Icons.Share) { share(ctx, t) })
        add(SongAction("info", "Info", "Song info", Icons.Info, keepOpen = true) { Sheet.show { SongInfoBody(t) } })
        if (playlistId != null) add(SongAction("remove", "Remove", "Remove from this playlist", Icons.Trash) { Sync.removeFromPlaylist(playlistId, t) })
    }
}

/** Android's share sheet with the song: its YouTube Music link when it has one, else its title and artist. */
private fun share(ctx: android.content.Context, t: Track) {
    val text = "${t.title} · ${t.artist}" + if (t.source == "yt") "\nhttps://music.youtube.com/watch?v=${t.ref}" else ""
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
    ctx.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Song info: what WeTune knows about [t]. */
@Composable
private fun SongInfoBody(t: Track) {
    SheetTitle("Song info")
    val file = Downloads.file(t)
    val source = when (t.source) { "yt" -> "YouTube Music"; "local" -> "On this phone"; "saavn" -> "JioSaavn"; "subsonic" -> "Your server"; else -> t.source }
    val rows = listOfNotNull(
        "Title" to t.title, "Artist" to t.artist, t.album.takeIf { it.isNotBlank() }?.let { "Album" to it },
        t.durationMs.takeIf { it > 0 }?.let { "Length" to "%d:%02d".format(it / 60_000, it / 1000 % 60) },
        "Source" to source,
        if (file.exists()) "Downloaded" to "%.1f MB".format(file.length() / 1_048_576.0) else null,
    )
    rows.forEach { (k, v) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // one fixed label column (fits "Downloaded"), so every value starts on the same line
            Txt(k, Type.body, Modifier.width(104.dp), color = WT.c.onSecondary)
            Txt(v, Type.body, Modifier.weight(1f), lines = 2)
        }
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

/**
 * Settings › Song menu: which actions are the top buttons (up to three, in the order picked; a fourth replaces the
 * first) and the order of the rest, dragged by their handles like the queue.
 */
@Composable
fun SongMenuSettingsBody() {
    val c = WT.c
    val names = SongActionNames.toMap()
    val pills = Settings.songMenuPills.split(',').filter { it in names && it != "remove" }
    Caption("Top buttons")
    Group {
        val pickable = SongActionNames.filter { it.first != "remove" }
        pickable.forEachIndexed { i, (id, name) ->
            val n = pills.indexOf(id)
            GroupRow(name, chevron = false, last = i == pickable.lastIndex, trailing = {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (n >= 0) c.primary else c.chip),
                    contentAlignment = Alignment.Center,
                ) { if (n >= 0) Txt("${n + 1}", Type.sub, color = c.onPrimary) }
            }) {
                Settings.songMenuPills = (if (n >= 0) pills - id else (pills + id).takeLast(3)).joinToString(",")
            }
        }
    }
    Note("Up to three, shown in the order you pick them. Picking a fourth replaces the first.")

    Caption("Menu order")
    var order by remember { mutableStateOf(songMenuOrder()) }
    var dragging by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var offset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(Settings.songMenuOrder) { if (dragging < 0) order = songMenuOrder() } // Reset, or a change elsewhere
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }
    fun save() { Settings.songMenuOrder = order.joinToString(",") }
    Group {
        order.forEachIndexed { i, id ->
            androidx.compose.runtime.key(id) {
                val dragged = dragging == i
                Row(
                    Modifier.zIndex(if (dragged) 1f else 0f).graphicsLayer { translationY = if (dragged) offset else 0f }
                        .background(if (dragged) c.surfaceHigh else androidx.compose.ui.graphics.Color.Transparent)
                        .fillMaxWidth().height(52.dp).padding(start = 16.dp)
                        .semantics {
                            customActions = listOfNotNull(
                                if (i > 0) androidx.compose.ui.semantics.CustomAccessibilityAction("Move up") { order = order.toMutableList().apply { add(i - 1, removeAt(i)) }; save(); true } else null,
                                if (i < order.lastIndex) androidx.compose.ui.semantics.CustomAccessibilityAction("Move down") { order = order.toMutableList().apply { add(i + 1, removeAt(i)) }; save(); true } else null,
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Txt(names[id] ?: id, Type.body)
                        if (id in pills) Txt("Top button", Type.sub, color = c.onSecondary)
                    }
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(52.dp).pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { dragging = order.indexOf(id); offset = 0f },
                                onDragEnd = { save(); dragging = -1; offset = 0f },
                                onDragCancel = { save(); dragging = -1; offset = 0f },
                            ) { ch, d ->
                                ch.consume()
                                offset += d.y
                                val to = (dragging + (offset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                                if (to != dragging) {
                                    order = order.toMutableList().apply { add(to, removeAt(dragging)) }
                                    offset -= (to - dragging) * rowPx
                                    dragging = to
                                }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Reorder, c.onSecondary, 18.dp) }
                }
            }
        }
    }
    Note("Drag the handles. Actions that don't apply to a song (for example Go to album for a song on your phone) are skipped.")
    Spacer(Modifier.height(16.dp))
    Group { GroupRow("Reset to default", chevron = false, last = true) { Settings.songMenuOrder = ""; Settings.songMenuPills = "next,like,playlist" } }
}

@Composable
private fun Note(text: String) = Txt(text, Type.sub, Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp), color = WT.c.onSecondary, lines = 3)
