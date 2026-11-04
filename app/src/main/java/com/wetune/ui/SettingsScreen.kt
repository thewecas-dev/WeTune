package com.wetune.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wetune.Library
import com.wetune.Settings
import com.wetune.io
import com.wetune.sources.Subsonic
import com.wetune.toast
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Sync

private data class Page(val id: String, val title: String, val detail: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val pages = listOf(
    Page("account", "Account", "YouTube Music sign-in and sync", Ic.Person),
    Page("appearance", "Appearance", "Theme, accent color, rotation", Ic.Palette),
    Page("playback", "Playback", "Maximum quality, volume, autoplay", Ic.Tune),
    Page("sources", "Sources", "Lossless add-ons, JioSaavn, Subsonic", Ic.Server),
    Page("backup", "Backup", "Save or restore your on-device library", Ic.Backup),
    Page("about", "About", "Version and credits", Ic.Info),
)

@Composable
private fun PageFrame(title: String, back: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(listPadding()).padding(horizontal = 16.dp)) {
        if (back != null) IconBtn(Ic.Back, "Back", back, Modifier.padding(bottom = 4.dp))
        Txt(title, Modifier.padding(start = 4.dp), style = Type.display)
        content()
    }
}

@Composable
fun SettingsScreen(open: (Screen) -> Unit) {
    PageFrame("Settings", null) {
        Spacer(Modifier.height(8.dp))
        Group {
            val account = Sync.account
            SettingRow(
                title = if (InnerTube.signedIn && account != null) account.name else "Not signed in",
                detail = if (InnerTube.signedIn && account != null) "YouTube Music • syncing both ways" else "Sign in to sync likes, playlists and history",
                onClick = { open(Screen.SettingsPage("account")) },
                trailing = { if (account?.photo != null) Art(account.photo, Modifier.size(40.dp), CircleShape) else Icon(Ic.Chevron, null, size = 20.dp, tint = WT.c.muted) },
            )
        }
        Group(null, Modifier.padding(top = 20.dp)) {
            pages.forEachIndexed { i, p ->
                if (i > 0) Divider(Modifier.padding(start = 62.dp))
                SettingRow(p.title, p.detail, p.icon, onClick = { open(Screen.SettingsPage(p.id)) }) {
                    Icon(Ic.Chevron, null, size = 20.dp, tint = WT.c.muted)
                }
            }
        }
    }
}

@Composable
fun SettingsPageScreen(id: String, open: (Screen) -> Unit, back: () -> Unit) {
    val title = pages.firstOrNull { it.id == id }?.title ?: "Settings"
    PageFrame(title, back) {
        when (id) {
            "account" -> AccountPage(open)
            "appearance" -> AppearancePage()
            "playback" -> PlaybackPage()
            "sources" -> SourcesPage()
            "backup" -> BackupPage()
            else -> AboutPage()
        }
    }
}

