package com.wetune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.wetune.Downloads
import com.wetune.Library
import com.wetune.Playback
import com.wetune.io
import com.wetune.sources.Card
import com.wetune.sources.Track
import com.wetune.toast
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Section
import com.wetune.youtube.Sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Room left at the bottom of every list for the floating mini-player and nav. */
val BottomSpace = 180.dp

@Composable
fun listPadding() = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, bottom = BottomSpace)

/** Loads [load] on IO when [key] changes; shows [placeholder], an error with Retry, or [content]. */
@Composable
fun <T> Load(
    key: Any?,
    load: suspend () -> T,
    placeholder: @Composable () -> Unit = { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { Spinner() } },
    content: @Composable (T, reload: () -> Unit) -> Unit,
) {
    var attempt by remember(key) { mutableIntStateOf(0) }
    var result by remember(key) { mutableStateOf<Result<T>?>(null) }
    LaunchedEffect(key, attempt) {
        result = null
        result = runCatching { withContext(Dispatchers.IO) { load() } }
    }
    // Skeleton → content (or error) fades instead of popping.
    androidx.compose.animation.Crossfade(result, animationSpec = androidx.compose.animation.core.tween(220), label = "load") { r ->
        when {
            r == null -> placeholder()
            r.isFailure -> EmptyState(Ic.Info, "Couldn't load this", r.exceptionOrNull()?.message ?: "Something went wrong") {
                Btn("Try again", { attempt++ }, kind = BtnKind.Secondary, icon = Ic.Refresh)
            }
            else -> content(r.getOrThrow()) { attempt++ }
        }
    }
}

// ---- Placeholders ----

@Composable
fun SkeletonRows(count: Int = 8) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(Tokens.RadiusTrackArt)).shimmer())
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.7f else 0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                    Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(5.dp)).shimmer())
                }
            }
        }
    }
}

@Composable
fun SkeletonCarousels(sections: Int = 3) {
    Column(Modifier.fillMaxWidth()) {
        repeat(sections) { s ->
            Box(Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp).size(160.dp, 20.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { Box(Modifier.size(if (s == 0) 220.dp else 150.dp).clip(RoundedCornerShape(Tokens.RadiusCard)).shimmer()) }
            }
        }
    }
}

@Composable
fun SkeletonGrid(count: Int = 6) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count / 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(2) { Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(Tokens.RadiusCard)).shimmer()) }
            }
        }
    }
}

// ---- States ----

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String? = null, action: (@Composable () -> Unit)? = null) {
    val c = WT.c
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.surfaceHigh), Alignment.Center) { Icon(icon, null, size = 28.dp, tint = c.muted) }
        Txt(title, Modifier.padding(top = 16.dp), style = Type.headline, align = TextAlign.Center)
        if (hint != null) Txt(hint, Modifier.padding(top = 6.dp), color = c.muted, align = TextAlign.Center)
        if (action != null) Box(Modifier.padding(top = 18.dp)) { action() }
    }
}

@Composable
fun ExpiredBanner() {
    if (!Sync.expired) return
    val c = WT.c
    Row(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(Tokens.RadiusCard)).background(c.danger.copy(alpha = 0.14f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Info, null, size = 20.dp, tint = c.danger)
        Txt("Sign-in expired. Sign in again in Settings → Account.", Modifier.padding(start = 12.dp), color = c.danger, style = Type.bodyStrong)
    }
}

// ---- Art ----

@Composable
fun Art(url: String?, modifier: Modifier, shape: Shape = RoundedCornerShape(Tokens.RadiusTrackArt)) {
    Box(modifier.clip(shape).background(WT.c.surfaceHigh), Alignment.Center) {
        Icon(Ic.Note, null, tint = WT.c.muted.copy(alpha = 0.5f))
        AsyncImage(url, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

/** The cover's vibrant color, for tinting chrome around it. */
@Composable
fun rememberArtTint(url: String?): Color? {
    val ctx = LocalContext.current
    return produceState<Color?>(null, url) {
        value = if (url == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).size(96).build())
                val p = Palette.from((r as SuccessResult).image.toBitmap()).generate()
                (p.vibrantSwatch ?: p.dominantSwatch)?.rgb?.let { Color(it) }
            }.getOrNull()
        }
    }.value
}

