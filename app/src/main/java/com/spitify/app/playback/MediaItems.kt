package com.spitify.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.spitify.app.data.Song

/** Queue items carry this placeholder URI; PlaybackService swaps in the real stream URL at load time. */
const val SONG_SCHEME = "spitify"
private const val EXTRA_DURATION = "dur"

fun songUri(id: String): Uri = Uri.parse("$SONG_SCHEME://song/$id")

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(songUri(id))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(thumb?.let(Uri::parse))
            .setExtras(Bundle().apply { putLong(EXTRA_DURATION, durationSec) })
            .build()
    )
    .build()

fun MediaItem.toSong(): Song = Song(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    durationSec = mediaMetadata.extras?.getLong(EXTRA_DURATION, -1) ?: -1,
    thumb = mediaMetadata.artworkUri?.toString(),
)
