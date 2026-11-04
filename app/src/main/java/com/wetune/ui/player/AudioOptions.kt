package com.wetune.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
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
        VersionRow("Automatic", "Best match from your sources, in your order", null, null, pinned == null) { Playback.useVersion(t, null); onDismiss() }
        // usually already fetched in the background while the song plays
        val sources = androidx.compose.runtime.remember(t.id) { Resolver.versionSources(t) }
        val tick by androidx.compose.runtime.produceState(0, sources) { while (sources.any { !it.future.isDone }) { kotlinx.coroutines.delay(150); value++ } ; value++ }
        tick // re-read the lookups as they finish
        // one list, highest quality first, whatever the source; anything new slots into its place as it arrives,
        // and a single row at the bottom says how many sources are still searching
        val found = sources.filter { it.future.isDone }.flatMap { runCatching { it.future.get() }.getOrDefault(emptyList()) } + Resolver.original(t)
        VersionList(t, found.sortedByDescending { rank(it.quality) }, playing, pinned, onDismiss)
        val waiting = sources.count { !it.future.isDone }
        if (waiting > 0) Row(Modifier.padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spinner(size = 14.dp, color = c.muted)
            Txt("Searching $waiting more source${if (waiting > 1) "s" else ""}…", Modifier.padding(start = 10.dp), style = Type.caption, color = c.muted)
        }
    }
}

@Composable
private fun VersionList(t: Track, list: List<Resolver.Version>, playing: String?, pinned: String?, onDismiss: () -> Unit) {
    list.forEach { v ->
        val len = if (v.durationMs > 0) " · %d:%02d".format(v.durationMs / 60_000, v.durationMs / 1000 % 60) else ""
        VersionRow(v.title, v.detail, Triple(v.quality, v.source, len), v.choice == playing, v.choice == pinned) {
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
private fun VersionRow(title: String, detail: String, quality: Triple<String, String, String>?, playing: Boolean?, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(scale = 0.98f, onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, style = Type.bodyStrong, maxLines = 1)
            Txt(detail, style = Type.caption, color = WT.c.muted, maxLines = 1)
            // badge · quality · source (highlighted) · length
            if (quality != null) Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                val (q, source, len) = quality
                Txt(
                    badgeFor(q), Modifier.padding(end = 6.dp).clip(RoundedCornerShape(3.dp)).background(WT.c.text.copy(alpha = 0.8f)).padding(horizontal = 3.dp),
                    style = Type.overline.copy(fontSize = 8.sp, letterSpacing = 0.3.sp, lineHeight = 11.sp), color = WT.c.bg, weight = FontWeight.Bold,
                )
                Txt(q, style = Type.caption, color = WT.c.text.copy(alpha = 0.8f), maxLines = 1)
                Txt(" · ", style = Type.caption, color = WT.c.muted)
                Txt(source, Modifier.weight(1f, fill = false), style = Type.caption, color = WT.c.accent, weight = FontWeight.SemiBold, maxLines = 1)
                Txt(len, style = Type.caption, color = WT.c.muted, maxLines = 1)
            }
        }
        if (playing == true) Icon(Ic.Volume, "Playing", Modifier.padding(start = 8.dp), size = 18.dp, tint = WT.c.accent)
        Row(Modifier.padding(start = 10.dp).size(24.dp), verticalAlignment = Alignment.CenterVertically) { CheckMark(chosen) }
    }
}

/** Sort key for a quality label: lossless above lossy; more bits / kHz / kbps higher ("FLAC · 24-bit · 48 kHz" > "AAC · 320 kbps"). */
private fun rank(quality: String): Int {
    val q = quality.lowercase()
    if ("flac" in q || "lossless" in q || "alac" in q) {
        val bits = Regex("""(\d+)-bit""").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 16
        val khz = Regex("""([\d.]+) khz""").find(q)?.groupValues?.get(1)?.toFloatOrNull() ?: 44.1f
        return 100_000 + bits * 1_000 + (khz * 10).toInt()
    }
    val kbps = Regex("""(\d+) kbps""").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return kbps * 10 - (if ("up to" in q) 1 else 0) // a fixed 160 kbps file edges out YouTube's "up to 160"
}

/** The same badge the player shows: HI-RES (over 16-bit or 48 kHz), LOSSLESS, HQ (256 kbps and up) or SQ. */
private fun badgeFor(quality: String): String {
    val r = rank(quality)
    return when {
        r >= 100_000 -> if (Regex("""(\d+)-bit""").find(quality)?.groupValues?.get(1)?.toIntOrNull()?.let { it > 16 } == true ||
            (Regex("""([\d.]+) kHz""").find(quality)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f) > 48f) "HI-RES" else "LOSSLESS"
        r >= 2_560 -> "HQ"
        else -> "SQ"
    }
}
