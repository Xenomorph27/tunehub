package com.spitify.app

import android.app.Application
import com.spitify.app.data.Downloads
import com.spitify.app.data.Library
import com.spitify.app.data.StreamResolver
import com.spitify.app.data.YtDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

class SpitifyApp : Application() {
    lateinit var library: Library
        private set
    lateinit var downloads: Downloads
        private set
    lateinit var http: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
        val locale = Locale.getDefault()
        // the content country decides which songs YouTube Music lets you stream
        NewPipe.init(
            YtDownloader(http),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifEmpty { "US" }),
        )
        library = Library(this)
        downloads = Downloads(this, http, library)

        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            library.data.map { it.dataSaver }.distinctUntilChanged().collect { StreamResolver.dataSaver = it }
        }
    }

    companion object {
        lateinit var instance: SpitifyApp
            private set

        val library: Library get() = instance.library
        val downloads: Downloads get() = instance.downloads
        val http: OkHttpClient get() = instance.http
    }
}
