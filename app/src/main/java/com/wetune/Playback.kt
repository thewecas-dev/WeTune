package com.wetune

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

const val CMD_SLEEP = "wetune.sleep"
const val CMD_OUTPUT = "wetune.output" // args "id": an AudioDeviceInfo id to play through, or -1 for Android's choice
const val CMD_LIKE = "wetune.like"
const val CMD_RELOAD = "wetune.reload" // reload the playing song at the same spot (after picking another version)

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

    private var appCtx: Context? = null
    private var connecting = false
    /** Commands asked for while the link to the player service was down; run once it's back. */
    private val pending = mutableListOf<(MediaController) -> Unit>()

    /**
     * Links the app to the player service. The service can be stopped or restarted by Android (in the background, or
     * after being swiped away while paused); the old link then goes dead, so this notices and links again, and any
     * button pressed meanwhile is replayed once linked (before, it silently did nothing).
     */
    fun connect(context: Context) {
        val ctx = context.applicationContext
        appCtx = ctx
        c?.let { if (it.isConnected) return else { it.release(); c = null } }
        if (connecting) return
        connecting = true
        val future = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) { if (c === controller) c = null }
            })
            .buildAsync()
        future.addListener({
            connecting = false
            val m = runCatching { future.get() }.getOrNull() ?: return@addListener
            c = m
            m.addListener(listener)
            sync(m, full = true)
            pending.toList().also { pending.clear() }.forEach { it(m) }
        }, ContextCompat.getMainExecutor(ctx))
    }

    /** Runs [f] on a live link to the player, linking again first if it dropped. */
    private fun cmd(f: (MediaController) -> Unit) {
        val m = c
        if (m != null && m.isConnected) f(m)
        else { pending += f; appCtx?.let(::connect) }
    }

    /** After a song failed to load the player sits idle: moving to another song must also start it loading again. */
    private fun revive(m: MediaController) { if (m.playbackState == Player.STATE_IDLE) m.prepare() }

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
        val now = p.currentMediaItem?.track()
        if (now != null && now.id != current?.id) Settings.addRecent(now) // Search's "Recently played"
        current = now
        playing = p.isPlaying
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
        buffering = p.playbackState == Player.STATE_BUFFERING
        duration().let { if (it != knownDuration) knownDuration = it }
        anchor(p) // play / pause / seek / skip / speed all arrive here: the clock snaps or stops at once
        if (sleepEndsAt != 0L && System.currentTimeMillis() > sleepEndsAt) sleepEndsAt = 0L
    }

    fun play(list: List<Track>, start: Int = 0, from: Pair<String, String>? = null) {
        if (list.isEmpty()) return
        cmd { c ->
            this.from = from
            Settings.radioSeed = "" // a new queue; startRadio sets its own seed right after
            Settings.lastFrom = from?.let { "${it.first}0000${it.second}" }.orEmpty()
            c.setMediaItems(list.map { it.toMediaItem() }, start.coerceIn(0, list.size - 1), 0)
            c.prepare()
            c.play()
        }
    }

    /**
     * Plays [t] followed by its YouTube radio (songs similar to it). Later auto-added songs keep using [t] as the
     * seed so they stay related, instead of drifting with whatever plays last.
     */
    fun startRadio(t: Track) {
        if (t.source != "yt") return play(listOf(t), from = "Radio" to t.title)
        val c0 = c
        if (c0 != null && c0.currentMediaItem?.mediaId == t.id) {
            // radio for the song that's already playing: it keeps playing; only the rest of the queue is replaced
            val i = c0.currentMediaItemIndex
            if (i + 1 < c0.mediaItemCount) c0.removeMediaItems(i + 1, c0.mediaItemCount)
            if (i > 0) c0.removeMediaItems(0, i)
            from = "Radio" to t.title
            Settings.lastFrom = "Radio0000${t.title}"
        } else {
            // start the song at once; the similar songs join the queue behind it when they arrive
            play(listOf(t), from = "Radio" to t.title)
        }
        Settings.radioSeed = t.ref
        appScope.launch {
            val more = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { com.wetune.youtube.YouTube.radio(t.ref) }.getOrDefault(emptyList()) }
            val c = c ?: return@launch
            if (c.currentMediaItem?.mediaId != t.id || Settings.radioSeed != t.ref) return@launch // user moved on
            c.addMediaItems(more.filter { it.id != t.id }.map { it.toMediaItem() })
        }
    }

    fun playNext(t: Track) = cmd { c ->
        if (c.mediaItemCount == 0) play(listOf(t))
        else { c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem()); toast("Plays next") }
    }

    fun enqueue(t: Track) = cmd { c ->
        if (c.mediaItemCount == 0) play(listOf(t))
        else { c.addMediaItem(t.toMediaItem()); toast("Added to queue") }
    }

    fun toggle() = cmd { c ->
        if (c.isPlaying) c.pause() else { revive(c); c.play() }
    }

    // moving to another song also restarts loading if the player went idle after a failed song
    fun next() = cmd { c -> c.seekToNext(); revive(c) }
    /** The previous song; past the first few seconds of a song, back to its start first (as every player does). */
    fun prev() = cmd { c -> c.seekToPrevious(); revive(c) }
    fun seek(ms: Long) = cmd { c -> c.seekTo(ms) }
    fun skipTo(i: Int) = cmd { c -> c.seekTo(i, 0); revive(c); c.play() }
    fun remove(i: Int) = cmd { c -> c.removeMediaItem(i) }
    fun move(from: Int, to: Int) = cmd { c -> c.moveMediaItem(from, to) }
    fun insert(i: Int, t: Track) = cmd { c -> c.addMediaItem(i.coerceIn(0, c.mediaItemCount), t.toMediaItem()) }
    fun toggleShuffle() = cmd { c -> c.shuffleModeEnabled = !c.shuffleModeEnabled }

    /** off → all → one → off */
    fun cycleRepeat() = cmd { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun position(): Long = c?.currentPosition ?: 0

    /** Where the song was at a moment, advanced every frame by [rememberPosition] (the player reports it only now and then). */
    var clock by mutableStateOf(PlayClock(0, 0, false, 1f, 0)); private set

    private fun anchor(p: Player) {
        clock = PlayClock(p.currentPosition, System.nanoTime() / 1_000_000, p.isPlaying, p.playbackParameters.speed, duration())
    }

    /** The slow check against the player: re-anchors only if the clock drifted, so the bar never twitches. */
    fun resync() {
        val p = c ?: return
        if (kotlin.math.abs(clock.at(System.nanoTime() / 1_000_000) - p.currentPosition) > 120) anchor(p)
    }
    // the player's own once known (C.TIME_UNSET before that), else the length the song came with
    fun duration(): Long = c?.duration?.takeIf { it > 0 } ?: current?.durationMs?.takeIf { it > 0 } ?: 0

    /** [duration] as state, refreshed with the position, so derived values (the seek bar) update when it becomes known. */
    var knownDuration by mutableLongStateOf(0L)

    /** The output the user picked in Audio (an AudioDeviceInfo id), or -1: Android routes as usual. Not kept across restarts (ids change). */
    var outputId by mutableIntStateOf(-1); private set

    fun setOutput(id: Int) {
        outputId = id
        c?.sendCustomCommand(SessionCommand(CMD_OUTPUT, Bundle.EMPTY), bundleOf("id" to id))
    }
    fun buffered(): Long = c?.bufferedPosition ?: 0
    /** Plays version [choice] of YouTube song [t] (from Audio options) right away, at the same spot, and from now on. */
    fun useVersion(t: Track, choice: String?) {
        Resolver.choose(t.ref, choice)
        c?.sendCustomCommand(SessionCommand(CMD_RELOAD, Bundle.EMPTY), Bundle.EMPTY)
    }

    fun speed(): Float = c?.playbackParameters?.speed ?: 1f

    /** 0 cancels the timer. */
    fun sleep(minutes: Int) {
        c?.sendCustomCommand(SessionCommand(CMD_SLEEP, Bundle.EMPTY), bundleOf("m" to minutes))
        sleepEndsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        toast(if (minutes > 0) "Stops in $minutes min" else "Sleep timer off")
    }
}

