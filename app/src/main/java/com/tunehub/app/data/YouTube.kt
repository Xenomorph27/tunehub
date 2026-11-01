package com.tunehub.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Thin wrapper over NewPipeExtractor's YouTube service. Non-suspend calls block: run them on Dispatchers.IO. */
object YouTube {
    const val FILTER_SONGS = YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
    const val FILTER_VIDEOS = YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS
    const val FILTER_ARTISTS = YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS
    const val FILTER_ALBUMS = YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
    const val FILTER_PLAYLISTS = YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS

    private val service get() = ServiceList.YouTube

    /** A paged search. Keep the instance around to load further pages. */
    class Search(query: String, private val filter: String) {
        private val extractor: SearchExtractor =
            service.getSearchExtractor(query, listOf(filter), "")
        private var nextPage: Page? = null
        private var started = false

        var hasMore = true
            private set

        fun loadNext(): List<BrowseItem> {
            val page = if (!started) {
                extractor.fetchPage()
                started = true
                extractor.initialPage
            } else {
                extractor.getPage(nextPage ?: return emptyList())
            }
            nextPage = page.nextPage
            hasMore = page.hasNextPage()
            return page.items.mapNotNull { it.toBrowseItem(isAlbum = filter == FILTER_ALBUMS) }
        }
    }

    /** Everything at once for the "All" tab. Failures in one section don't sink the others. */
    class TopResults(
        val songs: List<Song>,
        val artists: List<ArtistRef>,
        val albums: List<AlbumRef>,
        val playlists: List<AlbumRef>,
    )

    suspend fun searchAll(query: String): TopResults = coroutineScope {
        fun section(filter: String) = async(Dispatchers.IO) {
            runCatching { Search(query, filter).loadNext() }.getOrDefault(emptyList())
        }
        val songs = section(FILTER_SONGS)
        val artists = section(FILTER_ARTISTS)
        val albums = section(FILTER_ALBUMS)
        val playlists = section(FILTER_PLAYLISTS)
        TopResults(
            songs = songs.await().filterIsInstance<BrowseItem.SongItem>().map { it.song },
            artists = artists.await().filterIsInstance<BrowseItem.ArtistItem>().map { it.artist },
            albums = albums.await().filterIsInstance<BrowseItem.AlbumItem>().map { it.album },
            playlists = playlists.await().filterIsInstance<BrowseItem.AlbumItem>().map { it.album },
        )
    }

    fun suggestions(query: String): List<String> =
        service.suggestionExtractor.suggestionList(query)

    fun streamInfo(videoId: String): StreamInfo =
        StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId")

    fun songFrom(info: StreamInfo): Song = Song(
        id = info.id,
        title = info.name,
        artist = cleanArtist(info.uploaderName),
        durationSec = info.duration,
        thumb = bestThumb(info.thumbnails, info.id),
        artistUrl = info.uploaderUrl,
    )

    // ---------------------------------------------------------------- artists

    class ArtistPage(
        val artist: ArtistRef,
        val banner: String?,
        val popular: List<Song>,
        val albums: List<AlbumRef>,
        val videos: List<Song>,
    )

    suspend fun artistPage(url: String, nameHint: String): ArtistPage = coroutineScope {
        val info = async(Dispatchers.IO) { runCatching { ChannelInfo.getInfo(service, url) }.getOrNull() }
        // search by the name we already have, in parallel with the channel lookup
        val popular = async(Dispatchers.IO) {
            runCatching {
                val name = nameHint
                val found = Search(name, FILTER_SONGS).loadNext()
                    .filterIsInstance<BrowseItem.SongItem>().map { it.song }
                // keep songs actually by this artist; YouTube mixes in covers and features
                val own = found.filter { it.artistUrl == url || it.artist.contains(name, ignoreCase = true) }
                own.ifEmpty { found.take(5) }.take(10)
            }.getOrDefault(emptyList())
        }
        val channel = info.await()
        val name = channel?.name?.let(::cleanArtist) ?: nameHint
        val albums = async(Dispatchers.IO) {
            runCatching {
                val tab = channel?.tabs?.firstOrNull { it.contentFilters.contains(ChannelTabs.ALBUMS) }
                    ?: return@runCatching emptyList<AlbumRef>()
                ChannelTabInfo.getInfo(service, tab).relatedItems
                    .mapNotNull { (it as? PlaylistInfoItem)?.toAlbum(isAlbum = true) }
            }.getOrDefault(emptyList()).ifEmpty {
                // channel has no releases tab: fall back to an album search for the name
                runCatching {
                    Search(name, FILTER_ALBUMS).loadNext()
                        .filterIsInstance<BrowseItem.AlbumItem>().map { it.album }
                        .filter { it.subtitle.contains(name, ignoreCase = true) }
                }.getOrDefault(emptyList())
            }
        }
        val videos = async(Dispatchers.IO) {
            runCatching {
                val tab = channel?.tabs?.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
                    ?: return@runCatching emptyList<Song>()
                ChannelTabInfo.getInfo(service, tab).relatedItems
                    .mapNotNull { (it as? StreamInfoItem)?.toSong() }.take(12)
            }.getOrDefault(emptyList())
        }

        ArtistPage(
            artist = ArtistRef(
                url = url,
                name = name,
                thumb = channel?.avatars?.let { biggest(it) },
                subscribers = channel?.subscriberCount ?: -1,
            ),
            banner = channel?.banners?.let { biggest(it) },
            popular = popular.await(),
            albums = albums.await(),
            videos = videos.await(),
        )
    }

