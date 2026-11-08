package com.tunehub.app.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.tunehub.app.TuneHubApp
import com.tunehub.app.data.ArtistRef
import com.tunehub.app.data.Lyrics
import com.tunehub.app.data.Song
import com.tunehub.app.data.StreamResolver
import com.tunehub.app.data.formatDuration
import com.tunehub.app.playback.SleepTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Polls the player position while something on screen shows it. */
@Composable
private fun rememberPosition(intervalMs: Long = 500): Long {
    var position by remember { mutableLongStateOf(PlayerConnection.positionMs()) }
    LaunchedEffect(PlayerConnection.controller, intervalMs) {
        while (true) {
            position = PlayerConnection.positionMs()
            delay(intervalMs)
        }
    }
    return position
}

/** Horizontal swipe skips tracks: left = next, right = previous. */
private fun Modifier.swipeToSkip(offset: () -> Float, onOffset: (Float) -> Unit): Modifier =
    pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                val dx = offset()
                if (dx < -SWIPE_THRESHOLD) PlayerConnection.next() else if (dx > SWIPE_THRESHOLD) PlayerConnection.previous()
                onOffset(0f)
            },
            onDragCancel = { onOffset(0f) },
        ) { change, amount ->
            change.consume()
            onOffset(offset() + amount)
        }
    }

private const val SWIPE_THRESHOLD = 140f

// ------------------------------------------------------------------ mini player

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val song = PlayerConnection.current ?: return
    val position = rememberPosition()
    val duration = PlayerConnection.durationMs
    var dragX by remember { mutableFloatStateOf(0f) }
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .panel(RoundedCornerShape(14.dp), fill = TuneHubInk, stroke = Color.Transparent)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "Open now playing" }
            .swipeToSkip({ dragX }, { dragX = it }),
    ) {
        Row(
            Modifier
                .padding(start = 10.dp, top = 9.dp, bottom = 7.dp, end = 10.dp)
                .graphicsLayer { translationX = dragX / 2 },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.thumb, 42.dp, corner = 7.dp)
            Crossfade(
                targetState = song,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                animationSpec = tween(160),
                label = "miniPlayerTrack",
            ) {
                Column {
                    Text(it.title, style = MaterialTheme.typography.titleSmall, color = LcdText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(it.artist, style = MaterialTheme.typography.bodySmall, color = LcdMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            DockButton(AppIcons.SkipPrevious, "Previous track", iconSize = 22, tint = LcdMuted, onClick = PlayerConnection::previous)
            PlayPauseButton(size = 48)
            DockButton(AppIcons.SkipNext, "Next track", iconSize = 22, tint = LcdMuted, onClick = PlayerConnection::next)
        }
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 6.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LcdTrack),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(Brush.horizontalGradient(listOf(tuneColors.accent, TuneHubAmber))),
            )
        }
    }
}

private val LcdText = Color(0xFFFAF9F5)
private val LcdMuted = Color(0xFFC9C6B8)
private val LcdTrack = Color(0xFF826F5D)
private val LcdStroke = Color(0xFF50443A)

/** Shows a spinner while the stream buffers. */
@Composable
private fun PlayPauseButton(size: Int) {
    AccentCircleButton(
        if (PlayerConnection.isPlaying) AppIcons.Pause else AppIcons.Play,
        if (PlayerConnection.isPlaying) "Pause" else "Play",
        size = size.dp,
        content = if (PlayerConnection.isBuffering) {
            { CircularProgressIndicator(Modifier.size((size * 0.42f).dp), color = tuneColors.onAccent, strokeWidth = 2.5.dp) }
        } else null,
        onClick = PlayerConnection::togglePlay,
    )
}

// ------------------------------------------------------------------ now playing

private sealed interface LyricsState {
    /** Automatic lookup is off in Settings; nothing is sent until the user taps. */
    data object NotRequested : LyricsState
    data object Loading : LyricsState
    data object Missing : LyricsState
    /** LRCLIB couldn't be reached; tapping retries. */
    data object Failed : LyricsState
    class Found(val result: Lyrics.Result) : LyricsState
}

