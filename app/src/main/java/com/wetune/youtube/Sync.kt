package com.wetune.youtube

import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wetune.App
import com.wetune.httpGet
import com.wetune.objects
import com.wetune.sources.Card
import com.wetune.sources.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Two-way sync with the signed-in YouTube Music account, local-first:
 * - a mirror of the library (liked songs, saved playlists, albums, artists) that screens read instantly, offline too;
 * - every change made here applies to the mirror at once and joins a saved queue of pending changes;
 * - the queue goes to YouTube in order; network failures stay queued and retry with backoff and on reconnect;
 * - a pull (launch, resume, refresh, every 5 min in the foreground) replaces the mirror with YouTube's copy and then
 *   re-applies whatever is still pending, so changes made on YouTube Music show here and ours are never lost.
 * shortcut: the mirror is one JSON file; move to SQLite if libraries reach tens of thousands of songs.
 */
object Sync {
    data class Account(val name: String, val photo: String?)

    var account by mutableStateOf<Account?>(null); private set
    var expired by mutableStateOf(false)
    var liked by mutableStateOf(emptyList<Track>()); private set // newest first
    var playlists by mutableStateOf(emptyList<Card>()); private set
    var albums by mutableStateOf(emptyList<Card>()); private set
    var artists by mutableStateOf(emptyList<Card>()); private set
    var pending by mutableIntStateOf(0); private set // changes not yet on YouTube
    var pulling by mutableStateOf(false); private set
    var lastPull by mutableLongStateOf(0L); private set

    private val likedIds by derivedStateOf { liked.mapTo(HashSet()) { it.ref } }
    private val ops = mutableListOf<Op>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushLock = Mutex()
    private val pullLock = Mutex()
    private var retry: Job? = null
    private val file get() = File(App.ctx.filesDir, "sync.json")

    // ---- changes ---------------------------------------------------------------------------------------------

    /** One change waiting to reach YouTube. [apply] edits the mirror; [send] performs it (blocking). */
    private sealed class Op {
        abstract fun json(): JSONObject
        abstract fun apply()
        abstract fun send()

        data class Like(val t: Track, val on: Boolean) : Op() {
            override fun json() = JSONObject().put("k", "like").put("t", t.toJson()).put("on", on)
            override fun apply() { liked = if (on) listOf(t) + liked.filter { it.ref != t.ref } else liked.filter { it.ref != t.ref } }
            override fun send() { InnerTube.post(if (on) "like/like" else "like/removelike", target("videoId", t.ref)) }
        }

        /** Save or remove an album / playlist in the library. [saveId] is the playlist id YouTube's like button uses. */
        data class Save(val card: Card, val saveId: String, val on: Boolean) : Op() {
            override fun json() = JSONObject().put("k", "save").put("c", card.json()).put("s", saveId).put("on", on)
            override fun apply() {
                val album = card.browseId.startsWith("MPRE")
                fun List<Card>.edit() = if (on) listOf(card) + filter { it.browseId != card.browseId } else filter { it.browseId != card.browseId }
                if (album) albums = albums.edit() else playlists = playlists.edit()
            }
            override fun send() { InnerTube.post(if (on) "like/like" else "like/removelike", target("playlistId", saveId.removePrefix("VL"))) }
        }

        data class Add(var pid: String, val t: Track) : Op() {
            override fun json() = JSONObject().put("k", "add").put("p", pid).put("t", t.toJson())
            override fun apply() {}
            override fun send() = edit(pid, JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", t.ref))
        }

        data class Remove(var pid: String, val t: Track) : Op() {
            override fun json() = JSONObject().put("k", "remove").put("p", pid).put("t", t.toJson()).put("sv", t.setVideoId)
            override fun apply() {}
            override fun send() = edit(pid, JSONObject().put("action", "ACTION_REMOVE_VIDEO").put("removedVideoId", t.ref).put("setVideoId", t.setVideoId))
        }

        /** [id] starts as a local id ("local-…") and is swapped for YouTube's once created. */
        data class Create(val id: String, val title: String) : Op() {
            override fun json() = JSONObject().put("k", "create").put("id", id).put("title", title)
            override fun apply() { if (playlists.none { it.browseId == id }) playlists = listOf(Card(id, title, "Playlist", null)) + playlists }
            override fun send() {
                val real = "VL" + InnerTube.post("playlist/create", JSONObject().put("title", title).put("privacyStatus", "PRIVATE"))
                    .optString("playlistId").ifEmpty { throw IOException("YouTube didn't create the playlist") }
                remap(id, real)
            }
        }

        data class Delete(val pid: String) : Op() {
            override fun json() = JSONObject().put("k", "delete").put("p", pid)
            override fun apply() { playlists = playlists.filter { it.browseId != pid } }
            override fun send() { InnerTube.post("playlist/delete", JSONObject().put("playlistId", pid.removePrefix("VL"))) }
        }

