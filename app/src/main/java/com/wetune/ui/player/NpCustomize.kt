package com.wetune.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wetune.Playback
import com.wetune.rememberPosition
import com.wetune.sources.Track
import com.wetune.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Customize player: a live preview (the real player, scaled down) over tabs that edit a draft style. Nothing is saved
// until Apply.

private val TestCovers = listOf("This song", "White", "Black", "Busy", "Saturated")

@Composable
fun NpCustomize(onClose: () -> Unit) {
    val applied = NpStyleStore.style
    var draft by remember { mutableStateOf(applied) }
    var tab by remember { mutableIntStateOf(0) }
    var test by remember { mutableIntStateOf(0) } // 0: the song's own cover, else a worst-case test cover
    val clipboard = LocalClipboardManager.current
    BackHandler { onClose() }
    val track = Playback.current ?: Track("local", "preview", "Blinding Lights", "The Weeknd", "After Hours")
    val testArt by produceState<Pair<android.graphics.Bitmap, ArtData>?>(null, test) {
        value = if (test == 0) null else withContext(Dispatchers.Default) { testCover(test - 1).let { it to artData(it) } }
    }
    val art = testArt?.second ?: NowColors.art
    val pos = rememberPosition()
    val ui = PlayerUi(track, art?.palette ?: SunsetPalette, pos, "art", {}, {}, {}, {}, {}, art = art, preview = true, testArt = testArt?.first)
    val colors = remember(draft, art) { resolveColors(draft, art) }
    val c = WT.c

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.Back, "Back", onClose, size = 48.dp)
            Txt("Customize", Modifier.weight(1f).padding(start = 4.dp), style = Type.headline, maxLines = 1)
            Pill("Copy style", false, {
                clipboard.setText(AnnotatedString(draft.toJson()))
                toast("Style copied")
            })
            Spacer(Modifier.width(8.dp))
            Pill("Paste style", false, {
                val pasted = NowPlayingStyle.fromJson(clipboard.getText()?.text)
                if (pasted == null) toast("The clipboard doesn't hold a player style") else { draft = pasted; toast("Style pasted. Apply to keep it") }
            })
        }
        // live preview: the real player for the draft, at phone size, scaled down
        BoxWithConstraints(Modifier.fillMaxWidth().weight(0.95f).padding(vertical = 8.dp), Alignment.Center) {
            val phoneW = 390.dp; val phoneH = 844.dp
            val scale = minOf(maxHeight / phoneH, maxWidth / phoneW)
            Scaled(phoneW, phoneH, scale, Modifier.clip(RoundedCornerShape(24.dp)).border(1.dp, c.outline, RoundedCornerShape(24.dp))) {
                GlassPlayer(ui, draft)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("LIVE PREVIEW · ${NowPlayingStyle.nameOf(draft).uppercase()}${if (draft != applied) " · NOT APPLIED" else ""}", style = Type.overline, color = c.muted, maxLines = 1)
                Txt(
                    "Text %.1f:1 · Icons %.1f:1 · passes AA".format(colors.textContrast, colors.iconContrast),
                    style = Type.caption.copy(fontFeatureSettings = "tnum"), maxLines = 1,
                )
            }
            Pill("Cover: ${TestCovers[test]}", test != 0, { test = (test + 1) % TestCovers.size })
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), Arrangement.spacedBy(8.dp)) {
            listOf("Presets", "Background", "Cover", "Controls", "Colours", "General").forEachIndexed { i, label -> Pill(label, tab == i, { tab = i }) }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            when (tab) {
                0 -> PresetsTab(draft, ui) { draft = it }
                1 -> BackgroundTab(draft, colors) { draft = it }
                2 -> CoverTab(draft) { draft = it }
                3 -> ControlsTab(draft) { draft = it }
                4 -> ColoursTab(draft) { draft = it }
                else -> GeneralTab(draft) { draft = it }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
            Btn("Reset", { draft = applied }, kind = BtnKind.Secondary, enabled = draft != applied)
            Btn("Apply", { NpStyleStore.apply(draft); onClose() }, Modifier.weight(1f))
        }
    }
}

/**
 * [content] laid out at [w]×[h] and drawn at [scale]; touches don't reach it (it's a picture of the player). The
 * cover on top only takes the hit without consuming anything, so a clickable or scrolling parent still gets the tap.
 */
