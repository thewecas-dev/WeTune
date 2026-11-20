package com.wetune.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wetune.App
import com.wetune.Library
import com.wetune.Lyrics
import com.wetune.Settings
import com.wetune.io
import com.wetune.sources.Subsonic
import com.wetune.toast
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Sync

private data class Page(val id: String, val title: String, val detail: String, val icon: ImageVector)

// most used first
private val pages = listOf(
    Page("playback", "Playback", "Autoplay, volume, headphones", Ic.Play),
    Page("quality", "Audio quality", "Maximum quality on Wi-Fi and mobile data", Ic.Tune),
    Page("lyrics", "Lyrics", "Word by word, romanized, the lyrics panel", Ic.Lyrics),
    Page("appearance", "Appearance", "Theme, accent, text size, player look", Ic.Palette),
    Page("sources", "Sources", "Lossless add-ons, JioSaavn, Subsonic", Ic.Server),
    Page("storage", "Storage & privacy", "Cache, listening history, search history", Ic.Device),
    Page("backup", "Backup", "Library and settings, to a file and back", Ic.Backup),
    Page("about", "About", "Version and credits", Ic.Info),
)

/** One row in the settings search: where it lives, what it's called, extra words people might type. */
private data class Entry(val page: String, val title: String, val words: String = "")

private val index = listOf(
    Entry("account", "Sign in with Google", "youtube music account cookie sync sign out"),
    Entry("playback", "Autoplay", "infinite radio similar songs queue ends"),
    Entry("playback", "Autoplay picks", "radio same artist mix"),
    Entry("playback", "Normalize volume", "loudness gain replaygain"),
    Entry("playback", "Skip silence", "gaps quiet"),
    Entry("playback", "Pause when headphones disconnect", "bluetooth unplug headset noisy"),
    Entry("playback", "Stop when swiped away", "recents close kill background"),
    Entry("quality", "Maximum quality on Wi-Fi", "bitrate lossless flac 320 data saver streaming"),
    Entry("quality", "Maximum quality on mobile data", "bitrate cellular data saver streaming"),
    Entry("lyrics", "Romanized inline lyrics", "latin english letters transliteration"),
    Entry("lyrics", "Word by word", "karaoke syllable"),
    Entry("lyrics", "Fill the one-line lyric", "inline seek bar karaoke"),
    Entry("lyrics", "Moving between lines", "lyrics panel motion glide fade instant animation"),
    Entry("lyrics", "Lyrics alignment", "left center right"),
    Entry("lyrics", "Lyrics text size", "font"),
    Entry("lyrics", "Lyrics line height", "spacing leading"),
    Entry("lyrics", "Reset lyric timing", "offset sync delay"),
    Entry("appearance", "Theme", "dark light black amoled system night"),
    Entry("appearance", "Accent color", "match the song cover colour tint"),
    Entry("appearance", "Text size", "font scale bigger smaller accessibility"),
    Entry("appearance", "Customize player", "now playing cover background controls layout"),
    Entry("appearance", "Open on", "start tab home library"),
    Entry("appearance", "Auto-rotate", "landscape orientation portrait"),
    Entry("sources", "Lossless add-ons", "flac addon jiosaavn order"),
    Entry("sources", "Subsonic / Navidrome", "server self-hosted"),
    Entry("storage", "Audio cache size", "storage space offline"),
    Entry("storage", "Clear cache", "storage space free up images"),
    Entry("storage", "Send plays to YouTube history", "privacy pause watch history recommendations"),
    Entry("storage", "Clear search history", "privacy recent searches"),
    Entry("backup", "Back up library", "export playlists likes downloads"),
    Entry("backup", "Export settings", "backup json np style"),
    Entry("backup", "Import settings", "restore json"),
    Entry("about", "Version", "about credits licence"),
)

@Composable
private fun PageFrame(title: String, back: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(listPadding()).padding(horizontal = 16.dp)) {
        if (back != null) IconBtn(Ic.Back, "Back", back, Modifier.padding(bottom = 4.dp))
        Txt(title, Modifier.padding(start = 4.dp).semantics { heading() }, style = Type.display)
        content()
    }
}

@Composable
private fun Chevron() = Icon(Ic.Chevron, null, size = 20.dp, tint = WT.c.muted)

