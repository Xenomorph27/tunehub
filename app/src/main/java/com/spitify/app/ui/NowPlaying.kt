package com.spitify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.spitify.app.SpitifyApp
import com.spitify.app.data.formatDuration
import kotlinx.coroutines.delay

/** Polls the player position while something is on screen that shows it. */
@Composable
private fun rememberPosition(): Long {
    var position by remember { mutableLongStateOf(PlayerConnection.positionMs()) }
    LaunchedEffect(PlayerConnection.controller) {
        while (true) {
            position = PlayerConnection.positionMs()
            delay(500)
        }
    }
    return position
}

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val song = PlayerConnection.current ?: return
    val position = rememberPosition()
    val duration = PlayerConnection.durationMs

    Column(
        Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.thumb, 42.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlayPauseIcon(onClick = PlayerConnection::togglePlay, size = 28)
            IconButton(onClick = PlayerConnection::next) { Icon(AppIcons.SkipNext, "Next") }
        }
        LinearProgressIndicator(
            progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
            drawStopIndicator = {},
            gapSize = 0.dp,
            trackColor = Color.White.copy(alpha = 0.15f),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(2.dp),
        )
    }
}

@Composable
private fun PlayPauseIcon(onClick: () -> Unit, size: Int) {
    IconButton(onClick = onClick) {
        if (PlayerConnection.isBuffering) {
            CircularProgressIndicator(Modifier.size((size - 6).dp), strokeWidth = 2.dp)
        } else {
            Icon(
                if (PlayerConnection.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                if (PlayerConnection.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(size.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(onClose: () -> Unit) {
    val song = PlayerConnection.current
    if (song == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val data by SpitifyApp.library.data.collectAsStateWithLifecycle()
    val liked = data.liked.any { it.id == song.id }
    val position = rememberPosition()
    val duration = PlayerConnection.durationMs
    var dragging by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF26331C), MaterialTheme.colorScheme.background)))
            // swallow taps so they don't reach the screen underneath
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close", Modifier.size(30.dp)) }
            Text(
                "Now playing",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { showQueue = true }) { Icon(AppIcons.QueueMusic, "Queue") }
        }

        Spacer(Modifier.weight(1f))
        Artwork(
            song.thumb,
            size = 1000.dp,
            corner = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { SpitifyApp.library.toggleLike(song) }) {
                Icon(
                    if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (liked) "Unlike" else "Like",
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
        )
        Row {
            val shown = dragging?.let { (it * duration).toLong() } ?: position
            TimeLabel(shown)
            Spacer(Modifier.weight(1f))
            TimeLabel(duration)
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = PlayerConnection::toggleShuffle) {
                Icon(
                    AppIcons.Shuffle, "Shuffle",
                    tint = if (PlayerConnection.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = PlayerConnection::previous, modifier = Modifier.size(56.dp)) {
                Icon(AppIcons.SkipPrevious, "Previous", Modifier.size(36.dp))
            }
            Box(
                Modifier
                    .size(68.dp)
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
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            IconButton(onClick = PlayerConnection::next, modifier = Modifier.size(56.dp)) {
                Icon(AppIcons.SkipNext, "Next", Modifier.size(36.dp))
            }
            IconButton(onClick = PlayerConnection::cycleRepeat) {
                val mode = PlayerConnection.repeatMode
                Icon(
                    if (mode == Player.REPEAT_MODE_ONE) AppIcons.RepeatOne else AppIcons.Repeat,
                    "Repeat",
                    tint = if (mode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            QueueList()
        }
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    Text(
        formatDuration(ms / 1000),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun QueueList() {
    val queue = PlayerConnection.queue
    val current = PlayerConnection.index
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))

    Text(
        "Queue",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
    LazyColumn(state = listState, modifier = Modifier.padding(bottom = 16.dp)) {
        items(queue.size) { i ->
            val song = queue[i]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { PlayerConnection.jumpTo(i) }
                    .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
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
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (i != current) {
                    IconButton(onClick = { PlayerConnection.removeAt(i) }) {
                        Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