@Composable
private fun Scaled(w: Dp, h: Dp, scale: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.layout { m, _ ->
            val p = m.measure(Constraints.fixed(w.roundToPx(), h.roundToPx()))
            layout((p.width * scale).toInt(), (p.height * scale).toInt()) {
                p.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
            }
        }.clearAndSetSemantics { contentDescription = "Live preview of the player" },
    ) {
        content()
        Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
    }
}


@Composable
private fun PresetsTab(draft: NowPlayingStyle, ui: PlayerUi, set: (NowPlayingStyle) -> Unit) {
    Txt("Start from a look, then fine-tune it in the other tabs.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = Type.caption, color = WT.c.muted)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(NowPlayingStyle.Presets, key = { it.first }) { (name, style) ->
            val on = draft == style
            val ring by androidx.compose.animation.animateColorAsState(if (on) WT.c.accent else WT.c.outline, label = "presetRing")
            // the whole tile (preview + name) is one radio button
            Column(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .semantics(mergeDescendants = true) { selected = on }
                    .pressable(scale = 0.97f, role = Role.RadioButton) { set(style) }
                    .border(if (on) 2.dp else 1.dp, ring, RoundedCornerShape(16.dp)).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Scaled(390.dp, 844.dp, 0.3f, Modifier.clip(RoundedCornerShape(12.dp))) { GlassPlayer(ui, style) }
                    if (on) Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(WT.c.accent),
                        Alignment.Center,
                    ) { Icon(Ic.Check, null, size = 14.dp, tint = Color(onFill(WT.c.accent.toArgb()))) }
                }
                Txt(name, Modifier.padding(top = 6.dp).semantics { contentDescription = "$name preset" }, style = Type.label, weight = if (on) FontWeight.SemiBold else null)
            }
        }
    }
}

// Every tab reads top to bottom: what kind → only the options that kind has → the options every kind shares.

@Composable
private fun BackgroundTab(d: NowPlayingStyle, colors: NpColors, set: (NowPlayingStyle) -> Unit) {
    val b = d.background
    fun up(f: BackgroundStyle.() -> BackgroundStyle) = set(d.copy(background = b.f()))
    Section("Type", first = true)
    Chips(BgKind.entries, b.kind, { it.label }) { up { copy(kind = it) } }
    Section(b.kind.label)
    when (b.kind) {
        BgKind.Blur -> {
            EdSlider("Blur", "${b.blur}", b.blur / 100f) { up { copy(blur = (it * 100).toInt()) } }
            EdSlider("Zoom", "%.2f×".format(b.zoom), b.zoom - 1f) { up { copy(zoom = 1f + it) } }
        }
        BgKind.Mesh -> EdSlider("Drift", "${(b.motion * 100).toInt()}%", b.motion) { up { copy(motion = it) } }
        BgKind.Extend -> {
            EdSlider("Softness", "${b.blur}", b.blur / 100f) { up { copy(blur = (it * 100).toInt()) } }
            EdToggle("Reflection", "Mirror the cover above and below instead of stretching its edges", b.reflection) { up { copy(reflection = it) } }
        }
        BgKind.Solid -> {
            Label("Tint")
            Chips(SolidTint.entries, b.tint, { it.label }) { up { copy(tint = it) } }
        }
    }
    Section("Finish")
    // the dim starts at what this cover needs for readable text and can only add to it
    val shown = maxOf(b.dim, colors.autoDim)
    EdSlider("Dim", "${(shown * 100).toInt()}%", shown / 0.7f) { up { copy(dim = (it * 0.7f).coerceAtLeast(colors.autoDim)) } }
    Note("Starts at the automatic minimum for this cover (${(colors.autoDim * 100).toInt()}%), so text stays readable; you can only add to it.")
    EdSlider("Grain", "${(b.grain / 0.3f * 100).toInt()}%", b.grain / 0.3f) { up { copy(grain = it * 0.3f) } }
    Label("Texture")
    Chips(Texture.entries, b.texture, { it.label }) { up { copy(texture = it) } }
    if (b.texture == Texture.Ribbed) EdSlider("Rib width", "${b.ribs} dp", (b.ribs - 8) / 32f) { up { copy(ribs = 8 + (it * 32).toInt()) } }
}

