package com.tunehub.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val glow = rememberArtColor(song.thumb)
    var dragX by remember { mutableFloatStateOf(0f) }
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), fill = Aurora.Sheet.copy(alpha = 0.88f))
            .background(Brush.horizontalGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent)))
            .clickable(onClick = onOpen)
            .swipeToSkip({ dragX }, { dragX = it }),
    ) {
        Row(
            Modifier
                .padding(start = 10.dp, top = 9.dp, bottom = 7.dp, end = 10.dp)
                .graphicsLayer { translationX = dragX / 2 },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.thumb, 46.dp, corner = 14.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Aurora.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlayPauseButton(size = 46)
        }
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 6.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.14f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(Aurora.accent),
            )
        }
    }
}

/** Gradient play/pause circle; shows a spinner while the stream buffers. */
@Composable
private fun PlayPauseButton(size: Int) {
    GradientCircleButton(
        if (PlayerConnection.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
        if (PlayerConnection.isPlaying) "Pause" else "Play",
        size = size.dp,
        content = if (PlayerConnection.isBuffering) {
            { CircularProgressIndicator(Modifier.size((size * 0.42f).dp), color = Aurora.Ink, strokeWidth = 2.5.dp) }
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
    val glow = rememberArtColor(song.thumb, fallback = Aurora.Violet)
    var lyricsAttempt by remember(song.id) { mutableIntStateOf(0) }
    val lyrics = rememberLyrics(song, settings.autoLyrics, lyricsAttempt)
    val requestLyrics: () -> Unit = { lyricsAttempt++ }

    var seekPreview by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var artDrag by remember { mutableFloatStateOf(0f) }
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val openArtist: (() -> Unit)? = song.artistUrl?.let { url ->
        { nav.open(Route.Artist(ArtistRef(url, song.artist))) }
    }

    // this overlay sits outside the Scaffold, so set the content color it would have provided
    CompositionLocalProvider(LocalContentColor provides Aurora.Text) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // swallow taps so they don't reach the screen underneath
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            AuroraBackground(tint = glow, intensity = 1.35f)
            val screenHeight = maxHeight
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(
                    Modifier
                        .height(screenHeight)
                        .systemBarsPadding()
                        .padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        GlassIconButton(Icons.Default.KeyboardArrowDown, "Close", onClick = onClose)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = Aurora.Muted)
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                        GlassIconButton(Icons.Default.MoreVert, "More") { openMenu(SongMenuRequest(song, null)) }
                    }

                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(314.dp)
                            .graphicsLayer {
                                translationX = artDrag
                                alpha = 1f - (abs(artDrag) / 900f).coerceAtMost(0.5f)
                            }
                            .swipeToSkip({ artDrag }, { artDrag = it }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(314.dp)
                                .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent))),
                        )
                        ProgressRing(seekPreview ?: progress, Modifier.size(296.dp), stroke = 7.dp)
                        Artwork(song.thumb, 268.dp, corner = 134.dp)
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Aurora.Ink.copy(alpha = 0.85f))
                                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                        )
                    }
                    Spacer(Modifier.weight(1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Aurora.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (openArtist != null) Modifier.clickable(onClick = openArtist) else Modifier,
                            )
                        }
                        GlassIconButton(
                            if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            if (liked) "Unlike" else "Like",
                            size = 50.dp,
                            tint = if (liked) Color(0xFFFF8FD6) else Aurora.Text,
                            fill = if (liked) Aurora.Pink.copy(alpha = 0.25f) else Aurora.Glass,
                        ) { TuneHubApp.library.toggleLike(song) }
                    }

                    Spacer(Modifier.height(14.dp))
                    WaveformSeekBar(
                        seed = song.id,
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        onSeek = { f -> if (duration > 0) PlayerConnection.seekTo((f * duration).toLong()) },
                        onSeekPreview = { seekPreview = it },
                    )
                    Row(Modifier.padding(top = 6.dp)) {
                        TimeLabel(seekPreview?.let { (it * duration).toLong() } ?: position)
                        Spacer(Modifier.weight(1f))
                        TimeLabel(duration)
                    }

                    Spacer(Modifier.height(14.dp))
                    ControlDock()
                    Spacer(Modifier.height(14.dp))

                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassChip("Lyrics") { if (lyrics is LyricsState.Found) showLyrics = true else requestLyrics() }
                        GlassChip("Hi-Res", selected = hiRes) { PlayerConnection.setHiRes(!hiRes) }
                        GlassChip("UltraTune", selected = ultra) { TuneHubApp.library.updateEq { it.copy(ultra = !it.ultra) } }
                        GlassChip("Queue") { showQueue = true }
                        GlassChip(sleepLabel(sleep), selected = sleep != SleepTimer.State.Off) { showSleep = true }
                        Box(
                            Modifier
                                .size(38.dp)
                                .glass(CircleShape)
                                .clickable { shareSong(context, song) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Share, "Share", modifier = Modifier.size(18.dp)) }
                        if (quality != null) {
                            Text(
                                quality,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGrotesk),
                                color = Aurora.Cyan,
                                modifier = Modifier
                                    .border(1.dp, Aurora.Cyan.copy(alpha = 0.5f), RoundedCornerShape(50))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                LyricsCard(lyrics, position, glow, onRetry = requestLyrics) { showLyrics = true }
                Spacer(
                    Modifier
                        .navigationBarsPadding()
                        .height(32.dp)
                )
            }

            // Back closes the full-screen lyrics before it closes Now Playing
            BackHandler(enabled = showLyrics) { showLyrics = false }
            AnimatedVisibility(showLyrics, enter = fadeIn(), exit = fadeOut()) {
                val found = lyrics as? LyricsState.Found
                if (found != null) FullLyrics(found.result, position, glow) { showLyrics = false }
            }
        }

        if (showQueue) QueueSheet { showQueue = false }
        if (showSleep) SleepSheet { showSleep = false }
    }
}

