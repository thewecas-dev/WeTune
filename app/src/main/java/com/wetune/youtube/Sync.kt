package com.wetune.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wetune.httpGet
import com.wetune.sources.Card
import com.wetune.sources.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * Two-way sync with the signed-in YouTube Music account.
 * YouTube is the source of truth: library screens read live, actions write straight back.
 */
object Sync {
    data class Account(val name: String, val photo: String?)

    var account by mutableStateOf<Account?>(null)
    var expired by mutableStateOf(false)
    var liked by mutableStateOf(emptySet<String>()) // videoIds
    private var likedAt = 0L

    fun fetchAccount(): Account? {
        val h = findFirst(InnerTube.post("account/account_menu"), "activeAccountHeaderRenderer") as? JSONObject ?: return null
        return Account(h.optJSONObject("accountName").text(), thumbnail(h.optJSONObject("accountPhoto")))
    }

    /** Re-reads the account and liked songs. Blocking. Skips if refreshed in the last 5 minutes unless [force]. */
    fun refresh(force: Boolean = false) {
        if (!InnerTube.signedIn) {
            account = null; liked = emptySet(); expired = false; return
        }
        if (!force && System.currentTimeMillis() - likedAt < 5 * 60_000) return
        val a = fetchAccount()
        account = a
        expired = a == null
        if (a != null) {
            liked = likedSongs().map { it.ref }.toSet()
            likedAt = System.currentTimeMillis()
        }
    }

    fun likedSongs(): List<Track> = YouTube.page(Card("VLLM", "Liked music", "", null)).tracks

    fun setLiked(videoId: String, on: Boolean) {
        InnerTube.post(if (on) "like/like" else "like/removelike", JSONObject().put("target", JSONObject().put("videoId", videoId)))
        liked = if (on) liked + videoId else liked - videoId
    }

    private fun library(browseId: String) = items(InnerTube.post("browse", JSONObject().put("browseId", browseId)))
    fun playlists(): List<Card> = library("FEmusic_liked_playlists").cards
    fun albums(): List<Card> = library("FEmusic_liked_albums").cards
    fun artists(): List<Card> = library("FEmusic_library_corpus_track_artists").cards
    fun history(): List<Track> = library("FEmusic_history").tracks

    fun addToPlaylist(playlistId: String, videoId: String) =
        edit(playlistId, JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId))

    fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) =
        edit(playlistId, JSONObject().put("action", "ACTION_REMOVE_VIDEO").put("removedVideoId", videoId).put("setVideoId", setVideoId))

    private fun edit(playlistId: String, action: JSONObject) {
        InnerTube.post("browse/edit_playlist", JSONObject().put("playlistId", playlistId.removePrefix("VL")).put("actions", JSONArray().put(action)))
    }

    fun createPlaylist(title: String): String =
        InnerTube.post("playlist/create", JSONObject().put("title", title).put("privacyStatus", "PRIVATE")).optString("playlistId")

    fun deletePlaylist(playlistId: String) {
        InnerTube.post("playlist/delete", JSONObject().put("playlistId", playlistId.removePrefix("VL")))
    }

    private const val CPN_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    /** Tells YouTube the song was played, so it shows in history and shapes recommendations. */
    fun registerPlayback(trackingUrl: String) {
        val cpn = (1..16).map { CPN_CHARS.random() }.joinToString("")
        httpGet(trackingUrl.replace("https://s.youtube.com", InnerTube.ORIGIN) + "&ver=2&c=WEB_REMIX&cpn=$cpn", InnerTube.headers())
    }
}