@Composable
private fun CoverTab(d: NowPlayingStyle, set: (NowPlayingStyle) -> Unit) {
    val cv = d.cover
    fun up(f: CoverStyle.() -> CoverStyle) = set(d.copy(cover = cv.f()))
    Section("Style", first = true)
    Chips(CoverKind.entries, cv.kind, { it.label }) { up { copy(kind = it) } }
    when (cv.kind) {
        CoverKind.FullWidth -> {
            Section(cv.kind.label)
            EdSlider("Crop", "${(cv.crop * 100).toInt()}%", cv.crop / 0.2f) { up { copy(crop = it * 0.2f) } }
            EdSlider("Edge fade", "${(cv.feather * 100).toInt()}%", cv.feather / 0.4f) { up { copy(feather = it * 0.4f) } }
        }
        CoverKind.Disc -> {
            Section(cv.kind.label)
            EdToggle("Progress ring", "A ring around the disc that fills as the song plays", cv.ring) { up { copy(ring = it) } }
            if (cv.ring) EdSlider("Ring width", "${cv.ringWidth} dp", (cv.ringWidth - 1) / 7f) { up { copy(ringWidth = 1 + (it * 7).toInt()) } }
            EdToggle("Spin", "Turns slowly while playing", cv.spin) { up { copy(spin = it) } }
        }
        CoverKind.Vinyl -> {
            Section(cv.kind.label)
            EdToggle("Spin", "Turns slowly while playing", cv.spin) { up { copy(spin = it) } }
        }
        else -> Unit // Card, Pager and Motion art have nothing of their own
    }
    Section("Shape & shadow")
    if (cv.kind != CoverKind.FullWidth) EdSlider("Size", "${(cv.size * 100).toInt()}%", (cv.size - 0.6f) / 0.4f) { up { copy(size = 0.6f + it * 0.4f) } }
    if (cv.kind in setOf(CoverKind.Card, CoverKind.Pager, CoverKind.Motion)) EdSlider("Corner", "${cv.corner}", cv.corner / 48f) { up { copy(corner = (it * 48).toInt()) } }
    if (cv.kind != CoverKind.FullWidth && cv.kind != CoverKind.Vinyl) {
        Label("Shadow")
        Chips(CoverShadow.entries, cv.shadow, { it.label }) { up { copy(shadow = it) } }
    }
    EdToggle("Breathe", "The cover shrinks a little while paused", cv.breathe) { up { copy(breathe = it) } }
    val m = d.motion
    fun upM(f: MotionStyle.() -> MotionStyle) = set(d.copy(motion = m.f()))
    EdToggle("Depth effect", "Tilt your phone: the background shifts behind the cover and the edges sink into shadow", m.depth) { upM { copy(depth = it) } }
    if (m.depth) {
        EdSlider("Depth intensity", "${m.depthIntensity}", m.depthIntensity / 100f) { upM { copy(depthIntensity = (it * 20).toInt() * 5) } }
        EdToggle("Invert direction", "The background and cover move the other way", m.depthInvert) { upM { copy(depthInvert = it) } }
    }
}