        companion object {
            fun from(o: JSONObject): Op? = runCatching {
                when (o.getString("k")) {
                    "like" -> Like(Track.fromJson(o.getJSONObject("t")), o.getBoolean("on"))
                    "save" -> Save(card(o.getJSONObject("c")), o.getString("s"), o.getBoolean("on"))
                    "add" -> Add(o.getString("p"), Track.fromJson(o.getJSONObject("t")))
                    "remove" -> Remove(o.getString("p"), Track.fromJson(o.getJSONObject("t")).copy(setVideoId = o.optString("sv").ifEmpty { null }))
                    "create" -> Create(o.getString("id"), o.getString("title"))
                    "delete" -> Delete(o.getString("p"))
                    else -> null
                }
            }.getOrNull()
        }
    }

    private fun target(key: String, id: String) = JSONObject().put("target", JSONObject().put(key, id))

    private fun edit(pid: String, action: JSONObject) {
        if (pid.startsWith("local-")) throw IOException("Playlist not created yet") // its Create is still ahead in the queue
        InnerTube.post("browse/edit_playlist", JSONObject().put("playlistId", pid.removePrefix("VL")).put("actions", JSONArray().put(action)))
    }

    /** A playlist made offline got its YouTube id: point everything still queued (and the mirror) at it. */
    private fun remap(local: String, real: String) = synchronized(ops) {
        ops.forEach { if (it is Op.Add && it.pid == local) it.pid = real; if (it is Op.Remove && it.pid == local) it.pid = real }
        playlists = playlists.map { if (it.browseId == local) it.copy(browseId = real) else it }
    }

    private fun queue(op: Op) {
        synchronized(ops) { ops += op; pending = ops.size }
        op.apply()
        save()
        push()
    }

    // ---- public actions (instant; YouTube catches up) --------------------------------------------------------

    fun isLiked(t: Track) = t.ref in likedIds
    fun setLiked(t: Track, on: Boolean) = queue(Op.Like(t.copy(setVideoId = null), on))

    fun isSaved(card: Card) = (albums + playlists).any { it.browseId == card.browseId }
    fun setSaved(card: Card, saveId: String, on: Boolean) = queue(Op.Save(card, saveId, on))

    fun addToPlaylist(pid: String, t: Track) = queue(Op.Add(pid, t))
    fun removeFromPlaylist(pid: String, t: Track) = queue(Op.Remove(pid, t))
    fun createPlaylist(title: String): String = "local-${System.currentTimeMillis()}".also { queue(Op.Create(it, title)) }
    fun deletePlaylist(pid: String) {
        if (!pid.startsWith("local-")) return queue(Op.Delete(pid))
        // never reached YouTube: drop its create and everything aimed at it instead of sending anything
        synchronized(ops) { ops.removeAll { (it is Op.Create && it.id == pid) || (it is Op.Add && it.pid == pid) || (it is Op.Remove && it.pid == pid) }; pending = ops.size }
        Op.Delete(pid).apply()
        save()
    }

    /** A playlist's songs with this device's unsent adds and removes laid over YouTube's copy. */
    fun overlay(pid: String, tracks: List<Track>): List<Track> {
        var out = tracks
        synchronized(ops) {
            ops.forEach { op ->
                when {
                    op is Op.Add && op.pid == pid && out.none { it.ref == op.t.ref } -> out = out + op.t
                    op is Op.Remove && op.pid == pid -> out = out.filter { it.ref != op.t.ref || (op.t.setVideoId != null && it.setVideoId != op.t.setVideoId) }
                }
            }
        }
        return out
    }

    // ---- push / pull ------------------------------------------------------------------------------------------

    /** Sends queued changes in order. Network trouble keeps them queued and retries later; YouTube refusing one drops it. */
    fun push(): Job = scope.launch {
        if (!InnerTube.signedIn) return@launch
        pushLock.withLock {
            var failures = 0
            while (true) {
                val op = synchronized(ops) { ops.firstOrNull() } ?: break
                val error = runCatching { op.send() }.exceptionOrNull()
                if (error == null || (error is InnerTube.HttpError && error.code in 400..499 && error.code !in setOf(401, 408, 429))) {
                    synchronized(ops) { ops.remove(op); pending = ops.size }
                    save()
                    if (error != null) pullSoon() // YouTube said no: its copy wins
                } else {
                    failures++
                    scheduleRetry(failures)
                    break
                }
            }
        }
    }

    private fun scheduleRetry(failures: Int) {
        retry?.cancel()
        retry = scope.launch { delay((15_000L shl (failures - 1).coerceAtMost(4)).coerceAtMost(5 * 60_000L)); push() }
    }

    private fun pullSoon() { scope.launch { delay(1500); pull(force = true) } }

