package com.spitify.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spitify.app.data.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SpitifyTheme { AppRoot() } }
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        PlayerConnection.connect(this)
    }

    override fun onStop() {
        PlayerConnection.disconnect()
        super.onStop()
    }

    /** "Share → Spitify" from the YouTube app plays that video's audio. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val id = intent.getStringExtra(Intent.EXTRA_TEXT)?.let(YouTube::videoIdFromText) ?: return
        lifecycleScope.launch {
            val song = withContext(Dispatchers.IO) {
                runCatching { YouTube.songFrom(YouTube.streamInfo(id)) }.getOrNull()
            }
            if (song == null) {
                Toast.makeText(this@MainActivity, "Couldn't open that link", Toast.LENGTH_SHORT).show()
            } else {
                PlayerConnection.startRadio(song)
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Home", Icons.Default.Home),
    Tab("Search", Icons.Default.Search),
    Tab("Your Library", AppIcons.Library),
)

@Composable
private fun AppRoot() {
    val nav = remember { Navigator() }
    var menu by remember { mutableStateOf<SongMenuRequest?>(null) }
    val searchVm: SearchViewModel = viewModel(key = "search")
    val homeVm: HomeViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(PlayerConnection.error) {
        PlayerConnection.error?.let {
            Toast.makeText(context, "Playback error: $it", Toast.LENGTH_SHORT).show()
            PlayerConnection.error = null
        }
    }

    BackHandler(enabled = nav.canGoBack) { nav.back() }

    CompositionLocalProvider(LocalNav provides nav, LocalSongMenu provides { menu = it }) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Column {
                        MiniPlayer(onOpen = { nav.nowPlayingOpen = true })
                        NavigationBar(containerColor = Color.Black.copy(alpha = 0.92f)) {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = nav.tab == i,
                                    onClick = { nav.selectTab(i) },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = Color.Transparent,
                                        selectedIconColor = Color.White,
                                        selectedTextColor = Color.White,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (val route = nav.current) {
                        null -> when (nav.tab) {
                            0 -> HomeScreen(homeVm)
                            1 -> SearchScreen(searchVm)
                            else -> LibraryScreen()
                        }
                        Route.Liked -> LikedScreen(onBack = nav::back)
                        Route.Downloads -> DownloadsScreen(onBack = nav::back)
                        Route.Settings -> SettingsScreen(onBack = nav::back)
                        is Route.LocalPlaylist -> LocalPlaylistScreen(route.id, onBack = nav::back)
                        is Route.Album -> AlbumScreen(route.album, onBack = nav::back)
                        is Route.Artist -> ArtistScreen(route.artist, onBack = nav::back)
                        is Route.Genre -> GenreScreen(route.name, onBack = nav::back)
                    }
                }
            }

            AnimatedVisibility(
                visible = nav.nowPlayingOpen && PlayerConnection.current != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NowPlayingScreen(onClose = { nav.nowPlayingOpen = false })
            }
        }

        menu?.let { request -> SongActionsSheet(request) { menu = null } }
    }
}
