package com.tunehub.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tunehub.app.TuneHubApp
import com.tunehub.app.data.LoadResult
import com.tunehub.app.data.YouTube
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            TuneHubTheme { AppRoot() }
        }
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
    // survives rotation and the app being recreated
    val nav = rememberSaveable(saver = Navigator.Saver) { Navigator() }
    var menu by remember { mutableStateOf<SongMenuRequest?>(null) }
    val searchVm: SearchViewModel = viewModel(key = "search")
    val context = LocalContext.current
    val hasPlayer = PlayerConnection.current != null
    // Reserve the actual dock height so large navigation labels never overlap the screen.
    var dockHeight by remember { mutableStateOf(0.dp) }
    val bottomRoom by animateDpAsState(dockHeight, label = "bottomRoom")
    val density = LocalDensity.current

    // tell the user once if their library file had to be recovered
    LaunchedEffect(Unit) {
        when (val result = TuneHubApp.library.loadResult) {
            is LoadResult.Loaded -> if (result.fromBackup) {
                Toast.makeText(context, "Your library file was damaged, so the last good copy was restored", Toast.LENGTH_LONG).show()
            }
            is LoadResult.Failed -> Toast.makeText(
                context, "Your library couldn't be read. A copy was kept and your downloads are untouched", Toast.LENGTH_LONG,
            ).show()
            else -> Unit
        }
    }
    val saveError by TuneHubApp.library.saveError.collectAsStateWithLifecycle()
    LaunchedEffect(saveError) {
        saveError?.let { Toast.makeText(context, "Couldn't save your library: $it", Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(PlayerConnection.error) {
        PlayerConnection.error?.let {
            Toast.makeText(context, "Playback error: $it", Toast.LENGTH_SHORT).show()
            PlayerConnection.error = null
        }
    }

    BackHandler(enabled = nav.canGoBack) { nav.back() }

    // no Scaffold/Surface any more, so set the default text and icon color for every screen here
    CompositionLocalProvider(
        LocalNav provides nav,
        LocalSongMenu provides { menu = it },
        LocalContentColor provides tuneColors.text,
    ) {
        Box(Modifier.fillMaxSize()) {
            PaperBackground()

            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = bottomRoom),
            ) {
                when (val route = nav.current) {
                    null -> when (nav.tab) {
                        0 -> HomeScreen(viewModel())
                        1 -> SearchScreen(searchVm)
                        else -> LibraryScreen()
                    }
                    Route.Liked -> LikedScreen(onBack = nav::back)
                    Route.Recent -> RecentScreen(onBack = nav::back)
                    Route.Downloads -> DownloadsScreen(onBack = nav::back)
                    Route.Settings -> SettingsScreen(onBack = nav::back)
                    Route.Equalizer -> EqualizerScreen(onBack = nav::back)
                    Route.Import -> ImportScreen(onBack = nav::back)
                    is Route.LocalPlaylist -> LocalPlaylistScreen(route.id, onBack = nav::back)
                    is Route.Album -> AlbumScreen(route.album, onBack = nav::back)
                    is Route.Artist -> ArtistScreen(route.artist, onBack = nav::back)
                    is Route.Genre -> GenreScreen(route.name, onBack = nav::back)
                }
            }

            Box(Modifier.align(Alignment.BottomCenter).background(tuneColors.background).navigationBarsPadding()) {
                Column(
                    Modifier.onSizeChanged { size -> dockHeight = with(density) { size.height.toDp() } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MiniPlayer(onOpen = { nav.nowPlayingOpen = true })
                    BottomTabs(selected = nav.tab, onSelect = nav::selectTab)
                }
            }

            AnimatedVisibility(
                visible = nav.nowPlayingOpen && hasPlayer,
                enter = slideInVertically(tween(240)) { it },
                exit = slideOutVertically(tween(180)) { it },
            ) {
                NowPlayingScreen(onClose = { nav.nowPlayingOpen = false })
            }
        }

        menu?.let { request -> SongActionsSheet(request) { menu = null } }
    }
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    HorizontalDivider(color = tuneColors.rule)
    Row(
        Modifier
            .fillMaxWidth()
            .background(tuneColors.background)
            .selectableGroup()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, tab ->
            val active = i == selected
            val background by animateColorAsState(
                if (active) tuneColors.surface else tuneColors.background,
                tween(160),
                label = "tabBackground",
            )
            val tint by animateColorAsState(
                if (active) tuneColors.accentText else tuneColors.muted,
                tween(160),
                label = "tabContent",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .selectable(selected = active, role = Role.Tab) { onSelect(i) }
                    .semantics { contentDescription = tab.label }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    }
}

