package com.wetune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import com.wetune.ui.press
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.wetune.COVER_AUTO
import com.wetune.Settings
import com.wetune.ui.Caption
import com.wetune.ui.Group
import com.wetune.ui.GroupRow
import com.wetune.ui.Icon
import com.wetune.ui.Icons
import com.wetune.ui.Motion
import com.wetune.ui.Toggle
import com.wetune.ui.Txt
import com.wetune.ui.Type
import com.wetune.ui.WT
import kotlin.math.roundToInt

private val Styles = listOf("apple" to "Clean", "material" to "Expressive")

private val SeekStyles = listOf(
    "auto" to "Automatic", "thin" to "Thin", "thick" to "Thick", "squiggly" to "Squiggly", "wavy" to "Wavy", "waves" to "Layered waves",
)


private val Aspects = listOf(
    "1:1" to "Square", "4:5" to "Portrait 4:5", "3:4" to "Portrait 3:4", "9:16" to "Tall 9:16", "16:9" to "Wide 16:9",
    "device" to "Phone screen", "fill" to "Fill the space", "original" to "Uncropped",
)

/** Settings › Now Playing: how the player looks, its cover, and the card behind its controls. */
@Composable
fun NowPlayingSettingsBody() {
    var open by remember { mutableStateOf<String?>(null) } // the open dropdown's key: one at a time, across the sections
    @Composable fun drop(title: String, key: String, options: List<Pair<String, String>>, value: String, last: Boolean = false, set: (String) -> Unit) =
        Dropdown(title, options, value, open == key, last = last, key = key, onToggle = { open = if (open == key) null else key }) { set(it); open = null }

    Caption("Look")
    Group {
        drop("Style", "player_style", Styles, Settings.playerStyle) { Settings.playerStyle = it }
        drop("Background", "np2_background", Backdrops, backdrop) { Settings.npBackground = it }
        drop("Seek bar", "np2_seek", SeekStyles, Settings.seekStyle) { Settings.seekStyle = it }
        drop("Seek bar colour", "np2_seek_color", listOf("theme" to "Plain", "cover" to "Cover colour"), Settings.seekColor) { Settings.seekColor = it }
        SwitchRow("Shuffle and Repeat beside play", Settings.npShuffleRepeat) { Settings.npShuffleRepeat = it }
        SwitchRow("Hide status bar", Settings.npHideStatus, last = true) { Settings.npHideStatus = it }
    }

    Caption("Cover")
    Group {
        val edge = backdrop == "edge"
        if (!edge) drop("Style", "np2_cover_style", CoverStyles, coverStyle) { Settings.coverStyle = it }
        drop("Shape", "np2_cover_aspect", Aspects, Settings.coverAspect, last = false) { Settings.coverAspect = it }
        // the moving cover (Spotify Canvas or Apple Music's animated album art), apart from the backgrounds; any cover style, full width too
        SwitchRow("Animated cover", Settings.npVideoCover, last = edge && !Settings.npVideoCover) { Settings.npVideoCover = it }
        if (Settings.npVideoCover) {
            SwitchRow("Only on Wi-Fi", Settings.npVideoWifiOnly) { Settings.npVideoWifiOnly = it }
            // Spotify Canvas needs the user's Spotify login; without it, Apple Music's animated album covers are used
            val spotify = Settings.spotifyDc.isNotBlank()
            GroupRow("Spotify Canvas", value = if (spotify) "Signed in" else "Sign in", chevron = !spotify, last = edge) {
                if (!spotify) com.wetune.ui.Nav.open(com.wetune.ui.Route.SpotifyLogin)
                else com.wetune.ui.Sheet.show {
                    com.wetune.ui.SheetTitle("Spotify Canvas")
                    com.wetune.ui.MenuRow(Icons.SignOut, "Sign out of Spotify") { com.wetune.SpotifyCanvas.signOut() }
                }
            }
        }
        if (!edge) {
            // "Fill the space" takes all the room above the controls: no size or position to set
            if (Settings.coverAspect != "fill") SliderRow("Size", Settings.coverSize, 55..120, { "$it %" }, key = "np2_cover_size") { Settings.coverSize = it }
            // from touching the top edge (0 %) to touching the bottom edge (100 %); never moved = the default spot,
            // measured from the real player and marked by the notch
            val auto = NowPlaying.coverAutoPos.coerceIn(0, 100)
            if (Settings.coverAspect != "fill") SliderRow(
                "Position", if (Settings.coverPlace == COVER_AUTO) auto else Settings.coverPlace, 0..100,
                { when (it) { 0 -> "At the top"; 50 -> "Middle"; 100 -> "At the bottom"; else -> "$it %" } }, mark = auto,
                onReset = { Settings.coverPlace = COVER_AUTO }, // double-tap: back to the real default spot, not its %
            ) { Settings.coverPlace = it }
            val corner = { v: Int -> when (v) { 0 -> "Square"; 100 -> "Round"; else -> "$v %" } }
            val split = Settings.coverCornersSplit
            SwitchRow("Shape each corner", split) { on ->
                // start the four from the current shared value, so switching never jumps
                if (on) Settings.coverRadius.let { Settings.coverCornerTL = it; Settings.coverCornerTR = it; Settings.coverCornerBR = it; Settings.coverCornerBL = it }
                Settings.coverCornersSplit = on
            }
            if (!split) SliderRow("Corners", Settings.coverRadius, 0..100, corner, key = "np2_cover_radius") { Settings.coverRadius = it }
            else {
                SliderRow("Top left", Settings.coverCornerTL, 0..100, corner, key = "np2_cover_corner_tl") { Settings.coverCornerTL = it }
                SliderRow("Top right", Settings.coverCornerTR, 0..100, corner, key = "np2_cover_corner_tr") { Settings.coverCornerTR = it }
                SliderRow("Bottom right", Settings.coverCornerBR, 0..100, corner, key = "np2_cover_corner_br") { Settings.coverCornerBR = it }
                SliderRow("Bottom left", Settings.coverCornerBL, 0..100, corner, key = "np2_cover_corner_bl") { Settings.coverCornerBL = it }
            }
            SwitchRow("Shadow", Settings.coverShadow, last = !Settings.coverShadow) { Settings.coverShadow = it }
            if (Settings.coverShadow) SliderRow("Shadow strength", Settings.coverShadowStrength, 10..100, { "$it %" }, last = true, key = "np2_cover_shadow_strength") { Settings.coverShadowStrength = it }
        }
    }
    if (backdrop == "edge") Note("A full-width cover sits at the top, edge to edge.")

    Caption("Controls card")
    Group {
        val on = Settings.npCard
        SwitchRow("Card behind the controls", on, last = !on) { Settings.npCard = it }
        if (on) {
            TintPicker()
            if (cardTintKey() == "auto") GroupRow("Same frost as the navbar", value = "General › Glass", chevron = false)
            else {
                SliderRow("Tint strength", Settings.npCardStrength, 0..100, { when (it) { 0 -> "Clear"; 100 -> "Solid"; else -> "$it %" } }, key = "np2_card_strength") { Settings.npCardStrength = it }
                SliderRow("Blur", Settings.npCardBlur, 0..80, { if (it == 0) "None" else "$it dp" }, key = "np2_card_blur") { Settings.npCardBlur = it }
            }
            SliderRow("Margin", Settings.npCardInset, 0..24, { if (it == 0) "None" else "$it dp" }, key = "np2_card_inset") { Settings.npCardInset = it }
            SwitchRow("Shadow", Settings.npCardShadow) { Settings.npCardShadow = it }
            if (Settings.npCardShadow) SliderRow("Shadow strength", Settings.npCardShadowStrength, 10..100, { "$it %" }, key = "np2_card_shadow_strength") { Settings.npCardShadowStrength = it }
            val follow = Settings.sheetRadius < 0
            SwitchRow("Follow the screen's corners", follow, last = follow) { Settings.sheetRadius = if (it) -1 else 30 }
            if (!follow) SliderRow("Corner radius", Settings.sheetRadius, 0..48, { "$it dp" }, last = true, mark = 30) { Settings.sheetRadius = it }
        }
    }
    Note("Defaults are marked: “Default” in lists, a notch on sliders.")
}

