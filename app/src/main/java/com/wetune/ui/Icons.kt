package com.wetune.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One icon on a 24×24 grid: filled parts (stroke 0) and stroked parts, drawn in black and tinted when shown. */
class Ic(vararg parts: Pair<String, Float>) {
    val vector: ImageVector by lazy {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            parts.forEach { (d, stroke) ->
                if (stroke == 0f) addPath(addPathNodes(d), fill = SolidColor(Color.Black))
                else addPath(
                    addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }
}

private fun fill(d: String) = d to 0f
private fun line(d: String, w: Float = 2f) = d to w

/** In-house SF-style set (spec §5): rounded, mostly filled; no SF Symbols assets. Used everywhere. */
object Icons {
    private const val HEART = "M12 20.5s-7.6-4.6-9.4-9.2C1.3 7.9 3.4 4.5 7 4.5c2 0 3.6 1.1 5 2.8 1.4-1.7 3-2.8 5-2.8 3.6 0 5.7 3.4 4.4 6.8-1.8 4.6-9.4 9.2-9.4 9.2z"
    private const val REPEAT = "M4 11V9.5A3.5 3.5 0 0 1 7.5 6H19M16 3l3 3-3 3M20 13v1.5a3.5 3.5 0 0 1-3.5 3.5H5M8 21l-3-3 3-3"
    val Home = Ic(fill("M3.5 10.6 12 3.5l8.5 7.1V20a1 1 0 0 1-1 1H15v-6h-6v6H4.5a1 1 0 0 1-1-1z"))
    val Library = Ic(fill("M6 8.5h12a2.5 2.5 0 0 1 2.5 2.5v7.5A2.5 2.5 0 0 1 18 21H6a2.5 2.5 0 0 1-2.5-2.5V11A2.5 2.5 0 0 1 6 8.5zM7 5h10a1 1 0 0 1 0 2H7a1 1 0 0 1 0-2zm2.5-3h5a1 1 0 0 1 0 2h-5a1 1 0 0 1 0-2z"))
    val Search = Ic(line("M10.5 4a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM15.5 15.5l5 5", 2.4f))
    val You = Ic(fill("M12 3.8a4.2 4.2 0 1 1 0 8.4 4.2 4.2 0 0 1 0-8.4zM4 20.5c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5z"))
    val More = Ic(fill("M5 10a2 2 0 1 1 0 4 2 2 0 0 1 0-4zm7 0a2 2 0 1 1 0 4 2 2 0 0 1 0-4zm7 0a2 2 0 1 1 0 4 2 2 0 0 1 0-4z"))
    val Heart = Ic(fill(HEART))
    val HeartOutline = Ic(line(HEART, 2f))
    val Shuffle = Ic(line("M3 7h3.5c2 0 3.2 1 4.3 2.6l2.4 4.8c1.1 1.6 2.3 2.6 4.3 2.6H20M3 17h3.5c1.3 0 2.2-.4 3-1.2M14.5 8.2c.8-.8 1.7-1.2 3-1.2H20M17.5 4.5 20 7l-2.5 2.5M17.5 14.5 20 17l-2.5 2.5"))
    val Repeat = Ic(line(REPEAT))
    val RepeatOne = Ic(line(REPEAT), line("M11 10.6l1.4-.9v4.8", 1.8f))
    val Automix = Ic(line("M7 8.5a3.5 3.5 0 1 0 0 7c2.5 0 3.5-2 5-3.5s2.5-3.5 5-3.5a3.5 3.5 0 1 1 0 7c-2.5 0-3.5-2-5-3.5S9.5 8.5 7 8.5z"))
    val Prev = Ic(fill("M21.5 6.3v11.4a.9.9 0 0 1-1.4.74L12 12.74a.9.9 0 0 1 0-1.48l8.1-5.7a.9.9 0 0 1 1.4.74zm-9.6 0v11.4a.9.9 0 0 1-1.4.74l-8.1-5.7a.9.9 0 0 1 0-1.48l8.1-5.7a.9.9 0 0 1 1.4.74z"))
    val Next = Ic(fill("M2.5 6.3v11.4a.9.9 0 0 0 1.4.74l8.1-5.7a.9.9 0 0 0 0-1.48L3.9 5.56a.9.9 0 0 0-1.4.74zm9.6 0v11.4a.9.9 0 0 0 1.4.74l8.1-5.7a.9.9 0 0 0 0-1.48l-8.1-5.7a.9.9 0 0 0-1.4.74z"))
    val Play = Ic(fill("M7 4.9v14.2a1.1 1.1 0 0 0 1.66.95l11.9-7.1a1.1 1.1 0 0 0 0-1.9L8.66 3.95A1.1 1.1 0 0 0 7 4.9z"))
    val Pause = Ic(fill("M6.8 4h2a1.3 1.3 0 0 1 1.3 1.3v13.4A1.3 1.3 0 0 1 8.8 20h-2a1.3 1.3 0 0 1-1.3-1.3V5.3A1.3 1.3 0 0 1 6.8 4zm8.4 0h2a1.3 1.3 0 0 1 1.3 1.3v13.4a1.3 1.3 0 0 1-1.3 1.3h-2a1.3 1.3 0 0 1-1.3-1.3V5.3A1.3 1.3 0 0 1 15.2 4z"))
    val Lyrics = Ic(line("M5.5 3h13A3.5 3.5 0 0 1 22 6.5v8a3.5 3.5 0 0 1-3.5 3.5H12l-5 3.5V18h-1.5A3.5 3.5 0 0 1 2 14.5v-8A3.5 3.5 0 0 1 5.5 3z", 1.9f), line("M8.2 9.3a1.4 1.4 0 1 1 1.3 2c0 .9-.5 1.5-1.3 1.8M13.4 9.3a1.4 1.4 0 1 1 1.3 2c0 .9-.5 1.5-1.3 1.8", 1.9f))
    val Audio = Ic(line("M4 10v4M8 7v10M12 4v16M16 8v8M20 10.5v3", 2.2f)) // waveform, never a speaker
    val Queue = Ic(fill("M4.5 4.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm0 6a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm0 6a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z"), line("M9 6h11M9 12h11M9 18h11"))
    val Expand = Ic(line("M14 4h6v6M10 20H4v-6M20 4l-6.5 6.5M4 20l6.5-6.5", 2.4f))
    val Collapse = Ic(line("M4 14h6v6M20 10h-6V4M14 10l6.5-6.5M3.5 20.5 10 14", 2.4f))
    val Reorder = Ic(line("M5 8h14M5 12h14M5 16h14"))
    val Up = Ic(line("m6 15 6-6 6 6", 2.8f))
    val Down = Ic(line("m6 9 6 6 6-6", 2.6f))
    val Back = Ic(line("m15 4.5-7.5 7.5 7.5 7.5", 2.6f))
    val Chevron = Ic(line("m9 5 7 7-7 7", 2.6f))
    val Plus = Ic(line("M12 5v14M5 12h14", 2.4f))
    val Check = Ic(line("m5 12.5 4.5 4.5L19 7.5", 2.6f))
    val Share = Ic(line("M12 3v12M8 7l4-4 4 4M7 10H6a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2h-1", 2f))
    val Info = Ic(line("M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18zM12 11v6", 2f), fill("M12 7a1.25 1.25 0 1 1 0 2.5A1.25 1.25 0 0 1 12 7z"))
    val Close = Ic(line("M6 6l12 12M18 6 6 18", 2.4f))
    val Download = Ic(line("M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 19.5h14", 2.2f))
    val Downloaded = Ic(line("M12 2.5a9.5 9.5 0 1 1 0 19 9.5 9.5 0 0 1 0-19z", 1.8f), line("m7.5 12.5 3 3 6-6.5", 2.2f))
    val Synced = Ic(line("M7 18.5h10a4.5 4.5 0 0 0 .6-8.96A6 6 0 0 0 6.1 9.6 4.5 4.5 0 0 0 7 18.5zM9.5 13.5l2 2 3.5-4", 2f))
    val Reset = Ic(line("M4 12a8 8 0 1 0 2.3-5.6M4 4v4.5h4.5", 2.2f)) // counter-clockwise arrow: back to the default
    // the cover / Canvas switch in Now Playing: a square picture (the still cover), a tall frame with play (the Canvas)
    val Picture = Ic(
        line("M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", 2f),
        line("M4.5 17.5l4.5-4.5 4 4 2.5-2.5 4 4", 2f), fill("M15 7.4a1.6 1.6 0 1 1 0 3.2 1.6 1.6 0 0 1 0-3.2z"),
    )
    val Video = Ic(line("M8.5 3h7a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", 2f), fill("M10.5 9.4v5.2l4.2-2.6z"))
    val Offline = Ic(line("M7 18.5h10a4.5 4.5 0 0 0 1.9-.42M20.9 14.6a4.5 4.5 0 0 0-3.3-5.06A6 6 0 0 0 9.4 5.2M6.6 8.2a6 6 0 0 0-.5 1.4A4.5 4.5 0 0 0 7 18.5M3 3l18 18", 2f))
    val Radio = Ic(line("M8.5 15.5a5 5 0 0 1 0-7M15.5 8.5a5 5 0 0 1 0 7M5.6 18.4a9 9 0 0 1 0-12.8M18.4 5.6a9 9 0 0 1 0 12.8", 2f), fill("M12 10a2 2 0 1 1 0 4 2 2 0 0 1 0-4z"))
    val PlayNext = Ic(line("M4 6h11M4 12h7M4 18h7", 2f), fill("M14 11.2v7.6a.8.8 0 0 0 1.2.7l6.3-3.8a.8.8 0 0 0 0-1.4l-6.3-3.8a.8.8 0 0 0-1.2.7z"))
    val AddToPlaylist = Ic(line("M4 6h12M4 12h12M4 18h7M18 15v6M15 18h6", 2f))
    val Trash = Ic(line("M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3", 2f))
    val Album = Ic(fill("M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20zm0 7.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z"))
    val Artist = Ic(fill("M12 2.8a4.7 4.7 0 1 1 0 9.4 4.7 4.7 0 0 1 0-9.4zM3.5 21c0-4.4 3.8-7.2 8.5-7.2s8.5 2.8 8.5 7.2z"))
    val Headphones = Ic(line("M4 16v-4a8 8 0 0 1 16 0v4", 2f), fill("M3 15.5A1.5 1.5 0 0 1 4.5 14H7v7H4.5A1.5 1.5 0 0 1 3 19.5zm18 0a1.5 1.5 0 0 0-1.5-1.5H17v7h2.5a1.5 1.5 0 0 0 1.5-1.5z"))
    val Phone = Ic(line("M8 2.5h8A1.5 1.5 0 0 1 17.5 4v16a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V4A1.5 1.5 0 0 1 8 2.5zM11 18.5h2", 2f))
    val VolLow = Ic(fill("M4 9.5h3l4.3-3.6a.6.6 0 0 1 1 .46v11.28a.6.6 0 0 1-1 .46L7 14.5H4a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1z"))
    val VolHigh = Ic(fill("M4 9.5h3l4.3-3.6a.6.6 0 0 1 1 .46v11.28a.6.6 0 0 1-1 .46L7 14.5H4a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1z"), line("M15.5 9a4.2 4.2 0 0 1 0 6M18.3 6.5a8 8 0 0 1 0 11", 2f))
    val Gear = Ic(line("M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6zM19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-2.7-1.1l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H3a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.1-2.7l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7h.1a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1z", 1.8f))
    val SignOut = Ic(line("M15 4h3.5A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5H15M10 16.5 14.5 12 10 7.5M14.5 12H4", 2f))
    val Sparkle = Ic(fill("M12 2.5c.5 4.6 2.9 7 7.5 7.5-4.6.5-7 2.9-7.5 7.5-.5-4.6-2.9-7-7.5-7.5 4.6-.5 7-2.9 7.5-7.5z"))
}

/** Material Symbols paths (Apache 2.0) for Now Playing's Material style only. */
object MaterialIcons {
    val Play = Ic(fill("M8 5v14l11-7z"))
    val Pause = Ic(fill("M6 19h4V5H6v14zm8-14v14h4V5h-4z"))
    val Next = Ic(fill("M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"))
    val Prev = Ic(fill("M6 6h2v12H6zm3.5 6l8.5 6V6z"))
    val Shuffle = Ic(fill("M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"))
    val Repeat = Ic(fill("M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z"))
    val RepeatOne = Ic(fill("M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z"))
    val Automix = Ic(fill("M18.6 6.62c-1.44 0-2.8.56-3.77 1.53L12 10.66 10.48 12h.01L7.8 14.39c-.64.64-1.49.99-2.4.99-1.87 0-3.39-1.51-3.39-3.38S3.53 8.62 5.4 8.62c.91 0 1.76.35 2.44 1.03l1.13 1 1.51-1.34L9.22 8.2C8.2 7.18 6.84 6.62 5.4 6.62 2.42 6.62 0 9.04 0 12s2.42 5.38 5.4 5.38c1.44 0 2.8-.56 3.77-1.53l2.83-2.5.01.01L13.52 12h-.01l2.69-2.39c.64-.64 1.49-.99 2.4-.99 1.87 0 3.39 1.51 3.39 3.38s-1.52 3.38-3.39 3.38c-.9 0-1.76-.35-2.44-1.03l-1.14-1.01-1.51 1.34 1.27 1.12c1.02 1.01 2.37 1.57 3.82 1.57 2.98 0 5.4-2.41 5.4-5.38s-2.42-5.37-5.4-5.37z"))
    val Heart = Ic(fill("M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"))
    val HeartOutline = Ic(fill("M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z"))
    val Audio = Ic(fill("M7 18h2V6H7v12zm4 4h2V2h-2v20zm-8-8h2v-4H3v4zm12 4h2V6h-2v12zm4-8v4h2v-4h-2z")) // graphic_eq
    val Queue = Ic(fill("M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z"))
    val Lyrics = Ic(fill("M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H5.17L4 17.17V4h16v12zM12.75 5.5h4v2.25h-2.5v4.75a2.25 2.25 0 1 1-1.5-2.12z")) // speech bubble with a music note
    val Expand = Ic(fill("M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"))
    val Collapse = Ic(fill("M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"))
    val More = Ic(fill("M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"))
    val Reorder = Ic(fill("M20 9H4v2h16V9zM4 15h16v-2H4v2z"))
}

/** Tinted icon. Decorative unless the caller's button carries the label. */
@Composable
fun Icon(ic: Ic, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val filter = remember(tint) { ColorFilter.tint(tint) }
    Image(rememberVectorPainter(ic.vector), contentDescription = null, modifier = modifier.size(size), colorFilter = filter)
}
