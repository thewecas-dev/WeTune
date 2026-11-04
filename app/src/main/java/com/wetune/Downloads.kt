package com.wetune

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wetune.sources.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Saves songs into app storage so they play offline. */
object Downloads {
    var active by mutableStateOf(setOf<String>()); private set

    fun file(t: Track) = File(App.ctx.filesDir, "downloads/${t.id.hash("SHA-1")}")

    fun download(t: Track) = io {
        if (t.source == "local" || file(t).exists() || t.id in active) return@io
        withContext(Dispatchers.Main) { active = active + t.id }
        try {
            val url = Resolver.streamUrl(t.source, t.ref, high = true)
            val f = file(t)
            f.parentFile!!.mkdirs()
            val tmp = File(f.path + ".part")
            http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Download failed (${r.code})")
                tmp.outputStream().use { r.body!!.byteStream().copyTo(it) }
            }
            if (!tmp.renameTo(f)) throw IOException("Couldn't save the download")
            withContext(Dispatchers.Main) { Library.addDownload(t) }
            toast("Downloaded ${t.title}")
        } finally {
            withContext(Dispatchers.Main) { active = active - t.id }
        }
    }

    fun delete(t: Track) {
        file(t).delete()
        Library.removeDownload(t)
    }
}
