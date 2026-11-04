package com.wetune

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wetune.sources.Track
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Sync
import com.wetune.youtube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.pow

/** Owns ExoPlayer + MediaSession: background playback, notification, lock screen, queue persistence. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val scope = MainScope()
    private var sleepJob: Job? = null
    private var tracking: Pair<String, String>? = null // trackId to YouTube playback-tracking URL
    private var registered: String? = null
    private var radioFor: String? = null
    private var retried: String? = null // song already retried once after a playback error
    private val queueFile get() = File(filesDir, "queue.json")

    override fun onCreate() {
        super.onCreate()
        // wetune://<source>?r=<ref> is resolved to a real URL on the loader thread, so network calls are fine here.
        // Cache sits under the resolver and is keyed by the real stream (YouTube video+format, JioSaavn file), so two
        // different streams for the same song can never be mixed into one cached file.
        val cached = CacheDataSource.Factory().setCache(App.cache).setUpstreamDataSourceFactory(OkHttpDataSource.Factory(http))
            .setCacheKeyFactory { spec -> AudioInfo.cacheKey(spec.uri.toString()) }
        val upstream = ResolvingDataSource.Factory(cached) { spec ->
            if (spec.uri.scheme != "wetune") spec
            else spec.uri.authority!!.let { source -> val ref = spec.uri.getQueryParameter("r")!!; Resolver.streamUrl(source, ref, title = spec.uri.getQueryParameter("t"), artist = spec.uri.getQueryParameter("a"), durationMs = spec.uri.getQueryParameter("d")?.toLongOrNull() ?: 0).let { AudioInfo.resolved("$source:$ref", it); spec.withUri(Uri.parse(it)) } }
        }
        val dataSource = DefaultDataSource.Factory(this, upstream)
        if (!Settings.cacheV2) { // entries cached under the old song-id keys may mix streams; drop them once
            runCatching { App.cache.keys.toList().forEach(App.cache::removeResource) }
            Settings.cacheV2 = true
        }

        // the audio add-ons run inside the player, after decoding (speed/pitch and skip-silence stay in Media3's own chain)
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean) =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(EffectsProcessor())).build()
        }
        player = ExoPlayer.Builder(this, renderers)
            // start after ~0.7 s of audio instead of 2.5 s; keep a deep buffer once playing
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 700, 2_000).build())
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)
        player.addAnalyticsListener(audioWatcher)
        Resolver.onUpgrade = { ref -> scope.launch { upgradeToMatch("yt:$ref") } }
        com.wetune.sources.Addons.warmUp() // free add-on servers sleep; wake them before the first song needs one
        session = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        restoreQueue()
        watchEffects()
        watchNetwork()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveQueue()
        openSystemEffects(false)
        scope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    /** Feeds [AudioInfo] with the decoded format and decoder of the song that's playing. */
    private val audioWatcher = object : AnalyticsListener {
        override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
            AudioInfo.mime = format.sampleMimeType ?: format.containerMimeType
            AudioInfo.container = format.containerMimeType
            AudioInfo.codecs = format.codecs
            AudioInfo.bitrate = maxOf(format.averageBitrate, format.bitrate, 0)
            AudioInfo.sampleRate = maxOf(format.sampleRate, 0)
            AudioInfo.channels = maxOf(format.channelCount, 0)
        }

        override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            AudioInfo.decoder = decoderName
            AudioInfo.decoderInitMs = initializationDurationMs
        }

        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
            AudioInfo.outEncoding = when (audioTrackConfig.encoding) {
                C.ENCODING_PCM_16BIT -> "16-bit PCM"; C.ENCODING_PCM_FLOAT -> "32-bit float PCM"; C.ENCODING_PCM_24BIT -> "24-bit PCM"
                C.ENCODING_PCM_32BIT -> "32-bit PCM"; C.ENCODING_PCM_8BIT -> "8-bit PCM"; else -> "Compressed (passed through)"
            }
            AudioInfo.outRate = audioTrackConfig.sampleRate
            AudioInfo.outChannels = Integer.bitCount(audioTrackConfig.channelConfig)
            AudioInfo.outBufferBytes = audioTrackConfig.bufferSize
            AudioInfo.offload = audioTrackConfig.offload
        }

        override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
            AudioInfo.underruns++
        }

        override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
            AudioInfo.bandwidthKbps = bitrateEstimate / 1000
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            val t = item?.track() ?: return
            retried = null
            // local files and downloads never go through the resolver, so their address is known right away
            item.localConfiguration?.uri?.takeIf { it.scheme != "wetune" }?.let { AudioInfo.resolved(t.id, it.toString()) }
            normalize(null)
            if (t.source == "yt") scope.launch {
                val info = withContext(Dispatchers.IO) { runCatching { YouTube.playerInfo(t.ref) }.getOrNull() }
                if (player.currentMediaItem?.mediaId != t.id) return@launch
                normalize(info?.loudnessDb)
                tracking = info?.trackingUrl?.let { t.id to it }
                maybeRegister()
            }
            maybeAutoplay(t)
            updateLikeButton()
            prefetchNext()
            recordStart(t)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) maybeRegister() else saveQueue()
        }

        override fun onPlayerError(error: PlaybackException) {
            val t = player.currentMediaItem?.track()
            t?.let { Resolver.invalidate(it.source, it.ref) }
            // a broken cached copy or an expired link: drop the cached bytes and try this song once more with a fresh stream
            t?.let { tr -> AudioInfo.urlFor(tr.id)?.let { runCatching { App.cache.removeResource(AudioInfo.cacheKey(it)) } } }
            if (t != null && retried != t.id) {
                retried = t.id
                val at = player.currentPosition
                player.prepare(); player.seekTo(at); player.play()
                return
            }
            toast("Couldn't play ${t?.title ?: "this song"}, skipping")
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    /** Like YouTube: songs louder than the target are turned down by their loudnessDb; quiet songs are left alone. */
    private fun normalize(loudnessDb: Double?) {
        val over = if (Settings.normalize && loudnessDb != null) loudnessDb.coerceAtLeast(0.0) else 0.0
        normGain = 10.0.pow(-over / 20).toFloat()
        applyVolume()
        AudioInfo.gainDb = (-over).toFloat()
    }

    // ---- audio add-ons ----

    private var normGain = 1f
    private var sleepFadeFrom = 0L // when the sleep timer's fade-out began, 0 = not fading
    private var effectsOpen = false

    /** Player volume = normalization × song fade (in at the start, out at the end) × sleep-timer fade. */
    private fun applyVolume() {
        val p = Effects.current
        var fade = 1f
        val fadeMs = (p.fadeSec * 1000).toLong()
        val dur = player.duration
        if (fadeMs > 0 && dur > 0 && dur != C.TIME_UNSET && player.isPlaying) {
            val pos = player.currentPosition
            val fadeIn = (pos.toFloat() / (fadeMs / 2).coerceAtLeast(1)).coerceIn(0f, 1f) // the new song comes in in half the time
            val fadeOut = ((dur - pos).toFloat() / fadeMs).coerceIn(0f, 1f)
            // equal-power curve: the dip sounds even rather than sudden
            fade = kotlin.math.sin(minOf(fadeIn, fadeOut) * Math.PI.toFloat() / 2)
        }
        val sleep = if (sleepFadeFrom > 0) (1f - (System.currentTimeMillis() - sleepFadeFrom) / SLEEP_FADE_MS.toFloat()).coerceIn(0f, 1f) else 1f
        player.volume = normGain * fade * sleep
    }

    /** Applies speed, pitch and skip-silence when they change, and runs the fade clock while fades are on. */
    private fun watchEffects() {
        Effects.sessionId = player.audioSessionId
        openSystemEffects(true)
        scope.launch {
            androidx.compose.runtime.snapshotFlow { Effects.params }.collect { p ->
                player.playbackParameters = androidx.media3.common.PlaybackParameters(p.speed, p.pitch)
                player.skipSilenceEnabled = p.skipSilence
                applyVolume()
            }
        }
        scope.launch {
            while (true) {
                val busy = Effects.current.fadeSec > 0f || sleepFadeFrom > 0
                if (busy) applyVolume()
                delay(if (busy) 40 else 500)
            }
        }
    }

    /** Lets the phone's own effects (system equalizer, Dolby, SoundAlive…) attach to WeTune's audio. */
    private fun openSystemEffects(open: Boolean) {
        if (open == effectsOpen || player.audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        effectsOpen = open
        sendBroadcast(
            Intent(if (open) android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION else android.media.audiofx.AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC),
        )
    }

    // ---- quality: keep stepping up to the chosen maximum ----

    private var lastLevel = -1

    /** When the connection gets better (e.g. mobile data → Wi-Fi), the playing song reloads at the higher quality. */
    private fun watchNetwork() {
        lastLevel = Resolver.level()
        // raising the maximum in Settings upgrades the playing song too
        scope.launch {
            androidx.compose.runtime.snapshotFlow { Settings.maxWifi to Settings.maxMobile }.collect {
                val now = Resolver.level()
                if (now > lastLevel) upgradeCurrent("maximum raised to ${Resolver.levelName(now)}")
                lastLevel = now
            }
        }
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
        cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                scope.launch {
                    val now = Resolver.level()
                    val was = lastLevel
                    lastLevel = now
                    if (now > was) upgradeCurrent("connection allows ${Resolver.levelName(now)}")
                }
            }
        })
    }

    /** A better connection or a higher ceiling: the songs coming up start at the new level (the playing one isn't swapped). */
    private fun upgradeCurrent(why: String) {
        UpgradeLog.add("level up: $why")
        val next = player.currentMediaItemIndex + 1
        if (next < player.mediaItemCount) player.getMediaItemAt(next).let { m -> m.track()?.let { Resolver.forget(it.source, it.ref) }; refreshed(m)?.let { player.replaceMediaItem(next, it) } }
        prefetchNext()
    }

    /** Sends the play to YouTube once per song, after it actually starts playing. */
    private fun maybeRegister() {
        val (id, url) = tracking ?: return
        if (!player.isPlaying || player.currentMediaItem?.mediaId != id || registered == id || !InnerTube.signedIn) return
        registered = id
        scope.launch(Dispatchers.IO) { runCatching { Sync.registerPlayback(url) } }
    }

    /** Near the end of the queue, append YouTube's radio for the current song. */
    /**
     * Infinite: near the end of the queue, add similar songs. From the radio's seed first (so a radio stays on its
     * vibe), then from the song playing now once the seed's radio is used up.
     */
    private fun maybeAutoplay(t: Track) {
        if (!Settings.autoplay || t.source != "yt" || radioFor == t.ref) return
        if (player.currentMediaItemIndex < player.mediaItemCount - 2) return
        radioFor = t.ref
        val seed = Settings.radioSeed.ifBlank { null }
        scope.launch {
            val have = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            suspend fun radio(id: String) = withContext(Dispatchers.IO) { runCatching { YouTube.radio(id) }.getOrDefault(emptyList()) }.filter { it.id !in have }
            val more = seed?.let { radio(it) }.orEmpty().ifEmpty { radio(t.ref) }
            player.addMediaItems(more.map { it.toMediaItem() })
        }
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SessionCommand(CMD_SLEEP, Bundle.EMPTY)).add(SessionCommand(CMD_LIKE, Bundle.EMPTY)).build()
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_LIKE) player.currentMediaItem?.track()?.let { t ->
                io(then = { updateLikeButton() }) { Library.toggleLike(t) }
            }
            if (customCommand.customAction == CMD_SLEEP) {
                sleepJob?.cancel()
                sleepFadeFrom = 0; applyVolume()
                val minutes = args.getInt("m")
                if (minutes > 0) sleepJob = scope.launch {
                    val total = minutes * 60_000L
                    if (Effects.current.sleepFade && total > SLEEP_FADE_MS) {
                        // drift off: the music fades out over the last 30 s instead of stopping dead
                        delay(total - SLEEP_FADE_MS)
                        sleepFadeFrom = System.currentTimeMillis()
                        delay(SLEEP_FADE_MS)
                    } else delay(total)
                    player.pause()
                    sleepFadeFrom = 0; applyVolume()
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        // Controllers strip the URI when sending items across; rebuild them from the track stored in the metadata.
        override fun onAddMediaItems(
            session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.track()?.toMediaItem() ?: it }.toMutableList())
    }

    /**
     * A better copy of [id] was found in the background. The playing song is never swapped mid-song (any swap is
     * audible); the copy is kept for its next play. A song further down the queue that the player already started
     * loading is reloaded, so it begins on the better copy.
     */
    private fun upgradeToMatch(id: String) {
        if (player.currentMediaItem?.mediaId == id) { AudioInfo.upgrading(id, "none"); return }
        val i = (0 until player.mediaItemCount).firstOrNull { it != player.currentMediaItemIndex && player.getMediaItemAt(it).mediaId == id } ?: return
        val m = player.getMediaItemAt(i)
        m.track()?.let { Resolver.forget(it.source, it.ref) }
        refreshed(m)?.let { player.replaceMediaItem(i, it) }
        UpgradeLog.add("queued $id at #$i will start on its better copy")
    }

    /** The same item with a changed address, so the player loads it again (through the resolver, which now picks the better copy). */
    private fun refreshed(m: MediaItem): MediaItem? {
        val uri = m.localConfiguration?.uri ?: return null
        val u = uri.buildUpon().clearQuery().apply { uri.queryParameterNames.filter { it != "u" }.forEach { k -> appendQueryParameter(k, uri.getQueryParameter(k)) } }
        return m.buildUpon().setUri(u.appendQueryParameter("u", System.currentTimeMillis().toString()).build()).build()
    }

    /** Notes which copy and quality a song started on, for the pipeline's upgrade history ("0:00 · Qobuz · FLAC 24-bit"). */
    private fun recordStart(t: Track) {
        scope.launch {
            delay(900) // the decoder has reported the stream's format by now
            if (player.currentMediaItem?.mediaId != t.id) return@launch
            val url = AudioInfo.urlFor(t.id).orEmpty()
            val source = when {
                t.source == "local" || Downloads.file(t).exists() -> "This phone"
                t.source == "subsonic" -> "Your server"
                "googlevideo" in url -> "YouTube"
                "saavncdn" in url -> "JioSaavn"
                else -> Resolver.copyName(t.ref)?.substringAfter(" · ") ?: t.source
            }
            AudioInfo.event(t.id, player.currentPosition, listOfNotNull(source, AudioInfo.label).joinToString(" · "))
        }
    }

    /**
     * While a song plays, the next three are looked up ahead of time: their best copies are searched and matched
     * (a few KB of search results each, no audio), so each starts at the highest quality allowed right away.
     * The very next song's address is also resolved, so skipping starts instantly; the player itself buffers its
     * audio only near the end of the current one (for gapless playback).
     */
    private fun prefetchNext() {
        val here = player.currentMediaItemIndex
        (1..3).forEach { ahead ->
            val i = here + ahead
            if (i >= player.mediaItemCount) return
            val uri = player.getMediaItemAt(i).localConfiguration?.uri ?: return@forEach
            if (uri.scheme != "wetune") return@forEach
            val source = uri.authority ?: return@forEach; val ref = uri.getQueryParameter("r") ?: return@forEach
            val title = uri.getQueryParameter("t"); val artist = uri.getQueryParameter("a"); val dur = uri.getQueryParameter("d")?.toLongOrNull() ?: 0
            scope.launch(Dispatchers.IO) {
                runCatching {
                    if (ahead == 1) Resolver.streamUrl(source, ref, title = title, artist = artist, durationMs = dur)
                    else if (source == "yt" && title != null) Resolver.prepare(ref, title, artist.orEmpty(), dur)
                }
            }
        }
    }

    /** The heart in the media notification and on the lock screen; filled when the current song is liked. */
    private fun updateLikeButton() {
        val t = player.currentMediaItem?.track() ?: return
        val liked = Library.isLiked(t)
        session.setCustomLayout(
            listOf(
                CommandButton.Builder()
                    .setDisplayName(if (liked) "Remove from Liked" else "Like")
                    .setIconResId(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline)
                    .setSessionCommand(SessionCommand(CMD_LIKE, Bundle.EMPTY))
                    .build()
            )
        )
    }

    private fun saveQueue() {
        runCatching {
            val items = JSONArray()
            for (i in 0 until player.mediaItemCount) player.getMediaItemAt(i).track()?.let { items.put(it.toJson()) }
            queueFile.writeText(
                JSONObject().put("items", items).put("index", player.currentMediaItemIndex).put("pos", player.currentPosition).toString()
            )
        }
    }

    private fun restoreQueue() {
        runCatching {
            if (!queueFile.exists()) return
            val o = JSONObject(queueFile.readText())
            val items = o.optJSONArray("items").objects().map { Track.fromJson(it).toMediaItem() }
            if (items.isNotEmpty()) player.setMediaItems(items, o.optInt("index").coerceIn(0, items.size - 1), o.optLong("pos"))
        }
    }
    private companion object { const val SLEEP_FADE_MS = 30_000L }
}
