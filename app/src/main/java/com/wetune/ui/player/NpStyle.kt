package com.wetune.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

// Now Playing 2.0: the whole look of the player as one typed, immutable value. Presets are just values of it; the
// Customize editor edits a draft copy; the player and the editor's preview draw from it, never from global settings.
// Stored as one JSON string (Settings.npStyle), which is also the Copy / Paste format.

enum class BgKind(val label: String) { Blur("Soft blur"), Mesh("Mesh"), Extend("Extend"), Solid("Solid") }
enum class Texture(val label: String) { None("None"), Ribbed("Ribbed glass") }
enum class SolidTint(val label: String) { Deep("Deep tint"), Black("Black") }
enum class CoverKind(val label: String) { Card("Card"), FullWidth("Full width"), Pager("Pager"), Disc("Disc"), Vinyl("Vinyl"), Motion("Motion art") }
enum class CoverShadow(val label: String) { None("None"), Soft("Soft"), Tinted("Tinted") }
enum class ControlsKind(val label: String) { Classic("Classic"), Expressive("Expressive"), Dock("Dock"), Minimal("Minimal"), Waveform("Waveform") }
enum class PlayShape(val label: String) { Circle("Circle"), Pill("Pill"), Squircle("Squircle"), Morph("Morph") }
enum class PlayFill(val label: String) { Accent("Accent"), White("White"), Glass("Glass") }
enum class Progress(val label: String) { Line("Line"), Thick("Thick"), Wavy("Wavy"), Bars("Bars") }
enum class InlineLyrics(val label: String) { Off("Off"), Line("One line"), Stack("Three lines") }
enum class ColorSource(val label: String) { CoverHue("Cover"), Vibrant("Vibrant"), Muted("Muted"), Mono("Monochrome"), Custom("Custom") }
/** A button in the action row under the controls; [label] is the short one shown in the row, [title] the editor's. */
enum class NpAction(val label: String, val title: String) {
    Lyrics("Lyrics", "Lyrics"), Queue("Queue", "Queue"), Like("Like", "Like (heart)"), Sleep("Sleep", "Sleep timer"),
    Output("Output", "Output device"), Playlist("Playlist", "Add to playlist"), Download("Download", "Download"), Audio("Audio", "Audio options");
    companion object { val Defaults = listOf(Lyrics, Queue, Like, Sleep, Output) }
}
/** Spread: the first and last in the corners, the rest centred between them; Centred: all together; Even: evenly spaced. */
enum class ActionArrange(val label: String) { Spread("Spread"), Centred("Centred"), Even("Even") }

@Immutable
data class BackgroundStyle(
    val kind: BgKind = BgKind.Blur,
    val blur: Int = 60, // 0..100: how soft (Blur, Extend)
    val zoom: Float = 1.4f, // 1..2: Blur
    val dim: Float = 0.25f, // 0..0.7, on top of the automatic contrast minimum (never below it)
    val motion: Float = 0.3f, // 0..1: Mesh drift speed
    val grain: Float = 0f, // 0..0.3
    val texture: Texture = Texture.None,
    val ribs: Int = 14, // 8..40 dp: ribbed glass flute width
    val reflection: Boolean = false, // Extend: mirror the cover instead of stretching its edges
    val tint: SolidTint = SolidTint.Deep,
)

@Immutable
data class CoverStyle(
    val kind: CoverKind = CoverKind.Card,
    val size: Float = 0.86f, // 0.6..1 of the screen width
    val corner: Int = 22, // 0..48 dp
    val shadow: CoverShadow = CoverShadow.Tinted,
    val ring: Boolean = true, // Disc: progress ring
    val ringWidth: Int = 3, // 1..8 dp
    val spin: Boolean = false, // Disc, Vinyl
    val breathe: Boolean = true, // shrinks a little while paused
    val crop: Float = 0.06f, // 0..0.2: Full width side crop
    val feather: Float = 0.12f, // 0..0.4: Full width top/bottom fade
)

@Immutable
data class ControlsStyle(
    val kind: ControlsKind = ControlsKind.Classic,
    val playShape: PlayShape = PlayShape.Circle,
    val playFill: PlayFill = PlayFill.Accent,
    val playSize: Int = 72, // 56..96 dp
    val progress: Progress = Progress.Line,
    val shuffleRepeat: Boolean = true,
    val volume: Boolean = true,
    val actions: List<NpAction> = NpAction.Defaults, // the action row, in order
    val actionLabels: Boolean = false, // icon + label instead of icon only
    val actionArrange: ActionArrange = ActionArrange.Spread,
)

@Immutable
data class LayoutStyle(
    val header: Boolean = true, // "Playing from" at the top
    val hideStatus: Boolean = true,
    val lyrics: InlineLyrics = InlineLyrics.Line,
)