    // ---------------------------------------------------------------- albums & playlists

    class AlbumPage(val album: AlbumRef, val songs: List<Song>)

    suspend fun albumPage(url: String, hint: AlbumRef?): AlbumPage = withContext(Dispatchers.IO) {
        val normalized = url.replace("music.youtube.com", "www.youtube.com")
        val info = PlaylistInfo.getInfo(service, normalized)
        val songs = info.relatedItems.mapNotNull { it.toSong() }.toMutableList()
        var next = info.nextPage
        var pages = 0
        while (Page.isValid(next) && pages < MAX_PLAYLIST_PAGES) {
            val more = PlaylistInfo.getMoreItems(service, normalized, next)
            songs += more.items.mapNotNull { it.toSong() }
            next = more.nextPage
            pages++
        }
        val album = AlbumRef(
            url = url,
            // YouTube names album playlists "Album – Title"
            title = hint?.title?.takeIf { it.isNotBlank() }
                ?: info.name.replace(Regex("""^(Album|EP|Single)\s+[–-]\s+"""), ""),
            subtitle = hint?.subtitle?.takeIf { it.isNotBlank() } ?: cleanArtist(info.uploaderName),
            thumb = hint?.thumb ?: biggest(info.thumbnails) ?: songs.firstOrNull()?.thumb,
            isAlbum = hint?.isAlbum ?: true,
        )
        AlbumPage(album, songs.distinctBy { it.id })
    }

    /** YouTube's music trending chart, for the home screen. */
    fun trendingMusic(): List<Song> {
        val extractor = service.kioskList.getExtractorById("trending_music", null)
        extractor.fetchPage()
        return extractor.initialPage.items.mapNotNull { (it as? StreamInfoItem)?.toSong() }
    }

    // ---------------------------------------------------------------- helpers

    /** Pulls the video id out of any youtube.com / youtu.be / music.youtube.com link. */
    fun videoIdFromText(text: String): String? =
        Regex("""https?://\S+""").findAll(text).firstNotNullOfOrNull { videoIdFromUrl(it.value) }

    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

    // only real YouTube links: an unrelated URL with "v=" in it is ignored
    private fun videoIdFromUrl(url: String): String? {
        val uri = runCatching { java.net.URI(url.trimEnd('.', ',', ')', '!')) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in youtubeHosts) return null
        val path = uri.path.orEmpty().trim('/').split('/')
        val id = when {
            host == "youtu.be" -> path.firstOrNull()
            path.firstOrNull() in setOf("shorts", "embed", "live") -> path.getOrNull(1)
            else -> uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
        }
        return id?.takeIf(::isValidVideoId)
    }

    fun InfoItem.toBrowseItem(isAlbum: Boolean): BrowseItem? = when (this) {
        is StreamInfoItem -> toSong()?.let { BrowseItem.SongItem(it) }
        is ChannelInfoItem -> BrowseItem.ArtistItem(
            // the search result's subscriber count is misparsed (10x off); the artist page has the real one
            ArtistRef(url = url, name = cleanArtist(name), thumb = biggest(thumbnails))
        )
        is PlaylistInfoItem -> BrowseItem.AlbumItem(toAlbum(isAlbum))
        else -> null
    }

    fun StreamInfoItem.toSong(): Song? {
        if (streamType == StreamType.LIVE_STREAM) return null
        val id = runCatching { service.streamLHFactory.getId(url) }.getOrNull() ?: return null
        return Song(
            id = id,
            title = name,
            artist = cleanArtist(uploaderName),
            durationSec = duration,
            thumb = bestThumb(thumbnails, id),
            artistUrl = uploaderUrl,
        )
    }

    private fun PlaylistInfoItem.toAlbum(isAlbum: Boolean) = AlbumRef(
        url = url,
        title = name,
        subtitle = cleanArtist(uploaderName),
        thumb = biggest(thumbnails),
        isAlbum = isAlbum,
    )

    private fun cleanArtist(name: String?): String = name.orEmpty().removeSuffix(" - Topic")

    private fun biggest(images: List<Image>): String? =
        images.maxByOrNull { it.height }?.url?.let(::upscale)

    private fun bestThumb(images: List<Image>, id: String): String =
        biggest(images) ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    // YouTube Music art comes as tiny "=w60-h60" googleusercontent images; ask for a big one
    private fun upscale(url: String): String =
        url.replace(Regex("""=w\d+-h\d+[^/]*$"""), "=w544-h544-l90-rj")
            .replace(Regex("""=s\d+[^/]*$"""), "=s544")

    private const val MAX_PLAYLIST_PAGES = 10
}