/** Settings › Lyrics: inline lyrics under the title, the full lyrics, and romanization. */
@Composable
fun LyricsSettingsBody() {
    var open by remember { mutableStateOf<String?>(null) } // the open dropdown's key (titles repeat: two "Lines")
    @Composable fun drop(title: String, key: String, options: List<Pair<String, String>>, value: String, last: Boolean = false, set: (String) -> Unit) =
        Dropdown(title, options, value, open == key, last = last, key = key, onToggle = { open = if (open == key) null else key }) { set(it); open = null }

    Caption("Under the song title")
    Group {
        val inline = Settings.inlineLyrics > 0
        drop("Lines", "np2_inline_lyrics", listOf("0" to "None", "1" to "One", "3" to "Three"), Settings.inlineLyrics.toString(), last = !inline) { Settings.inlineLyrics = it.toInt() }
        if (inline) SwitchRow("Romanized", Settings.romanInline, last = true) { Settings.romanInline = it }
    }

    Caption("Full lyrics")
    Group {
        SwitchRow("Word-by-word highlight", Settings.lyricsWords) { Settings.lyricsWords = it }
        SwitchRow("Romanized line beneath", Settings.lyricsRoman) { Settings.lyricsRoman = it }
        drop("Alignment", "lyrics_align", listOf("start" to "Left", "center" to "Centre", "end" to "Right"), Settings.lyricsAlign) { Settings.lyricsAlign = it }
        drop("Size", "lyrics_size", listOf("24" to "Small", "30" to "Medium", "36" to "Large"), Settings.lyricsSize.toString(), last = true) { Settings.lyricsSize = it.toInt() }
    }

    // which source's lyrics load first: automatic (the best match for title, artist and length) or the user's order
    Caption("Sources")
    Group {
        val order = Settings.lyricsOrder.split(',').filter { it.isNotBlank() }
        val auto = order.isEmpty()
        fun save(list: List<String>) { Settings.lyricsOrder = list.joinToString(","); com.wetune.Lyrics.clearCache() }
        SwitchRow("Automatic order", auto, last = auto) { on -> if (on) save(emptyList()) else save(com.wetune.Lyrics.Sources) }
        if (!auto) {
            // every source, in the chosen order (sources added later join at the end)
            val list = order.filter { it in com.wetune.Lyrics.Sources } + com.wetune.Lyrics.Sources.filter { it !in order }
            list.forEachIndexed { i, s ->
                GroupRow("${i + 1}. $s", chevron = false, last = i == list.lastIndex, trailing = {
                    Row {
                        OrderButton(Icons.Up, "Move $s up", i > 0) { save(list.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                        OrderButton(Icons.Down, "Move $s down", i < list.lastIndex) { save(list.toMutableList().apply { add(i + 1, removeAt(i)) }) }
                    }
                })
            }
        }
    }
    Note(if (Settings.lyricsOrder.isBlank()) "Automatic loads the version that best matches the song's title, artist and length, word by word and timed lyrics first."
        else "The first source with lyrics that match the song is used; you can still pick another in the lyrics view.")

    Caption("Romanization")
    Group {
        // languages the user reads: never romanized, inline or in the full lyrics
        val keep = Settings.romanKeep.split(',').filter { it.isNotBlank() }.toSet()
        Dropdown(
            "Never romanize", RomanScripts, "", open == "keep", last = true, onToggle = { open = if (open == "keep") null else "keep" },
            isOn = { it in keep },
            summary = if (keep.isEmpty()) "None" else RomanScripts.filter { it.first in keep }.joinToString { it.second.substringBefore(" (").substringBefore(",") },
        ) { k -> Settings.romanKeep = (if (k in keep) keep - k else keep + k).joinToString(",") }
    }
    Note("Romanizing adds Latin letters to lyrics in other scripts, except the languages listed here.")
}

@Composable
internal fun Note(text: String) = Txt(text, Type.sub, Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp), color = WT.c.onSecondary, lines = 3)

@Composable
internal fun SwitchRow(title: String, on: Boolean, last: Boolean = false, set: (Boolean) -> Unit) =
    GroupRow(title, chevron = false, last = last, trailing = { Toggle(on, title) { set(it) } })

/**
 * A settings row showing the current choice; tapping it unfolds the options beneath (chevron turns), picking folds it.
 * The option that is the setting's default ([key]) says so.
 */
@Composable
internal fun Dropdown(
    title: String, options: List<Pair<String, String>>, value: String, open: Boolean, last: Boolean, onToggle: () -> Unit,
    // multi-select: which options are ticked, and the summary shown on the row (picking then toggles, without folding)
    isOn: ((String) -> Boolean)? = null, summary: String? = null,
    key: String? = null,
    onSelect: (String) -> Unit,
) {
    val c = WT.c
    val turn by animateFloatAsState(if (open) 1f else 0f, Motion.small(), label = "chevron")
    val default = key?.let { Settings.defaultOf(it)?.toString() }
    Column(Modifier.fillMaxWidth()) {
        GroupRow(
            title, value = summary ?: options.firstOrNull { it.first == value }?.second, chevron = false, last = last && !open,
            trailing = { Box(Modifier.padding(start = 8.dp).graphicsLayer { rotationZ = 90f + 180f * turn }) { Icon(Icons.Chevron, c.onTertiary, 14.dp) } },
            onClick = onToggle,
        )
        AnimatedVisibility(open, enter = expandVertically(Motion.push()) + fadeIn(Motion.small()), exit = shrinkVertically(Motion.fold()) + fadeOut(Motion.small())) {
            Column(Modifier.fillMaxWidth().background(c.on.copy(alpha = 0.04f))) {
                options.forEachIndexed { i, (v, label) ->
                    GroupRow(
                        label, modifier = Modifier.padding(start = 12.dp), chevron = false, last = last && i == options.lastIndex,
                        value = if (v == default) "Default" else null,
                        trailing = { Box(Modifier.padding(start = 10.dp).width(18.dp)) { if (isOn?.invoke(v) ?: (v == value)) Icon(Icons.Check, c.on, 18.dp) } },
                    ) { onSelect(v) }
                }
            }
        }
    }
}

/** A slider with its value; [mark] (or the default of [key]) is shown as a notch on the track. */
@Composable
private fun SliderRow(title: String, value: Int, range: IntRange, label: (Int) -> String, last: Boolean = false, key: String? = null, mark: Int? = null, onReset: (() -> Unit)? = null, onChange: (Int) -> Unit) {
    val c = WT.c
    val notch = mark ?: key?.let { Settings.defaultOf(it) as? Int }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(title, Type.body, Modifier.weight(1f))
                Txt(label(value), Type.body, color = c.onSecondary)
            }
            Slider(value, range, title, notch, onReset, onChange)
        }
        if (!last) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(c.separator))
    }
}