@Immutable
data class ColorStyle(val source: ColorSource = ColorSource.CoverHue, val customHue: Int = 0) // customHue 0..359

@Immutable
data class MotionStyle(
    val ambient: Boolean = true, // mesh drift, spin, motion art; always off with the system's "Remove animations"
    val depth: Boolean = false,
    val depthIntensity: Int = 50, // 0..100
    val depthInvert: Boolean = false,
)

@Immutable
data class NowPlayingStyle(
    val background: BackgroundStyle = BackgroundStyle(),
    val cover: CoverStyle = CoverStyle(),
    val controls: ControlsStyle = ControlsStyle(),
    val layout: LayoutStyle = LayoutStyle(),
    val colors: ColorStyle = ColorStyle(),
    val motion: MotionStyle = MotionStyle(),
) {
    /** Every number within its range (styles come from the clipboard too). */
    fun clamped() = copy(
        background = background.copy(
            blur = background.blur.coerceIn(0, 100), zoom = background.zoom.coerceIn(1f, 2f), dim = background.dim.coerceIn(0f, 0.7f),
            motion = background.motion.coerceIn(0f, 1f), grain = background.grain.coerceIn(0f, 0.3f), ribs = background.ribs.coerceIn(8, 40),
        ),
        cover = cover.copy(
            size = cover.size.coerceIn(0.6f, 1f), corner = cover.corner.coerceIn(0, 48), ringWidth = cover.ringWidth.coerceIn(1, 8),
            crop = cover.crop.coerceIn(0f, 0.2f), feather = cover.feather.coerceIn(0f, 0.4f),
        ),
        controls = controls.copy(playSize = controls.playSize.coerceIn(56, 96)),
        colors = colors.copy(customHue = colors.customHue.coerceIn(0, 359)),
        motion = motion.copy(depthIntensity = motion.depthIntensity.coerceIn(0, 100)),
    )

    fun toJson(): String = JSONObject()
        .put("version", VERSION)
        .put("background", JSONObject().put("kind", background.kind.name).put("blur", background.blur).put("zoom", background.zoom.toDouble())
            .put("dim", background.dim.toDouble()).put("motion", background.motion.toDouble()).put("grain", background.grain.toDouble())
            .put("texture", background.texture.name).put("ribs", background.ribs).put("reflection", background.reflection).put("tint", background.tint.name))
        .put("cover", JSONObject().put("kind", cover.kind.name).put("size", cover.size.toDouble()).put("corner", cover.corner).put("shadow", cover.shadow.name)
            .put("ring", cover.ring).put("ringWidth", cover.ringWidth).put("spin", cover.spin).put("breathe", cover.breathe)
            .put("crop", cover.crop.toDouble()).put("feather", cover.feather.toDouble()))
        .put("controls", JSONObject().put("kind", controls.kind.name).put("playShape", controls.playShape.name).put("playFill", controls.playFill.name)
            .put("playSize", controls.playSize).put("progress", controls.progress.name).put("shuffleRepeat", controls.shuffleRepeat)
            .put("volume", controls.volume).put("actions", org.json.JSONArray(controls.actions.map { it.name })).put("actionLabels", controls.actionLabels)
            .put("actionArrange", controls.actionArrange.name))
        .put("layout", JSONObject().put("header", layout.header).put("hideStatus", layout.hideStatus).put("lyrics", layout.lyrics.name))
        .put("colors", JSONObject().put("source", colors.source.name).put("customHue", colors.customHue))
        .put("motion", JSONObject().put("ambient", motion.ambient).put("depth", motion.depth).put("depthIntensity", motion.depthIntensity).put("depthInvert", motion.depthInvert))
        .toString()

    companion object {
        /** 2: the action row (actions, actionLabels, actionArrange) replaced like / sleep / output; the status bar is hidden by default. */
        const val VERSION = 2

        /**
         * A style from JSON, clamped. Missing fields and unknown values (a newer app's style) fall back to the defaults;
         * null when it isn't a style at all.
         */
        fun fromJson(json: String?): NowPlayingStyle? {
            val o = runCatching { JSONObject(json?.trim().orEmpty()) }.getOrNull() ?: return null
            if (listOf("background", "cover", "controls", "layout", "colors", "motion").none { o.optJSONObject(it) != null }) return null
            val d = NowPlayingStyle()
            fun obj(k: String) = o.optJSONObject(k) ?: JSONObject()
            val version = o.optInt("version", 1)
            val b = obj("background"); val c = obj("cover"); val k = obj("controls"); val l = obj("layout"); val co = obj("colors"); val m = obj("motion")
            return NowPlayingStyle(
                BackgroundStyle(
                    b.enum("kind", d.background.kind), b.optInt("blur", d.background.blur), b.float("zoom", d.background.zoom), b.float("dim", d.background.dim),
                    b.float("motion", d.background.motion), b.float("grain", d.background.grain), b.enum("texture", d.background.texture),
                    b.optInt("ribs", d.background.ribs), b.optBoolean("reflection", d.background.reflection), b.enum("tint", d.background.tint),
                ),
                CoverStyle(
                    c.enum("kind", d.cover.kind), c.float("size", d.cover.size), c.optInt("corner", d.cover.corner), c.enum("shadow", d.cover.shadow),
                    c.optBoolean("ring", d.cover.ring), c.optInt("ringWidth", d.cover.ringWidth), c.optBoolean("spin", d.cover.spin),
                    c.optBoolean("breathe", d.cover.breathe), c.float("crop", d.cover.crop), c.float("feather", d.cover.feather),
                ),
                ControlsStyle(
                    k.enum("kind", d.controls.kind), k.enum("playShape", d.controls.playShape), k.enum("playFill", d.controls.playFill),
                    k.optInt("playSize", d.controls.playSize), k.enum("progress", d.controls.progress), k.optBoolean("shuffleRepeat", d.controls.shuffleRepeat),
                    k.optBoolean("volume", d.controls.volume),
                    k.optJSONArray("actions")?.let { a -> (0 until a.length()).mapNotNull { i -> NpAction.entries.firstOrNull { it.name.equals(a.optString(i), true) } }.distinct() }
                        ?: d.controls.actions,
                    k.optBoolean("actionLabels", d.controls.actionLabels), k.enum("actionArrange", d.controls.actionArrange),
                ),
                // version 1 saved every preset with the status bar showing (its default was wrong): hidden again
                LayoutStyle(l.optBoolean("header", d.layout.header), version < 2 || l.optBoolean("hideStatus", d.layout.hideStatus), l.enum("lyrics", d.layout.lyrics)),
                ColorStyle(co.enum("source", d.colors.source), co.optInt("customHue", d.colors.customHue)),
                MotionStyle(m.optBoolean("ambient", d.motion.ambient), m.optBoolean("depth", d.motion.depth), m.optInt("depthIntensity", d.motion.depthIntensity), m.optBoolean("depthInvert", d.motion.depthInvert)),
            ).clamped()
        }

        /**
         * The closest style to the pre-2.0 settings (np_cover, np_fill, np_layout, …); [get] reads a raw preference
         * (null when never set, so the old defaults apply).
         */
        fun fromLegacy(get: (String) -> Any?): NowPlayingStyle {
            fun str(k: String, def: String) = get(k) as? String ?: def
            fun bool(k: String, def: Boolean) = get(k) as? Boolean ?: def
            fun int(k: String, def: Int) = (get(k) as? Number)?.toInt() ?: def
            val coverKind = when (str("np_cover", "edge")) { "card", "strips" -> CoverKind.Card; "disc" -> CoverKind.Disc; "vinyl" -> CoverKind.Vinyl; else -> CoverKind.FullWidth }
            val bg = when (str("np_fill", "stretch")) {
                "mirror" -> BackgroundStyle(BgKind.Extend, blur = 14, dim = 0.18f, grain = 0.08f, reflection = true)
                "blur" -> BackgroundStyle(BgKind.Blur, blur = 70, zoom = 1.6f, dim = 0.2f)
                "fade" -> BackgroundStyle(BgKind.Blur, blur = 50, zoom = 1.4f, dim = 0.4f)
                "ribbed" -> BackgroundStyle(BgKind.Blur, blur = 55, zoom = 1.6f, dim = 0.3f, texture = Texture.Ribbed, ribs = 20)
                "black" -> BackgroundStyle(BgKind.Solid, tint = SolidTint.Black, dim = 0f)
                else -> BackgroundStyle(BgKind.Extend, blur = 18, dim = 0.18f, grain = 0.1f)
            }
            val controls = when (str("np_layout", "classic")) {
                "dock" -> ControlsStyle(ControlsKind.Dock)
                "bigtitle" -> ControlsStyle(ControlsKind.Expressive, playShape = PlayShape.Pill, progress = Progress.Thick)
                "wave" -> ControlsStyle(ControlsKind.Waveform, progress = Progress.Bars)
                "beside" -> ControlsStyle(ControlsKind.Minimal, playSize = 64)
                else -> ControlsStyle(ControlsKind.Classic)
            }.copy(volume = bool("show_volume", true))
            val lyrics = if (!bool("show_inline", true)) InlineLyrics.Off else if (str("inline_style", "line") == "stack") InlineLyrics.Stack else InlineLyrics.Line
            return NowPlayingStyle(
                background = bg,
                cover = CoverStyle(coverKind, size = if (coverKind == CoverKind.Card) 0.88f else 0.8f, spin = coverKind == CoverKind.Vinyl, shadow = CoverShadow.Soft),
                controls = controls,
                layout = LayoutStyle(header = bool("np_header", true), hideStatus = bool("np_hide_status", true), lyrics = lyrics),
                motion = MotionStyle(depth = bool("np_depth", false), depthIntensity = int("depth_intensity", 50), depthInvert = bool("depth_invert", false)),
            ).clamped()
        }

        val Studio = NowPlayingStyle(
            BackgroundStyle(BgKind.Blur, blur = 60, zoom = 1.4f, dim = 0.25f),
            CoverStyle(CoverKind.Pager, size = 0.86f, corner = 22, shadow = CoverShadow.Tinted),
            ControlsStyle(ControlsKind.Classic, progress = Progress.Line),
        )
        val Edge = NowPlayingStyle(
            BackgroundStyle(BgKind.Extend, blur = 14, dim = 0.18f, grain = 0.1f),
            CoverStyle(CoverKind.FullWidth, size = 1f, corner = 0, shadow = CoverShadow.None, crop = 0.04f),
            ControlsStyle(ControlsKind.Classic, progress = Progress.Line),
        )
        val Aurora = NowPlayingStyle(
            BackgroundStyle(BgKind.Mesh, dim = 0.12f, motion = 0.3f),
            CoverStyle(CoverKind.Card, size = 0.8f, corner = 28, shadow = CoverShadow.Tinted),
            ControlsStyle(ControlsKind.Expressive, playShape = PlayShape.Morph, playSize = 72, progress = Progress.Wavy, volume = false),
            colors = ColorStyle(ColorSource.Vibrant),
        )
        val Night = NowPlayingStyle(
            BackgroundStyle(BgKind.Solid, tint = SolidTint.Black, dim = 0f),
            CoverStyle(CoverKind.Card, size = 0.78f, corner = 22, shadow = CoverShadow.None, breathe = false),
            ControlsStyle(ControlsKind.Minimal, playFill = PlayFill.White, playSize = 64, progress = Progress.Line, volume = false),
            colors = ColorStyle(ColorSource.Mono),
            motion = MotionStyle(ambient = false),
        )
        val Record = NowPlayingStyle(
            BackgroundStyle(BgKind.Blur, blur = 70, zoom = 1.5f, dim = 0.55f),
            CoverStyle(CoverKind.Vinyl, size = 0.82f, spin = true, shadow = CoverShadow.Soft),
            ControlsStyle(ControlsKind.Dock, volume = false),
            colors = ColorStyle(ColorSource.Muted),
        )
        val Glass = NowPlayingStyle(
            BackgroundStyle(BgKind.Blur, blur = 60, zoom = 1.4f, dim = 0.3f, texture = Texture.Ribbed, ribs = 14),
            CoverStyle(CoverKind.Disc, size = 0.8f, ring = true, ringWidth = 3, shadow = CoverShadow.Soft),
            ControlsStyle(ControlsKind.Dock, playFill = PlayFill.Glass, volume = false),
            colors = ColorStyle(ColorSource.Vibrant),
        )
        val Presets = listOf("Studio" to Studio, "Edge" to Edge, "Aurora" to Aurora, "Night" to Night, "Record" to Record, "Glass" to Glass)

        /** The preset this style is, or "Custom". */
        fun nameOf(s: NowPlayingStyle) = Presets.firstOrNull { it.second == s }?.first ?: "Custom"
    }
}

private inline fun <reified E : Enum<E>> JSONObject.enum(key: String, def: E): E =
    optString(key, "").let { v -> enumValues<E>().firstOrNull { it.name.equals(v, ignoreCase = true) } } ?: def

private fun JSONObject.float(key: String, def: Float): Float = optDouble(key, def.toDouble()).toFloat().takeIf { it.isFinite() } ?: def

/** The applied style, as Compose state. First run: built from the old settings, so the player keeps its look. */
object NpStyleStore {
    var style by mutableStateOf(load()); private set

    fun apply(s: NowPlayingStyle) {
        style = s.clamped()
        com.wetune.Settings.npStyle = style.toJson()
    }

    private fun load(): NowPlayingStyle =
        NowPlayingStyle.fromJson(com.wetune.Settings.npStyle)
            ?: NowPlayingStyle.fromLegacy(com.wetune.Settings::legacy).also { com.wetune.Settings.npStyle = it.toJson() }
}

/** Whether the Customize editor is open (from Now Playing's ⋯ or Settings → Appearance); drawn over everything by AppUi. */
object NpEditor {
    var open by mutableStateOf(false)
}
