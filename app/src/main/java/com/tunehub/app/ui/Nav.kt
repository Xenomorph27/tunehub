package com.tunehub.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.tunehub.app.data.AlbumRef
import com.tunehub.app.data.ArtistRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface Route {
    @Serializable data object Liked : Route
    @Serializable data object Downloads : Route
    @Serializable data object Settings : Route
    @Serializable data object Equalizer : Route
    @Serializable data object Import : Route
    @Serializable data class LocalPlaylist(val id: String) : Route
    @Serializable data class Album(val album: AlbumRef) : Route
    @Serializable data class Artist(val artist: ArtistRef) : Route
    @Serializable data class Genre(val name: String) : Route
}

/** What [Navigator] saves across rotation and process recreation. */
@Serializable
data class NavState(
    val tab: Int = 0,
    val stacks: List<List<Route>> = List(Navigator.TAB_COUNT) { emptyList() },
    val nowPlayingOpen: Boolean = false,
)

/** Each bottom tab keeps its own back stack, like most music apps. */
class Navigator(initial: NavState = NavState()) {
    var tab by mutableIntStateOf(initial.tab.coerceIn(0, TAB_COUNT - 1))
        private set
    var nowPlayingOpen by mutableStateOf(initial.nowPlayingOpen)
    private val stacks = List(TAB_COUNT) { i -> mutableStateListOf<Route>().apply { addAll(initial.stacks.getOrNull(i).orEmpty()) } }

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

    fun state() = NavState(tab, stacks.map { it.toList() }, nowPlayingOpen)

    companion object {
        const val TAB_COUNT = 3
        private val json = Json { ignoreUnknownKeys = true }

        /** For rememberSaveable: stored as a JSON string, which a Bundle can hold. */
        val Saver: Saver<Navigator, String> = Saver(
            save = { json.encodeToString(NavState.serializer(), it.state()) },
            restore = { saved -> runCatching { Navigator(json.decodeFromString(NavState.serializer(), saved)) }.getOrNull() },
        )
    }
}

val LocalNav = staticCompositionLocalOf<Navigator> { error("No Navigator") }
