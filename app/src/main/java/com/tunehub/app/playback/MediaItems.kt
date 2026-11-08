package com.tunehub.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.tunehub.app.data.BoundedCache
import com.tunehub.app.data.Song

/** Queue items carry this placeholder URI; PlaybackService swaps in the real stream URL at load time. */
const val SONG_SCHEME = "tunehub"
private const val EXTRA_DURATION = "dur"
private const val EXTRA_ARTIST_URL = "artist_url"
private const val EXTRA_RADIO = "radio"
private const val EXTRA_PLAYLIST_ID = "playlist_id"
private const val EXTRA_PLAYLIST_START = "playlist_start"

data class PlaylistStart(val playlistId: String, val token: String)

/** UI and service share a process, so keep full Song objects instead of relying on metadata round trips. */
private val knownSongs = BoundedCache<String, Song>(2000)

fun songUri(id: String): Uri = Uri.Builder().scheme(SONG_SCHEME).authority("song").appendPath(id).build()

/**
 * [radio] marks the seed of a "song radio": the service keeps such a queue going with related
 * songs even when autoplay is off.
 */
fun Song.toMediaItem(radio: Boolean = false, playlistStart: PlaylistStart? = null): MediaItem {
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
                        putBoolean(EXTRA_RADIO, radio)
                        if (playlistStart != null) {
                            putString(EXTRA_PLAYLIST_ID, playlistStart.playlistId)
                            putString(EXTRA_PLAYLIST_START, playlistStart.token)
                        }
                    }
                )
                .build()
        )
        .build()
}

fun MediaItem.isRadio(): Boolean = mediaMetadata.extras?.getBoolean(EXTRA_RADIO, false) == true

internal fun MediaItem.playlistStart(): PlaylistStart? {
    val extras = mediaMetadata.extras ?: return null
    val id = extras.getString(EXTRA_PLAYLIST_ID) ?: return null
    val token = extras.getString(EXTRA_PLAYLIST_START) ?: return null
    return PlaylistStart(id, token)
}

fun MediaItem.toSong(): Song = knownSongs[mediaId] ?: Song(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    durationSec = mediaMetadata.extras?.getLong(EXTRA_DURATION, -1) ?: -1,
    thumb = mediaMetadata.artworkUri?.toString(),
    artistUrl = mediaMetadata.extras?.getString(EXTRA_ARTIST_URL),
)