// Compose lint misses the producer's value assignments with this Kotlin toolchain.
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun rememberLyrics(song: Song, automatic: Boolean, attempt: Int): LyricsState =
    produceState<LyricsState>(LyricsState.Loading, song.id, automatic, attempt) {
        if (!automatic && attempt == 0) {
            value = LyricsState.NotRequested
            return@produceState
        }
        value = LyricsState.Loading
        value = withContext(Dispatchers.IO) {
            try {
                Lyrics.find(TuneHubApp.http, song)?.let { LyricsState.Found(it) } ?: LyricsState.Missing
            } catch (e: Exception) {
                LyricsState.Failed
            }
        }
    }.value

/** "OPUS 160K", or "OFFLINE" for downloads; appears once the service has resolved the stream. */
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun rememberQuality(song: Song, hiRes: Boolean): String? =
    // re-read after a quality switch
    produceState<String?>(null, song.id, hiRes) {
        value = null
        repeat(30) {
            if (TuneHubApp.downloads.fileFor(song.id) != null) {
                value = "OFFLINE"
                return@produceState
            }
            StreamResolver.peek(song.id)?.let {
                value = it.quality
                return@produceState
            }
            delay(1000)
        }
    }.value

@Composable
fun NowPlayingScreen(onClose: () -> Unit) {
    val song = PlayerConnection.current
    if (song == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val nav = LocalNav.current
    val context = LocalContext.current
    val openMenu = LocalSongMenu.current
    val likedIds by TuneHubApp.library.likedIds.collectAsStateWithLifecycle()
    val sleep by SleepTimer.state.collectAsStateWithLifecycle()
    val liked = song.id in likedIds
    val settings by TuneHubApp.library.data.collectAsStateWithLifecycle()
    val hiRes = !settings.dataSaver
    val ultra = settings.eq.ultra
    val quality = rememberQuality(song, hiRes)
    val position = rememberPosition(250)
    val duration = PlayerConnection.durationMs
    var lyricsAttempt by remember(song.id) { mutableIntStateOf(0) }
    val lyrics = key(song.id) { rememberLyrics(song, settings.autoLyrics, lyricsAttempt) }
    val requestLyrics: () -> Unit = { lyricsAttempt++ }

    var seekPreview by remember(song.id) { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var artDrag by remember { mutableFloatStateOf(0f) }
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val openArtist: (() -> Unit)? = song.artistUrl?.let { url ->
        { nav.open(Route.Artist(ArtistRef(url, song.artist))) }
    }

    // this overlay sits outside the Scaffold, so set the content color it would have provided
    CompositionLocalProvider(LocalContentColor provides tuneColors.text) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // swallow taps so they don't reach the screen underneath
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            PaperBackground()
            val gap = if (maxHeight < 720.dp) 6.dp else 14.dp
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Column(
                    Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, bottom = gap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        PanelIconButton(Icons.Default.KeyboardArrowDown, "Close", onClick = onClose)
                        Crossfade(
                            targetState = quality ?: "—",
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            animationSpec = tween(160),
                            label = "streamQuality",
                        ) {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = tuneColors.accentText, maxLines = 1)
                        }
                        TextButton(onClick = {
                            showLyrics = true
                            if (lyrics == LyricsState.NotRequested || lyrics == LyricsState.Failed) requestLyrics()
                        }) {
                            Text("Lyrics", style = MaterialTheme.typography.labelSmall)
                        }
                        PanelIconButton(Icons.Default.MoreVert, "More") { openMenu(SongMenuRequest(song, null)) }
                    }

                    Spacer(Modifier.height(gap))
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val artworkSize = minOf(maxWidth, maxHeight, 272.dp)
                        if (artworkSize > 16.dp) Box(
                            Modifier
                                .graphicsLayer {
                                    translationX = artDrag
                                    alpha = 1f - (abs(artDrag) / 900f).coerceAtMost(0.5f)
                                }
                                .swipeToSkip({ artDrag }, { artDrag = it })
                                .shadow(8.dp, RoundedCornerShape(16.dp))
                                .panel(RoundedCornerShape(16.dp), fill = tuneColors.raised)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Artwork(song.thumb, artworkSize - 16.dp, corner = 10.dp)
                        }
                    }
                    Spacer(Modifier.height(gap))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = tuneColors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (openArtist != null) Modifier.clickable(onClick = openArtist) else Modifier,
                            )
                        }
                        PanelIconButton(
                            if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            if (liked) "Unlike" else "Like",
                            size = 50.dp,
                            tint = if (liked) tuneColors.danger else tuneColors.text,
                            fill = tuneColors.surface,
                        ) { TuneHubApp.library.toggleLike(song) }
                    }

                    Spacer(Modifier.height(gap))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .panel(RoundedCornerShape(12.dp), fill = TuneHubInk, stroke = LcdStroke)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        SegmentedSeekBar(
                            songId = song.id,
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            playedColor = tuneColors.accent,
                            playheadColor = TuneHubAmber,
                            unplayedColor = LcdTrack,
                            onSeek = { f -> if (duration > 0) PlayerConnection.seekTo((f * duration).toLong()) },
                            onSeekPreview = { seekPreview = it },
                        )
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            TimeLabel(seekPreview?.let { (it * duration).toLong() } ?: position)
                            Spacer(Modifier.weight(1f))
                            TimeLabel(duration)
                        }
                    }

                    Spacer(Modifier.height(gap))
                    ControlDock()
                    Spacer(Modifier.height(gap))

                    PlayerActionGrid(
                        hiRes = hiRes,
                        ultra = ultra,
                        sleep = sleep,
                        onQueue = { showQueue = true },
                        onSleep = { showSleep = true },
                        onShare = { shareSong(context, song) },
                    )
                }

            }

            // Back closes the full-screen lyrics before it closes Now Playing
            BackHandler(enabled = showLyrics) { showLyrics = false }
            AnimatedVisibility(showLyrics, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
                FullLyrics(lyrics, position, onRequest = requestLyrics, onClose = { showLyrics = false })
            }
        }

        if (showQueue) QueueSheet { showQueue = false }
        if (showSleep) SleepSheet { showSleep = false }
    }
}

