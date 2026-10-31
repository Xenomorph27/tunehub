package com.spitify.app

import android.app.Application
import com.spitify.app.data.Library
import com.spitify.app.data.YtDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

class SpitifyApp : Application() {
    lateinit var library: Library
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
        val locale = Locale.getDefault()
        // the content country decides which songs YouTube Music lets you stream
        NewPipe.init(
            YtDownloader(client),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifEmpty { "US" }),
        )
        library = Library(this)
    }

    companion object {
        lateinit var instance: SpitifyApp
            private set

        val library: Library get() = instance.library
    }
}
