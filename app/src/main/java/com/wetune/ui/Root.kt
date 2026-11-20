package com.wetune.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.wetune.ui.player.NowPlaying
import com.wetune.ui.player.NowPlayingLayer
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** The whole app: pages (blurred under the dock), the one dock, Now Playing above, and the shared sheet on top. */
@Composable
fun Root() = WeTheme {
    val c = WT.c
    val haze = remember { HazeState() }
    val pages = rememberSaveableStateHolder()
    NowPlaying.scope = androidx.compose.runtime.rememberCoroutineScope()
    // status / navigation bar icons follow the app's theme (not the phone's): dark icons on light, light on dark
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !c.dark; isAppearanceLightNavigationBars = !c.dark }
        }
    }
    CompositionLocalProvider(LocalHaze provides haze) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Box(Modifier.fillMaxSize().hazeSource(haze).nestedScroll(Dock.scroll)) {
                Aura() // the light, grainy colour wash at the top of every page
                AnimatedContent(
                    targetState = Nav.route,
                    contentKey = { it.key },
                    transitionSpec = {
                        val px = 24
                        when (Nav.move) {
                            Nav.Move.Tab -> fadeIn(tween(Motion.SMALL, easing = Motion.Standard)) togetherWith fadeOut(tween(Motion.SMALL, easing = Motion.Standard))
                            Nav.Move.Push -> (fadeIn(Motion.push()) + slideInHorizontally(Motion.push()) { px * 3 }) togetherWith
                                (fadeOut(tween(Motion.PUSH / 2)) + slideOutHorizontally(Motion.push()) { -px * 3 })
                            Nav.Move.Pop -> (fadeIn(Motion.push()) + slideInHorizontally(Motion.push()) { -px * 3 }) togetherWith
                                (fadeOut(tween(Motion.PUSH / 2)) + slideOutHorizontally(Motion.push()) { px * 3 })
                        }
                    },
                    label = "pages",
                ) { r -> pages.SaveableStateProvider(r.key) { Screen(r) } }
            }
            DockHost()
            NowPlayingLayer()
            SheetHost()
        }
    }
    BackHandler(enabled = Sheet.content == null && !NowPlaying.isOpen && Nav.canGoBack) { Nav.back() }
    BackHandler(enabled = Sheet.content == null && NowPlaying.isOpen) { NowPlaying.back() }
    BackHandler(enabled = Sheet.content != null) { Sheet.hide() }
}

@Composable
private fun Screen(r: Route) = when (r) {
    is Route.Root -> when (r.tab) {
        Tab.Home -> HomeScreen()
        Tab.Library -> LibraryScreen()
        Tab.Search -> SearchScreen()
        Tab.You -> YouScreen()
    }
    is Route.Collection -> CollectionScreen(r.card)
    is Route.Shelf -> ShelfScreen(r)
    is Route.Login -> LoginScreen()
    is Route.SpotifyLogin -> SpotifyLoginScreen()
    is Route.Settings -> SettingsScreen(r.page)
}

/** The app's one bottom sheet (song menus, pickers): the same frost as the navbar, over a dimmed screen. */
@Composable
private fun SheetHost() {
    val c = WT.c
    val content = Sheet.content
    AnimatedVisibility(content != null, enter = fadeIn(Motion.small()), exit = fadeOut(Motion.small())) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(remember { MutableInteractionSource() }, null) { Sheet.hide() })
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            content != null,
            enter = slideInVertically(Motion.push()) { it } + fadeIn(Motion.small()),
            exit = slideOutVertically(tween(260, easing = Motion.Standard)) { it } + fadeOut(tween(260)),
        ) {
            // keep showing the last content while it slides away
            val shown = remember { mutableLast() }
            if (content != null) shown.value = content
            Column(
                Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding()
                    .frost(RoundedCornerShape(30.dp), overPlayer = true)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(bottom = 6.dp).size(36.dp, 5.dp).background(c.onTertiary.copy(alpha = 0.5f), CircleShape))
                shown.value?.invoke()
            }
        }
    }
}

private fun mutableLast() = androidx.compose.runtime.mutableStateOf<(@Composable () -> Unit)?>(null)
