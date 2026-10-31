package com.spitify.app.data

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Thin wrapper over NewPipeExtractor's YouTube service. Every call here blocks: run on Dispatchers.IO. */
object YouTube {
    const val FILTER_SONGS = YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
    const val FILTER_VIDEOS = YoutubeSearchQueryHandlerFactory.VIDEOS

    private val service get() = ServiceList.YouTube

    /** A paged search. Keep the instance around to load further pages. */
    class Search(query: String, filter: String) {
        private val extractor: SearchExtractor =
            service.getSearchExtractor(query, listOf(filter), "")
        private var nextPage: Page? = null
        private var started = false

        var hasMore = true
            private set

        fun loadNext(): List<Song> {
            val page = if (!started) {
                extractor.fetchPage()
                started = true
                extractor.initialPage
            } else {
                extractor.getPage(nextPage ?: return emptyList())
            }
            nextPage = page.nextPage
            hasMore = page.hasNextPage()
            return page.items.mapNotNull { (it as? StreamInfoItem)?.toSong() }
        }
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
    )

    /** Pulls the video id out of any youtube.com / youtu.be / music.youtube.com link. */
    fun videoIdFromText(text: String): String? =
        Regex("""(?:v=|youtu\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})""")
            .find(text)?.groupValues?.get(1)

    fun StreamInfoItem.toSong(): Song? {
        if (streamType == StreamType.LIVE_STREAM) return null
        val id = runCatching { service.streamLHFactory.getId(url) }.getOrNull() ?: return null
        return Song(
            id = id,
            title = name,
            artist = cleanArtist(uploaderName),
            durationSec = duration,
            thumb = bestThumb(thumbnails, id),
        )
    }

    private fun cleanArtist(name: String?): String = name.orEmpty().removeSuffix(" - Topic")

    private fun bestThumb(images: List<Image>, id: String): String {
        val url = images.maxByOrNull { it.height }?.url
            ?: return "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        // YouTube Music art comes as tiny "=w60-h60" googleusercontent images; ask for a big one
        return url.replace(Regex("""=w\d+-h\d+[^/]*$"""), "=w544-h544-l90-rj")
    }
}
