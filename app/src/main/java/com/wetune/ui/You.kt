package com.wetune.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wetune.Library
import com.wetune.Playback
import com.wetune.Settings
import com.wetune.io
import com.wetune.sources.Addons
import com.wetune.toast
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Sync

@Composable
fun YouScreen() {
    val c = WT.c
    val state = rememberLazyListState()
    ReselectToTop(state)
    val signedIn = InnerTube.signedIn
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = statusTop(), bottom = Dock.bottomPadding(Playback.current != null))) {
            item("title", "title") { PageTitle("You") }
            item("account", "account") {
                if (!signedIn) SignInCard()
                else {
                    // the account; tapping it opens Account & sync (sync status, history, sign out)
                    val a = Sync.account
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().glass(RoundedCornerShape(12.dp))
                            .press("Account and sync", scale = 0.985f) { Nav.open(Route.Settings("account")) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (a?.photo != null) Cover(a.photo, 56.dp, 28.dp, description = "Profile photo")
                        else Box(Modifier.size(56.dp).background(c.surfaceHigh, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.You, c.onSecondary, 28.dp) }
                        Column(Modifier.weight(1f)) {
                            Txt(a?.name ?: "YouTube Music", Type.title.copy(fontSize = Type.title.fontSize * 0.9f))
                            Spacer(Modifier.height(2.dp))
                            SyncStatus()
                        }
                        Icon(Icons.Chevron, c.onTertiary, 14.dp)
                    }
                }
            }
            item("settings", "group") {
                Column {
                    Caption("Settings")
                    Group {
                        PAGES.forEachIndexed { i, (key, title, ic) ->
                            GroupRow(title, ic = ic, last = i == PAGES.lastIndex) { Nav.open(Route.Settings(key)) }
                        }
                    }
                }
            }
        }
        CollapsedBar("You", state)
    }
}

/** The settings pages, as listed on the You page (one level deep: no page links to another). */
private val PAGES = listOf(
    Triple("general", "General", Icons.Gear),
    Triple("nowplaying", "Now Playing", Icons.Play),
    Triple("lyrics", "Lyrics", Icons.Lyrics),
    Triple("songmenu", "Song menu", Icons.More),
    Triple("playback", "Playback", Icons.Audio),
    Triple("sources", "Sources", Icons.Radio),
    Triple("storage", "Storage & backup", Icons.Download),
    Triple("about", "About", Icons.Sparkle),
)
private val TITLES = PAGES.associate { it.first to it.second } + ("account" to "Account & sync")

/** Levels match Resolver.level(): what each step reaches when the source has it. */
private val LEVELS = listOf(
    "lossless" to "Lossless · FLAC from add-ons",
    "max" to "High · 320 kbps",
    "high" to "Normal · 160 kbps",
    "data" to "Data saver · 70 kbps",
)

@Composable
fun SettingsScreen(page: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = statusTop(), bottom = Dock.bottomPadding(Playback.current != null) + 16.dp)) {
        item("back", "back") { BackTitle(TITLES[page] ?: page) }
        item("body", "body") {
            Column {
                when (page) {
                    "general" -> General()
                    "nowplaying" -> com.wetune.ui.player.NowPlayingSettingsBody()
                    "lyrics" -> com.wetune.ui.player.LyricsSettingsBody()
                    "songmenu" -> SongMenuSettingsBody()
                    "playback" -> PlaybackSettings()
                    "sources" -> Sources()
                    "storage" -> Storage()
                    "account" -> Account()
                    else -> About()
                }
            }
        }
    }
}

/** One open dropdown at a time on a page; [drop] draws one with its default marked. */
private class Drops { var open by mutableStateOf<String?>(null) }

@Composable
private fun Drops.drop(title: String, key: String, options: List<Pair<String, String>>, value: String, last: Boolean = false, set: (String) -> Unit) =
    com.wetune.ui.player.Dropdown(title, options, value, open == key, last = last, key = key, onToggle = { open = if (open == key) null else key }) { set(it); open = null }