@Composable
private fun PlayerActionGrid(
    hiRes: Boolean,
    ultra: Boolean,
    sleep: SleepTimer.State,
    onQueue: () -> Unit,
    onSleep: () -> Unit,
    onShare: () -> Unit,
) {
    val tileHeight = with(LocalDensity.current) { maxOf(60.dp, 38.sp.toDp() + 16.dp) }
    Box(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RemoteGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(RemoteGap)) {
                SoundQualityMenu(hiRes, PlayerConnection::setHiRes, Modifier.weight(1f).height(tileHeight), QualityTile)
                PlayerTile(
                    modifier = Modifier.weight(1f).height(tileHeight).semantics { selected = ultra },
                    shape = UltraTile,
                    active = ultra,
                    onClick = { TuneHubApp.library.updateEq { it.copy(ultra = !it.ultra) } },
                ) {
                    TileLabel("UltraTune")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(RemoteGap)) {
                PlayerTile(Modifier.weight(1f).height(tileHeight), QueueTile, onClick = onQueue) { TileLabel("Queue") }
                PlayerTile(Modifier.weight(1f).height(tileHeight), SleepTile, active = sleep != SleepTimer.State.Off, onClick = onSleep) {
                    TileLabel(sleepLabel(sleep))
                }
            }
        }
        PlayerTile(Modifier.align(Alignment.Center).size(ShareSize), CircleShape, onClick = onShare) {
            Icon(Icons.Default.Share, "Share", modifier = Modifier.size(20.dp))
        }
    }
}

