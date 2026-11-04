package com.wetune

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.wetune.sources.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

const val CMD_SLEEP = "wetune.sleep"
const val CMD_LIKE = "wetune.like"

/** Full MediaItem for a track. The URI is a wetune:// placeholder the service resolves when it loads. */
fun Track.toMediaItem(): MediaItem {
    val downloaded = Downloads.file(this)
    val uri = when {
        downloaded.exists() -> Uri.fromFile(downloaded)
        source == "local" -> Uri.parse(ref)
        // title, artist and length ride along so the resolver can find the same song on another source
        else -> Uri.Builder().scheme("wetune").authority(source).appendQueryParameter("r", ref)
            .appendQueryParameter("t", title).appendQueryParameter("a", artist).appendQueryParameter("d", durationMs.toString()).build()
    }
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)

        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title).setArtist(artist).setAlbumTitle(album)
                .setArtworkUri(art?.let(Uri::parse))
                .setExtras(bundleOf("track" to toJson().toString()))
                .build()
        )
        .build()
}

fun MediaItem.track(): Track? = mediaMetadata.extras?.getString("track")
    ?.let { runCatching { Track.fromJson(JSONObject(it)) }.getOrNull() }

/** UI-side handle on the playback service, exposed as Compose state. */
object Playback {
    private var c: MediaController? = null

    var current by mutableStateOf<Track?>(null); private set
    var playing by mutableStateOf(false); private set
    var queue by mutableStateOf(emptyList<Track>()); private set
    var index by mutableIntStateOf(0); private set
    var shuffle by mutableStateOf(false); private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var buffering by mutableStateOf(false); private set
    /** What the queue was started from, shown at the top of the player: kind ("Album", "Playlist", "Search"…) to name. */
    var from by mutableStateOf(Settings.lastFrom.split("0000").takeIf { it.size == 2 }?.let { it[0] to it[1] }); private set
    /** When the sleep timer stops playback (epoch ms), or 0 when no timer is set. */
    var sleepEndsAt by mutableStateOf(0L); private set

    fun connect(context: Context) {
        if (c != null) return
        val ctx = context.applicationContext
        val future = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        future.addListener({
            c = future.get().also {
                it.addListener(listener)
                sync(it, full = true)
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun release() {
        c?.release()
        c = null
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) =
            sync(player, full = events.contains(Player.EVENT_TIMELINE_CHANGED))
    }

    private fun sync(p: Player, full: Boolean) {
        if (full) queue = (0 until p.mediaItemCount).mapNotNull { p.getMediaItemAt(it).track() }
        index = p.currentMediaItemIndex
        current = p.currentMediaItem?.track()
        playing = p.isPlaying
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
        buffering = p.playbackState == Player.STATE_BUFFERING
        if (sleepEndsAt != 0L && System.currentTimeMillis() > sleepEndsAt) sleepEndsAt = 0L
    }

    fun play(list: List<Track>, start: Int = 0, from: Pair<String, String>? = null) {
        val c = c ?: return
        if (list.isEmpty()) return
        this.from = from
        Settings.radioSeed = "" // a new queue; startRadio sets its own seed right after
        Settings.lastFrom = from?.let { "${it.first}0000${it.second}" }.orEmpty()
        c.setMediaItems(list.map { it.toMediaItem() }, start.coerceIn(0, list.size - 1), 0)
        c.prepare()
        c.play()
    }

    /**
     * Plays [t] followed by its YouTube radio (songs similar to it). Later auto-added songs keep using [t] as the
     * seed so they stay related, instead of drifting with whatever plays last.
     */
    fun startRadio(t: Track) {
        if (t.source != "yt") return play(listOf(t), from = "Radio" to t.title)
        // start the song at once; the similar songs join the queue behind it when they arrive
        play(listOf(t), from = "Radio" to t.title)
        Settings.radioSeed = t.ref
        appScope.launch {
            val more = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { com.wetune.youtube.YouTube.radio(t.ref) }.getOrDefault(emptyList()) }
            val c = c ?: return@launch
            if (c.currentMediaItem?.mediaId != t.id || Settings.radioSeed != t.ref) return@launch // user moved on
            c.addMediaItems(more.filter { it.id != t.id }.map { it.toMediaItem() })
        }
    }

    fun playNext(t: Track) {
        val c = c ?: return play(listOf(t))
        if (c.mediaItemCount == 0) return play(listOf(t))
        c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
        toast("Plays next")
    }

    fun enqueue(t: Track) {
        val c = c ?: return
        if (c.mediaItemCount == 0) return play(listOf(t))
        c.addMediaItem(t.toMediaItem())
        toast("Added to queue")
    }

    fun toggle() {
        val c = c ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = c?.seekToNext()
    fun prev() = c?.seekToPrevious()
    fun seek(ms: Long) = c?.seekTo(ms)
    fun skipTo(i: Int) = c?.seekTo(i, 0)
    fun remove(i: Int) = c?.removeMediaItem(i)
    fun move(from: Int, to: Int) = c?.moveMediaItem(from, to)
    fun toggleShuffle() = c?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    /** off → all → one → off */
    fun cycleRepeat() = c?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun position(): Long = c?.currentPosition ?: 0
    fun duration(): Long = c?.duration?.takeIf { it > 0 } ?: 0
    fun buffered(): Long = c?.bufferedPosition ?: 0
    fun speed(): Float = c?.playbackParameters?.speed ?: 1f

    /** 0 cancels the timer. */
    fun sleep(minutes: Int) {
        c?.sendCustomCommand(SessionCommand(CMD_SLEEP, Bundle.EMPTY), bundleOf("m" to minutes))
        sleepEndsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        toast(if (minutes > 0) "Stops in $minutes min" else "Sleep timer off")
    }
}

@Composable
fun rememberPosition(): State<Long> = produceState(0L) {
    while (true) {
        value = Playback.position()
        delay(250)
    }
}
