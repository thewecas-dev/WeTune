package com.wetune

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wetune.sources.Track
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Sync
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-device library: playlists and likes for songs that don't live on YouTube, plus downloads.
 * shortcut: one JSON file; move to Room if it grows past a few thousand songs or needs queries.
 */
object Library {
    data class Playlist(val name: String, val tracks: List<Track>)

    var playlists by mutableStateOf(listOf<Playlist>()); private set
    var liked by mutableStateOf(listOf<Track>()); private set
    var downloads by mutableStateOf(listOf<Track>()); private set

    private val file get() = File(App.ctx.filesDir, "library.json")

    fun load() {
        runCatching { if (file.exists()) fromJson(JSONObject(file.readText())) }
    }

    private fun save() = file.writeText(toJson().toString())

    private fun List<Track>.json() = JSONArray(map { it.toJson() })
    private fun JSONArray?.tracks() = objects().mapNotNull { runCatching { Track.fromJson(it) }.getOrNull() }

    fun toJson(): JSONObject = JSONObject()
        .put("playlists", JSONArray(playlists.map { JSONObject().put("name", it.name).put("tracks", it.tracks.json()) }))
        .put("liked", liked.json())
        .put("downloads", downloads.json())

    private fun fromJson(o: JSONObject) {
        playlists = o.optJSONArray("playlists").objects().map { Playlist(it.optString("name"), it.optJSONArray("tracks").tracks()) }
        liked = o.optJSONArray("liked").tracks()
        downloads = o.optJSONArray("downloads").tracks().filter { Downloads.file(it).exists() }
    }

    fun export(uri: Uri) {
        App.ctx.contentResolver.openOutputStream(uri)?.use { it.write(toJson().toString(2).toByteArray()) }
    }

    fun import(uri: Uri) {
        val text = App.ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return
        fromJson(JSONObject(text))
        save()
    }

    // Likes: YouTube songs sync to the account when signed in; everything else is stored here.
    fun isLiked(t: Track): Boolean =
        if (t.source == "yt" && InnerTube.signedIn) t.ref in Sync.liked else liked.any { it.id == t.id }

    /** Blocking when it talks to YouTube; call via [io]. */
    fun toggleLike(t: Track) {
        val on = !isLiked(t)
        if (t.source == "yt" && InnerTube.signedIn) Sync.setLiked(t.ref, on)
        else {
            liked = if (on) listOf(t) + liked else liked.filter { it.id != t.id }
            save()
        }
    }

    fun createPlaylist(name: String) {
        if (playlists.none { it.name == name }) playlists = playlists + Playlist(name, emptyList())
        save()
    }

    fun deletePlaylist(name: String) {
        playlists = playlists.filter { it.name != name }
        save()
    }

    fun addTo(name: String, t: Track) = update(name) { it + t }
    fun removeFrom(name: String, t: Track) = update(name) { list -> list.filter { it.id != t.id } }

    private fun update(name: String, change: (List<Track>) -> List<Track>) {
        playlists = playlists.map { if (it.name == name) it.copy(tracks = change(it.tracks)) else it }
        save()
    }

    fun addDownload(t: Track) {
        downloads = listOf(t) + downloads.filter { it.id != t.id }
        save()
    }

    fun removeDownload(t: Track) {
        downloads = downloads.filter { it.id != t.id }
        save()
    }
}