private val RemoteGap = 8.dp
private val ShareSize = 52.dp
private val QualityTile = RemoteTileShape(innerAtEnd = true, innerAtBottom = true)
private val UltraTile = RemoteTileShape(innerAtEnd = false, innerAtBottom = true)
private val QueueTile = RemoteTileShape(innerAtEnd = true, innerAtBottom = false)
private val SleepTile = RemoteTileShape(innerAtEnd = false, innerAtBottom = false)

/** Each tile extends across its half of the grid with a cutout for the central Share button. */
private class RemoteTileShape(
    private val innerAtEnd: Boolean,
    private val innerAtBottom: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val halfGap = RemoteGap.toPx() / 2
        val innerAtRight = innerAtEnd == (layoutDirection == LayoutDirection.Ltr)
        val center = Offset(
            if (innerAtRight) size.width + halfGap else -halfGap,
            if (innerAtBottom) size.height + halfGap else -halfGap,
        )
        val tile = Path().apply {
            addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(12.dp.toPx())))
        }
        val cutout = Path().apply { addOval(Rect(center, ShareSize.toPx() / 2 + 5.dp.toPx())) }
        Outline.Generic(Path.combine(PathOperation.Difference, tile, cutout))
    }
}

@Composable
private fun PlayerTile(
    modifier: Modifier,
    shape: Shape,
    active: Boolean = false,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val fill by animateColorAsState(
        if (active) tuneColors.accent.copy(alpha = 0.15f).compositeOver(tuneColors.surface) else tuneColors.surface,
        tween(160),
        label = "playerTileFill",
    )
    CompositionLocalProvider(LocalContentColor provides if (active) tuneColors.accentText else tuneColors.text) {
        Row(
            modifier
                .heightIn(min = 52.dp)
                .panel(shape, fill = fill)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun TileLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SoundQualityMenu(hiRes: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier, shape: Shape) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        PlayerTile(
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Sound quality" },
            shape = shape,
            onClick = { expanded = true },
        ) {
            TileLabel(if (hiRes) "Hi-Res" else "Data Saver", Modifier.weight(1f, fill = false))
            Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Hi-Res") },
                trailingIcon = { if (hiRes) Icon(Icons.Default.Check, "Selected") },
                onClick = {
                    expanded = false
                    if (!hiRes) onSelect(true)
                },
            )
            DropdownMenuItem(
                text = { Text("Data Saver") },
                trailingIcon = { if (!hiRes) Icon(Icons.Default.Check, "Selected") },
                onClick = {
                    expanded = false
                    if (hiRes) onSelect(false)
                },
            )
        }
    }
}

@Composable
private fun ControlDock() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton(
            AppIcons.Shuffle, "Shuffle",
            tint = if (PlayerConnection.shuffle) tuneColors.accentText else tuneColors.muted,
            onClick = PlayerConnection::toggleShuffle,
        )
        DockButton(AppIcons.SkipPrevious, "Previous", iconSize = 32, onClick = PlayerConnection::previous)
        PlayPauseButton(size = 64)
        DockButton(AppIcons.SkipNext, "Next", iconSize = 32, onClick = PlayerConnection::next)
        val mode = PlayerConnection.repeatMode
        DockButton(
            if (mode == Player.REPEAT_MODE_ONE) AppIcons.RepeatOne else AppIcons.Repeat,
            "Repeat",
            tint = if (mode == Player.REPEAT_MODE_OFF) tuneColors.muted else tuneColors.accentText,
            onClick = PlayerConnection::cycleRepeat,
        )
    }
}

@Composable
private fun DockButton(icon: ImageVector, label: String, iconSize: Int = 24, tint: Color = tuneColors.text, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    Text(formatDuration(ms / 1000), style = MaterialTheme.typography.labelSmall, color = TuneHubAmber)
}

