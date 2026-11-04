package com.wetune.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import com.wetune.rememberPosition
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wetune.Playback
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

private data class Tab(val screen: Screen, val icon: ImageVector, val label: String)

// Search is its own glowing circle next to the bar.
private val tabs = listOf(
    Tab(Screen.Home, Ic.Home, "Home"),
    Tab(Screen.Library, Ic.Library, "Library"),
    Tab(Screen.Settings, Ic.Person, "You"),
)

@Composable
fun AppUi() {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val haze = remember { HazeState() }
    val style = glassStyle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val art = Playback.current?.art
    androidx.compose.runtime.LaunchedEffect(art) { NowColors.palette = art?.let { artPalette(ctx, it) } }
    val open: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    BackHandler(playerOpen || stack.size > 1) { if (playerOpen) playerOpen = false else back() }

    Box(Modifier.fillMaxSize().background(WT.c.bg)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            // The playing song's colors glow softly behind the top of the main tabs.
            if (stack.size == 1 && art != null) coil3.compose.AsyncImage(
                art, null,
                Modifier.fillMaxWidth().height(420.dp).offset(y = (-120).dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .blur(80.dp, BlurredEdgeTreatment.Unbounded)
                    .drawWithContent { drawContent(); drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), blendMode = BlendMode.DstIn) },
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = if (WT.c.dark) 0.5f else 0.35f,
            )
            when (val s = stack.last()) {
                Screen.Home -> HomeScreen(open)
                Screen.Search -> SearchScreen(open)
                Screen.Library -> LibraryScreen(open)
                Screen.Settings -> SettingsScreen(open)
                Screen.Login -> {} // drawn below, outside the glass layer
                is Screen.SettingsPage -> SettingsPageScreen(s.id, open, back)
                is Screen.Yt -> YtPageScreen(s.card, open, back)
                is Screen.LocalPlaylist -> LocalPlaylistScreen(s.name, back)
            }
        }
        // A soft fade behind the status bar (no hard edge) so scrolled content doesn't clash with the clock and icons.
        val statusH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(Modifier.fillMaxWidth().height(statusH + 28.dp).background(Brush.verticalGradient(listOf(WT.c.bg.copy(alpha = 0.7f), WT.c.bg.copy(alpha = 0.35f), Color.Transparent))))
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (Playback.current != null) MiniPlayer(haze, style) { playerOpen = true }
            NavBar(haze, style, stack.first()) { tab ->
                stack.clear()
                stack.add(tab)
            }
        }
        // The player rises out of the mini-player: a short slide with a scale from the bottom, eased like a spring.
        AnimatedVisibility(
            playerOpen,
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(220)) +
                scaleIn(spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(220)) + scaleOut(tween(260), targetScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 1f)),
        ) {
            PlayerScreen(onClose = { playerOpen = false }, onSearch = { q ->
                playerOpen = false
                SearchRequest.query = q
                stack.clear(); stack.add(Screen.Search)
            })
        }
        // Sign-in is a WebView: kept out of the blurred glass layer (re-capturing it every frame made it flicker) and
        // above the nav bar and mini-player, which it doesn't need
        if (stack.last() == Screen.Login) LoginScreen(back)
    }
}

@Composable
private fun MiniPlayer(haze: HazeState, style: HazeStyle, onOpen: () -> Unit) {
    val t = Playback.current ?: return
    val c = WT.c
    val pos by rememberPosition()
    val duration = Playback.duration().takeIf { it > 0 } ?: t.durationMs
    val fraction = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
    var drag by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier.fillMaxWidth().height(64.dp).glass(haze, style, shape)
            .drawBehind {
                // thin progress line along the bottom edge, inset from the rounded ends
                val inset = 28.dp.toPx()
                val w = size.width - inset * 2
                val y = size.height - 2.dp.toPx()
                drawRect(c.text.copy(alpha = 0.15f), Offset(inset, y), Size(w, 2.dp.toPx()))
                drawRect(c.text, Offset(inset, y), Size(w * fraction, 2.dp.toPx()))
            }
            .pointerInput(t.id) {
                detectHorizontalDragGestures(onDragEnd = {
                    when {
                        drag < -80.dp.toPx() -> Playback.next()
                        drag > 80.dp.toPx() -> Playback.prev()
                    }
                    drag = 0f
                }) { _, amount -> drag += amount }
            }
            .semantics { contentDescription = "Now playing ${t.title}. Swipe left or right to skip." },
    ) {
        Row(
            Modifier.fillMaxSize().offset { IntOffset((drag / 3).roundToInt(), 0) }.pressable(scale = 0.98f, onClick = onOpen).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(t.art, Modifier.size(48.dp), CircleShape)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Txt(t.title, style = Type.bodyStrong, weight = FontWeight.SemiBold, maxLines = 1)
                Txt(t.artist, style = Type.caption, color = WT.c.muted, maxLines = 1)
            }
            IconBtn(if (Playback.playing) Ic.Pause else Ic.Play, if (Playback.playing) "Pause" else "Play", Playback::toggle)
            IconBtn(Ic.Next, "Next", { Playback.next() })
        }
    }
}

/** N1: a glass bar only as wide as its tabs; the active tab is a solid pill with its label. Search glows beside it. */
@Composable
private fun NavBar(haze: HazeState, style: HazeStyle, root: Screen, onTab: (Screen) -> Unit) {
    val c = WT.c
    val pillBg = if (c.dark) Color.White else Color(0xFF1C1C1E)
    val pillFg = if (c.dark) Color(0xFF111111) else Color.White
    val idle = c.text.copy(alpha = 0.72f)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.height(66.dp).glass(haze, style, CircleShape).padding(horizontal = 6.dp).animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = root == tab.screen
                Row(
                    Modifier.height(54.dp).clip(CircleShape).background(if (selected) pillBg else Color.Transparent)
                        .semantics { this.selected = selected; contentDescription = tab.label }
                        .pressable(scale = 0.92f, role = Role.Tab) { onTab(tab.screen) }
                        .padding(horizontal = if (selected) 20.dp else 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(tab.icon, null, size = 22.dp, tint = if (selected) pillFg else idle)
                    if (selected) Txt(tab.label, Modifier.padding(start = 8.dp), style = Type.label.copy(fontSize = 15.sp), color = pillFg, weight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        val searching = root == Screen.Search
        Box(
            Modifier.size(66.dp).glass(haze, style, CircleShape).then(if (searching) Modifier.background(pillBg) else Modifier)
                .semantics { this.selected = searching; contentDescription = "Search" }
                .pressable(scale = 0.92f, role = Role.Tab) { onTab(Screen.Search) },
            Alignment.Center,
        ) { Icon(Ic.Search, null, size = 22.dp, tint = if (searching) pillFg else c.text) }
    }
}
