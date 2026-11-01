package com.tunehub.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.tunehub.app.TuneHubApp
import com.tunehub.app.data.AlbumRef
import com.tunehub.app.data.ArtistRef
import com.tunehub.app.data.Song
import com.tunehub.app.data.StreamResolver
import com.tunehub.app.data.formatDuration
import com.tunehub.app.data.thumbSized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// ------------------------------------------------------------------ artwork

/** Album art at the right resolution for its size, over a glass placeholder. */
@Composable
fun Artwork(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    placeholder: ImageVector = AppIcons.MusicNote,
) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Aurora.GlassStrong),
        contentAlignment = Alignment.Center,
    ) {
        Icon(placeholder, null, tint = Aurora.Muted, modifier = Modifier.fillMaxSize(0.4f))
        if (url != null) {
            AsyncImage(
                model = thumbSized(url, px),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 2×2 collage of the first four distinct covers, or a single cover when there are fewer. */
@Composable
fun PlaylistCover(songs: List<Song>, size: Dp, corner: Dp = 14.dp) {
    val thumbs = songs.mapNotNull { it.thumb }.distinct().take(4)
    if (thumbs.size < 4) {
        Artwork(thumbs.firstOrNull(), size, corner = corner, placeholder = AppIcons.QueueMusic)
        return
    }
    val half = size / 2
    val px = with(LocalDensity.current) { half.roundToPx() }
    Column(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner)),
    ) {
        for (row in 0..1) {
            Row {
                for (col in 0..1) {
                    AsyncImage(
                        model = thumbSized(thumbs[row * 2 + col], px),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(half),
                    )
                }
            }
        }
    }
}

@Composable
private fun IconCover(size: Dp, corner: Dp, brush: Brush, icon: ImageVector, tint: Color) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.fillMaxSize(0.42f))
    }
}

@Composable
fun LikedCover(size: Dp, corner: Dp = 14.dp) =
    IconCover(size, corner, Brush.linearGradient(listOf(Aurora.Violet, Aurora.Pink)), Icons.Default.Favorite, Color.White)

@Composable
fun DownloadsCover(size: Dp, corner: Dp = 14.dp) =
    IconCover(size, corner, Brush.linearGradient(listOf(Aurora.Cyan, Aurora.Violet)), AppIcons.Download, Aurora.Ink)

private val artColors = ConcurrentHashMap<String, Color>()

/** A vivid color pulled from the artwork, for the glows behind it. */
@Composable
fun rememberArtColor(url: String?, fallback: Color = Aurora.Violet): Color {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf(url?.let { artColors[it] } ?: fallback) }
    LaunchedEffect(url) {
        if (url == null || artColors.containsKey(url)) return@LaunchedEffect
        val extracted = withContext(Dispatchers.Default) { extractColor(context, url) }
        if (extracted != null) {
            artColors[url] = extracted
            color = extracted
        }
    }
    return animateColorAsState(color, tween(700), label = "artColor").value
}

private suspend fun extractColor(context: Context, url: String): Color? = runCatching {
    val request = ImageRequest.Builder(context).data(thumbSized(url, 96)).size(96).allowHardware(false).build()
    val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
        ?: return@runCatching null
    val palette = Palette.from(bitmap).generate()
    val argb = palette.getVibrantColor(palette.getLightVibrantColor(palette.getDominantColor(0)))
    if (argb == 0) null else Color(argb)
}.getOrNull()

// ------------------------------------------------------------------ song rows & actions