@Composable
private fun sleepLabel(state: SleepTimer.State): String = when (state) {
    is SleepTimer.State.At -> {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(state) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
        "Sleep " + formatDuration(((state.endsAtMs - now) / 1000).coerceAtLeast(0))
    }
    SleepTimer.State.EndOfSong -> "Sleep: end of song"
    SleepTimer.State.Off -> "Sleep"
}

// ------------------------------------------------------------------ lyrics

private fun currentLine(lines: List<Lyrics.Line>, positionMs: Long): Int =
    lines.indexOfLast { it.timeMs <= positionMs + 300 }

@Composable
private fun FullLyrics(
    state: LyricsState,
    position: Long,
    onRequest: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        PaperBackground()
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelIconButton(Icons.Default.Close, "Close lyrics", onClick = onClose)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Lyrics", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        PlayerConnection.current?.title.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = tuneColors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when (state) {
                LyricsState.Loading -> LoadingBox(Modifier.fillMaxSize())
                LyricsState.Missing -> EmptyHint("No lyrics found for this song.")
                LyricsState.Failed -> ErrorBox("Couldn't reach the lyrics service.", onRetry = onRequest)
                LyricsState.NotRequested -> TextButton(
                    onClick = onRequest,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text("Look up lyrics")
                }
                is LyricsState.Found -> LyricsText(state.result, position)
            }
        }
    }
}

@Composable
private fun LyricsText(result: Lyrics.Result, position: Long) {
    val listState = rememberLazyListState()
    val current = if (result.synced) currentLine(result.lines, position) else -1

    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }

    if (result.synced) {
        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
            itemsIndexed(result.lines) { i, line ->
                val color by animateColorAsState(
                    if (i == current) tuneColors.accentText else tuneColors.muted,
                    tween(180),
                    label = "lyricHighlight",
                )
                Text(
                    line.text.ifBlank { "♪" },
                    fontFamily = EditorialSerif,
                    fontSize = 27.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { PlayerConnection.seekTo(line.timeMs) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    } else {
        Text(
            result.plain,
            fontFamily = EditorialSerif,
            fontSize = 23.sp,
            lineHeight = 33.sp,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        )
    }
}

// ------------------------------------------------------------------ sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSheet(onDismiss: () -> Unit) {
    val state by SleepTimer.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = tuneColors.surface) {
        Text("Sleep timer", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 22.dp, bottom = 8.dp))
        listOf(5, 15, 30, 45, 60).forEach { minutes ->
            SheetOption("$minutes minutes") { SleepTimer.start(minutes); onDismiss() }
        }
        SheetOption("End of song") { SleepTimer.endOfSong(); onDismiss() }
        if (state != SleepTimer.State.Off) SheetOption("Turn off timer") { SleepTimer.cancel(); onDismiss() }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetOption(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(onDismiss: () -> Unit) {
    val queue = PlayerConnection.queue
    val current = PlayerConnection.index
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = tuneColors.surface) {
        Row(Modifier.padding(start = 22.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (current < queue.size - 1) TextButton(onClick = PlayerConnection::clearUpcoming) { Text("Clear upcoming", color = tuneColors.accentText) }
        }
        LazyColumn(state = listState, modifier = Modifier.padding(bottom = 16.dp)) {
            itemsIndexed(queue, key = { i, s -> "$i-${s.id}" }) { i, song ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { PlayerConnection.jumpTo(i) }
                        .padding(start = 22.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(song.thumb, 46.dp, corner = 14.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                    ) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (i == current) tuneColors.accentText else tuneColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (i == current) "Now playing" else song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = tuneColors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (i > current) {
                        if (i > current + 1) {
                            DockButton(Icons.Default.KeyboardArrowUp, "Move up", tint = tuneColors.muted) { PlayerConnection.move(i, i - 1) }
                        }
                        DockButton(Icons.Default.Close, "Remove", tint = tuneColors.muted) { PlayerConnection.removeAt(i) }
                    }
                }
            }
        }
    }
}

