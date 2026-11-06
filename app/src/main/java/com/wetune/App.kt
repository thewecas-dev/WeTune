package com.wetune

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.wetune.youtube.InnerTube
import com.wetune.youtube.NewPipeDownloader
import com.wetune.youtube.Sync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import coil3.request.crossfade
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = this
        // Covers fade in instead of popping.
        coil3.SingletonImageLoader.setSafe { coil3.ImageLoader.Builder(it).crossfade(200).build() }
        NewPipe.init(NewPipeDownloader)
        InnerTube.cookieProvider = { Settings.cookie }
        InnerTube.onAuthError = { Sync.expired = true }
        Library.load()
        io { Sync.refresh(force = true) }
    }

    companion object {
        lateinit var ctx: App

        @OptIn(UnstableApi::class)
        val cache: SimpleCache by lazy {
            SimpleCache(File(ctx.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(512L shl 20), StandaloneDatabaseProvider(ctx))
        }
    }
}

/** Lives as long as the process, so work started from a list row survives scrolling away. */
val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Runs [block] on IO; toasts [success] or the error message. */
fun io(success: String? = null, then: () -> Unit = {}, block: suspend () -> Unit) = appScope.launch {
    runCatching { withContext(Dispatchers.IO) { block() } }
        .onSuccess { success?.let(::toast); then() }
        .onFailure { toast(it.message ?: "Something went wrong") }
}
