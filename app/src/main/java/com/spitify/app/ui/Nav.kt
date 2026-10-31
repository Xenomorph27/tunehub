package com.spitify.app.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.spitify.app.data.AlbumRef
import com.spitify.app.data.ArtistRef

sealed interface Route {
    data object Liked : Route
    data object Downloads : Route
    data object Settings : Route
    data class LocalPlaylist(val id: String) : Route
    data class Album(val album: AlbumRef) : Route
    data class Artist(val artist: ArtistRef) : Route
    data class Genre(val name: String) : Route
}

/** Each bottom tab keeps its own back stack, like most music apps. */
class Navigator {
    var tab by mutableIntStateOf(0)
        private set
    var nowPlayingOpen by mutableStateOf(false)
    private val stacks = List(TAB_COUNT) { mutableStateListOf<Route>() }

    val current: Route? get() = stacks[tab].lastOrNull()
    val canGoBack: Boolean get() = nowPlayingOpen || stacks[tab].isNotEmpty() || tab != 0

    fun open(route: Route) {
        nowPlayingOpen = false
        stacks[tab].add(route)
    }

    fun selectTab(i: Int) {
        // tapping the current tab again pops back to its root
        if (i == tab) stacks[i].clear() else tab = i
    }

    fun back() {
        when {
            nowPlayingOpen -> nowPlayingOpen = false
            stacks[tab].isNotEmpty() -> stacks[tab].removeAt(stacks[tab].lastIndex)
            else -> tab = 0
        }
    }

    companion object {
        const val TAB_COUNT = 3
    }
}

val LocalNav = staticCompositionLocalOf<Navigator> { error("No Navigator") }
