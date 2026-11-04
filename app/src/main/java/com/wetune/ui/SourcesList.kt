package com.wetune.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wetune.Settings
import com.wetune.io
import com.wetune.sources.Addons

/**
 * Settings → Sources: where songs can come from, tried in this order when a song steps up in quality —
 * the user's lossless add-ons, then JioSaavn (320 kbps), then YouTube Music (always on; search, radio, the catalogue).
 */
@Composable
fun SourcesList() {
    val addons = Addons.list
    val order = com.wetune.Resolver.fullOrder()
    Group("Sources, checked in this order") {
        order.forEachIndexed { i, key ->
            val first = i == 0; val last = i == order.lastIndex
            if (key == "saavn") SaavnRow(i + 1, first, last)
            else addons.firstOrNull { it.id == key }?.let { AddonRow(i + 1, it, first, last) }
            Divider(Modifier.padding(start = 62.dp))
        }
        SourceRow(order.size + 1, Ic.Play, "YouTube Music", "Lossy · Full catalogue · Radio · the fallback") { Txt("Always on", style = Type.caption, color = WT.c.muted) }
        Divider(Modifier.padding(start = 62.dp))
        AddAddon()
    }
    Txt(
        "For each song WeTune checks these from the top, up to Maximum quality (Settings → Playback), and plays the first one " +
            "that has the exact same recording. Playback never waits long: if nothing better is ready within about a second, " +
            "the song starts on what is, and the better copy is used the next time. Tap a source to move it. " +
            "Add-ons are servers run by other people: they can be slow to wake, go offline, or stop working at any time.",
        Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp), style = Type.caption, color = WT.c.muted,
    )
    SourceTester()
}

/** Settings → Sources → Test: checks every source with one song, so you can see which ones work and what they give. */
@Composable
private fun SourceTester() {
    val now = com.wetune.Playback.current
    var query by remember { mutableStateOf(now?.let { "${it.title} ${it.artist.substringBefore(',')}" } ?: "Blinding Lights The Weeknd") }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<com.wetune.sources.SourceTest.Result>?>(null) }
    Group("Test sources") {
        Column(Modifier.padding(16.dp)) {
            Txt("Searches every source for one song, then checks the top result really streams.", style = Type.caption, color = WT.c.muted)
            Field(query, { query = it }, "Song and artist", Modifier.fillMaxWidth().padding(top = 10.dp), onClear = { query = "" })
            Btn(if (busy) "Testing… (sleeping servers can take a minute)" else "Run test", {
                if (busy || query.isBlank()) return@Btn
                busy = true; results = null
                io { try { results = com.wetune.sources.SourceTest.run(query.trim()) } finally { busy = false } }
            }, Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !busy)
        }
        results?.forEach { r ->
            Divider(Modifier.padding(start = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                Txt(if (r.ok) "✓" else "✕", Modifier.width(24.dp), style = Type.bodyStrong, color = if (r.ok) WT.c.accent else WT.c.danger)
                Column(Modifier.weight(1f)) {
                    Txt(r.source, style = Type.bodyStrong, maxLines = 1)
                    r.quality?.let { Txt(it, style = Type.caption, color = WT.c.text) }
                    r.found?.let { Txt(it, style = Type.caption, color = WT.c.muted, maxLines = 1) }
                    r.problem?.let { Txt(it, style = Type.caption, color = WT.c.danger, maxLines = 3) }
                    if (r.searchMs > 0) Txt("Search %.1f s".format(r.searchMs / 1000f) + if (r.streamMs > 0) " · stream %.1f s".format(r.streamMs / 1000f) else "", style = Type.caption, color = WT.c.muted)
                }
            }
        }
    }
}

@Composable
private fun SaavnRow(n: Int, first: Boolean, last: Boolean) {
    var menu by remember { mutableStateOf(false) }
    SourceRow(n, Ic.Volume, "JioSaavn", "AAC · 320 kbps · May match the wrong version of a song now and then", onClick = { menu = true }) {
        ToggleKnob(Settings.saavnOn) { Settings.saavnOn = it }
    }
    Menu(menu, { menu = false }, "JioSaavn") {
        if (!first) MenuItem("Move up", { com.wetune.Resolver.move("saavn", -1); menu = false }, Ic.ArrowUp)
        if (!last) MenuItem("Move down", { com.wetune.Resolver.move("saavn", 1); menu = false }, Ic.ArrowDown)
        if (first && last) MenuItem("Add an add-on to change the order", { menu = false }, enabled = false)
    }
}

@Composable
private fun SourceRow(n: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(scale = 0.98f, onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(if (n > 0) "$n" else "", Modifier.width(20.dp), style = Type.body, color = WT.c.muted)
        Icon(icon, null, Modifier.padding(start = 4.dp, end = 14.dp), size = 22.dp)
        Column(Modifier.weight(1f)) {
            Txt(title, style = Type.bodyStrong, maxLines = 1)
            Txt(detail, style = Type.caption, color = WT.c.muted, maxLines = 3)
        }
        Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

@Composable
private fun AddonRow(n: Int, a: Addons.Addon, first: Boolean, last: Boolean) {
    var menu by remember { mutableStateOf(false) }
    val quality = a.qualities.firstOrNull { it.first == a.quality }?.second
    val detail = listOfNotNull(
        "v${a.version}".takeIf { a.version.isNotBlank() }, "FLAC · Lossless", quality,
        if (a.downloads) "Downloads allowed" else "Streaming only",
    ).joinToString(" · ")
    SourceRow(n, Ic.Server, a.name, detail, onClick = { menu = true }) { ToggleKnob(a.enabled) { Addons.setEnabled(a, it) } }
    Menu(menu, { menu = false }, a.name) {
        if (!first) MenuItem("Move up", { com.wetune.Resolver.move(a.id, -1); menu = false }, Ic.ArrowUp)
        if (!last) MenuItem("Move down", { com.wetune.Resolver.move(a.id, 1); menu = false }, Ic.ArrowDown)
        a.qualities.forEach { (value, label) ->
            MenuItem(if (value == a.quality) "✓  $label" else label, { Addons.setQuality(a, value); menu = false })
        }
        MenuItem("Refresh", { menu = false; io("Updated ${a.name}") { Addons.add(a.base) } })
        MenuItem("Remove", { Addons.remove(a); menu = false }, Ic.Delete, danger = true)
    }
}

@Composable
private fun AddAddon() {
    var open by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    SourceRow(0, Ic.Plus, "Add an add-on", "Paste the link of an add-on server you host or were given", onClick = { open = !open }) {}
    if (open) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Field(link, { link = it }, "https://…", Modifier.fillMaxWidth(), keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Btn(if (busy) "Connecting… (free servers can take a minute to wake)" else "Add", {
            if (busy || link.isBlank()) return@Btn
            busy = true
            io {
                try {
                    val a = Addons.add(link)
                    link = ""; open = false
                    com.wetune.toast("Added ${a.name}")
                } finally { busy = false } // errors still reach the toast from io()
            }
        }, Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !busy)
    }
}

@Composable
private fun ToggleKnob(checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.pressable(scale = 0.95f, role = androidx.compose.ui.semantics.Role.Switch) { onChange(!checked) }) { SwitchKnob(checked) }
}
