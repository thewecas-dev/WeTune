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
    var n = 0
    Group("Sources, tried in this order") {
        addons.forEach { a ->
            n++
            AddonRow(n, a, first = a == addons.first(), last = a == addons.last())
            Divider(Modifier.padding(start = 62.dp))
        }
        n++
        SourceRow(n, Ic.Volume, "JioSaavn", "320 kbps · May match the wrong version of a song now and then") {
            ToggleKnob(Settings.saavnOn) { Settings.saavnOn = it }
        }
        Divider(Modifier.padding(start = 62.dp))
        n++
        SourceRow(n, Ic.Play, "YouTube Music", "Lossy · Full catalogue · Radio") { Txt("Always on", style = Type.caption, color = WT.c.muted) }
        Divider(Modifier.padding(start = 62.dp))
        AddAddon()
    }
    Txt(
        "A song starts on YouTube and moves up this list as far as Maximum quality (Settings → Playback) allows. " +
            "Add-ons are servers run by other people: they can be slow to wake, go offline, or stop working at any time.",
        Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp), style = Type.caption, color = WT.c.muted,
    )
}

@Composable
private fun SourceRow(n: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(scale = 0.98f, onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("$n", Modifier.width(20.dp), style = Type.body, color = WT.c.muted)
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
        if (!first) MenuItem("Move up", { Addons.move(a, -1); menu = false }, Ic.ArrowUp)
        if (!last) MenuItem("Move down", { Addons.move(a, 1); menu = false }, Ic.ArrowDown)
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
