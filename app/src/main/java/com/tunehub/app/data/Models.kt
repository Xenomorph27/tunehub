package com.tunehub.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long = -1,
    val thumb: String? = null,
    val artistUrl: String? = null,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList(),
    val updatedAt: Long = 0,
    /** When it was created (0 for playlists saved before this field existed). */
    val addedAt: Long = 0,
)

/** A YouTube Music artist (a YouTube channel). */
@Serializable
data class ArtistRef(
    val url: String,
    val name: String,
    val thumb: String? = null,
    val subscribers: Long = -1,
    /** When the user followed it (0 for older entries). */
    val addedAt: Long = 0,
)

/** A YouTube Music album or a public playlist. */
@Serializable
data class AlbumRef(
    val url: String,
    val title: String,
    val subtitle: String = "",
    val thumb: String? = null,
    val isAlbum: Boolean = true,
    /** When the user saved it (0 for older entries). */
    val addedAt: Long = 0,
)

/** One row of search / browse results. */
sealed interface BrowseItem {
    data class SongItem(val song: Song) : BrowseItem
    data class ArtistItem(val artist: ArtistRef) : BrowseItem
    data class AlbumItem(val album: AlbumRef) : BrowseItem
}

fun formatDuration(seconds: Long): String {
    if (seconds < 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatCount(n: Long): String = when {
    n < 0 -> ""
    n >= 1_000_000_000 -> "%.1fB".format(n / 1e9).replace(".0B", "B")
    n >= 1_000_000 -> "%.1fM".format(n / 1e6).replace(".0M", "M")
    n >= 1_000 -> "%.1fK".format(n / 1e3).replace(".0K", "K")
    else -> n.toString()
}

fun totalDuration(songs: List<Song>): String {
    val total = songs.sumOf { it.durationSec.coerceAtLeast(0) }
    if (total <= 0) return ""
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "$h hr $m min" else "$m min"
}

/**
 * The same artwork at about [px] pixels: list rows don't need the 544 px covers,
 * and smaller images download and decode much faster.
 */
fun thumbSized(url: String?, px: Int): String? {
    if (url == null) return null
    val size = px.coerceIn(60, 544)
    return when {
        Regex("""=w\d+-h\d+[^/]*$""").containsMatchIn(url) ->
            url.replace(Regex("""=w\d+-h\d+[^/]*$"""), "=w$size-h$size-l90-rj")
        Regex("""=s\d+[^/]*$""").containsMatchIn(url) -> url.replace(Regex("""=s\d+[^/]*$"""), "=s$size")
        size <= 200 && url.contains("i.ytimg.com") && url.contains("/hqdefault") ->
            url.replace("/hqdefault", "/mqdefault")
        else -> url
    }
}

private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

/** YouTube video ids are exactly 11 characters from this alphabet; anything else is rejected. */
fun isValidVideoId(id: String?): Boolean = id != null && VIDEO_ID.matches(id)

/**
 * A small thread-safe map that drops the least recently used entries beyond [maxSize], for
 * session caches that would otherwise grow with every song touched.
 */
class BoundedCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > maxSize
    }

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    @Synchronized
    fun containsKey(key: K): Boolean = map.containsKey(key)

    @Synchronized
    fun getOrPut(key: K, create: () -> V): V = map[key] ?: create().also { map[key] = it }

    @Synchronized
    fun clear() = map.clear()
}