@Composable
private fun AccountPage(open: (Screen) -> Unit) {
    val c = WT.c
    val account = Sync.account
    if (InnerTube.signedIn && account != null) {
        Group("Signed in") {
            SettingRow(account.name, "Likes, playlists and history sync both ways", trailing = { Art(account.photo, Modifier.size(44.dp), CircleShape) })
        }
        Btn("Sign out", { Settings.cookie = ""; io { Sync.refresh(force = true) } }, Modifier.fillMaxWidth().padding(top = 20.dp), kind = BtnKind.Danger)
        return
    }
    Group("YouTube Music") {
        Column(Modifier.padding(16.dp)) {
            Txt("Sign in with your Google account to sync likes, playlists and listening history with YouTube Music.", color = c.muted)
            Btn("Sign in with Google", { open(Screen.Login) }, Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
    Group("Or paste a cookie") {
        Column(Modifier.padding(16.dp)) {
            var cookie by remember { mutableStateOf("") }
            Txt(
                "In a private window, sign in at music.youtube.com, open developer tools (F12) → Network, copy the whole \"cookie\" header of any music.youtube.com request, then close the window without signing out.",
                style = Type.caption, color = c.muted,
            )
            Field(cookie, { cookie = it }, "Paste cookie", Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = false, minLines = 3)
            Btn("Use pasted cookie", {
                if (InnerTube.sapisid(cookie) == null) return@Btn toast("That cookie has no SAPISID. Copy the full cookie header while signed in.")
                Settings.cookie = cookie.trim()
                io(then = { toast(Sync.account?.let { "Signed in as ${it.name}" } ?: "YouTube didn't accept this cookie") }) { Sync.refresh(force = true) }
            }, Modifier.fillMaxWidth().padding(top = 12.dp), kind = BtnKind.Secondary)
        }
    }
}

@Composable
private fun AppearancePage() {
    Group("Theme") {
        Segmented(
            listOf("system" to "System", "light" to "Light", "dark" to "Dark", "black" to "Black"),
            Settings.theme, { Settings.theme = it }, Modifier.padding(12.dp),
        )
    }
    Group("Accent color") {
        ToggleRow("Match the song", "Buttons, pills and highlights take their color from the current cover", Settings.matchAccent) { Settings.matchAccent = it }
    }
    Group("Now Playing") {
        ToggleRow("Hide the status bar", "The clock and notification icons step away while Now Playing is open. Swipe down from the top to see them.", Settings.npHideStatus) { Settings.npHideStatus = it }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow("Grabber and \"Playing from\"", "The bar at the top and what the queue was started from. Off: the cover moves up; swipe down or Back still closes the player.", Settings.npHeader) { Settings.npHeader = it }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow("Volume slider", "Show the phone's volume under the play controls", Settings.showVolume) { Settings.showVolume = it }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow("Romanized inline lyrics", "The lyric line above the seek bar shows Tamil, Hindi, Telugu… in English letters. The full lyrics stay as they are.", Settings.romanInline) { Settings.romanInline = it }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow("Lyrics on Now Playing", "The line being sung, by the title. Tap it to open the full lyrics.", Settings.showInline) { Settings.showInline = it }
        if (Settings.showInline) Column(Modifier.padding(12.dp)) {
            Segmented(listOf("line" to "One line", "stack" to "Three lines"), Settings.inlineStyle, { Settings.inlineStyle = it })
            Txt(
                if (Settings.inlineStyle == "stack") "Above the title, centered: the line being sung with the one before and after it dimmed." else "Under the title: just the line being sung.",
                Modifier.padding(start = 4.dp, top = 10.dp), style = Type.caption, color = WT.c.muted,
            )
        }
    }
    Group("Lyrics") {
        ToggleRow("Word by word", "When the lyrics are timed per word, light each line up letter by letter as it's sung", Settings.lyricsWords) { Settings.lyricsWords = it; com.wetune.Lyrics.clearCache() }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow("Fill the one-line lyric too", "The lyric above the seek bar fills letter by letter as well", Settings.inlineWords) { Settings.inlineWords = it }
    }
    Group("Lyrics panel") {
        Column(Modifier.padding(12.dp)) {
            Txt("Moving between lines", Modifier.padding(start = 4.dp, bottom = 8.dp), style = Type.label)
            Segmented(listOf("glide" to "Glide", "fade" to "Fade", "instant" to "Instant"), Settings.lyricsMotion, { Settings.lyricsMotion = it })
            Txt(
                when (Settings.lyricsMotion) {
                    "fade" -> "Scrolls smoothly; lines only brighten and dim."
                    "instant" -> "Jumps straight to the next line, no animation."
                    else -> "Scrolls smoothly; the line being sung grows a little as it lights up."
                },
                Modifier.padding(start = 4.dp, top = 10.dp), style = Type.caption, color = WT.c.muted,
            )
            Txt("Alignment", Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp), style = Type.label)
            Segmented(listOf("start" to "Left", "center" to "Center", "end" to "Right"), Settings.lyricsAlign, { Settings.lyricsAlign = it })
        }
        SettingSlider("Text size", "${Settings.lyricsSize} sp", (Settings.lyricsSize - 18) / 22f) { Settings.lyricsSize = 18 + Math.round(it * 22) }
        SettingSlider("Line height", "${Settings.lyricsLeading}%", (Settings.lyricsLeading - 100) / 80f) { Settings.lyricsLeading = 100 + Math.round(it * 16) * 5 }
        // a sample of how the panel will look
        Txt(
            "Every word you sang\nstill lights the way home",
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            style = Type.body.copy(fontSize = Settings.lyricsSize.sp, lineHeight = (Settings.lyricsSize * Settings.lyricsLeading / 100f).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            align = when (Settings.lyricsAlign) { "center" -> androidx.compose.ui.text.style.TextAlign.Center; "end" -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Start },
        )
    }
    Group("Now Playing style") {
        Column(Modifier.padding(12.dp)) {
            Segmented(NowStyles, Settings.npStyle, { Settings.npStyle = it })
            Txt(
                when (Settings.npStyle) {
                    "classic" -> "The cover as a rounded card over a soft blur of its colors."
                    "strips" -> "The cover as a card over its colors seen through ribbed glass."
                    "disc" -> "A round cover; its colors blur across the top and fade into a deep tint."
                    else -> "The cover runs edge to edge and its colors bleed out to fill the screen."
                },
                Modifier.padding(start = 4.dp, top = 10.dp), style = Type.caption, color = WT.c.muted,
            )
            if (Settings.npStyle == "bleed") {
                Txt("Around the cover", Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp), style = Type.label)
                Segmented(FillModes, Settings.npFill, { Settings.npFill = it })
                Txt(
                    when (Settings.npFill) {
                        "stretch" -> "The cover, trimmed slightly at the sides; its top and bottom edges stretch out to fill the screen."
                        "mirror" -> "The cover, trimmed slightly at the sides, reflected softly above and below."
                        else -> "A blurred, zoomed copy of the cover fills the screen."
                    },
                    Modifier.padding(start = 4.dp, top = 10.dp), style = Type.caption, color = WT.c.muted,
                )
            }
        }
    }
    Group("Display") {
        ToggleRow("Auto-rotate", "Off keeps WeTune in portrait. On follows your phone's rotation setting.", Settings.autoRotate) { Settings.autoRotate = it }
    }
}

@Composable
private fun PlaybackPage() {
    val levels = listOf("data" to "Data", "high" to "High", "max" to "320", "lossless" to "Lossless")
    Group("Maximum quality") {
        Column(Modifier.padding(12.dp)) {
            Txt("On Wi-Fi", Modifier.padding(start = 4.dp, bottom = 8.dp), style = Type.label)
            Segmented(levels, Settings.maxWifi, { Settings.maxWifi = it })
            Txt("On mobile data", Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp), style = Type.label)
            Segmented(levels, Settings.maxMobile, { Settings.maxMobile = it })
            Txt(
                "Songs start right away and keep stepping up to this ceiling: YouTube's best stream (about 160 kbps Opus) first, " +
                    "then JioSaavn's 320 kbps file, then lossless FLAC from your add-ons (Settings → Sources), each only when the " +
                    "exact same recording is found. Lossless uses about 1–3 MB per minute. A better connection " +
                    "(say, Wi-Fi after mobile data) upgrades the playing song. Data saver uses about 70 kbps. " +
                    "Likes, history and recommendations always go to YouTube Music.",
                Modifier.padding(start = 4.dp, top = 12.dp), style = Type.caption, color = WT.c.muted,
            )
        }
    }
    Group("Listening") {
        ToggleRow("Normalize volume", "Turns down songs mastered louder than others (YouTube)", Settings.normalize) { Settings.normalize = it }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow("Autoplay", "Keep playing similar songs when the queue ends", Settings.autoplay) { Settings.autoplay = it }
    }
}

@Composable
private fun SourcesPage() {
    SourcesList()
    var url by remember { mutableStateOf(Settings.subsonicUrl) }
    var user by remember { mutableStateOf(Settings.subsonicUser) }
    var pass by remember { mutableStateOf(Settings.subsonicPass) }
    Group("Subsonic / Navidrome") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(url, { url = it }, "https://music.example.com", label = "Server URL", keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Field(user, { user = it }, "Username", label = "Username")
            Field(pass, { pass = it }, "Password", label = "Password", password = true, keyboard = KeyboardOptions(keyboardType = KeyboardType.Password))
            Btn("Save and test", {
                Settings.subsonicUrl = url.trim(); Settings.subsonicUser = user.trim(); Settings.subsonicPass = pass
                io("Connected to your server") { Subsonic.ping() }
            }, Modifier.fillMaxWidth())
        }
    }
    Group("Also") {
        SettingRow("This device", "Songs stored on your phone")
    }
}

