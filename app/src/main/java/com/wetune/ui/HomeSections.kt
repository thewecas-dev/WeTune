package com.wetune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wetune.Playback
import com.wetune.sources.Card
import com.wetune.sources.Track
import com.wetune.youtube.Section

/** How a Home section is laid out, from what it holds and what YouTube calls it. */
enum class HomeKind { QuickPicks, ListenAgain, Ranked, NewReleases, Banner, Cards, Grid, Spotlight, Big }

/** The layout a section's name asks for, or null for an ordinary section (those take turns between several layouts). */
private fun namedKind(s: Section): HomeKind? {
    val t = s.title.lowercase()
    return when {
        "quick pick" in t -> HomeKind.QuickPicks
        "listen again" in t -> HomeKind.ListenAgain
        listOf("trending", "chart", "top ", "top-", "hits").any { it in t } && s.tracks.size >= 4 -> HomeKind.Ranked
        "new release" in t || "fresh" in t -> HomeKind.NewReleases
        listOf("szn", "diwali", "holi", "festive", "festival", "season", "mood", "vibe", "party", "christmas", "eid", "navratri").any { it in t } -> HomeKind.Banner
        else -> null
    }
}

// ordinary song sections cycle through these; album/playlist sections through the card ones
private val songTurns = listOf(HomeKind.Spotlight, HomeKind.Cards, HomeKind.Grid, HomeKind.Big)
private val cardTurns = listOf(HomeKind.Cards, HomeKind.Big, HomeKind.Grid)

/**
 * Quick picks and Listen again first, then festive, trending and new releases; everything else in YouTube's order,
 * each with a layout different from the section above it so Home never repeats the same look twice in a row.
 */
fun arrangeHome(sections: List<Section>): List<Pair<Section, HomeKind>> {
    val sorted = sections.withIndex().sortedBy { (i, s) ->
        when (namedKind(s)) {
            HomeKind.QuickPicks -> 0; HomeKind.ListenAgain -> 1; HomeKind.Banner -> 2; HomeKind.Ranked -> 3; HomeKind.NewReleases -> 4; else -> 10
        } * 1000 + i
    }.map { it.value }
    var prev: HomeKind? = null
    var turn = 0
    return sorted.map { s ->
        val kind = namedKind(s) ?: run {
            val turns = if (s.tracks.size >= 4) songTurns else cardTurns
            var k = turns[turn++ % turns.size]
            if (k == prev) k = turns[turn++ % turns.size]
            k
        }
        prev = kind
        s to kind
    }
}

@Composable
fun HomeSection(s: Section, kind: HomeKind, open: (Card) -> Unit) {
    val from = "Home" to s.title.ifBlank { "Home" }
    // a song tapped on Home starts its radio, so what follows is related to it
    val playSong: (Track) -> Unit = { Playback.startRadio(it) }
    when (kind) {
        HomeKind.QuickPicks -> { SectionTitle(s.title.ifBlank { "Quick picks" }); RowPages(s.tracks, ranked = false, onPlay = playSong) }
        HomeKind.Ranked -> { SectionTitle(s.title); RowPages(s.tracks, ranked = true) { t -> Playback.play(s.tracks, s.tracks.indexOf(t), from) } }
        HomeKind.ListenAgain, HomeKind.Grid -> { SectionTitle(s.title); ListenAgain(s, open, playSong) }
        HomeKind.NewReleases -> { SectionTitle(s.title); BigCards(s, open, playSong, badge = "NEW") }
        HomeKind.Big -> { SectionTitle(s.title); BigCards(s, open, playSong, badge = null) }
        HomeKind.Banner -> { BannerTitle(s.title); Banners(s, open, playSong) }
        HomeKind.Spotlight -> { SectionTitle(s.title); Spotlight(s.tracks) { t -> Playback.play(s.tracks, s.tracks.indexOf(t), from) } }
        HomeKind.Cards -> SectionView(s, open)
    }
}

/** One large featured song with a play button, and the next three as compact rows under it. */
@Composable
private fun Spotlight(tracks: List<Track>, onPlay: (Track) -> Unit) {
    val lead = tracks.first()
    Column(Modifier.padding(horizontal = 16.dp)) {
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).pressable(scale = 0.98f) { onPlay(lead) }) {
            Art(lead.art, Modifier.fillMaxSize(), RoundedCornerShape(20.dp))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
            Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(lead.title, style = Type.title, color = Color.White, maxLines = 2)
                    Txt(lead.artist, style = Type.caption, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                }
                Box(Modifier.size(46.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Ic.Play, "Play", size = 22.dp, tint = Color.Black)
                }
            }
        }
        tracks.drop(1).take(3).forEach { t ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).pressable(scale = 0.98f) { onPlay(t) }, verticalAlignment = Alignment.CenterVertically) {
                Art(t.art, Modifier.size(48.dp), RoundedCornerShape(8.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Txt(t.title, style = Type.bodyStrong, maxLines = 1)
                    Txt(t.artist, style = Type.caption, color = WT.c.muted, maxLines = 1)
                }
                TrackMenu(t)
            }
        }
    }
}