/** [pos] ms at [atMs] (a monotonic ms clock), moving at [speed] while [playing], kept within 0..[duration] (0 = unknown). */
data class PlayClock(val pos: Long, val atMs: Long, val playing: Boolean, val speed: Float, val duration: Long) {
    fun at(nowMs: Long): Long {
        val p = if (playing) pos + ((nowMs - atMs).coerceAtLeast(0) * speed).toLong() else pos
        return (if (duration > 0) p.coerceAtMost(duration) else p).coerceAtLeast(0)
    }
}

/**
 * The song's position, advanced every display frame from [Playback.clock] while playing (smooth, instead of stepping
 * 4×/s as when it was polled), still while paused (no frames run). Read it in the draw phase so only drawing repeats.
 */
@Composable
fun rememberPosition(): State<Long> = produceState(Playback.clock.at(System.nanoTime() / 1_000_000)) {
    var synced = 0L
    while (true) {
        val k = Playback.clock
        if (!k.playing) { value = k.at(0); snapshotFlow { Playback.clock }.first { it != k }; continue }
        withFrameNanos { f ->
            val now = f / 1_000_000 // frame time: the same clock as System.nanoTime, evenly spaced at the display's rate
            value = Playback.clock.at(now)
            if (now - synced > 1000) { synced = now; Playback.resync() }
        }
    }
}