@Composable
private fun General() {
    val d = remember { Drops() }
    Caption("Look")
    Group {
        d.drop("Theme", "theme", listOf("system" to "Automatic", "light" to "Light", "dark" to "Dark"), Settings.theme) { Settings.theme = it }
        ToggleRow("Rotate with the phone", Settings.autoRotate) { Settings.autoRotate = it }
        d.drop("Library layout", "library_layout", listOf("list" to "List", "grid" to "Grid", "grid3" to "Compact grid"), Settings.libraryLayout, last = true) { Settings.libraryLayout = it }
    }
    Note("Automatic follows the phone's dark mode. With rotation off, WeTune stays upright.")
    com.wetune.ui.player.GlassSettingsBody()
}

@Composable
private fun PlaybackSettings() {
    val d = remember { Drops() }
    Caption("Quality")
    Group {
        d.drop("On Wi-Fi", "max_wifi", LEVELS, Settings.maxWifi) { Settings.maxWifi = it }
        d.drop("On mobile data", "max_mobile", LEVELS, Settings.maxMobile, last = true) { Settings.maxMobile = it }
    }
    Note("The best step a source has the song in; otherwise the best YouTube copy plays.")
    Caption("Sound")
    Group {
        ToggleRow("Even out loudness", Settings.normalize) { Settings.normalize = it }
        ToggleRow("Skip silence", Settings.skipSilence, last = true) { Settings.skipSilence = it }
    }
    Caption("When the queue ends")
    Group {
        ToggleRow("Keep playing similar songs", Settings.autoplay, last = !Settings.autoplay) { Settings.autoplay = it }
        if (Settings.autoplay) d.drop("Play", "autoplay_mix", listOf("radio" to "Songs like it", "artist" to "More by the artist"), Settings.autoplayMix, last = true) { Settings.autoplayMix = it }
    }
    Caption("Headphones & the app")
    Group {
        ToggleRow("Pause when headphones disconnect", Settings.pauseOnUnplug) { Settings.pauseOnUnplug = it }
        ToggleRow("Stop when swiped from recent apps", Settings.stopOnSwipe, last = true) { Settings.stopOnSwipe = it }
    }
}

