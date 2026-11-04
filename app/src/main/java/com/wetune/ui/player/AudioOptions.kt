package com.wetune.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wetune.AudioInfo
import com.wetune.Playback
import com.wetune.Resolver
import com.wetune.sources.Track

/**
 * Tap the quality tag: what's playing (source and quality) and every other version WeTune found — JioSaavn's and each
 * add-on's results, plus the YouTube original — so a wrong match can be swapped for the right one. The choice is
 * remembered for the song. "Audio pipeline" opens the full signal path.
 */
@Composable
fun AudioOptionsSheet(t: Track, onPipeline: () -> Unit, onDismiss: () -> Unit) {
    val c = WT.c
    val playing = if (t.source == "yt") Resolver.playingChoice(t.ref, AudioInfo.url) else null
    val pinned = if (t.source == "yt") Resolver.pin(t.ref) else null
    Sheet("Audio", onDismiss, actions = { Btn("Done", onDismiss, kind = BtnKind.Ghost) }) {
        // now playing
        Column(Modifier.fillMaxWidth().glassChip(RoundedCornerShape(16.dp)).padding(14.dp)) {
            Txt("PLAYING NOW", style = Type.overline, color = c.muted)
            Txt(nowSource(t, playing), Modifier.padding(top = 4.dp), style = Type.bodyStrong)
            Txt(AudioInfo.label ?: "Loading…", style = Type.caption, color = c.muted)
        }
        Btn("Audio pipeline", onPipeline, Modifier.fillMaxWidth().padding(top = 10.dp), kind = BtnKind.Secondary, icon = Ic.Pipeline)

        if (t.source != "yt") return@Sheet Txt("This song has only one version.", Modifier.padding(top = 16.dp), style = Type.caption, color = c.muted)
        Txt("OTHER VERSIONS", Modifier.padding(top = 20.dp, bottom = 4.dp), style = Type.overline, color = c.muted)
        Txt("Pick the right one if the song playing isn't the recording you wanted. WeTune remembers it for this song.", style = Type.caption, color = c.muted)
        VersionRow("Automatic", "Best match from your sources, in your order", null, pinned == null) { Playback.useVersion(t, null); onDismiss() }
        // each source shows its versions as soon as it answers (usually already fetched in the background);
        // a source still searching shows a small spinner by its name
        val sources = androidx.compose.runtime.remember(t.id) { Resolver.versionSources(t) }
        val tick by androidx.compose.runtime.produceState(0, sources) { while (sources.any { !it.future.isDone }) { kotlinx.coroutines.delay(150); value++ } ; value++ }
        tick // re-read the lookups as they finish
        sources.forEach { s ->
            val done = s.future.isDone
            val list = if (done) runCatching { s.future.get() }.getOrDefault(emptyList()) else emptyList()
            SourceHeader(s.name, loading = !done)
            if (done && list.isEmpty()) Txt("No versions found", Modifier.padding(start = 4.dp, bottom = 6.dp), style = Type.caption, color = c.muted)
            VersionList(t, list, playing, pinned, onDismiss)
        }
        SourceHeader("YouTube", loading = false)
        VersionList(t, listOf(Resolver.original(t)), playing, pinned, onDismiss)
    }
}

@Composable
private fun VersionList(t: Track, list: List<Resolver.Version>, playing: String?, pinned: String?, onDismiss: () -> Unit) {
    list.forEach { v ->
        val len = if (v.durationMs > 0) " · %d:%02d".format(v.durationMs / 60_000, v.durationMs / 1000 % 60) else ""
        VersionRow(v.title, listOf(v.detail, v.quality).filter(String::isNotBlank).joinToString(" · ") + len, v.choice == playing, v.choice == pinned) {
            Playback.useVersion(t, v.choice); onDismiss()
        }
        Divider(Modifier.padding(start = 4.dp))
    }
}

private fun nowSource(t: Track, playing: String?): String = when {
    t.source == "local" -> "This phone"
    t.source == "subsonic" -> "Your server"
    playing == null || playing == "yt" -> "YouTube"
    playing.startsWith("saavn|") -> "JioSaavn"
    else -> Resolver.copyName(t.ref)?.substringAfter(" · ") ?: "Add-on"
}

@Composable
private fun VersionRow(title: String, detail: String, playing: Boolean?, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(scale = 0.98f, onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, style = Type.bodyStrong, maxLines = 1)
            Txt(detail, style = Type.caption, color = WT.c.muted, maxLines = 2)
        }
        if (playing == true) Icon(Ic.Volume, "Playing", Modifier.padding(start = 8.dp), size = 18.dp, tint = WT.c.accent)
        Row(Modifier.padding(start = 10.dp).size(24.dp), verticalAlignment = Alignment.CenterVertically) { CheckMark(chosen) }
    }
}

/** A source's name over its versions, with a small spinner while it's still searching. */
@Composable
private fun SourceHeader(name: String, loading: Boolean) {
    Row(Modifier.padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(name.uppercase(), style = Type.overline, color = WT.c.accent)
        if (loading) Spinner(Modifier.padding(start = 8.dp), size = 12.dp, color = WT.c.muted)
    }
}
