package com.spitify.app

import com.spitify.app.data.StreamResolver
import com.spitify.app.data.YouTube
import com.spitify.app.data.YtDownloader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Live smoke test against YouTube: search, resolve a stream, and fetch its first bytes
 * the same way the player does. YouTube sometimes blocks datacenter IPs, so CI treats
 * a failure here as a warning.
 */
class ExtractionTest {

    @Test
    fun searchResolveAndFetch() {
        val songs = YouTube.Search("daft punk get lucky", YouTube.FILTER_SONGS).loadNext()
        println("search returned ${songs.size} songs; first: ${songs.firstOrNull()}")
        assertTrue("search returned nothing", songs.isNotEmpty())

        val stream = StreamResolver.resolve(songs.first().id)
        println("stream host: ${stream.url.substringAfter("://").substringBefore("/")}")
        println("headers: ${stream.headers.keys}, related: ${stream.related.size}")
        assertTrue(stream.url.startsWith("https://"))

        val request = Request.Builder()
            .url(stream.url + "&rn=1")
            .post(byteArrayOf(0x78, 0).toRequestBody())
            .header("Range", "bytes=0-65535")
            .apply { stream.headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes()?.size ?: 0
            println("stream fetch: HTTP ${response.code}, $bytes bytes")
            assertTrue("stream fetch failed: HTTP ${response.code}", response.code in 200..299 && bytes > 0)
        }
    }

    companion object {
        private val client = OkHttpClient()

        @JvmStatic
        @BeforeClass
        fun setUp() {
            NewPipe.init(YtDownloader(client), Localization("en", "US"), ContentCountry("US"))
        }
    }
}