/** A plain slider: drag or tap anywhere on the track; announces and accepts values for accessibility. [mark]: a notch (the default). */
@Composable
fun Slider(value: Int, range: IntRange, label: String, mark: Int? = null, onReset: (() -> Unit)? = null, onChange: (Int) -> Unit) {
    val c = WT.c
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    // the gesture handlers live across recompositions: read the current value and callbacks, not the first ones
    val latest = androidx.compose.runtime.rememberUpdatedState(value)
    val change = androidx.compose.runtime.rememberUpdatedState(onChange)
    val reset = androidx.compose.runtime.rememberUpdatedState(onReset ?: mark?.let { m -> { onChange(m) } })
    val span = (range.last - range.first).coerceAtLeast(1)
    val frac = (value - range.first).toFloat() / span
    // the finger's spot on the track (the knob's travel, inset by its radius) → a value, pulled onto the default or a
    // step of 5 when close; a light tick when it lands on one
    fun set(x: Float, width: Int) {
        val r = 12.dp.value * density; val travel = (width - 2 * r).coerceAtLeast(1f)
        val raw = range.first + ((x - r) / travel).coerceIn(0f, 1f) * span
        val v = snapValue(raw, range, mark, unitPx = travel / span, markPx = 14.dp.value * density, stepPx = 7.dp.value * density)
        if (v != latest.value) {
            if (v == mark || v % 5 == 0) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            change.value(v)
        }
    }
    Box(
        Modifier.fillMaxWidth().height(32.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat())
                setProgress { v -> onChange(v.roundToInt().coerceIn(range)); true }
            }
            // tap to jump there; double-tap: back to the default
            .pointerInput(range) {
                detectTapGestures(
                    onDoubleTap = { reset.value?.let { r -> haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); r() } },
                    onTap = { set(it.x, size.width) },
                )
            }
            .pointerInput(range) { detectHorizontalDragGestures { ch, _ -> ch.consume(); set(ch.position.x, size.width) } }
            .drawBehind {
                val cy = size.height / 2; val r = 12.dp.toPx(); val track = 4.dp.toPx()
                val x = r + (size.width - 2 * r) * frac
                drawRoundRect(c.surfaceHigh, Offset(0f, cy - track / 2), Size(size.width, track), CornerRadius(track / 2))
                drawRoundRect(c.on, Offset(0f, cy - track / 2), Size(x, track), CornerRadius(track / 2))
                if (mark != null && mark in range) {
                    // the default: a small notch through the track
                    val mx = r + (size.width - 2 * r) * ((mark - range.first).toFloat() / span)
                    val w = 2.dp.toPx(); val h = 12.dp.toPx()
                    drawRoundRect(c.onSecondary, Offset(mx - w / 2, cy - h / 2), Size(w, h), CornerRadius(w / 2))
                }
                drawCircle(Color.Black.copy(alpha = 0.18f), r + 1.dp.toPx(), Offset(x, cy + 1.dp.toPx()))
                drawCircle(Color.White, r, Offset(x, cy))
            },
    )
}

