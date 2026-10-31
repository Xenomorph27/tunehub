package com.spitify.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.spitify.app.SpitifyApp
import com.spitify.app.data.AlbumRef
import com.spitify.app.data.ArtistRef
import com.spitify.app.data.Song
import com.spitify.app.data.formatCount
import com.spitify.app.data.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// ------------------------------------------------------------------ artwork

@Composable
fun Artwork(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 4.dp,
    placeholder: ImageVector = AppIcons.MusicNote,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            placeholder, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.4f),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 2×2 collage of the first four distinct covers, or a single cover when there are fewer. */
@Composable
fun PlaylistCover(songs: List<Song>, size: Dp, corner: Dp = 4.dp) {
    val thumbs = songs.mapNotNull { it.thumb }.distinct().take(4)
    if (thumbs.size < 4) {
        Artwork(thumbs.firstOrNull(), size, corner = corner, placeholder = AppIcons.QueueMusic)
        return
    }
    Column(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner)),
    ) {
        for (row in 0..1) {
            Row {
                for (col in 0..1) {
                    AsyncImage(
                        model = thumbs[row * 2 + col],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(size / 2),
                    )
                }
            }
        }
    }
}

@Composable
fun LikedCover(size: Dp, corner: Dp = 4.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Color(0xFF4C2BD9), Color(0xFF8FB9E8)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.42f))
    }
}

@Composable
fun DownloadsCover(size: Dp, corner: Dp = 4.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Color(0xFF0C6E4F), Lime))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Download, null, tint = Ink, modifier = Modifier.fillMaxSize(0.42f))
    }
}

private val artColors = ConcurrentHashMap<String, Color>()

/** A dark, saturated color pulled from the artwork, for backgrounds behind it. */
@Composable
fun rememberArtColor(url: String?, fallback: Color = Color(0xFF2B3036)): Color {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf(url?.let { artColors[it] } ?: fallback) }
    LaunchedEffect(url) {
        if (url == null || artColors.containsKey(url)) return@LaunchedEffect
        val extracted = withContext(Dispatchers.IO) { extractColor(context, url) }
        if (extracted != null) {
            artColors[url] = extracted
            color = extracted
        }
    }
    return animateColorAsState(color, tween(600), label = "artColor").value
}

private suspend fun extractColor(context: Context, url: String): Color? = runCatching {
    val request = ImageRequest.Builder(context).data(url).size(96).allowHardware(false).build()
    val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
        ?: return@runCatching null
    val palette = Palette.from(bitmap).generate()
    val argb = palette.getDarkVibrantColor(palette.getVibrantColor(palette.getDominantColor(0)))
    if (argb == 0) null else lerp(Color(argb), Color.Black, 0.25f)
}.getOrNull()

// ------------------------------------------------------------------ song rows & actions

/** Opens the song actions sheet; provided by the app root so there's only ever one sheet. */
class SongMenuRequest(val song: Song, val playlistId: String?)

