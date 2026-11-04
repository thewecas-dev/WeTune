package com.wetune.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Material icon paths, so we don't pull in the large material-icons-extended artifact.
private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

/** Outline icon in WeTune's line style: 1.7 stroke, round caps and joins. */
private fun line(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()

object Ic {
    val Play by lazy { icon("play", "M8 5.5v13a.8.8 0 0 0 1.2.7l10.4-6.5a.8.8 0 0 0 0-1.4L9.2 4.8A.8.8 0 0 0 8 5.5z") }
    val Pause by lazy { icon("pause", "M7.4 5h1.2a1.4 1.4 0 0 1 1.4 1.4v11.2a1.4 1.4 0 0 1-1.4 1.4H7.4A1.4 1.4 0 0 1 6 17.6V6.4A1.4 1.4 0 0 1 7.4 5zm8 0h1.2a1.4 1.4 0 0 1 1.4 1.4v11.2a1.4 1.4 0 0 1-1.4 1.4h-1.2a1.4 1.4 0 0 1-1.4-1.4V6.4A1.4 1.4 0 0 1 15.4 5z") }
    val Next by lazy { icon("next", "M6.5 5.6v12.8a.8.8 0 0 0 1.2.7l8.3-5.7V18a1 1 0 0 0 2 0V6a1 1 0 0 0-2 0v4.6L7.7 4.9a.8.8 0 0 0-1.2.7z") }
    val Prev by lazy { icon("prev", "M17.5 5.6v12.8a.8.8 0 0 1-1.2.7L8 13.4V18a1 1 0 0 1-2 0V6a1 1 0 0 1 2 0v4.6l8.3-5.7a.8.8 0 0 1 1.2.7z") }
    val Shuffle by lazy { line("shuffle", "M16 4.5h3.5V8M4.5 19.5l15-15M19.5 16v3.5H16M14.5 14.5l5 5M4.5 4.5l5 5") }
    val Repeat by lazy { line("repeat", "M17 3.5l3 3-3 3M4 11.5v-1a4 4 0 0 1 4-4h12M7 20.5l-3-3 3-3M20 12.5v1a4 4 0 0 1-4 4H4") }
    val RepeatOne by lazy { line("repeat_one", "M17 3.5l3 3-3 3M4 11.5v-1a4 4 0 0 1 4-4h12M7 20.5l-3-3 3-3M20 12.5v1a4 4 0 0 1-4 4H4M11.5 10.5l1-.5v4") }
    val PlayNext by lazy { line("play_next", "M4 6.5h11M4 12h11M4 17.5h7M16.5 14.5v6l4.5-3z") }
    val AddPlaylist by lazy { line("add_playlist", "M4 6.5h12M4 12h12M4 17.5h7M18 14v6M15 17h6") }
    val Note by lazy { icon("note", "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z") }
    val Download by lazy { line("download", "M12 4v11M7.5 10.5L12 15l4.5-4.5M5 19.5h14") }
    val Downloaded by lazy { icon("downloaded", "M5 18h14v2H5v-2zm4.6-2.7L5 10.7l2-1.9 2.6 2.6L17 4l2 2-9.4 9.3z") }
    val Library by lazy { line("library", "M4 4v16M9 4v16M14 5l5 15") }
    val Queue by lazy { line("queue", "M4 6.5h16M4 12h16M4 17.5h10") }
    val Lyrics by lazy { line("lyrics", "M4.5 5.5h15v10h-9l-4 3.5v-3.5h-2z") }
    val Timer by lazy { icon("timer", "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z") }
    val Heart by lazy { icon("heart", "M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z") }
    val HeartOutline by lazy { line("heart_outline", "M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z") }
    val More by lazy { icon("more", "M5 10.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2zm7 0a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2zm7 0a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2z") }
    val Search by lazy { line("search", "M11 4.5a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13zM16 16l4.5 4.5") }
    val Home by lazy { line("home", "M3.5 10.5L12 3.5l8.5 7V20a.8.8 0 0 1-.8.8H15v-6H9v6H4.3a.8.8 0 0 1-.8-.8z") }
    val Back by lazy { line("back", "M14.5 6l-6 6 6 6") }
    val Down by lazy { line("down", "M6 9.5l6 6 6-6") }
    val Add by lazy { line("add", "M12 5v14M5 12h14") }
    val Refresh by lazy { icon("refresh", "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z") }
    val Close by lazy { line("close", "M7 7l10 10M17 7L7 17") }
    val Check by lazy { icon("check", "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z") }
    val Chevron by lazy { line("chevron", "M9 6l6 6-6 6") }
    val Person by lazy { line("person", "M12 4.7a3.8 3.8 0 1 0 0 7.6a3.8 3.8 0 1 0 0-7.6zM4.5 20.5c1.4-3.6 4.2-5.5 7.5-5.5s6.1 1.9 7.5 5.5") }
    val Tune by lazy { icon("tune", "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z") }
    val Server by lazy { icon("server", "M2 20h20v-4H2v4zm2-3h2v2H4v-2zM2 4v4h20V4H2zm4 3H4V5h2v2zm-4 7h20v-4H2v4zm2-3h2v2H4v-2z") }
    val Backup by lazy { icon("backup", "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z") }
    val Info by lazy { icon("info", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z") }
    val Palette by lazy { icon("palette", "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z") }
    val Grip by lazy { line("grip", "M5 9h14M5 15h14") }
    val Pipeline by lazy { line("pipeline", "M4 12h2M8 8v8M12 5v14M16 9v6M20 11v2") }
    val Device by lazy { line("device", "M8 3.5h8a3 3 0 0 1 3 3v11a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3v-11a3 3 0 0 1 3-3zM12 11a3 3 0 1 0 0 6a3 3 0 1 0 0-6z") }
    val Rewind by lazy { icon("rewind", "M20.5 6.2v11.6a.8.8 0 0 1-1.25.66L12 13.3v4.5a.8.8 0 0 1-1.25.66l-8.2-5.8a.8.8 0 0 1 0-1.32l8.2-5.8A.8.8 0 0 1 12 6.2v4.5l7.25-5.16a.8.8 0 0 1 1.25.66z") }
    val Forward by lazy { icon("forward", "M3.5 6.2v11.6a.8.8 0 0 0 1.25.66L12 13.3v4.5a.8.8 0 0 0 1.25.66l8.2-5.8a.8.8 0 0 0 0-1.32l-8.2-5.8A.8.8 0 0 0 12 6.2v4.5L4.75 5.54A.8.8 0 0 0 3.5 6.2z") }
    val VolumeLow by lazy { line("volume_low", "M5 9.5h3.5L13 5.5v13l-4.5-4H5zM16.5 9.5a3.5 3.5 0 0 1 0 5") }
    val LyricsQuote by lazy { line("lyrics_quote", "M6 4.5h12a2.5 2.5 0 0 1 2.5 2.5v8a2.5 2.5 0 0 1-2.5 2.5h-6l-4.5 3.5v-3.5H6A2.5 2.5 0 0 1 3.5 15V7A2.5 2.5 0 0 1 6 4.5zM9 9v3M9 9h.01M15 9v3M15 9h.01") }
    val QueueList by lazy { line("queue_list", "M9 6.5h11M9 12h11M9 17.5h11M4.5 6.5h.01M4.5 12h.01M4.5 17.5h.01") }
    val ArrowUp by lazy { line("arrow_up", "M12 19V5M6 11l6-6 6 6") }
    val Sleep by lazy { line("sleep", "M19.5 14.2A7.5 7.5 0 0 1 9.8 4.5a7.5 7.5 0 1 0 9.7 9.7zM15 3.5h4l-4 4h4") }
    val Output by lazy { line("output", "M7 18.5a8 8 0 1 1 10 0M9.2 15.6a4.6 4.6 0 1 1 5.6 0M12 15.5l-4 4.5h8z") }
    val ArrowDown by lazy { line("arrow_down", "M12 5v14M6 13l6 6 6-6") }
    val Clock by lazy { line("clock", "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16zM12 8v4l3 2") }
    val Versions by lazy { line("versions", "M7 4.5h10M5 8h14M4.5 11.5h15v8h-15z") }
    val Latin by lazy { line("latin", "M4 18l4.5-12L13 18M5.7 14h5.6M15 12.5a2.8 2.8 0 1 1 0 5.5h-.2M17.8 12v6") }
    val Collapse by lazy { line("collapse", "M8 9l4-4 4 4M8 15l4 4 4-4") }
    val Radio by lazy { line("radio", "M12 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3zM8.5 15.5a5 5 0 0 1 0-7M15.5 8.5a5 5 0 0 1 0 7M5.6 18.4a9 9 0 0 1 0-12.8M18.4 5.6a9 9 0 0 1 0 12.8") }
    val Infinite by lazy { line("infinite", "M12 12c-2-2.5-3.6-4-5.5-4a4 4 0 0 0 0 8c1.9 0 3.5-1.5 5.5-4zm0 0c2 2.5 3.6 4 5.5 4a4 4 0 0 0 0-8c-1.9 0-3.5 1.5-5.5 4z") }
    val Volume by lazy { line("volume", "M4 9.5h3.5L12 5.5v13l-4.5-4H4zM15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11") }
    val Moon by lazy { line("moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z") }
    val Delete by lazy { icon("delete", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z") }
}
