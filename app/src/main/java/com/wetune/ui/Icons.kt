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
    val Play by lazy { icon("play", "M8 6.82v10.36c0 .79.87 1.27 1.54.84l8.14-5.18c.62-.39.62-1.29 0-1.69L9.54 5.98C8.87 5.55 8 6.03 8 6.82z") }
    val Pause by lazy { icon("pause", "M8 19c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2s-2 .9-2 2v10c0 1.1.9 2 2 2zm6-12v10c0 1.1.9 2 2 2s2-.9 2-2V7c0-1.1-.9-2-2-2s-2 .9-2 2z") }
    val Next by lazy { icon("next", "M6.5 5.6v12.8a.8.8 0 0 0 1.2.7l8.3-5.7V18a1 1 0 0 0 2 0V6a1 1 0 0 0-2 0v4.6L7.7 4.9a.8.8 0 0 0-1.2.7z") }
    val Prev by lazy { icon("prev", "M17.5 5.6v12.8a.8.8 0 0 1-1.2.7L8 13.4V18a1 1 0 0 1-2 0V6a1 1 0 0 1 2 0v4.6l8.3-5.7a.8.8 0 0 1 1.2.7z") }
    val Shuffle by lazy { icon("shuffle", "M10.59 9.17L6.12 4.7c-.39-.39-1.02-.39-1.41 0-.39.39-.39 1.02 0 1.41l4.46 4.46 1.42-1.4zm4.76-4.32l1.19 1.19L4.7 17.88c-.39.39-.39 1.02 0 1.41.39.39 1.02.39 1.41 0L17.96 7.46l1.19 1.19c.31.31.85.09.85-.36V4.5c0-.28-.22-.5-.5-.5h-3.79c-.45 0-.67.54-.36.85zm-.52 8.56l-1.41 1.41 3.13 3.13-1.2 1.2c-.31.31-.09.85.36.85h3.79c.28 0 .5-.22.5-.5v-3.79c0-.45-.54-.67-.85-.35l-1.19 1.19-3.13-3.14z") }
    val Repeat by lazy { icon("repeat", "M7 7h10v1.79c0 .45.54.67.85.35l2.79-2.79c.2-.2.2-.51 0-.71l-2.79-2.79c-.31-.31-.85-.09-.85.36V5H6c-.55 0-1 .45-1 1v4c0 .55.45 1 1 1s1-.45 1-1V7zm10 10H7v-1.79c0-.45-.54-.67-.85-.35l-2.79 2.79c-.2.2-.2.51 0 .71l2.79 2.79c.31.31.85.09.85-.36V19h11c.55 0 1-.45 1-1v-4c0-.55-.45-1-1-1s-1 .45-1 1v3z") }
    val RepeatOne by lazy { icon("repeat_one", "M7 7h10v1.79c0 .45.54.67.85.35l2.79-2.79c.2-.2.2-.51 0-.71l-2.79-2.79c-.31-.31-.85-.09-.85.36V5H6c-.55 0-1 .45-1 1v4c0 .55.45 1 1 1s1-.45 1-1V7zm10 10H7v-1.79c0-.45-.54-.67-.85-.35l-2.79 2.79c-.2.2-.2.51 0 .71l2.79 2.79c.31.31.85.09.85-.36V19h11c.55 0 1-.45 1-1v-4c0-.55-.45-1-1-1s-1 .45-1 1v3zm-4-2.75V9.81c0-.45-.36-.81-.81-.81-.13 0-.25.03-.36.09l-1.49.74c-.21.1-.34.32-.34.55 0 .34.28.62.62.62h.88v3.25c0 .41.34.75.75.75s.75-.34.75-.75z") }
    val PlayNext by lazy { line("play_next", "M4 6.5h11M4 12h11M4 17.5h7M16.5 14.5v6l4.5-3z") }
    val AddPlaylist by lazy { line("add_playlist", "M4 6.5h12M4 12h12M4 17.5h7M18 14v6M15 17h6") }
    val Note by lazy { icon("note", "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z") }
    val Download by lazy { line("download", "M12 4v11M7.5 10.5L12 15l4.5-4.5M5 19.5h14") }
    val Downloaded by lazy { icon("downloaded", "M5 18h14v2H5v-2zm4.6-2.7L5 10.7l2-1.9 2.6 2.6L17 4l2 2-9.4 9.3z") }
    val Library by lazy { line("library", "M4 4v16M9 4v16M14 5l5 15") }
    val Queue by lazy { line("queue", "M4 6.5h16M4 12h16M4 17.5h10") }
    val Lyrics by lazy { line("lyrics", "M4.5 5.5h15v10h-9l-4 3.5v-3.5h-2z") }
    val Timer by lazy { icon("timer", "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z") }
    val Heart by lazy { icon("heart", "M13.35 20.13c-.76.69-1.93.69-2.69-.01l-.11-.1C5.3 15.27 1.87 12.16 2 8.28c.06-1.7.93-3.33 2.34-4.29 2.64-1.8 5.9-.96 7.66 1.1 1.76-2.06 5.02-2.91 7.66-1.1 1.41.96 2.28 2.59 2.34 4.29.14 3.88-3.3 6.99-8.55 11.76l-.1.09z") }
    val HeartOutline by lazy { icon("heart_outline", "M19.66 3.99c-2.64-1.8-5.9-.96-7.66 1.1-1.76-2.06-5.02-2.91-7.66-1.1-1.4.96-2.28 2.58-2.34 4.29-.14 3.88 3.3 6.99 8.55 11.76l.1.09c.76.69 1.93.69 2.69-.01l.11-.1c5.25-4.76 8.68-7.87 8.55-11.75-.06-1.7-.94-3.32-2.34-4.28zM12.1 18.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z") }
    val More by lazy { icon("more", "M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z") }
    val Search by lazy { line("search", "M11 4.5a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13zM16 16l4.5 4.5") }
    val Home by lazy { line("home", "M3.5 10.5L12 3.5l8.5 7V20a.8.8 0 0 1-.8.8H15v-6H9v6H4.3a.8.8 0 0 1-.8-.8z") }
    val Back by lazy { line("back", "M14.5 6l-6 6 6 6") }
    val Down by lazy { icon("down", "M8.12 9.29L12 13.17l3.88-3.88c.39-.39 1.02-.39 1.41 0 .39.39.39 1.02 0 1.41l-4.59 4.59c-.39.39-1.02.39-1.41 0L6.7 10.7c-.39-.39-.39-1.02 0-1.41.39-.38 1.03-.39 1.42 0z") }
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
    val Rewind by lazy { icon("rewind", "M11 16.07V7.93c0-.81-.91-1.28-1.58-.82l-5.77 4.07c-.56.4-.56 1.24 0 1.63l5.77 4.07c.67.47 1.58 0 1.58-.81zm1.66-3.25l5.77 4.07c.66.47 1.58-.01 1.58-.82V7.93c0-.81-.91-1.28-1.58-.82l-5.77 4.07c-.57.4-.57 1.24 0 1.64z") }
    val Forward by lazy { icon("forward", "M5.58 16.89l5.77-4.07c.56-.4.56-1.24 0-1.63L5.58 7.11C4.91 6.65 4 7.12 4 7.93v8.14c0 .81.91 1.28 1.58.82zM13 7.93v8.14c0 .81.91 1.28 1.58.82l5.77-4.07c.56-.4.56-1.24 0-1.63l-5.77-4.07c-.67-.47-1.58 0-1.58.81z") }
    val VolumeLow by lazy { icon("volume_low", "M16.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 10v4c0 .55.45 1 1 1h3l3.29 3.29c.63.63 1.71.18 1.71-.71V6.41c0-.89-1.08-1.34-1.71-.71L9 9H6c-.55 0-1 .45-1 1z") }
    val LyricsQuote by lazy { line("lyrics_quote", "M6 4.5h12a2.5 2.5 0 0 1 2.5 2.5v8a2.5 2.5 0 0 1-2.5 2.5h-6l-4.5 3.5v-3.5H6A2.5 2.5 0 0 1 3.5 15V7A2.5 2.5 0 0 1 6 4.5zM9 9v3M9 9h.01M15 9v3M15 9h.01") }
    val QueueList by lazy { icon("queue_list", "M14 6H4c-.55 0-1 .45-1 1s.45 1 1 1h10c.55 0 1-.45 1-1s-.45-1-1-1zm0 4H4c-.55 0-1 .45-1 1s.45 1 1 1h10c.55 0 1-.45 1-1s-.45-1-1-1zM4 16h6c.55 0 1-.45 1-1s-.45-1-1-1H4c-.55 0-1 .45-1 1s.45 1 1 1zM19 6c-1.1 0-2 .9-2 2v6.18c-.31-.11-.65-.18-1-.18-1.84 0-3.28 1.64-2.95 3.54.21 1.21 1.2 2.2 2.41 2.41 1.9.33 3.54-1.11 3.54-2.95V8h2c.55 0 1-.45 1-1s-.45-1-1-1h-2z") }
    val ArrowUp by lazy { line("arrow_up", "M12 19V5M6 11l6-6 6 6") }
    val Sleep by lazy { icon("sleep", "M12.34 2.02C6.59 1.82 2 6.42 2 12c0 5.52 4.48 10 10 10 3.71 0 6.93-2.02 8.66-5.02-7.51-.25-12.09-8.43-8.32-14.96z") }
    val Output by lazy { icon("output", "M12 3a9 9 0 0 0-9 9v7c0 1.1.9 2 2 2h1c1.1 0 2-.9 2-2v-4c0-1.1-.9-2-2-2H5v-1c0-3.87 3.13-7 7-7s7 3.13 7 7v1h-1c-1.1 0-2 .9-2 2v4c0 1.1.9 2 2 2h1c1.1 0 2-.9 2-2v-7a9 9 0 0 0-9-9z") }
    val ArrowDown by lazy { line("arrow_down", "M12 5v14M6 13l6 6 6-6") }
    val Clock by lazy { line("clock", "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16zM12 8v4l3 2") }
    val Versions by lazy { line("versions", "M7 4.5h10M5 8h14M4.5 11.5h15v8h-15z") }
    val Karaoke by lazy { line("karaoke", "M8 14l4-9 4 9M9.5 11h5M5 18.5h7M14 18.5h5") } // a letter over a part-filled bar: word-by-word lyrics
    val Latin by lazy { line("latin", "M4 18l4.5-12L13 18M5.7 14h5.6M15 12.5a2.8 2.8 0 1 1 0 5.5h-.2M17.8 12v6") }
    val Collapse by lazy { line("collapse", "M8 9l4-4 4 4M8 15l4 4 4-4") }
    val Radio by lazy { line("radio", "M12 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3zM8.5 15.5a5 5 0 0 1 0-7M15.5 8.5a5 5 0 0 1 0 7M5.6 18.4a9 9 0 0 1 0-12.8M18.4 5.6a9 9 0 0 1 0 12.8") }
    val Infinite by lazy { line("infinite", "M12 12c-2-2.5-3.6-4-5.5-4a4 4 0 0 0 0 8c1.9 0 3.5-1.5 5.5-4zm0 0c2 2.5 3.6 4 5.5 4a4 4 0 0 0 0-8c-1.9 0-3.5 1.5-5.5 4z") }
    val Volume by lazy { icon("volume", "M3 10v4c0 .55.45 1 1 1h3l3.29 3.29c.63.63 1.71.18 1.71-.71V6.41c0-.89-1.08-1.34-1.71-.71L7 9H4c-.55 0-1 .45-1 1zm13.5 2c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 4.45v.2c0 .38.25.71.6.85C17.18 6.53 19 9.06 19 12s-1.82 5.47-4.4 6.5c-.36.14-.6.47-.6.85v.2c0 .63.63 1.07 1.21.85C18.6 19.11 21 15.84 21 12s-2.4-7.11-5.79-8.4c-.58-.23-1.21.22-1.21.85z") }
    val Moon by lazy { line("moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z") }
    val Delete by lazy { icon("delete", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z") }
    val Plus by lazy { icon("plus", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }
}