val LocalSongMenu = staticCompositionLocalOf<(SongMenuRequest) -> Unit> { {} }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    playlistId: String? = null,
    trackNumber: Int? = null,
    showArt: Boolean = true,
) {
    val openMenu = LocalSongMenu.current
    val active = PlayerConnection.current?.id == song.id
    val downloads by SpitifyApp.library.data.collectAsStateWithLifecycle()
    val downloaded = downloads.downloads.any { it.id == song.id }

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { openMenu(SongMenuRequest(song, playlistId)) })
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trackNumber != null) {
            Text(
                trackNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 28.dp),
            )
        }
        if (showArt) Artwork(song.thumb, 50.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloaded) {
                    Icon(
                        AppIcons.DownloadDone, "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 2.dp),
                    )
                }
                val duration = formatDuration(song.durationSec)
                Text(
                    if (duration.isEmpty()) song.artist else "${song.artist} • $duration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { openMenu(SongMenuRequest(song, playlistId)) }) {
            Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(request: SongMenuRequest, onDismiss: () -> Unit) {
    val song = request.song
    val library = SpitifyApp.library
    val downloads = SpitifyApp.downloads
    val data by library.data.collectAsStateWithLifecycle()
    val progress by downloads.progress.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val context = LocalContext.current
    var pickingPlaylist by remember { mutableStateOf(false) }

    val liked = data.liked.any { it.id == song.id }
    val downloaded = data.downloads.any { it.id == song.id }
    val downloading = progress.containsKey(song.id)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.thumb, 52.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

        if (pickingPlaylist) {
            PlaylistPicker(song, onDone = onDismiss)
            return@ModalBottomSheet
        }

        SheetAction(
            if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            if (liked) "Remove from Liked Songs" else "Add to Liked Songs",
            tint = if (liked) MaterialTheme.colorScheme.primary else null,
        ) { library.toggleLike(song); onDismiss() }
        SheetAction(AppIcons.PlaylistAdd, "Add to playlist") { pickingPlaylist = true }
        SheetAction(AppIcons.SkipNext, "Play next") { PlayerConnection.playNext(song); onDismiss() }
        SheetAction(AppIcons.QueueMusic, "Add to queue") { PlayerConnection.enqueue(song); onDismiss() }
        SheetAction(AppIcons.Radio, "Go to song radio") { PlayerConnection.startRadio(song); onDismiss() }
        when {
            downloaded -> SheetAction(Icons.Default.Delete, "Remove download") { downloads.remove(song.id); onDismiss() }
            downloading -> SheetAction(AppIcons.Download, "Downloading…", enabled = false) {}
            else -> SheetAction(AppIcons.Download, "Download") { downloads.download(song); onDismiss() }
        }
        if (song.artistUrl != null) {
            SheetAction(Icons.Default.Person, "Go to artist") {
                onDismiss()
                nav.open(Route.Artist(ArtistRef(song.artistUrl, song.artist)))
            }
        }
        SheetAction(Icons.Default.Share, "Share") { shareSong(context, song); onDismiss() }
        if (request.playlistId != null) {
            SheetAction(Icons.Default.Delete, "Remove from this playlist") {
                library.removeFromPlaylist(request.playlistId, song.id)
                onDismiss()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ColumnScope.PlaylistPicker(song: Song, onDone: () -> Unit) {
    val library = SpitifyApp.library
    val data by library.data.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    SheetAction(Icons.Default.Add, "New playlist") { creating = true }
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        items(data.playlists, key = { it.id }) { p ->
            val already = p.songs.any { it.id == song.id }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !already) { library.addToPlaylist(p.id, song); onDone() }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaylistCover(p.songs, 44.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (already) "Already added" else "${p.songs.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))

    if (creating) {
        NamePlaylistDialog("New playlist", onDismiss = { creating = false }) { name ->
            library.createPlaylist(name, listOf(song))
            onDone()
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp),
        )
    }
}

fun shareSong(context: Context, song: Song) {
    val text = "${song.title} – ${song.artist}\nhttps://music.youtube.com/watch?v=${song.id}"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ------------------------------------------------------------------ albums & artists

@Composable
fun AlbumCard(album: AlbumRef, size: Dp = 150.dp, onClick: () -> Unit) {
    Column(
        Modifier
            .width(size)
            .clickable(onClick = onClick),
    ) {
        Artwork(album.thumb, size, corner = 6.dp, placeholder = AppIcons.Album)
        Text(
            album.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (album.subtitle.isNotBlank()) {
            Text(
                album.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ArtistCircle(artist: ArtistRef, size: Dp = 130.dp, onClick: () -> Unit) {
    Column(
        Modifier
            .width(size)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(artist.thumb, size, corner = size / 2, placeholder = Icons.Default.Person)
        Text(
            artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("Artist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** List row for an album, playlist or artist (artists get round art). */
@Composable
fun BrowseRow(
    title: String,
    subtitle: String,
    thumb: String?,
    round: Boolean = false,
    placeholder: ImageVector = AppIcons.Album,
    art: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (art != null) art() else Artwork(thumb, 56.dp, corner = if (round) 28.dp else 4.dp, placeholder = placeholder)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun artistSubtitle(artist: ArtistRef): String =
    if (artist.subscribers > 0) "Artist • ${formatCount(artist.subscribers)} subscribers" else "Artist"

// ------------------------------------------------------------------ collection pages

/** Header for albums, playlists, Liked Songs and Downloads: art, titles, actions, big play button. */
@Composable
fun CollectionHeader(
    title: String,
    subtitle: String,
    meta: String,
    tint: Color,
    onBack: () -> Unit,
    art: @Composable () -> Unit,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSubtitleClick: (() -> Unit)? = null,
    topActions: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(tint, MaterialTheme.colorScheme.background))),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            topActions()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) { art() }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .then(if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier),
                )
            }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShuffle, enabled = playEnabled) {
                    Icon(AppIcons.Shuffle, "Shuffle play", modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(8.dp))
                PlayCircle(enabled = playEnabled, onClick = onPlay)
            }
        }
    }
}

@Composable
fun PlayCircle(enabled: Boolean = true, size: Dp = 56.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.PlayArrow, "Play", tint = Ink, modifier = Modifier.size(size * 0.55f))
    }
}

/** Download-all toggle for a list of songs; shows progress while downloading. */
@Composable
fun DownloadAllButton(songs: List<Song>) {
    val data by SpitifyApp.library.data.collectAsStateWithLifecycle()
    val progress by SpitifyApp.downloads.progress.collectAsStateWithLifecycle()
    val downloadedIds = data.downloads.map { it.id }.toSet()
    val all = songs.isNotEmpty() && songs.all { it.id in downloadedIds }
    val busy = songs.any { progress.containsKey(it.id) }

    IconButton(
        enabled = songs.isNotEmpty() && !busy,
        onClick = { if (all) songs.forEach { SpitifyApp.downloads.remove(it.id) } else SpitifyApp.downloads.downloadAll(songs) },
    ) {
        when {
            busy -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            all -> Icon(AppIcons.DownloadDone, "Remove downloads", tint = MaterialTheme.colorScheme.primary)
            else -> Icon(AppIcons.Download, "Download all")
        }
    }
}

// ------------------------------------------------------------------ misc

@Composable
fun NamePlaylistDialog(
    title: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("My playlist") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator(Modifier.size(32.dp)) }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(6.dp))
            Text("Retry")
        }
    }
}
