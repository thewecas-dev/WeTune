package com.wetune.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.wetune.appScope
import com.wetune.ui.Motion
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput

/** The Now Playing layer's state, shared by the mini player (which opens it) and the layer itself. */
object NowPlaying {
    /** 0 = closed (mini player), 1 = open. Driven by open/close and by dragging. */
    val progress = Animatable(0f)

    enum class Panel { None, Lyrics, Audio, Queue }

    /** Which part of the controls sheet is open (spec §6): Lyrics / Audio grow the sheet, Queue goes full screen. */
    var panel by mutableStateOf(Panel.None)
    var lyricsFull by mutableStateOf(false)

    /** Where the mini player's cover sits on screen, so opening can grow the cover from there. Plain field: no recomposition. */
    var miniCover = Rect.Zero

    /** Where the mini player's title + artist sit on screen, for the title flight. Plain field, like [miniCover]. */
    var miniText = Rect.Zero

    /** Where the cover sits by default, as % of the way from the screen's top edge to its bottom edge: the Position
     *  slider starts there (measured by the player; a typical phone's value until the player has been opened). */
    var coverAutoPos by androidx.compose.runtime.mutableIntStateOf(30)

    /** With Music video cover on: the video showing (true) or the artwork (false), switched at the top of Now Playing. */
    var videoOn by mutableStateOf(true)
    /** The song whose music video was found (the Cover / Video switch shows only for it). */
    var videoFor by mutableStateOf<String?>(null)

    val isOpen: Boolean get() = progress.targetValue > 0.5f

    /** A composition scope (set by Root): animations need its frame clock; a plain app scope crashes them. */
    var scope: kotlinx.coroutines.CoroutineScope = appScope

    fun open() = scope.launch { progress.animateTo(1f, tween(420, easing = Motion.Decelerate)) }

    fun close() = scope.launch {
        progress.animateTo(0f, tween(360, easing = Motion.Decelerate))
        panel = Panel.None
        lyricsFull = false
    }

    /** Finger moved by [dy] px on a layer [height] px tall (up = negative opens). */
    fun drag(dy: Float, height: Float) = scope.launch { progress.snapTo((progress.value - dy / height).coerceIn(0f, 1f)) }

    /** Finger lifted: finish toward the nearer end, or the way it was flung. [velocity] in px/s, up = negative. */
    fun settle(velocity: Float) = if (velocity < -800f || (velocity < 800f && progress.value > 0.5f)) open() else close()

    // The Lyrics, Audio and Queue buttons. Lyrics: closed → sheet → full screen → back to the sheet; tapping the cover
    // (or Back) closes the sheet. Queue opens full screen and closes again. Audio opens and closes its sheet.
    /** The Lyrics button always means full-screen lyrics; the sheet's own lyrics are the inline ones (Settings). */
    fun tapLyrics() { if (panel == Panel.Lyrics) panel = Panel.None else { lyricsFull = true; panel = Panel.Lyrics } }

    fun tapQueue() { lyricsFull = false; panel = if (panel == Panel.Queue) Panel.None else Panel.Queue }

    fun tapAudio() { lyricsFull = false; panel = if (panel == Panel.Audio) Panel.None else Panel.Audio }

    /** Back from inside Now Playing: full-screen lyrics → sheet → closed. */
    fun back() {
        when {
            panel != Panel.None -> panel = Panel.None
            else -> close()
        }
    }
}

/** Vertical drag that opens / closes Now Playing, following the finger and settling by fling velocity. */
fun androidx.compose.ui.Modifier.sheetDrag(heightPx: Float): androidx.compose.ui.Modifier = pointerInput(heightPx) {
    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
    var travelled = 0f // the layer moves under the finger, so track distance dragged, not the position in the layer
    detectVerticalDragGestures(
        onDragStart = { tracker.resetTracking(); travelled = 0f },
        onDragEnd = { NowPlaying.settle(tracker.calculateVelocity().y) },
        onDragCancel = { NowPlaying.settle(0f) },
        onVerticalDrag = { change, dy ->
            change.consume()
            travelled += dy
            tracker.addPosition(change.uptimeMillis, androidx.compose.ui.geometry.Offset(0f, travelled))
            NowPlaying.drag(dy, heightPx)
        },
    )
}
