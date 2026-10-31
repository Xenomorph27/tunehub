package com.tunehub.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.tunehub.app.data.Song
import java.util.concurrent.ConcurrentHashMap

/** Queue items carry this placeholder URI; PlaybackService swaps in the real stream URL at load time. */
const val SONG_SCHEME = "tunehub"
private const val EXTRA_DURATION = "dur"
private const val EXTRA_ARTIST_URL = "artist_url"

/** UI and service share a process, so keep full Song objects instead of relying on metadata round trips. */
private val knownSongs = ConcurrentHashMap<String, Song>()

fun songUri(id: String): Uri = Uri.parse("$SONG_SCHEME://song/$id")

fun Song.toMediaItem(): MediaItem {
    knownSongs[id] = this
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(songUri(id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(thumb?.let(Uri::parse))
                .setExtras(
                    Bundle().apply {
                        putLong(EXTRA_DURATION, durationSec)
                        putString(EXTRA_ARTIST_URL, artistUrl)
                    }
                )
                .build()
        )
        .build()
}

fun MediaItem.toSong(): Song = knownSongs[mediaId] ?: Song(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    durationSec = mediaMetadata.extras?.getLong(EXTRA_DURATION, -1) ?: -1,
    thumb = mediaMetadata.artworkUri?.toString(),
    artistUrl = mediaMetadata.extras?.getString(EXTRA_ARTIST_URL),
)