@Composable
fun SettingsScreen(open: (Screen) -> Unit) {
    var query by remember { mutableStateOf("") }
    PageFrame("Settings", null) {
        Field(
            query, { query = it }, "Search settings", Modifier.fillMaxWidth().padding(top = 12.dp),
            leading = Ic.Search, onClear = { query = "" },
        )
        val q = query.trim()
        if (q.isNotEmpty()) {
            val found = index.filter { e -> val page = pages.firstOrNull { it.id == e.page }?.title.orEmpty(); q.split(' ').all { w -> "${e.title} ${e.words} $page".contains(w, ignoreCase = true) } }
            Group("Results", Modifier.padding(top = 4.dp)) {
                if (found.isEmpty()) SettingRow("Nothing matches \"$q\"", "Try another word, like quality, lyrics or theme")
                found.forEachIndexed { i, e ->
                    if (i > 0) Divider(Modifier.padding(start = 16.dp))
                    SettingRow(e.title, pages.firstOrNull { it.id == e.page }?.title ?: "Account", onClick = { open(Screen.SettingsPage(e.page)) }) { Chevron() }
                }
            }
            return@PageFrame
        }
        Spacer(Modifier.height(16.dp))
        Group {
            val account = Sync.account
            SettingRow(
                title = if (InnerTube.signedIn && account != null) account.name else "Not signed in",
                detail = if (InnerTube.signedIn && account != null) "YouTube Music • syncing both ways" else "Sign in to sync likes, playlists and history",
                onClick = { open(Screen.SettingsPage("account")) },
                trailing = { if (account?.photo != null) Art(account.photo, Modifier.size(40.dp), CircleShape) else Chevron() },
            )
        }
        Group(null, Modifier.padding(top = 20.dp)) {
            pages.forEachIndexed { i, p ->
                if (i > 0) Divider(Modifier.padding(start = 62.dp))
                SettingRow(p.title, p.detail, p.icon, onClick = { open(Screen.SettingsPage(p.id)) }) { Chevron() }
            }
        }
    }
}

@Composable
fun SettingsPageScreen(id: String, open: (Screen) -> Unit, back: () -> Unit) {
    val title = pages.firstOrNull { it.id == id }?.title ?: when (id) { "account" -> "Account"; else -> "Settings" }
    PageFrame(title, back) {
        when (id) {
            "account" -> AccountPage(open)
            "playback" -> PlaybackPage()
            "quality" -> QualityPage(open)
            "lyrics" -> LyricsPage()
            "appearance" -> AppearancePage(open)
            "sources" -> SourcesPage()
            "storage" -> StoragePage()
            "backup" -> BackupPage()
            else -> AboutPage()
        }
    }
}

/** A titled choice of a few options, with an optional line under it that explains the current one. */
@Composable
private fun ChoiceRow(title: String, options: List<Pair<String, String>>, selected: String, detail: String? = null, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Txt(title, Modifier.padding(bottom = 10.dp), style = Type.bodyStrong)
        Segmented(options, selected, onSelect)
        if (detail != null) Txt(detail, Modifier.padding(top = 10.dp), style = Type.caption, color = WT.c.muted)
    }
}

@Composable
private fun Rows(vararg rows: @Composable () -> Unit) = rows.forEachIndexed { i, r ->
    if (i > 0) Divider(Modifier.padding(start = 16.dp))
    r()
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
private fun PlaybackPage() {
    Group("Listening") {
        Rows(
            {
                ToggleRow("Autoplay", "Keep playing similar songs when the queue ends (YouTube songs)", Settings.autoplay) { Settings.autoplay = it }
                if (Settings.autoplay) ChoiceRow(
                    "Autoplay picks", listOf("radio" to "Similar songs", "artist" to "Same artist"), Settings.autoplayMix,
                    if (Settings.autoplayMix == "artist") "More songs by the artist that's playing." else "A radio of songs like the one that started it.",
                ) { Settings.autoplayMix = it }
            },
            { ToggleRow("Normalize volume", "Turns down songs mastered louder than others (YouTube)", Settings.normalize) { Settings.normalize = it } },
            { ToggleRow("Skip silence", "Skips quiet stretches inside songs, like long intros and gaps", Settings.skipSilence) { Settings.skipSilence = it } },
        )
    }
    Group("Headphones and closing") {
        Rows(
            { ToggleRow("Pause when headphones disconnect", "Wired or Bluetooth: music stops instead of playing out loud", Settings.pauseOnUnplug) { Settings.pauseOnUnplug = it } },
            { ToggleRow("Stop when swiped away", "Swiping WeTune out of recent apps stops the music. Off keeps it playing.", Settings.stopOnSwipe) { Settings.stopOnSwipe = it } },
        )
    }
}

@Composable
private fun QualityPage(open: (Screen) -> Unit) {
    val levels = listOf("data" to "Data saver", "high" to "High", "max" to "320", "lossless" to "Lossless")
    fun about(l: String) = when (l) {
        "data" -> "About 70 kbps."
        "high" -> "YouTube's best stream, about 160 kbps."
        "max" -> "JioSaavn's 320 kbps file when the same recording is found, else High."
        else -> "FLAC from your add-ons when found (about 1–3 MB a minute), else 320 or High."
    }
    Group("Maximum quality") {
        Rows(
            { ChoiceRow("On Wi-Fi", levels, Settings.maxWifi, about(Settings.maxWifi)) { Settings.maxWifi = it } },
            { ChoiceRow("On mobile data", levels, Settings.maxMobile, about(Settings.maxMobile)) { Settings.maxMobile = it } },
        )
    }
    Txt(
        "Songs start right away and step up to this ceiling, only when the exact same recording is found. " +
            "A better connection (say, Wi-Fi after mobile data) lifts the songs coming up. Likes, history and recommendations always go to YouTube Music.",
        Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), style = Type.caption, color = WT.c.muted,
    )
    Group(null, Modifier.padding(top = 16.dp)) {
        SettingRow("Sources", "Lossless add-ons and JioSaavn, and the order they're tried", Ic.Server, onClick = { open(Screen.SettingsPage("sources")) }) { Chevron() }
    }
}

