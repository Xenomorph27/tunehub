package com.spitify.app.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.spitify.app.SpitifyApp
import com.spitify.app.data.ArtistRef
import com.spitify.app.data.Lyrics
import com.spitify.app.data.Song
import com.spitify.app.data.formatDuration
import com.spitify.app.playback.SleepTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

/** Horizontal swipe on [content] skips tracks: left = next, right = previous. */
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
    val tint = rememberArtColor(song.thumb)
    val liked by SpitifyApp.library.data.collectAsStateWithLifecycle()
    val isLiked = liked.liked.any { it.id == song.id }
    var dragX by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint)
            .clickable(onClick = onOpen)
            .swipeToSkip({ dragX }, { dragX = it }),
    ) {
        Row(
            Modifier
                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
                .graphicsLayer { translationX = dragX / 2 },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.thumb, 42.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { SpitifyApp.library.toggleLike(song) }) {
                Icon(
                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            IconButton(onClick = PlayerConnection::togglePlay) {
                if (PlayerConnection.isBuffering) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(
                        if (PlayerConnection.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                        if (PlayerConnection.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
            drawStopIndicator = {},
            gapSize = 0.dp,
            trackColor = Color.White.copy(alpha = 0.2f),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(2.dp),
        )
    }
}

// ------------------------------------------------------------------ now playing

private sealed interface LyricsState {
    data object Loading : LyricsState
    data object Missing : LyricsState
    class Found(val result: Lyrics.Result) : LyricsState
}

@Composable
private fun rememberLyrics(song: Song): LyricsState =
    produceState<LyricsState>(LyricsState.Loading, song.id) {
        value = LyricsState.Loading
        val found = withContext(Dispatchers.IO) { runCatching { Lyrics.find(SpitifyApp.http, song) }.getOrNull() }
        value = if (found == null) LyricsState.Missing else LyricsState.Found(found)
    }.value

@OptIn(ExperimentalMaterial3Api::class)
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
    val data by SpitifyApp.library.data.collectAsStateWithLifecycle()
    val sleep by SleepTimer.state.collectAsStateWithLifecycle()
    val liked = data.liked.any { it.id == song.id }
    val position = rememberPosition(250)
    val duration = PlayerConnection.durationMs
    val tint = rememberArtColor(song.thumb, fallback = Color(0xFF3A4048))
    val lyrics = rememberLyrics(song)

    var dragging by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var artDrag by remember { mutableFloatStateOf(0f) }

    val openArtist: (() -> Unit)? = song.artistUrl?.let { url ->
        { nav.open(Route.Artist(ArtistRef(url, song.artist))) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tint, lerp(tint, Color.Black, 0.6f), Color.Black)))
            // swallow taps so they don't reach the screen underneath
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val screenHeight = maxHeight
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(
                Modifier
                    .height(screenHeight)
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close", Modifier.size(30.dp)) }
                    Text(
                        "NOW PLAYING",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { openMenu(SongMenuRequest(song, null)) }) { Icon(Icons.Default.MoreVert, "More") }
                }

                Spacer(Modifier.weight(1f))
                Artwork(
                    song.thumb,
                    size = 1000.dp,
                    corner = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer {
                            translationX = artDrag
                            alpha = 1f - (kotlin.math.abs(artDrag) / 900f).coerceAtMost(0.5f)
                        }
                        .swipeToSkip({ artDrag }, { artDrag = it }),
                )
                Spacer(Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (openArtist != null) Modifier.clickable(onClick = openArtist) else Modifier,
                        )
                    }
                    IconButton(onClick = { SpitifyApp.library.toggleLike(song) }) {
                        Icon(
                            if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            if (liked) "Unlike" else "Like",
                            tint = if (liked) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Slider(
                    value = dragging ?: fraction,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { PlayerConnection.seekTo((it * duration).toLong()) }
                        dragging = null
                    },
                    enabled = duration > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                )
                Row {
                    TimeLabel(dragging?.let { (it * duration).toLong() } ?: position)
                    Spacer(Modifier.weight(1f))
                    TimeLabel(duration)
                }

                Spacer(Modifier.height(8.dp))
                Controls()
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSleep = true }) {
                        Icon(AppIcons.Timer, "Sleep timer", tint = if (sleep != SleepTimer.State.Off) MaterialTheme.colorScheme.primary else Color.White)
                    }
                    if (sleep != SleepTimer.State.Off) SleepLabel(sleep)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { shareSong(context, song) }) { Icon(Icons.Default.Share, "Share") }
                    IconButton(onClick = { showQueue = true }) { Icon(AppIcons.QueueMusic, "Queue") }
                }
                Spacer(Modifier.height(8.dp))
            }

            LyricsCard(lyrics, position, tint) { showLyrics = true }
            Spacer(Modifier.navigationBarsPadding().height(32.dp))
        }

        AnimatedVisibility(showLyrics, enter = fadeIn(), exit = fadeOut()) {
            val found = lyrics as? LyricsState.Found
            if (found != null) FullLyrics(found.result, position, tint) { showLyrics = false }
        }
    }

    if (showQueue) QueueSheet { showQueue = false }
    if (showSleep) SleepSheet { showSleep = false }
}