// ---- Headers ----

@Composable
fun Header(title: String, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, Modifier.weight(1f), style = Type.display, maxLines = 1)
        action()
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 12.dp)) {
        if (subtitle != null) Txt(subtitle.uppercase(), style = Type.overline, color = WT.c.muted, maxLines = 1)
        Txt(title, style = Type.title, maxLines = 1)
    }
}

// ---- Tracks ----

@Composable
fun TrackRow(t: Track, onClick: () -> Unit, onRemove: (() -> Unit)? = null, number: Int? = null) {
    val c = WT.c
    val playing = Playback.current?.id == t.id
    val menu = remember { mutableStateOf(false) }
    // The playing row floats as a glass capsule; the rest stay flat so the glass stays special.
    val rowShape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).then(if (playing) Modifier.glassChip(rowShape) else Modifier.clip(rowShape))
            .pressable(scale = 0.98f, onLongClick = { menu.value = true }, onClick = onClick).padding(start = 8.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Box(Modifier.size(32.dp), Alignment.Center) {
                if (playing) Equalizer(Playback.playing, c.text) else Txt("$number", style = Type.bodyStrong, color = c.muted)
            }
        } else Art(t.art, Modifier.size(48.dp), RoundedCornerShape(10.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Txt(t.title, style = Type.bodyStrong, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Library.downloads.any { it.id == t.id }) Icon(Ic.Downloaded, "Downloaded", Modifier.padding(end = 4.dp), size = 14.dp, tint = c.accent)
                Txt(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" • "), style = Type.caption, color = c.muted, maxLines = 1)
            }
        }
        if (playing && number == null) Equalizer(Playback.playing, c.text, Modifier.padding(end = 8.dp))
        else if (t.durationMs > 0) Txt(formatTime(t.durationMs), style = Type.caption, color = c.muted)
        TrackMenu(t, onRemove, open = menu)
    }
}

@Composable
fun TrackMenu(t: Track, onRemove: (() -> Unit)? = null, tint: Color = LocalContentColor.current, open: MutableState<Boolean> = remember { mutableStateOf(false) }, removeLabel: String = "Remove from this playlist") {
    var pick by remember { mutableStateOf(false) }
    fun close(action: () -> Unit): () -> Unit = { open.value = false; action() }
    Box {
        Box(Modifier.size(44.dp), Alignment.Center) {
            Box(Modifier.size(36.dp).glassChip(CircleShape).pressable { open.value = true }.semantics { contentDescription = "More options for ${t.title}" }, Alignment.Center) {
                Icon(Ic.More, null, size = 16.dp, tint = tint)
            }
        }
        Menu(open.value, { open.value = false }) {
            MenuItem("Play next", close { Playback.playNext(t) }, Ic.PlayNext)
            if (t.source == "yt") MenuItem("Start radio", close { Playback.startRadio(t) }, Ic.Radio)
            MenuItem("Add to queue", close { Playback.enqueue(t) }, Ic.Queue)
            val liked = Library.isLiked(t)
            MenuItem(if (liked) "Remove from Liked" else "Like", close { io { Library.toggleLike(t) } }, if (liked) Ic.Heart else Ic.HeartOutline)
            MenuItem("Add to playlist…", close { pick = true }, Ic.AddPlaylist)
            if (t.source != "local") when {
                Library.downloads.any { it.id == t.id } -> MenuItem("Remove download", close { Downloads.delete(t) }, Ic.Delete)
                t.id in Downloads.active -> MenuItem("Downloading…", {}, Ic.Download, enabled = false)
                else -> MenuItem("Download", close { Downloads.download(t) }, Ic.Download)
            }
            if (onRemove != null) MenuItem(removeLabel, close(onRemove), Ic.Close, danger = true)
        }
    }
    if (pick) PlaylistPicker(t) { pick = false }
}