@Composable
private fun BackupPage() {
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { runCatching { Library.export(it) }.onSuccess { toast("Backup saved") }.onFailure { toast("Couldn't save backup: ${it.message}") } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runCatching { Library.import(it) }.onSuccess { toast("Library restored") }.onFailure { toast("Couldn't restore: ${it.message}") } }
    }
    Group("On-device library") {
        Column(Modifier.padding(16.dp)) {
            Txt(
                "Saves on-device playlists (${Library.playlists.size}), likes (${Library.liked.size}) and the download list. YouTube data already lives in your account.",
                color = WT.c.muted,
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Back up", { export.launch("wetune-backup.json") }, Modifier.weight(1f))
                Btn("Restore", { import.launch(arrayOf("application/json")) }, Modifier.weight(1f), kind = BtnKind.Secondary)
            }
        }
    }
}

@Composable
private fun AboutPage() {
    Group {
        SettingRow("WeTune", "Version 0.3.0")
        Divider(Modifier.padding(start = 16.dp))
        SettingRow("Sources", "YouTube Music, JioSaavn, Subsonic, this device")
        Divider(Modifier.padding(start = 16.dp))
        SettingRow("Built with", "Jetpack Compose, Media3, NewPipe Extractor, LRCLIB lyrics")
    }
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt("Unofficial client. Not affiliated with YouTube, JioSaavn or Google.", style = Type.caption, color = WT.c.muted)
    }
}

@Composable
private fun SettingSlider(title: String, value: String, fraction: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(title, Modifier.weight(1f), style = Type.body)
            Txt(value, style = Type.caption.copy(fontFeatureSettings = "tnum"), color = WT.c.muted)
        }
        SeekSlider(fraction.coerceIn(0f, 1f), { onChange(it.coerceIn(0f, 1f)) }, {}, Modifier.fillMaxWidth().padding(top = 8.dp), color = WT.c.accent, thumb = true)
    }
}
