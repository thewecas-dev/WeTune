package com.wetune.sources

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore.Audio.Media
import com.wetune.App
import java.io.IOException

/** Songs on this device, from MediaStore. */
object Local : Source {
    override val label = "Device"

    fun all(): List<Track> {
        val ctx = App.ctx
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ctx.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            throw IOException("Allow music access in Android settings to see songs on this device")
        }
        val uri = Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(Media._ID, Media.TITLE, Media.ARTIST, Media.ALBUM, Media.ALBUM_ID, Media.DURATION)
        return ctx.contentResolver.query(uri, cols, "${Media.IS_MUSIC} != 0", null, "${Media.TITLE} COLLATE NOCASE")?.use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Track(
                        source = "local",
                        ref = ContentUris.withAppendedId(uri, c.getLong(0)).toString(),
                        title = c.getString(1) ?: "Unknown",
                        artist = c.getString(2)?.takeIf { it != "<unknown>" }.orEmpty(),
                        album = c.getString(3).orEmpty(),
                        art = "content://media/external/audio/albumart/${c.getLong(4)}",
                        durationMs = c.getLong(5),
                    )
                )
            }
        } ?: emptyList()
    }

    override fun search(query: String) =
        all().filter { t -> listOf(t.title, t.artist, t.album).any { it.contains(query, ignoreCase = true) } }

    override fun streamUrl(ref: String, high: Boolean) = ref
}