@Composable
private fun PickRow(label: String, onClick: () -> Unit) {
    Txt(label, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(scale = 0.98f, onClick = onClick).padding(12.dp), style = Type.bodyStrong)
}

@Composable
fun PlaylistPicker(t: Track, onDismiss: () -> Unit) {
    val c = WT.c
    var name by remember { mutableStateOf("") }
    Sheet("Add to playlist", onDismiss, actions = { Btn("Close", onDismiss, kind = BtnKind.Ghost) }) {
        if (t.source == "yt" && InnerTube.signedIn) {
            Txt("Your account", style = Type.overline, color = c.accent)
            Load("yt-playlists", { Sync.playlists().filter { it.browseId.startsWith("VLPL") } }) { list, _ ->
                if (list.isEmpty()) Txt("No playlists yet", color = c.muted)
                list.forEach { p ->
                    PickRow(p.title) {
                        io("Added to ${p.title}") { Sync.addToPlaylist(p.browseId, t.ref) }
                        onDismiss()
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Txt("On this device", style = Type.overline, color = c.accent)
        Library.playlists.forEach { p ->
            PickRow(p.name) {
                Library.addTo(p.name, t)
                toast("Added to ${p.name}")
                onDismiss()
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(name, { name = it }, "New playlist", Modifier.weight(1f))
            IconBtn(Ic.Add, "Create playlist", {
                val n = name.trim()
                if (n.isNotEmpty()) {
                    Library.createPlaylist(n)
                    Library.addTo(n, t)
                    toast("Added to $n")
                    onDismiss()
                }
            }, tint = c.accent)
        }
    }
}

// ---- Cards ----

@Composable
fun CardTile(title: String, subtitle: String, art: String?, round: Boolean = false, size: Dp = 150.dp, onClick: () -> Unit) {
    Column(Modifier.width(size).pressable(scale = 0.96f, onClick = onClick)) {
        Art(art, Modifier.size(size).shadow(if (size > 160.dp) 18.dp else 0.dp, if (round) CircleShape else RoundedCornerShape(Tokens.RadiusCard)), if (round) CircleShape else RoundedCornerShape(Tokens.RadiusCard))
        Txt(title, Modifier.fillMaxWidth().padding(top = 8.dp), style = Type.bodyStrong, maxLines = 2, align = if (round) TextAlign.Center else TextAlign.Start)
        Txt(subtitle, Modifier.fillMaxWidth(), style = Type.caption, color = WT.c.muted, maxLines = 1, align = if (round) TextAlign.Center else TextAlign.Start)
    }
}

fun Card.isArtist() = browseId.startsWith("UC")

@Composable
fun SectionView(s: Section, open: (Card) -> Unit, hero: Boolean = false) {
    if (s.title.isNotBlank()) SectionTitle(s.title)
    val size = if (hero) 220.dp else 150.dp
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(s.tracks, key = { it.id }) { t -> CardTile(t.title, t.artist, t.art, size = size) { Playback.play(listOf(t), from = "Home" to s.title.ifBlank { "Home" }) } }
        items(s.cards, key = { it.browseId }) { c -> CardTile(c.title, c.subtitle, c.art, c.isArtist(), size) { open(c) } }
    }
}

data class GridEntry(val key: String, val title: String, val subtitle: String, val art: String?, val round: Boolean, val onClick: () -> Unit)

/** Two-column grid of covers for playlists, albums and artists. */
@Composable
fun CardGrid(entries: List<GridEntry>, empty: String = "Nothing here yet") {
    if (entries.isEmpty()) return EmptyState(Ic.Library, empty)
    LazyVerticalGrid(
        GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = BottomSpace),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(entries, key = { it.key }) { e ->
            Column(Modifier.pressable(scale = 0.96f, onClick = e.onClick)) {
                Art(e.art, Modifier.fillMaxWidth().aspectRatio(1f), if (e.round) CircleShape else RoundedCornerShape(Tokens.RadiusCard))
                Txt(e.title, Modifier.fillMaxWidth().padding(top = 8.dp), style = Type.bodyStrong, maxLines = 1, align = if (e.round) TextAlign.Center else TextAlign.Start)
                Txt(e.subtitle, Modifier.fillMaxWidth(), style = Type.caption, color = WT.c.muted, maxLines = 1, align = if (e.round) TextAlign.Center else TextAlign.Start)
            }
        }
    }
}

fun Card.entry(open: (Card) -> Unit) = GridEntry(browseId, title, subtitle, art, isArtist()) { open(this) }

// ---- Pages ----

@Composable
fun PlayButtons(tracks: List<Track>, from: Pair<String, String>? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Btn("Play", { Playback.play(tracks, from = from) }, Modifier.weight(1f), icon = Ic.Play)
        Btn("Shuffle", { Playback.play(tracks.shuffled(), from = from) }, Modifier.weight(1f), kind = BtnKind.Secondary, icon = Ic.Shuffle)
    }
}

/** Cover blurred behind the header, fading into the page. */
@Composable
fun PageHeader(title: String, subtitle: String, art: String?, round: Boolean, back: () -> Unit, onDelete: (() -> Unit)? = null, count: Int? = null) {
    val c = WT.c
    val tint = rememberArtTint(art) ?: c.surfaceHigh
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().background(tint.copy(alpha = if (c.dark) 0.35f else 0.25f)))
        AsyncImage(art, null, Modifier.matchParentSize().blur(60.dp), contentScale = ContentScale.Crop, alpha = 0.55f)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.2f), c.bg.copy(alpha = 0.6f), c.bg))))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                IconBtn(Ic.Back, "Back", back)
                Spacer(Modifier.weight(1f))
                if (onDelete != null) IconBtn(Ic.Delete, "Delete playlist", onDelete, tint = c.danger)
            }
            Art(art, Modifier.size(240.dp).shadow(30.dp, if (round) CircleShape else RoundedCornerShape(Tokens.RadiusLarge)), if (round) CircleShape else RoundedCornerShape(Tokens.RadiusLarge))
            Txt(title, Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp), style = Type.display.copy(fontSize = Type.title.fontSize * 1.25f), align = TextAlign.Center, maxLines = 3)
            val meta = listOfNotNull(subtitle.takeIf { it.isNotBlank() }, count?.let { "$it songs" }).joinToString(" • ")
            if (meta.isNotBlank()) Txt(meta, Modifier.padding(horizontal = 24.dp, vertical = 6.dp), color = c.muted, align = TextAlign.Center, maxLines = 2)
        }
    }
}

/** A song list with Play/Shuffle; [extra] adds more items (e.g. carousels) after the songs. */
@Composable
fun TrackList(
    tracks: List<Track>,
    from: Pair<String, String>? = null,
    padding: PaddingValues = PaddingValues(bottom = BottomSpace),
    header: @Composable () -> Unit = {},
    onRemove: ((Track) -> Unit)? = null,
    numbered: Boolean = false,
    extra: LazyListScope.() -> Unit = {},
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { header() }
        if (tracks.isEmpty()) item { EmptyState(Ic.Note, "Nothing here yet") } else item { PlayButtons(tracks, from) }
        itemsIndexed(tracks, key = { i, t -> "$i:${t.id}" }) { i, t ->
            TrackRow(t, { Playback.play(tracks, i, from) }, onRemove?.let { { it(t) } }, number = if (numbered) i + 1 else null)
        }
        extra()
    }
}