/**
 * A slider's value for raw position [raw]: the default [mark] if within [markPx] of it, else the nearest multiple of 5
 * if within [stepPx], else the nearest whole value ([unitPx] = pixels per unit on the track).
 */
internal fun snapValue(raw: Float, range: IntRange, mark: Int?, unitPx: Float, markPx: Float, stepPx: Float): Int {
    if (mark != null && mark in range && kotlin.math.abs(raw - mark) * unitPx <= markPx) return mark
    val step = ((raw / 5f).roundToInt() * 5).coerceIn(range)
    if (kotlin.math.abs(raw - step) * unitPx <= stepPx) return step
    return raw.roundToInt().coerceIn(range)
}

/** Settings › General › Glass: how frosted surfaces look across the app (navbar, mini player, menus, cards). */
@Composable
fun GlassSettingsBody() {
    Caption("Glass")
    Group {
        SliderRow("Tint", Settings.glassTint, 0..100, { when (it) { 0 -> "Clear"; 100 -> "Solid"; else -> "$it %" } }, key = "glass_tint") { Settings.glassTint = it }
        SliderRow("Blur", Settings.glassBlur, 0..60, { if (it == 0) "None" else "$it dp" }, last = true, key = "glass_blur") { Settings.glassBlur = it }
    }
    Note("Tint is how much colour sits over the blur; less shows more of what's behind. Blur applies to the navbar, the mini player and menus.")
}