@Composable
private fun ControlDock() {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(34.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton(
            AppIcons.Shuffle, "Shuffle",
            tint = if (PlayerConnection.shuffle) Aurora.Cyan else Aurora.Muted,
            onClick = PlayerConnection::toggleShuffle,
        )
        DockButton(AppIcons.SkipPrevious, "Previous", iconSize = 32, onClick = PlayerConnection::previous)
        PlayPauseButton(size = 76)
        DockButton(AppIcons.SkipNext, "Next", iconSize = 32, onClick = PlayerConnection::next)
        val mode = PlayerConnection.repeatMode
        DockButton(
            if (mode == Player.REPEAT_MODE_ONE) AppIcons.RepeatOne else AppIcons.Repeat,
            "Repeat",
            tint = if (mode == Player.REPEAT_MODE_OFF) Aurora.Muted else Aurora.Cyan,
            onClick = PlayerConnection::cycleRepeat,
        )
    }
}

@Composable
private fun DockButton(icon: ImageVector, label: String, iconSize: Int = 24, tint: Color = Aurora.Text, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    Text(formatDuration(ms / 1000), style = MaterialTheme.typography.bodySmall, color = Aurora.Muted)
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
private fun LyricsCard(state: LyricsState, position: Long, glow: Color, onRetry: () -> Unit, onExpand: () -> Unit) {
    val found = (state as? LyricsState.Found)?.result
    val retryable = state is LyricsState.Failed || state is LyricsState.NotRequested
    Column(
        Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(30.dp), fill = Aurora.GlassStrong)
            .background(Brush.verticalGradient(listOf(glow.copy(alpha = 0.25f), Color.Transparent)))
            .clickable(enabled = found != null || retryable, onClick = if (found != null) onExpand else onRetry)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lyrics", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (found != null) Icon(Icons.Default.KeyboardArrowUp, "Expand lyrics")
        }
        Spacer(Modifier.height(12.dp))
        when {
            state is LyricsState.Loading -> Text("Looking for lyrics…", color = Aurora.Muted)
            state is LyricsState.NotRequested -> Text("Tap to look up lyrics on LRCLIB", color = Aurora.Muted)
            state is LyricsState.Failed -> Text("Couldn't reach the lyrics service. Tap to try again.", color = Aurora.Muted)
            found == null -> Text("No lyrics found for this song.", color = Aurora.Muted)
            found.synced -> {
                val current = currentLine(found.lines, position).coerceAtLeast(0)
                found.lines.drop(current).take(5).forEachIndexed { i, line ->
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (i == 0) Aurora.Text else Aurora.Text.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
            else -> Text(found.plain.lines().take(8).joinToString("\n"), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun FullLyrics(result: Lyrics.Result, position: Long, glow: Color, onClose: () -> Unit) {
    val listState = rememberLazyListState()
    val current = if (result.synced) currentLine(result.lines, position) else -1

    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        AuroraBackground(tint = glow, intensity = 1.6f)
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Default.Close, "Close lyrics", onClick = onClose)
                Text(
                    PlayerConnection.current?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            if (result.synced) {
                LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 26.dp, vertical = 24.dp)) {
                    itemsIndexed(result.lines) { i, line ->
                        Text(
                            line.text.ifBlank { "♪" },
                            fontFamily = SpaceGrotesk,
                            fontSize = 27.sp,
                            lineHeight = 35.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                i == current -> Aurora.Text
                                i < current -> Aurora.Text.copy(alpha = 0.55f)
                                else -> Aurora.Text.copy(alpha = 0.28f)
                            },
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
                    fontFamily = SpaceGrotesk,
                    fontSize = 23.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(26.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSheet(onDismiss: () -> Unit) {
    val state by SleepTimer.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Aurora.Sheet) {
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

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Aurora.Sheet) {
        Row(Modifier.padding(start = 22.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (current < queue.size - 1) TextButton(onClick = PlayerConnection::clearUpcoming) { Text("Clear upcoming", color = Aurora.Lilac) }
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
                            color = if (i == current) Aurora.Cyan else Aurora.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (i == current) "Now playing" else song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Aurora.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (i > current) {
                        if (i > current + 1) {
                            DockButton(Icons.Default.KeyboardArrowUp, "Move up", tint = Aurora.Muted) { PlayerConnection.move(i, i - 1) }
                        }
                        DockButton(Icons.Default.Close, "Remove", tint = Aurora.Muted) { PlayerConnection.removeAt(i) }
                    }
                }
            }
        }
    }
}