/** Swipeable pages of four song rows (Quick picks), optionally with big rank numbers (charts). */
@Composable
private fun RowPages(tracks: List<Track>, ranked: Boolean, onPlay: (Track) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val colW = maxWidth - 56.dp
        LazyHorizontalGrid(
            GridCells.Fixed(4), Modifier.fillMaxWidth().height(64.dp * 4),
            contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(tracks.take(20), key = { _, t -> t.id }) { i, t ->
                Row(
                    Modifier.width(colW).height(64.dp).clip(RoundedCornerShape(12.dp)).pressable(scale = 0.98f) { onPlay(t) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ranked) Txt("${i + 1}", Modifier.width(34.dp), style = Type.title.copy(fontSize = 22.sp, fontFeatureSettings = "tnum"), weight = FontWeight.Bold, color = if (i < 3) WT.c.accent else WT.c.muted)
                    Art(t.art, Modifier.size(52.dp), RoundedCornerShape(8.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Txt(t.title, style = Type.bodyStrong, maxLines = 1)
                        Txt(t.artist, style = Type.caption, color = WT.c.muted, maxLines = 1)
                    }
                    TrackMenu(t)
                }
            }
        }
    }
}

/** Two rows of small covers. */
@Composable
private fun ListenAgain(s: Section, open: (Card) -> Unit, onPlay: (Track) -> Unit) {
    val tile = 112.dp
    LazyHorizontalGrid(
        GridCells.Fixed(2), Modifier.fillMaxWidth().height((tile + 46.dp) * 2 + 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(s.tracks, key = { _, t -> "t" + t.id }) { _, t -> SmallTile(t.title, t.artist, t.art, tile, false) { onPlay(t) } }
        itemsIndexed(s.cards, key = { _, c -> "c" + c.browseId }) { _, c -> SmallTile(c.title, c.subtitle, c.art, tile, c.isArtist()) { open(c) } }
    }
}

@Composable
private fun SmallTile(title: String, subtitle: String, art: String?, size: Dp, round: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(size).pressable(scale = 0.96f, onClick = onClick)) {
        Art(art, Modifier.size(size), if (round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(10.dp))
        Txt(title, Modifier.padding(top = 6.dp), style = Type.label, maxLines = 1)
        Txt(subtitle, style = Type.caption.copy(fontSize = 11.sp), color = WT.c.muted, maxLines = 1)
    }
}

/** Large covers with a small badge (New releases). */
@Composable
private fun BigCards(s: Section, open: (Card) -> Unit, onPlay: (Track) -> Unit, badge: String?) {
    val size = 190.dp
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(s.cards, key = { it.browseId }) { c -> BigCard(c.title, c.subtitle, c.art, size, badge) { open(c) } }
        items(s.tracks, key = { it.id }) { t -> BigCard(t.title, t.artist, t.art, size, badge) { onPlay(t) } }
    }
}

@Composable
private fun BigCard(title: String, subtitle: String, art: String?, size: Dp, badge: String?, onClick: () -> Unit) {
    Column(Modifier.width(size).pressable(scale = 0.96f, onClick = onClick)) {
        Box {
            Art(art, Modifier.size(size), RoundedCornerShape(Tokens.RadiusCard))
            if (badge != null) Txt(
                badge, Modifier.padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 7.dp, vertical = 2.dp),
                style = Type.overline.copy(fontSize = 9.sp), color = Color.White, weight = FontWeight.Bold,
            )
        }
        Txt(title, Modifier.padding(top = 8.dp), style = Type.bodyStrong, maxLines = 2)
        Txt(subtitle, style = Type.caption, color = WT.c.muted, maxLines = 1)
    }
}

/** A bigger, accent-colored heading for seasonal and mood sections ("Diwali szn", "Party vibes"…). */
@Composable
private fun BannerTitle(title: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 30.dp, bottom = 12.dp)) {
        Txt("FOR THE SEASON", style = Type.overline, color = WT.c.accent)
        Txt(title, style = Type.display.copy(fontSize = 26.sp, lineHeight = 31.sp), maxLines = 2)
    }
}

/** Wide cards with the title over the image, fading to dark at the bottom. */
@Composable
private fun Banners(s: Section, open: (Card) -> Unit, onPlay: (Track) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(s.cards, key = { it.browseId }) { c -> Banner(c.title, c.subtitle, c.art) { open(c) } }
        items(s.tracks, key = { it.id }) { t -> Banner(t.title, t.artist, t.art) { onPlay(t) } }
    }
}

@Composable
private fun Banner(title: String, subtitle: String, art: String?, onClick: () -> Unit) {
    Box(Modifier.size(300.dp, 176.dp).clip(RoundedCornerShape(20.dp)).pressable(scale = 0.97f, onClick = onClick)) {
        Art(art, Modifier.fillMaxSize(), RoundedCornerShape(20.dp))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Txt(title, style = Type.title, color = Color.White, maxLines = 2)
            Txt(subtitle, style = Type.caption, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}