@Composable
private fun ControlsTab(d: NowPlayingStyle, set: (NowPlayingStyle) -> Unit) {
    val k = d.controls
    fun up(f: ControlsStyle.() -> ControlsStyle) = set(d.copy(controls = k.f()))
    Section("Layout", first = true)
    Chips(ControlsKind.entries, k.kind, { it.label }) { up { copy(kind = it) } }
    Note(
        when (k.kind) {
            ControlsKind.Classic -> "Title, seek bar, transport and the action row."
            ControlsKind.Expressive -> "A big title and a wide play button between half-pill skip buttons; shuffle and repeat bracket the action row."
            ControlsKind.Dock -> "The transport sits in a glass dock, with the output and audio quality under the action row."
            ControlsKind.Minimal -> "The play button beside the title; skip buttons and the options menu in one row."
            ControlsKind.Waveform -> "The song after this one shows above the action row."
        },
    )
    Section("Play button")
    Label("Shape")
    Chips(PlayShape.entries, k.playShape, { it.label }) { up { copy(playShape = it) } }
    Label("Colour")
    Chips(PlayFill.entries, k.playFill, { it.label }) { up { copy(playFill = it) } }
    EdSlider("Size", "${k.playSize} dp", (k.playSize - 56) / 40f) { up { copy(playSize = 56 + (it * 40).toInt()) } }
    Section("Progress")
    Chips(Progress.entries, k.progress, { it.label }) { up { copy(progress = it) } }
    Section("Action row")
    ActionList(k.actions) { up { copy(actions = it) } }
    Label("Show")
    Chips(listOf(false, true), k.actionLabels, { if (it) "Icons and labels" else "Icons only" }) { up { copy(actionLabels = it) } }
    Label("Arrangement")
    Chips(ActionArrange.entries, k.actionArrange, { it.label }) { up { copy(actionArrange = it) } }
    Note(
        when (k.actionArrange) {
            ActionArrange.Spread -> "The first and last in the corners, the rest centred between them."
            ActionArrange.Centred -> "All together in the middle."
            ActionArrange.Even -> "Evenly spaced across the row."
        },
    )
    Section("Extras")
    EdToggle("Shuffle and repeat", null, k.shuffleRepeat) { up { copy(shuffleRepeat = it) } }
    EdToggle("Volume", "The phone's volume under the controls", k.volume) { up { copy(volume = it) } }
}

@Composable
private fun ColoursTab(d: NowPlayingStyle, set: (NowPlayingStyle) -> Unit) {
    val co = d.colors
    Section("Accent", first = true)
    Chips(ColorSource.entries, co.source, { it.label }) { set(d.copy(colors = co.copy(source = it))) }
    if (co.source == ColorSource.Custom) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).size(28.dp).clip(RoundedCornerShape(14.dp)).background(hueAccent(co.customHue.toFloat(), dark = true)))
        EdSlider("Hue", "${co.customHue}°", co.customHue / 359f) { set(d.copy(colors = co.copy(customHue = (it * 359).toInt()))) }
    }
    Note("The accent is lightened when needed so it always stands out (3:1) from the background behind it.")
}

@Composable
private fun GeneralTab(d: NowPlayingStyle, set: (NowPlayingStyle) -> Unit) {
    val l = d.layout
    fun upL(f: LayoutStyle.() -> LayoutStyle) = set(d.copy(layout = l.f()))
    Section("Screen", first = true)
    EdToggle("\"Playing from\"", "What the queue was started from, at the top. Swipe down or Back closes the player.", l.header) { upL { copy(header = it) } }
    EdToggle("Hide the status bar", "The clock and notifications step away while Now Playing is open", l.hideStatus) { upL { copy(hideStatus = it) } }
    Section("Lyric line")
    Chips(InlineLyrics.entries, l.lyrics, { it.label }) { upL { copy(lyrics = it) } }
    Section("Motion")
    EdToggle("Ambient motion", "Mesh drift, spinning discs and motion art. Always still when Android's \"Remove animations\" is on.", d.motion.ambient) { set(d.copy(motion = d.motion.copy(ambient = it))) }
    Note("Swipe the cover sideways to skip; swipe down to close.")
}

private fun NpAction.icon(): ImageVector = when (this) {
    NpAction.Lyrics -> Ic.LyricsQuote
    NpAction.Queue -> Ic.QueueList
    NpAction.Like -> Ic.HeartOutline
    NpAction.Sleep -> Ic.Moon
    NpAction.Output -> Ic.Output
    NpAction.Playlist -> Ic.AddPlaylist
    NpAction.Download -> Ic.Download
    NpAction.Audio -> Ic.Tune
}

/**
 * The action row's buttons in order: drag a row by its handle, or use the up / down buttons (TalkBack, switch
 * access); × takes one out. The ones not in the row are listed under it, tap to add.
 */
