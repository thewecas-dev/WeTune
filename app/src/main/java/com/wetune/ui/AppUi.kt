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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import dev.chrisbanes.haze.HazeState
import com.wetune.Settings
import androidx.compose.runtime.CompositionLocalProvider
import dev.chrisbanes.haze.HazeStyle
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
    val stack = remember { mutableStateListOf<Screen>(if (Settings.startTab == "library") Screen.Library else Screen.Home) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val haze = remember { HazeState() }
    val style = glassStyle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val art = Playback.current?.art
    androidx.compose.runtime.LaunchedEffect(art) { NowColors.art = loadArt(ctx, art) }
    val open: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    BackHandler(playerOpen || stack.size > 1) { if (playerOpen) playerOpen = false else back() }

    Box(Modifier.fillMaxSize().background(WT.c.bg)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
          Box(Modifier.fillMaxSize()) {
            // The playing song's colors glow softly behind every page, with a faint wash of them down the whole screen,
            // so the frosted glass over it has colour to soften.
            if (art != null) coil3.compose.AsyncImage(
                art, null,
                Modifier.fillMaxWidth().fillMaxHeight().offset(y = (-120).dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .blur(80.dp, BlurredEdgeTreatment.Unbounded)
                    .drawWithContent { drawContent(); drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.3f))), blendMode = BlendMode.DstIn) },
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = if (WT.c.dark) 0.5f else 0.35f,
            )
          }
            // Pages slide in from the right going deeper and back out going back; tab switches crossfade.
            AnimatedContent(
                stack.size to stack.last(),
                transitionSpec = {
                    val depth = targetState.first - initialState.first
                    val ease = spring<IntOffset>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
                    when {
                        depth > 0 -> (slideInHorizontally(ease) { it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(ease) { -it / 10 } + fadeOut(tween(160)))
                        depth < 0 -> (slideInHorizontally(ease) { -it / 10 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(ease) { it / 4 } + fadeOut(tween(160)))
                        else -> {
                            // tab switch: slide toward the tab's side of the nav (Search sits at the far right)
                            fun order(s: Screen) = tabs.indexOfFirst { it.screen == s }.let { if (it < 0) tabs.size else it }
                            val dir = if (order(targetState.second) > order(initialState.second)) 1 else -1
                            (slideInHorizontally(ease) { dir * it / 5 } + fadeIn(tween(220, delayMillis = 40))) togetherWith
                                (slideOutHorizontally(ease) { -dir * it / 5 } + fadeOut(tween(140)))
                        }
                    }.using(SizeTransform(clip = false))
                },
                contentKey = { it.second },
                label = "screen",
            ) { (_, s) ->
                when (s) {
                    Screen.Home -> HomeScreen(open)
                    Screen.Search -> SearchScreen(open)
                    Screen.Library -> LibraryScreen(open)
                    Screen.Settings -> SettingsScreen(open)
                    Screen.Login -> Box(Modifier.fillMaxSize()) // drawn below, outside the glass layer
                    is Screen.SettingsPage -> SettingsPageScreen(s.id, open, back)
                    is Screen.Yt -> YtPageScreen(s.card, open, back)
                    is Screen.LocalPlaylist -> LocalPlaylistScreen(s.name, back)
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Rises in above the nav; the nav itself never moves.
            AnimatedVisibility(
                Playback.current != null,
                enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
            ) { Playback.current?.let { NowBar(it, "open", onOpen = { playerOpen = true }, haze = haze) } }
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
            }, onOpen = { c -> playerOpen = false; stack.add(Screen.Yt(c)) })
        }
        // Customize player: over everything (opened from Now Playing's ⋯ or Settings → Appearance)
        AnimatedVisibility(NpEditor.open, enter = slideInVertically(tween(280)) { it / 3 } + fadeIn(tween(220)), exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180))) {
            NpCustomize { NpEditor.open = false }
        }
        // Sign-in is a WebView: kept out of the blurred glass layer (re-capturing it every frame made it flicker) and
        // above the nav bar and mini-player, which it doesn't need
        if (stack.last() == Screen.Login) LoginScreen(back)
    }
}

/** N1: a glass bar only as wide as its tabs; the active tab is a solid pill with its label. Search glows beside it. */
@Composable
private fun NavBar(haze: HazeState, style: HazeStyle, root: Screen, onTab: (Screen) -> Unit) {
    NavRow(Modifier.glass(haze, style, CircleShape), Modifier.glass(haze, style, CircleShape), root, onTab)
}

@Composable
private fun NavRow(barGlass: Modifier, searchGlass: Modifier, root: Screen, onTab: (Screen) -> Unit) {
    val c = WT.c
    val pillBg = if (c.dark) Color.White else Color(0xFF1C1C1E)
    val pillFg = if (c.dark) Color(0xFF111111) else Color.White
    val idle = c.text.copy(alpha = 0.72f)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.height(66.dp).then(barGlass).padding(horizontal = 6.dp).animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = root == tab.screen
                val bg by animateColorAsState(if (selected) pillBg else pillBg.copy(alpha = 0f), tween(220), label = "tab")
                val fg by animateColorAsState(if (selected) pillFg else idle, tween(220), label = "tabFg")
                Row(
                    Modifier.height(54.dp).clip(CircleShape).background(bg)
                        .semantics { this.selected = selected; contentDescription = tab.label }
                        .pressable(scale = 0.92f, role = Role.Tab) { onTab(tab.screen) }
                        .padding(horizontal = if (selected) 20.dp else 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(tab.icon, null, size = 22.dp, tint = fg)
                    if (selected) Txt(tab.label, Modifier.padding(start = 8.dp), style = Type.label.copy(fontSize = 15.sp), color = pillFg, weight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        val searching = root == Screen.Search
        Box(
            Modifier.size(66.dp).then(searchGlass).then(if (searching) Modifier.clip(CircleShape).background(pillBg) else Modifier)
                .semantics { this.selected = searching; contentDescription = "Search" }
                .pressable(scale = 0.92f, role = Role.Tab) { onTab(Screen.Search) },
            Alignment.Center,
        ) { Icon(Ic.Search, null, size = 22.dp, tint = if (searching) pillFg else c.text) }
    }
}
