package com.wetune.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wetune.sources.Card

enum class Tab(val label: String, val ic: Ic) {
    Home("Home", Icons.Home), Library("Library", Icons.Library), Search("Search", Icons.Search), You("You", Icons.You)
}

/** Every screen. [key] identifies it for saved scroll state and transitions. */
sealed interface Route {
    val key: String

    data class Root(val tab: Tab) : Route { override val key get() = "tab:$tab" }
    data class Collection(val card: Card) : Route { override val key get() = "page:${card.browseId}" }
    /** A "see all" list from Home or an artist page. */
    data class Shelf(val title: String, val tracks: List<com.wetune.sources.Track>, val cards: List<Card>) : Route { override val key get() = "shelf:$title:${tracks.size}:${cards.size}" }
    data object Login : Route { override val key get() = "login" }
    data object SpotifyLogin : Route { override val key get() = "spotify-login" }
    /** A settings sub-page: appearance | audio | lyrics | sources | storage | about. */
    data class Settings(val page: String) : Route { override val key get() = "settings:$page" }
}

/** One back stack per tab, so switching tabs keeps each tab where it was. */
object Nav {
    var tab by mutableStateOf(Tab.Home); private set
    private val stacks = Tab.entries.associateWith { mutableStateListOf<Route>(Route.Root(it)) }

    val route: Route get() = stacks.getValue(tab).last()
    val canGoBack: Boolean get() = stacks.getValue(tab).size > 1 || tab != Tab.Home

    /** How the last change happened, so the transition matches it: push, pop or a tab switch. */
    enum class Move { Push, Pop, Tab }
    var move by mutableStateOf(Move.Tab); private set

    /** Bumped when the current tab is tapped again at its root: screens scroll to the top. */
    var reselect by mutableIntStateOf(0); private set

    fun open(r: Route) {
        move = Move.Push
        if (r is Route.Collection) com.wetune.Settings.addRecent(r.card) // Search's "Recently played"
        stacks.getValue(tab).add(r)
    }

    fun back(): Boolean {
        val s = stacks.getValue(tab)
        return when {
            s.size > 1 -> { move = Move.Pop; s.removeAt(s.lastIndex); true }
            tab != Tab.Home -> { select(Tab.Home); true }
            else -> false
        }
    }

    fun select(t: Tab) {
        if (t == tab) {
            val s = stacks.getValue(t)
            if (s.size > 1) { move = Move.Pop; while (s.size > 1) s.removeAt(s.lastIndex) } else reselect++
            return
        }
        move = Move.Tab
        tab = t
    }
}