/** Starts looking up a song's stream as soon as a finger touches it, before the tap completes. */
fun Modifier.prefetchOnTouch(songId: String): Modifier = pointerInput(songId) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        StreamResolver.prefetch(songId)
    }
}

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
    val downloadedIds by TuneHubApp.library.downloadedIds.collectAsStateWithLifecycle()
    val downloaded = song.id in downloadedIds

    Row(
        Modifier
            .fillMaxWidth()
            .prefetchOnTouch(song.id)
            .combinedClickable(onClick = onClick, onLongClick = { openMenu(SongMenuRequest(song, playlistId)) })
            .padding(start = 20.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trackNumber != null) {
            Text(
                trackNumber.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = if (active) Aurora.Cyan else Aurora.Muted,
                modifier = Modifier.widthIn(min = 30.dp),
            )
        }
        if (showArt) Artwork(song.thumb, 54.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (active) Aurora.Cyan else Aurora.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloaded) {
                    Icon(
                        AppIcons.DownloadDone, "Downloaded",
                        tint = Aurora.Cyan,
                        modifier = Modifier
                            .size(15.dp)
                            .padding(end = 3.dp),
                    )
                }
                val duration = formatDuration(song.durationSec)
                Text(
                    if (duration.isEmpty()) song.artist else "${song.artist} • $duration",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Aurora.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { openMenu(SongMenuRequest(song, playlistId)) }) {
            Icon(Icons.Default.MoreVert, "More", tint = Aurora.Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(request: SongMenuRequest, onDismiss: () -> Unit) {
    val song = request.song
    val library = TuneHubApp.library
    val downloads = TuneHubApp.downloads
    val likedIds by library.likedIds.collectAsStateWithLifecycle()
    val downloadedIds by library.downloadedIds.collectAsStateWithLifecycle()
    val progress by downloads.progress.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val context = LocalContext.current
    var pickingPlaylist by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmDialog(confirm) { confirm = null }

    val liked = song.id in likedIds
    val downloaded = song.id in downloadedIds
    val downloading = progress.containsKey(song.id)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Aurora.Sheet) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.thumb, 56.dp)
            Column(Modifier.padding(start = 14.dp)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = Aurora.Muted)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Aurora.Stroke)

        if (pickingPlaylist) {
            PlaylistPicker(song, onDone = onDismiss)
            return@ModalBottomSheet
        }

        SheetAction(
            if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            if (liked) "Remove from Liked Songs" else "Add to Liked Songs",
            tint = if (liked) Aurora.Pink else null,
        ) { library.toggleLike(song); onDismiss() }
        SheetAction(AppIcons.PlaylistAdd, "Add to playlist") { pickingPlaylist = true }
        SheetAction(AppIcons.SkipNext, "Play next") { PlayerConnection.playNext(song); onDismiss() }
        SheetAction(AppIcons.QueueMusic, "Add to queue") { PlayerConnection.enqueue(song); onDismiss() }
        SheetAction(AppIcons.Radio, "Start song radio") { PlayerConnection.startRadio(song); onDismiss() }
        when {
            downloaded -> SheetAction(Icons.Default.Delete, "Remove download") {
                confirm = ConfirmRequest(
                    "Remove download?",
                    "\"${song.title}\" will no longer play offline. You can download it again any time.",
                    "Remove",
                ) { downloads.remove(song.id); onDismiss() }
            }
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
                confirm = ConfirmRequest(
                    "Remove from playlist?",
                    "\"${song.title}\" will be removed from this playlist.",
                    "Remove",
                ) {
                    library.removeFromPlaylist(request.playlistId, song.id)
                    onDismiss()
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ColumnScope.PlaylistPicker(song: Song, onDone: () -> Unit) {
    val library = TuneHubApp.library
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
                PlaylistCover(p.songs, 46.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (already) "Already added" else "${p.songs.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = Aurora.Muted,
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
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .glass(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint ?: Aurora.Lilac, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Aurora.Text else Aurora.Muted,
            modifier = Modifier.padding(start = 16.dp),
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
        Artwork(album.thumb, size, corner = 26.dp, placeholder = AppIcons.Album)
        Text(
            album.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (album.subtitle.isNotBlank()) {
            Text(
                album.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Aurora.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ArtistCircle(artist: ArtistRef, size: Dp = 128.dp, onClick: () -> Unit) {
    Column(
        Modifier
            .width(size)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Aurora.accent)
                .padding(3.dp),
        ) {
            Artwork(artist.thumb, size - 6.dp, corner = size / 2, placeholder = Icons.Default.Person)
        }
        Text(
            artist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("Artist", style = MaterialTheme.typography.bodySmall, color = Aurora.Muted)
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
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (art != null) art() else Artwork(thumb, 58.dp, corner = if (round) 29.dp else 14.dp, placeholder = placeholder)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Aurora.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun artistSubtitle(artist: ArtistRef): String = "Artist"

// ------------------------------------------------------------------ collection pages

/** A scrolling page with a glass back button that stays put while the content scrolls. */
@Composable
fun BackPage(onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            content()
            item { CreditFooter() }
        }
        GlassIconButton(
            Icons.AutoMirrored.Filled.ArrowBack,
            "Back",
            modifier = Modifier.padding(12.dp),
            fill = Aurora.Ink.copy(alpha = 0.55f),
            onClick = onBack,
        )
    }
}

/** Header for albums, playlists, Liked Songs and Downloads: glowing art, titles, actions, play button. */
@Composable
fun CollectionHeader(
    title: String,
    subtitle: String,
    meta: String,
    tint: Color,
    art: @Composable () -> Unit,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSubtitleClick: (() -> Unit)? = null,
    topActions: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        // the back arrow floats above the page (see BackPage); this row leaves room for it
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) { topActions() }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(300.dp)
                    .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.55f), Color.Transparent))),
            )
            art()
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Aurora.Lilac,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .then(if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier),
                )
            }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Aurora.Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                actions()
                Spacer(Modifier.weight(1f))
                GlassIconButton(AppIcons.Shuffle, "Shuffle play", size = 48.dp, onClick = { if (playEnabled) onShuffle() })
                PlayCircle(enabled = playEnabled, onClick = onPlay)
            }
        }
    }
}

