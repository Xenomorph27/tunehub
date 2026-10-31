package com.spitify.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long = -1,
    val thumb: String? = null,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList(),
)

fun formatDuration(seconds: Long): String {
    if (seconds < 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