/**
 * The card's tint as a row of colour circles: Frosted (the navbar's glass), Black, White, and the playing cover's
 * Accent, Dominant and Neutral tones (shown in their real colours). The chosen one has a ring.
 */
@Composable
private fun TintPicker() {
    val c = WT.c
    val tones = coverTones(coverColors(com.wetune.Playback.current?.art))
    val grey = Color(0xFF8E8E93)
    val options = listOf(
        Triple("auto", "Frosted", null as Color?),
        Triple("black", "Black", Color.Black), Triple("white", "White", Color.White),
        Triple("accent", "Accent", tones?.first ?: grey), Triple("dominant", "Dominant", tones?.second ?: grey), Triple("neutral", "Neutral", tones?.third ?: grey),
    )
    val chosen = cardTintKey()
    val noise = remember { androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(com.wetune.ui.grain, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated)) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            options.forEach { (key, label, color) ->
                val on = key == chosen
                Column(
                    Modifier.width(52.dp).press("$label tint" + if (on) ", chosen" else "", scale = 0.92f) { Settings.npCardTint = key },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(44.dp).border(2.dp, if (on) c.on else Color.Transparent, CircleShape).padding(4.dp).clip(CircleShape)
                            .drawBehind {
                                if (color == null) { // frosted: the frost colour with its grain
                                    drawRect(Brush.linearGradient(listOf(c.frost.copy(alpha = 0.9f), c.on.copy(alpha = 0.25f))))
                                    drawRect(noise, alpha = 0.7f)
                                } else drawRect(color)
                            }
                            .border(0.5.dp, c.separator, CircleShape),
                    )
                    Spacer(Modifier.height(4.dp))
                    Txt(label, Type.caption, color = if (on) c.on else c.onSecondary)
                }
            }
        }
        Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(c.separator))
    }
}

/** A small up / down arrow for reordering a list row; dimmed and inert at the ends. */
@Composable
private fun OrderButton(ic: com.wetune.ui.Ic, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = WT.c
    Box(
        Modifier.size(40.dp).graphicsLayer { alpha = if (enabled) 1f else 0.3f }
            .then(if (enabled) Modifier.press(label, scale = 0.85f, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Icon(ic, c.on, 16.dp) }
}