@Composable
private fun Controls() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = PlayerConnection::toggleShuffle) {
            Icon(
                AppIcons.Shuffle, "Shuffle",
                tint = if (PlayerConnection.shuffle) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        IconButton(onClick = PlayerConnection::previous, modifier = Modifier.size(56.dp)) {
            Icon(AppIcons.SkipPrevious, "Previous", Modifier.size(38.dp))
        }
        Box(
            Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = PlayerConnection::togglePlay),
            contentAlignment = Alignment.Center,
        ) {
            if (PlayerConnection.isBuffering) {
                CircularProgressIndicator(Modifier.size(30.dp), color = Color.Black, strokeWidth = 3.dp)
            } else {
                Icon(
                    if (PlayerConnection.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                    if (PlayerConnection.isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        IconButton(onClick = PlayerConnection::next, modifier = Modifier.size(56.dp)) {
            Icon(AppIcons.SkipNext, "Next", Modifier.size(38.dp))
        }
        IconButton(onClick = PlayerConnection::cycleRepeat) {
            val mode = PlayerConnection.repeatMode
            Icon(
                if (mode == Player.REPEAT_MODE_ONE) AppIcons.RepeatOne else AppIcons.Repeat,
                "Repeat",
                tint = if (mode == Player.REPEAT_MODE_OFF) Color.White else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    Text(formatDuration(ms / 1000), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
}

@Composable
private fun SleepLabel(state: SleepTimer.State) {
    val text = when (state) {
        is SleepTimer.State.At -> {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(state) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(1000)
                }
            }
            formatDuration(((state.endsAtMs - now) / 1000).coerceAtLeast(0))
        }
        SleepTimer.State.EndOfSong -> "End of song"
        SleepTimer.State.Off -> ""
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}

// ------------------------------------------------------------------ lyrics

private fun currentLine(lines: List<Lyrics.Line>, positionMs: Long): Int =
    lines.indexOfLast { it.timeMs <= positionMs + 300 }

@Composable
private fun LyricsCard(state: LyricsState, position: Long, tint: Color, onExpand: () -> Unit) {
    val found = (state as? LyricsState.Found)?.result
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(lerp(tint, Color.White, 0.12f))
            .clickable(enabled = found != null, onClick = onExpand)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lyrics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (found != null) Icon(Icons.Default.KeyboardArrowUp, "Expand lyrics")
        }
        Spacer(Modifier.height(12.dp))
        when {
            state is LyricsState.Loading -> Text("Looking for lyrics…", color = Color.White.copy(alpha = 0.7f))
            found == null -> Text("No lyrics found for this song.", color = Color.White.copy(alpha = 0.7f))
            found.synced -> {
                val current = currentLine(found.lines, position).coerceAtLeast(0)
                found.lines.drop(current).take(5).forEachIndexed { i, line ->
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (i == 0) Color.White else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
            else -> Text(
                found.plain.lines().take(8).joinToString("\n"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FullLyrics(result: Lyrics.Result, position: Long, tint: Color, onClose: () -> Unit) {
    val listState = rememberLazyListState()
    val current = if (result.synced) currentLine(result.lines, position) else -1

    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(lerp(tint, Color.Black, 0.2f))
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close lyrics") }
            Text(PlayerConnection.current?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (result.synced) {
            LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp)) {
                itemsIndexed(result.lines) { i, line ->
                    Text(
                        line.text.ifBlank { "♪" },
                        fontSize = 26.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            i == current -> Color.White
                            i < current -> Color.White.copy(alpha = 0.6f)
                            else -> Color.Black.copy(alpha = 0.55f)
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
                fontSize = 22.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSheet(onDismiss: () -> Unit) {
    val state by SleepTimer.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Text("Sleep timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
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
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(onDismiss: () -> Unit) {
    val queue = PlayerConnection.queue
    val current = PlayerConnection.index
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (current < queue.size - 1) TextButton(onClick = PlayerConnection::clearUpcoming) { Text("Clear upcoming") }
        }
        LazyColumn(state = listState, modifier = Modifier.padding(bottom = 16.dp)) {
            itemsIndexed(queue, key = { i, s -> "$i-${s.id}" }) { i, song ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { PlayerConnection.jumpTo(i) }
                        .padding(start = 20.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(song.thumb, 44.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            song.title,
                            color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (i == current) "Now playing" else song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (i > current) {
                        if (i > current + 1) {
                            IconButton(onClick = { PlayerConnection.move(i, i - 1) }) {
                                Icon(Icons.Default.KeyboardArrowUp, "Move up", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { PlayerConnection.removeAt(i) }) {
                            Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
