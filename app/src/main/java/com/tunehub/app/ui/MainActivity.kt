package com.tunehub.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tunehub.app.data.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TuneHubTheme { AppRoot() } }
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

    /** "Share → TuneHub" from the YouTube app plays that video's audio. */
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
    Tab("Library", AppIcons.Library),
)

@Composable
private fun AppRoot() {
    val nav = remember { Navigator() }
    var menu by remember { mutableStateOf<SongMenuRequest?>(null) }
    val searchVm: SearchViewModel = viewModel(key = "search")
    val homeVm: HomeViewModel = viewModel()
    val context = LocalContext.current
    val artTint = rememberArtColor(PlayerConnection.current?.thumb, fallback = Aurora.Cyan)
    val hasPlayer = PlayerConnection.current != null
    // room for the floating mini player and nav pill, so lists end above them
    val bottomRoom by animateDpAsState(if (hasPlayer) 168.dp else 96.dp, label = "bottomRoom")

    LaunchedEffect(PlayerConnection.error) {
        PlayerConnection.error?.let {
            Toast.makeText(context, "Playback error: $it", Toast.LENGTH_SHORT).show()
            PlayerConnection.error = null
        }
    }

    BackHandler(enabled = nav.canGoBack) { nav.back() }

    CompositionLocalProvider(LocalNav provides nav, LocalSongMenu provides { menu = it }) {
        Box(Modifier.fillMaxSize()) {
            AuroraBackground(tint = if (hasPlayer) artTint else null)

            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = bottomRoom),
            ) {
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

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MiniPlayer(onOpen = { nav.nowPlayingOpen = true })
                PillNav(selected = nav.tab, onSelect = nav::selectTab)
            }

            AnimatedVisibility(
                visible = nav.nowPlayingOpen && hasPlayer,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NowPlayingScreen(onClose = { nav.nowPlayingOpen = false })
            }
        }

        menu?.let { request -> SongActionsSheet(request) { menu = null } }
    }
}

/** Floating glass pill with the three tabs; the active one sits in a gradient bubble. */
@Composable
private fun PillNav(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .padding(top = 10.dp)
            .width(260.dp)
            .height(66.dp)
            .glass(RoundedCornerShape(33.dp), fill = Aurora.Sheet.copy(alpha = 0.82f)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, tab ->
            val active = i == selected
            Box(
                Modifier
                    .size(width = 64.dp, height = 46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .then(if (active) Modifier.background(Aurora.accent) else Modifier)
                    .clickable { onSelect(i) }
                    .semantics { contentDescription = tab.label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(tab.icon, null, tint = if (active) Aurora.Ink else Aurora.Muted, modifier = Modifier.size(24.dp))
            }
        }
    }
}

