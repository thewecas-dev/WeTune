package com.wetune.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

// Pure functions from (style, cover) to what the layers draw: the colour tokens with the contrast rules applied, and
// where everything goes. No Compose state, no settings: the player, the editor's preview and the tests share them.

/** The least scrim the layers always draw behind text (header, lyric line, title and controls). */
const val TextScrim = 0.3f

@Immutable
data class NpColors(
    val base: Color, // fill under everything
    val tint: Color, // Solid background
    val mesh: List<Color>,
    val accent: Color,
    val onAccent: Color,
    val shadow: Color, // the cover's shadow (Transparent: none)
    val dim: Float, // full-screen dim actually drawn: the style's, never below [autoDim]
    val autoDim: Float, // the least dim that keeps text ≥ 4.5:1 and icons ≥ 3:1 over this cover
    val textContrast: Double, // dimmest text (muted) over the brightest background behind it
    val iconContrast: Double,
)

fun resolveColors(style: NowPlayingStyle, art: ArtData?): NpColors {
    val pal = art?.palette ?: SunsetPalette
    val base = lerp(pal.sea, Color.Black, 0.35f)
    val bg = style.background
    val tint = if (bg.tint == SolidTint.Black) Color.Black else pal.sea
    val mesh = art?.mesh ?: listOf(pal.band, pal.sea, lerp(pal.band, Color.Gray, 0.5f), pal.band)
    // the brightest part of the background that text can sit on, before the dim
    val cells = art?.cells
    val g = ArtData.Grid
    var bright = when (bg.kind) {
        BgKind.Solid -> tint.toArgb()
        BgKind.Mesh -> mesh.maxBy { luminance(it.toArgb()) }.toArgb()
        BgKind.Extend -> cells?.filterIndexed { i, _ -> i / g == 0 || i / g == g - 1 }?.maxByOrNull(::luminance) ?: base.toArgb()
        BgKind.Blur -> cells?.maxByOrNull(::luminance) ?: base.toArgb()
    }
    if (bg.texture == Texture.Ribbed) bright = over(0xFFFFFFFF.toInt(), 0.07f, bright)
    val auto = minDim(bright, TextScrim)
    val dim = maxOf(bg.dim, auto)
    val under = over(0xFF08080A.toInt(), dim + TextScrim * (1 - dim), bright)
    val (text, icons) = textIconContrast(under)
    val raw = when (style.colors.source) {
        ColorSource.CoverHue -> pal.hue?.let { hueAccent(it, dark = true) } ?: Color(0xFFEDEDEF)
        ColorSource.Vibrant -> art?.vibrant ?: pal.band
        ColorSource.Muted -> art?.muted ?: lerp(pal.band, Color.Gray, 0.5f)
        ColorSource.Mono -> Color(0xFFEDEDEF)
        ColorSource.Custom -> hueAccent(style.colors.customHue.toFloat(), dark = true)
    }
    val accent = Color(ensureContrast(raw.toArgb(), under, 3.0))
    val shadow = when (style.cover.shadow) {
        CoverShadow.None -> Color.Transparent
        CoverShadow.Soft -> Color.Black.copy(alpha = 0.55f)
        CoverShadow.Tinted -> lerp(pal.band, Color.Black, 0.25f).copy(alpha = 0.7f)
    }
    return NpColors(base, tint, mesh, accent, Color(onFill(accent.toArgb())), shadow, dim, auto, text, icons)
}

/** Where everything goes, in dp, from the screen size alone (no measuring, so no layout feedback loops). */
@Immutable
data class NpGeometry(
    val width: Float, val height: Float,
    val headerTop: Float, val headerH: Float,
    val coverLeft: Float, val coverTop: Float, val coverW: Float, val coverH: Float,
    val controlsTop: Float, val controlsH: Float,
) {
    val coverBottom get() = coverTop + coverH
}

/** Height of the controls block for a style (rows inside it are spread evenly). */
fun controlsHeight(c: ControlsStyle): Float = when (c.kind) {
    ControlsKind.Classic -> 236f
    ControlsKind.Expressive -> 272f
    ControlsKind.Dock -> 280f
    ControlsKind.Minimal -> 196f
    ControlsKind.Waveform -> 300f
} + (if (c.volume) 44f else 0f)

/**
 * The lyric line's room is always kept (empty for songs without synced lyrics), so nothing moves when it opens or
 * closes. On the mockups' 390×844 screen the cover sits ~30 dp under the header and ~36 dp above the title; any extra
 * height goes 35 % above the cover, 15 % under it and 50 % into the controls' own spacing.
 */
fun npGeometry(style: NowPlayingStyle, width: Float, height: Float, topInset: Float, bottomInset: Float): NpGeometry {
    val reserve = when (style.layout.lyrics) { InlineLyrics.Off -> 0f; InlineLyrics.Line -> 52f; InlineLyrics.Stack -> 112f }
    val baseH = controlsHeight(style.controls)
    val bottomEdge = height - bottomInset - 8f
    val headerTop = maxOf(topInset, 8f)
    val headerH = if (style.layout.header) 48f else 0f
    val full = style.cover.kind == CoverKind.FullWidth
    // full width starts under the header (its top fades beneath it); cards start below it
    val bandTop = headerTop + if (full) headerH * 0.6f else headerH
    val (baseAbove, baseBelow) = if (full) 0f to 20f else 30f to 36f
    val room = bottomEdge - baseH - bandTop - reserve - if (full) 8f else 32f
    val side = minOf(if (full) width else width * style.cover.size, room).coerceAtLeast(120f)
    val free = (bottomEdge - baseH - bandTop - reserve - side).coerceAtLeast(0f)
    val base = baseAbove + baseBelow
    val extra = (free - base).coerceAtLeast(0f)
    val above = (free - extra) * baseAbove / base + extra * if (full) 0f else 0.35f
    val below = (free - extra) * baseBelow / base + extra * if (full) 0.3f else 0.15f
    val controlsH = baseH + (free - above - below)
    val controlsTop = bottomEdge - controlsH
    val coverW = if (full) width else side
    return NpGeometry(width, height, headerTop, headerH, (width - coverW) / 2f, bandTop + above, coverW, side, controlsTop, controlsH)
}