    /**
     * Replaces the mirror with YouTube's library, then re-applies unsent changes. Skips when pulled in the last
     * 5 minutes unless [force]. Safe to call from anywhere; never throws.
     */
    fun pull(force: Boolean = false): Job = scope.launch {
        if (!InnerTube.signedIn) { clear(); return@launch }
        if (!force && System.currentTimeMillis() - lastPull < 5 * 60_000) return@launch
        if (!pullLock.tryLock()) return@launch
        pulling = true
        try {
            push().join()
            val a = runCatching { fetchAccount() }.getOrNull()
            if (a == null) { expired = true; return@launch }
            account = a; expired = false
            val l = async { YouTube.page(Card("VLLM", "Liked music", "", null)).tracks }
            val p = async { library("FEmusic_liked_playlists").cards.filter { it.browseId != "VLLM" && it.browseId != "VLSE" } }
            val al = async { library("FEmusic_liked_albums").cards }
            val ar = async { library("FEmusic_library_corpus_track_artists").cards }
            val server = runCatching { listOf(l.await(), p.await(), al.await(), ar.await()) }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                @Suppress("UNCHECKED_CAST")
                liked = server[0] as List<Track>
                @Suppress("UNCHECKED_CAST")
                playlists = server[1] as List<Card>
                @Suppress("UNCHECKED_CAST")
                albums = server[2] as List<Card>
                @Suppress("UNCHECKED_CAST")
                artists = server[3] as List<Card>
                synchronized(ops) { ops.forEach { it.apply() } }
                lastPull = System.currentTimeMillis()
            }
            save()
        } finally {
            pulling = false
            pullLock.unlock()
        }
    }

    /** While the app is open: pull every 5 minutes, and push the moment the network comes back. */
    fun start() {
        load()
        pull(force = true)
        scope.launch { while (true) { delay(5 * 60_000L); if (App.foreground) pull() } }
        App.ctx.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { if (pending > 0) push() }
        })
    }

    fun signedOut() = clear()

    private fun clear() {
        synchronized(ops) { ops.clear(); pending = 0 }
        account = null; expired = false; liked = emptyList(); playlists = emptyList(); albums = emptyList(); artists = emptyList()
        lastPull = 0
        file.delete()
    }

    // ---- YouTube reads ------------------------------------------------------------------------------------------

    private fun fetchAccount(): Account? {
        val h = findFirst(InnerTube.post("account/account_menu"), "activeAccountHeaderRenderer") as? JSONObject ?: return null
        return Account(h.optJSONObject("accountName").text(), thumbnail(h.optJSONObject("accountPhoto")))
    }

    private fun library(browseId: String) = items(InnerTube.post("browse", JSONObject().put("browseId", browseId)))
    fun history(): List<Track> = library("FEmusic_history").tracks

    private const val CPN_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    /** Tells YouTube the song was played, so it shows in history and shapes recommendations. */
    fun registerPlayback(trackingUrl: String) {
        val cpn = (1..16).map { CPN_CHARS.random() }.joinToString("")
        httpGet(trackingUrl.replace("https://s.youtube.com", InnerTube.ORIGIN) + "&ver=2&c=WEB_REMIX&cpn=$cpn", InnerTube.headers())
    }

    // ---- disk -------------------------------------------------------------------------------------------------

    private fun Card.json() = JSONObject().put("b", browseId).put("t", title).put("s", subtitle).put("a", art)
    private fun card(o: JSONObject) = Card(o.getString("b"), o.optString("t"), o.optString("s"), o.optString("a").ifEmpty { null }.takeIf { it != "null" })
    private fun JSONArray?.cards() = objects().mapNotNull { runCatching { card(it) }.getOrNull() }

    private fun save() = scope.launch { runCatching { write() } }

    private fun write() {
        val o = synchronized(ops) {
            JSONObject()
                .put("account", account?.let { JSONObject().put("name", it.name).put("photo", it.photo) })
                .put("liked", JSONArray(liked.map { it.toJson() }))
                .put("playlists", JSONArray(playlists.map { it.json() }))
                .put("albums", JSONArray(albums.map { it.json() }))
                .put("artists", JSONArray(artists.map { it.json() }))
                .put("lastPull", lastPull)
                .put("ops", JSONArray(ops.map { it.json() }))
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file) // atomic: a crash mid-write never loses the queue
    }

    private fun load() {
        val o = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        account = o.optJSONObject("account")?.let { Account(it.optString("name"), it.optString("photo").ifEmpty { null }) }
        liked = o.optJSONArray("liked").objects().mapNotNull { runCatching { Track.fromJson(it) }.getOrNull() }
        playlists = o.optJSONArray("playlists").cards()
        albums = o.optJSONArray("albums").cards()
        artists = o.optJSONArray("artists").cards()
        lastPull = o.optLong("lastPull")
        synchronized(ops) { ops.clear(); ops += o.optJSONArray("ops").objects().mapNotNull { Op.from(it) }; pending = ops.size }
    }
}
