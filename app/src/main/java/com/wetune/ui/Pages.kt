package com.wetune.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.wetune.Playback
import com.wetune.sources.Track
import com.wetune.youtube.Section

enum class PageKind(val label: String) { Album("Album"), Playlist("Playlist"), Artist("Artist") }

/**
 * Album, playlist and artist pages: a poster.
 * The cover (or artist photo) runs edge to edge and dissolves into a page tinted with its hue; the title sits large over
 * its lower edge. The image scrolls at half speed, and a slim title bar fades in once the poster has gone by.
 */
@Composable
fun CollectionPage(
    kind: PageKind,
    title: String,
    subtitle: String,
    art: String?,
    tracks: List<Track>,
    back: () -> Unit,
    sections: List<Section> = emptyList(),
    openCard: (com.wetune.sources.Card) -> Unit = {},
    onDelete: (() -> Unit)? = null,
    onRemove: ((Track) -> Unit)? = null,
    loading: Boolean = false,
) {
    val c = WT.c
    val from = kind.label to title
    val list = rememberLazyListState()
    val density = LocalDensity.current
    // the page takes the cover's hue (fixed strength, like the app accent)
    val hue = rememberArtTint(art)?.let { tint -> FloatArray(3).also { android.graphics.Color.colorToHSV(tint.toArgb(), it) }[0] }
    val accent by animateColorAsState(hue?.let { hueAccent(it, c.dark) } ?: c.text, tween(500), label = "pageAccent")
    val wash by animateColorAsState(hue?.let { Color.hsl(it, 0.35f, if (c.dark) 0.16f else 0.9f) } ?: c.bg, tween(500), label = "pageWash")
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // the title bar shows once the poster has scrolled under it
    val barAlpha by remember {
        derivedStateOf {
            if (list.firstVisibleItemIndex > 0) 1f
            else with(density) { ((list.firstVisibleItemScrollOffset - 220.dp.toPx()) / 80.dp.toPx()).coerceIn(0f, 1f) }
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Box(Modifier.fillMaxWidth().height(720.dp).background(Brush.verticalGradient(listOf(wash, c.bg))))
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = BottomSpace)) {
            item(key = "poster") {
                Box(Modifier.fillMaxWidth().aspectRatio(if (kind == PageKind.Artist) 0.92f else 1f)) {
                    AsyncImage(
                        art, null,
                        Modifier.fillMaxSize().graphicsLayer {
                            // parallax: the image moves at half the scroll speed
                            if (list.firstVisibleItemIndex == 0) translationY = list.firstVisibleItemScrollOffset * 0.5f
                        },
                        contentScale = ContentScale.Crop,
                    )
                    // the image dissolves into the tinted page
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.25f to Color.Transparent, 0.55f to Color.Transparent, 1f to wash)))
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 4.dp)) {
                        Txt(kind.label.uppercase(), style = Type.overline, color = c.text.copy(alpha = 0.75f))
                        Txt(
                            title, Modifier.padding(top = 4.dp), maxLines = 3,
                            style = Type.display.copy(fontSize = if (kind == PageKind.Artist) 46.sp else 34.sp, lineHeight = if (kind == PageKind.Artist) 48.sp else 38.sp, letterSpacing = (-1.2).sp),
                            weight = FontWeight.Bold,
                        )
                    }
                }
            }
            item(key = "meta") {
                val total = tracks.sumOf { it.durationMs }
                val meta = listOfNotNull(
                    subtitle.takeIf { it.isNotBlank() },
                    tracks.size.takeIf { kind != PageKind.Artist && it > 0 }?.let { "$it songs" },
                    total.takeIf { kind != PageKind.Artist && it > 0 }?.let(::longDuration),
                ).joinToString(" · ")
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (meta.isNotBlank()) Txt(meta, Modifier.padding(top = 6.dp), style = Type.caption, color = c.muted, maxLines = 2)
                    if (tracks.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // play: a filled circle in the cover's accent, like Now Playing
                        Box(
                            Modifier.size(58.dp).clip(CircleShape).background(accent).pressable(scale = 0.92f) { Playback.play(tracks, from = from) }
                                .semantics { contentDescription = "Play $title" },
                            Alignment.Center,
                        ) { Icon(Ic.Play, null, size = 26.dp, tint = if (accent.luminance() > 0.45f) Color(0xFF111111) else Color.White) }
                        Spacer(Modifier.width(10.dp))
                        IconBtn(Ic.Shuffle, "Shuffle", { Playback.play(tracks.shuffled(), from = from) }, size = 48.dp, iconSize = 22.dp, tint = c.text)
                        Spacer(Modifier.weight(1f))
                        if (onDelete != null) IconBtn(Ic.Delete, "Delete playlist", onDelete, size = 48.dp, iconSize = 21.dp, tint = c.danger)
                    }
                }
            }
            if (kind == PageKind.Artist && tracks.isNotEmpty()) item(key = "top") { SectionTitle("Top songs") }
            if (loading) item(key = "loading") { SkeletonRows(6) }
            else if (tracks.isEmpty() && sections.isEmpty()) item(key = "empty") { EmptyState(Ic.Note, "Nothing here yet") }
            itemsIndexed(tracks, key = { i, t -> "$i:${t.id}" }) { i, t ->
                TrackRow(t, { Playback.play(tracks, i, from) }, onRemove?.let { { it(t) } }, number = if (kind == PageKind.Album) i + 1 else null)
            }
            items(sections, key = { "s:" + it.title }) { s -> SectionView(s, openCard) }
        }

        // slim bar: back always; the title and a soft fill fade in once the poster has scrolled away
        Row(
            Modifier.fillMaxWidth().background(c.bg.copy(alpha = 0.92f * barAlpha)).padding(top = statusTop).height(52.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f * (1f - barAlpha))).pressable(scale = 0.9f, onClick = back).semantics { contentDescription = "Back" }, Alignment.Center) {
                Icon(Ic.Back, null, size = 22.dp, tint = if (barAlpha > 0.5f) c.text else Color.White)
            }
            Txt(title, Modifier.weight(1f).padding(start = 8.dp).graphicsLayer { alpha = barAlpha }, style = Type.headline, maxLines = 1)
        }
    }
}


/** 3_725_000 ms → "1 hr 2 min"; under an hour → "47 min". */
fun longDuration(ms: Long): String {
    val min = (ms / 60_000).toInt()
    return if (min >= 60) "${min / 60} hr ${min % 60} min" else "$min min"
}