@Composable
private fun ActionList(order: List<NpAction>, set: (List<NpAction>) -> Unit) {
    val c = WT.c
    val put by rememberUpdatedState(set)
    // the order as of the last move, so moves in one frame (before the list recomposes) build on each other
    val live = remember { arrayOf(order) }
    live[0] = order
    fun move(a: NpAction, by: Int) {
        val l = live[0]
        val i = l.indexOf(a)
        if (i < 0 || i + by !in l.indices) return
        live[0] = l.toMutableList().apply { add(i + by, removeAt(i)) }
        put(live[0])
    }
    var dragging by remember { mutableStateOf<NpAction?>(null) }
    var dy by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 56.dp.toPx() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        order.forEachIndexed { i, a ->
            key(a) {
                val lifted = dragging == a
                Row(
                    Modifier.fillMaxWidth().height(56.dp).zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { translationY = if (lifted) dy else 0f }
                        .clip(RoundedCornerShape(14.dp)).background(if (lifted) c.text.copy(alpha = 0.08f) else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(48.dp).semantics { contentDescription = "Drag to reorder ${a.title}" }.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { dragging = a; dy = 0f },
                                onDragEnd = { dragging = null; dy = 0f },
                                onDragCancel = { dragging = null; dy = 0f },
                            ) { change, drag ->
                                change.consume()
                                dy += drag.y
                                // past half a row: the row takes the next slot and the finger stays on it
                                if (dy > rowPx / 2 && live[0].indexOf(a) < live[0].lastIndex) { move(a, 1); dy -= rowPx }
                                else if (dy < -rowPx / 2 && live[0].indexOf(a) > 0) { move(a, -1); dy += rowPx }
                            }
                        },
                        Alignment.Center,
                    ) { Icon(Ic.Grip, null, size = 18.dp, tint = c.muted) }
                    Icon(a.icon(), null, size = 20.dp, tint = c.text)
                    Txt(a.title, Modifier.weight(1f).padding(start = 12.dp), style = Type.body, maxLines = 1)
                    IconBtn(Ic.ChevronUp, "Move ${a.title} up", { move(a, -1) }, size = 48.dp, iconSize = 20.dp, enabled = i > 0)
                    IconBtn(Ic.Down, "Move ${a.title} down", { move(a, 1) }, size = 48.dp, iconSize = 22.dp, enabled = i < order.lastIndex)
                    IconBtn(Ic.Close, "Remove ${a.title} from the row", { put(order - a) }, size = 48.dp, iconSize = 18.dp)
                }
            }
        }
        val rest = NpAction.entries.filter { it !in order }
        if (order.isEmpty()) Note("No buttons: the row is hidden.")
        rest.forEach { a ->
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
                    .semantics(mergeDescendants = true) { contentDescription = "Add ${a.title} to the row" }
                    .pressable(scale = 0.98f) { put(order + a) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp), Alignment.Center) { Icon(Ic.Add, null, size = 18.dp, tint = c.muted) }
                Icon(a.icon(), null, size = 20.dp, tint = c.muted)
                Txt(a.title, Modifier.weight(1f).padding(start = 12.dp), style = Type.body, color = c.muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Segmented(options.map { it.toString() to label(it) }, selected.toString(), { k -> options.first { it.toString() == k }.let(onSelect) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/** A group's header: the same overline everywhere, with room above it to set groups apart. */
@Composable
private fun Section(title: String, first: Boolean = false) =
    Txt(title.uppercase(), Modifier.padding(start = 20.dp, end = 20.dp, top = if (first) 4.dp else 24.dp, bottom = 2.dp).semantics { heading() }, style = Type.overline, color = WT.c.text)

/** A label for the chips under it, inside a group. */
@Composable
private fun Label(text: String) = Txt(text, Modifier.padding(start = 20.dp, top = 10.dp), style = Type.label, color = WT.c.muted)

@Composable
private fun Note(text: String) = Txt(text, Modifier.padding(horizontal = 20.dp, vertical = 2.dp), style = Type.caption.copy(fontSize = 12.sp), color = WT.c.muted)

@Composable
private fun EdSlider(title: String, value: String, fraction: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(title, Modifier.weight(1f), style = Type.body)
            Txt(value, style = Type.caption.copy(fontFeatureSettings = "tnum"), color = WT.c.muted)
        }
        SeekSlider(fraction.coerceIn(0f, 1f), { onChange(it.coerceIn(0f, 1f)) }, {}, Modifier.fillMaxWidth().padding(top = 4.dp), color = WT.c.accent, thumb = true)
    }
}

@Composable
private fun EdToggle(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) =
    Box(Modifier.padding(horizontal = 4.dp)) { ToggleRow(title, detail, checked, onChange) }