@Composable
private fun Account() {
    Caption("Sync")
    Group {
        GroupRow("Sync now", ic = Icons.Synced, value = if (Sync.pulling) "Syncing…" else null, chevron = false) { Sync.pull(force = true) }
        GroupRow("Waiting to sync", ic = Icons.Offline, value = if (Sync.pending == 0) "Nothing" else "${Sync.pending}", chevron = false)
        GroupRow(
            "Last synced", ic = Icons.Check, chevron = false, last = true,
            value = if (Sync.lastPull == 0L) "Never" else DateUtils.getRelativeTimeSpanString(Sync.lastPull, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
        )
    }
    Caption("Privacy")
    Group { ToggleRow("Add plays to YouTube Music history", Settings.sendHistory, last = true) { Settings.sendHistory = it } }
    Note("Your history shapes YouTube Music's recommendations, here and everywhere you use it.")
    Group(Modifier.padding(top = 22.dp)) {
        GroupRow("Sign out", ic = Icons.SignOut, chevron = false, last = true) {
            Settings.cookie = ""
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            Sync.signedOut()
            Nav.back()
        }
    }
}

@Composable
private fun Sources() {
    Caption("Better copies")
    Group { ToggleRow("JioSaavn (320 kbps)", Settings.saavnOn, last = true) { Settings.saavnOn = it } }
    Caption("Lossless add-ons")
    Group {
        Addons.list.forEach { a ->
            GroupRow(a.name, value = a.version, chevron = false, trailing = {
                Spacer(Modifier.size(8.dp))
                Toggle(a.enabled, "Use ${a.name}") { Addons.setEnabled(a, it) }
            }) { Sheet.show { SheetTitle(a.name); MenuRow(Icons.Trash, "Remove add-on") { Addons.remove(a) } } }
        }
        GroupRow("Add an add-on", ic = Icons.Plus, chevron = false, last = true) { Sheet.show { AddAddon() } }
    }
    Caption("Your server")
    Group {
        GroupRow("Subsonic / Navidrome", value = Settings.subsonicUrl.ifBlank { "Not set" }, last = true) { Sheet.show { SubsonicSheet() } }
    }
}

@Composable
private fun AddAddon() {
    var link by remember { mutableStateOf("") }
    Column(Modifier.imePadding().padding(horizontal = 20.dp)) {
        SheetTitle("Add an add-on")
        Field(link, { link = it }, "Add-on link (…/manifest.json)")
        Spacer(Modifier.height(12.dp))
        BigButton("Add", null, primary = true, modifier = Modifier.fillMaxWidth()) {
            val l = link.trim()
            if (l.isNotEmpty()) { Sheet.hide(); io(success = "Add-on added") { Addons.add(l) } }
        }
    }
}

@Composable
private fun SubsonicSheet() {
    var url by remember { mutableStateOf(Settings.subsonicUrl) }
    var user by remember { mutableStateOf(Settings.subsonicUser) }
    var pass by remember { mutableStateOf(Settings.subsonicPass) }
    Column(Modifier.imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetTitle("Subsonic / Navidrome")
        Field(url, { url = it }, "Server address (https://…)")
        Field(user, { user = it }, "Username")
        Field(pass, { pass = it }, "Password", password = true)
        BigButton("Save", null, primary = true, modifier = Modifier.fillMaxWidth()) {
            Settings.subsonicUrl = url.trim(); Settings.subsonicUser = user.trim(); Settings.subsonicPass = pass
            Sheet.hide()
        }
    }
}

@Composable
private fun Storage() {
    val ctx = LocalContext.current
    fun write(uri: Uri?, text: () -> String) = uri?.let { u -> io(success = "Saved") { ctx.contentResolver.openOutputStream(u)?.use { it.write(text().toByteArray()) } } }
    val exportLib = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> io(success = "Library saved") { Library.export(u) } } }
    val importLib = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> io(success = "Library restored") { Library.import(u) } } }
    val exportSet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { write(it) { Settings.exportJson() } }
    val importSet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> io { val text = ctx.contentResolver.openInputStream(u)?.use { it.readBytes().decodeToString() } ?: return@io; toast("${Settings.importJson(text)} settings applied") } }
    }
    Caption("Space")
    val d = remember { Drops() }
    Group { d.drop("Song cache", "cache_mb", listOf("256" to "256 MB", "512" to "512 MB", "1024" to "1 GB", "2048" to "2 GB"), Settings.cacheMb.toString(), last = true) { Settings.cacheMb = it.toInt() } }
    Note("Takes effect the next time WeTune starts.")
    Caption("On-device library")
    Group {
        GroupRow("Back up library", chevron = false) { exportLib.launch("wetune-library.json") }
        GroupRow("Restore library", chevron = false, last = true) { importLib.launch(arrayOf("application/json")) }
    }
    Caption("Settings")
    Group {
        GroupRow("Save settings to a file", chevron = false) { exportSet.launch("wetune-settings.json") }
        GroupRow("Load settings from a file", chevron = false, last = true) { importSet.launch(arrayOf("application/json")) }
    }
}

@Composable
private fun About() {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    Spacer(Modifier.height(16.dp))
    Group {
        GroupRow("Version", value = version, chevron = false)
        GroupRow("Source code", last = true) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thewecas-dev/WeTune")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    Note("Plays YouTube Music with your account, synced both ways. Not affiliated with Google or YouTube.")
}

@Composable
private fun ToggleRow(title: String, on: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) =
    GroupRow(title, chevron = false, last = last, trailing = { Spacer(Modifier.size(8.dp)); Toggle(on, title, onChange) })

@Composable
private fun Note(text: String) = Txt(text, Type.sub, Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp), color = WT.c.onSecondary, lines = 3)

