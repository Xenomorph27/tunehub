package com.tunehub.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

internal const val UPSTREAM_RELEASES_URL = "https://github.com/Xenomorph27/tunehub/releases"

internal object ReleaseCheck {
    private const val latest = "https://api.github.com/repos/Xenomorph27/tunehub/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }
    private val buildTag = Regex("build-([0-9]+)")
    private val legacyTitle = Regex("TuneHub build ([0-9]+)")

    @Serializable
    private data class Release(val tag_name: String, val name: String? = null)

    fun hasNewerBuild(http: OkHttpClient, upstreamBuild: Int): Boolean {
        val request = Request.Builder().url(latest).header("Accept", "application/vnd.github+json").build()
        val call = http.newCall(request)
        call.timeout().timeout(8, TimeUnit.SECONDS)
        return call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub returned ${response.code}")
            isNewer(response.body?.string() ?: throw IOException("Missing release data"), upstreamBuild)
        }
    }

    internal fun isNewer(body: String, upstreamBuild: Int): Boolean {
        val release = json.decodeFromString<Release>(body)
        val match = if (release.tag_name == "latest") {
            legacyTitle.matchEntire(release.name.orEmpty())
        } else {
            buildTag.matchEntire(release.tag_name)
        }
        val build = match?.groupValues?.get(1)?.toIntOrNull()
            ?: throw IOException("Unrecognized TuneHub release")
        return build > upstreamBuild
    }
}