@Composable
private fun LyricsPage() {
    Group("Lyrics") {
        Rows(
            { ToggleRow("Word by word", "When the lyrics are timed per word, light each line up letter by letter as it's sung", Settings.lyricsWords) { Settings.lyricsWords = it; Lyrics.clearCache() } },
            { ToggleRow("Fill the one-line lyric", "The lyric above the seek bar fills letter by letter as well", Settings.inlineWords) { Settings.inlineWords = it } },
            { ToggleRow("Romanized inline lyrics", "The lyric line above the seek bar shows Tamil, Hindi, Telugu… in English letters. The full lyrics stay as they are.", Settings.romanInline) { Settings.romanInline = it } },
        )
    }
    Group("Lyrics panel") {
        ChoiceRow(
            "Moving between lines", listOf("glide" to "Glide", "fade" to "Fade", "instant" to "Instant"), Settings.lyricsMotion,
            when (Settings.lyricsMotion) {
                "fade" -> "Scrolls smoothly; lines only brighten and dim."
                "instant" -> "Jumps straight to the next line, no animation."
                else -> "Scrolls smoothly; the line being sung grows a little as it lights up."
            },
        ) { Settings.lyricsMotion = it }
        Divider(Modifier.padding(start = 16.dp))
        ChoiceRow("Alignment", listOf("start" to "Left", "center" to "Center", "end" to "Right"), Settings.lyricsAlign) { Settings.lyricsAlign = it }
        Divider(Modifier.padding(start = 16.dp))
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
    Group("Timing") {
        var count by remember { mutableIntStateOf(Lyrics.offsetCount()) }
        SettingRow(
            "Reset lyric timing", if (count == 0) "No song has its own timing" else "$count song${if (count == 1) "" else "s"} have lyrics moved earlier or later. Put them all back.",
            onClick = if (count == 0) null else ({ Lyrics.resetOffsets(); count = 0; toast("Lyric timing reset") }),
        )
    }
}

// fixed accents: hues, so each stays readable in light and dark (see hueAccent)
private val accents = listOf(-1 to "WeTune", 210 to "Blue", 265 to "Violet", 330 to "Pink", 0 to "Red", 28 to "Orange", 140 to "Green", 175 to "Teal")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppearancePage(open: (Screen) -> Unit) {
    val c = WT.c
    Group("Theme") {
        ChoiceRow("Theme", listOf("system" to "System", "light" to "Light", "dark" to "Dark", "black" to "Black"), Settings.theme) { Settings.theme = it }
        Divider(Modifier.padding(start = 16.dp))
        ChoiceRow(
            "Text size", listOf("90" to "Small", "100" to "Default", "115" to "Large", "130" to "Larger"), Settings.textScale.toString(),
            "On top of your phone's own font size.",
        ) { Settings.textScale = it.toInt() }
    }
    Group("Accent color") {
        ToggleRow("Match the song", "Buttons, pills and highlights take their color from the current cover", Settings.matchAccent) { Settings.matchAccent = it }
        Divider(Modifier.padding(start = 16.dp))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Txt(if (Settings.matchAccent) "When nothing is playing" else "Accent", Modifier.padding(bottom = 10.dp), style = Type.bodyStrong)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                accents.forEach { (hue, name) ->
                    val on = Settings.accentHue == hue
                    val color = if (hue < 0) (if (c.dark) Tokens.Dark else Tokens.Light).accent else hueAccent(hue.toFloat(), c.dark)
                    Box(
                        Modifier.size(48.dp).semantics { contentDescription = name; selected = on }
                            .pressable(scale = 0.9f, role = Role.RadioButton) { Settings.accentHue = hue },
                        Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(34.dp).then(if (on) Modifier.border(2.dp, c.text, CircleShape) else Modifier).padding(if (on) 4.dp else 0.dp)
                                .background(color, CircleShape),
                        )
                    }
                }
            }
        }
    }
    Group("Now Playing") {
        SettingRow("Customize player", "${NowPlayingStyle.nameOf(NpStyleStore.style)} · background, cover, controls and colours, with a live preview", onClick = { NpEditor.open = true }) { Chevron() }
    }
    Group("Layout") {
        ChoiceRow("Open on", listOf("home" to "Home", "library" to "Library"), Settings.startTab, "The tab WeTune shows when it starts.") { Settings.startTab = it }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow("Auto-rotate", "Off keeps WeTune in portrait. On follows your phone's rotation setting.", Settings.autoRotate) { Settings.autoRotate = it }
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
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun StoragePage() {
    val ctx = LocalContext.current
    var used by remember { mutableLongStateOf(-1L) }
    androidx.compose.runtime.LaunchedEffect(Unit) { io { used = App.cache.cacheSpace } }
    fun mb(bytes: Long) = if (bytes >= 1L shl 30) "%.1f GB".format(bytes / (1L shl 30).toDouble()) else "${bytes shr 20} MB"
    Group("Audio cache") {
        ChoiceRow(
            "Cache size", listOf("256" to "256 MB", "512" to "512 MB", "1024" to "1 GB", "2048" to "2 GB", "4096" to "4 GB"), Settings.cacheMb.toString(),
            "Songs you've played stay here, so replays start instantly and use no data. A new size applies the next time WeTune starts.",
        ) { Settings.cacheMb = it.toInt() }
        Divider(Modifier.padding(start = 16.dp))
        SettingRow("Clear cache", if (used < 0) "Checking…" else "${mb(used)} of songs, plus cached covers", onClick = {
            io("Cache cleared", then = { used = 0 }) {
                App.cache.keys.toList().forEach { runCatching { App.cache.removeResource(it) } }
                coil3.SingletonImageLoader.get(ctx).let { it.memoryCache?.clear(); it.diskCache?.clear() }
            }
        }) { Icon(Ic.Delete, null, size = 20.dp, tint = WT.c.muted) }
    }
    Group("Privacy") {
        Rows(
            { ToggleRow("Send plays to YouTube history", "Songs you play show in your YouTube Music history and shape recommendations. Off pauses that.", Settings.sendHistory) { Settings.sendHistory = it } },
            {
                val n = Settings.recentSearches.lines().count { it.isNotBlank() }
                SettingRow("Clear search history", if (n == 0) "No recent searches" else "$n recent search${if (n == 1) "" else "es"} on this phone", onClick = if (n == 0) null else ({ Settings.recentSearches = ""; toast("Search history cleared") }))
            },
        )
    }
}

@Composable
private fun BackupPage() {
    val ctx = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { runCatching { Library.export(it) }.onSuccess { toast("Backup saved") }.onFailure { toast("Couldn't save backup: ${it.message}") } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runCatching { Library.import(it) }.onSuccess { toast("Library restored") }.onFailure { toast("Couldn't restore: ${it.message}") } }
    }
    val exportSettings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            runCatching { ctx.contentResolver.openOutputStream(it)!!.use { o -> o.write(Settings.exportJson().toByteArray()) } }
                .onSuccess { toast("Settings saved") }.onFailure { toast("Couldn't save settings: ${it.message}") }
        }
    }
    val importSettings = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                val n = Settings.importJson(ctx.contentResolver.openInputStream(it)!!.use { i -> i.readBytes().decodeToString() })
                NowPlayingStyle.fromJson(Settings.npStyle)?.let(NpStyleStore::apply) // the player picks up the imported look
                n
            }.onSuccess { n -> toast("$n settings restored") }.onFailure { toast("Couldn't restore settings: ${it.message}") }
        }
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
    Group("App settings") {
        Column(Modifier.padding(16.dp)) {
            Txt(
                "Saves every setting, including your Now Playing look, to move to another phone or try things safely. Your sign-in and Subsonic password are never included.",
                color = WT.c.muted,
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Export", { exportSettings.launch("wetune-settings.json") }, Modifier.weight(1f))
                Btn("Import", { importSettings.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.weight(1f), kind = BtnKind.Secondary)
            }
        }
    }
}

@Composable
private fun AboutPage() {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    Group {
        Rows(
            { SettingRow("WeTune", "Version $version") },
            { SettingRow("Sources", "YouTube Music, JioSaavn, lossless add-ons, Subsonic, this device") },
            { SettingRow("Built with", "Jetpack Compose, Media3, NewPipe Extractor; lyrics from LRCLIB, BetterLyrics and others") },
        )
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