@Composable
fun PlayCircle(enabled: Boolean = true, size: Dp = 60.dp, onClick: () -> Unit) =
    GradientCircleButton(Icons.Default.PlayArrow, "Play", size, enabled = enabled, onClick = onClick)

/** Download-all toggle for a list of songs; shows progress while downloading. */
@Composable
fun DownloadAllButton(songs: List<Song>) {
    val downloadedIds by TuneHubApp.library.downloadedIds.collectAsStateWithLifecycle()
    val progress by TuneHubApp.downloads.progress.collectAsStateWithLifecycle()
    val all = songs.isNotEmpty() && songs.all { it.id in downloadedIds }
    val busy = songs.any { progress.containsKey(it.id) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmDialog(confirm) { confirm = null }

    Box(
        Modifier
            .size(48.dp)
            .glass(RoundedCornerShape(17.dp))
            .clickable(enabled = songs.isNotEmpty() && !busy) {
                if (all) {
                    confirm = ConfirmRequest(
                        "Remove ${songs.size} downloads?",
                        "These songs will no longer play offline. You can download them again any time.",
                        "Remove all",
                    ) { TuneHubApp.downloads.removeAll(songs.map { it.id }) }
                } else {
                    TuneHubApp.downloads.downloadAll(songs)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            busy -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Aurora.Cyan)
            all -> Icon(AppIcons.DownloadDone, "Remove downloads", tint = Aurora.Cyan)
            else -> Icon(AppIcons.Download, "Download all", tint = Aurora.Text)
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
        containerColor = Aurora.Sheet,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("My playlist") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Aurora.Muted) } },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 10.dp, top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Aurora.Lilac) }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = Aurora.Muted,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 24.dp),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator(Modifier.size(34.dp), color = Aurora.Cyan, strokeWidth = 3.dp) }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Aurora.Muted)
        GlassChip("Retry", onClick = onRetry)
    }
}


/** A destructive action waiting for the user to confirm it. */
class ConfirmRequest(val title: String, val message: String, val confirmLabel: String, val action: () -> Unit)

/** Asks before anything is deleted; nothing happens unless the user taps the red button. */
@Composable
fun ConfirmDialog(request: ConfirmRequest?, onDismiss: () -> Unit) {
    request ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aurora.Sheet,
        title = { Text(request.title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(request.message, style = MaterialTheme.typography.bodyLarge, color = Aurora.Muted) },
        confirmButton = {
            TextButton(onClick = { request.action(); onDismiss() }) { Text(request.confirmLabel, color = Aurora.Pink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Aurora.Text) } },
    )
}

private const val REPO_URL = "https://github.com/Xenomorph27/tunehub"

/** "Made by Barnak · Open Source" at the bottom of every page; "Open Source" opens the repo. */
@Composable
fun CreditFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Made by Barnak  ·  ", style = MaterialTheme.typography.bodySmall, color = Aurora.Muted)
        Text(
            "Open Source",
            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
            color = Aurora.Lilac,
            modifier = Modifier.clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(REPO_URL))) }
            },
        )
    }
}
