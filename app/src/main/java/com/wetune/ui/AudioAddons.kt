package com.wetune.ui

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wetune.Effects
import com.wetune.toast

/** Settings → Audio add-ons: equalizer, tone, space (width, reverb, 8D), remix (speed/pitch) and transitions. */
@Composable
fun AudioAddonsPage() {
    val p = Effects.params
    val ctx = LocalContext.current

    Group("Remix") {
        PillRow(Effects.REMIXES.map { it.first }, Effects.REMIXES.firstOrNull { (_, v) -> v.first == p.speed && v.second == p.pitch && v.third == p.reverb }?.first) { name ->
            val (speed, pitch, reverb) = Effects.REMIXES.first { it.first == name }.second
            Effects.update { it.copy(speed = speed, pitch = pitch, reverb = reverb) }
        }
        Slider("Speed", "%.2f×".format(p.speed), (p.speed - 0.5f) / 1.5f) { f -> Effects.update { it.copy(speed = step(0.5f + f * 1.5f, 0.05f)) } }
        Slider("Pitch", "%.2f×".format(p.pitch), (p.pitch - 0.5f) / 1.5f) { f -> Effects.update { it.copy(pitch = step(0.5f + f * 1.5f, 0.05f)) } }
    }

    Group("Equalizer") {
        ToggleRow("Equalizer", "10 bands, 31 Hz to 16 kHz", p.eqOn) { on -> Effects.update { it.copy(eqOn = on) } }
        if (p.eqOn) {
            PillRow(Effects.PRESETS.map { it.first }, Effects.PRESETS.firstOrNull { it.second == p.bands }?.first) { name ->
                Effects.update { it.copy(bands = Effects.PRESETS.first { pr -> pr.first == name }.second) }
            }
            Slider("Preamp", "%+.0f dB".format(p.preamp), (p.preamp + 12f) / 18f) { f -> Effects.update { it.copy(preamp = step(-12f + f * 18f, 1f)) } }
            Effects.BANDS.forEachIndexed { i, hz ->
                val label = if (hz >= 1000) "${(hz / 1000).toInt()} kHz" else "${hz.toInt()} Hz"
                Slider(label, "%+.0f dB".format(p.bands[i]), (p.bands[i] + 12f) / 24f) { f ->
                    Effects.update { it.copy(bands = it.bands.toMutableList().also { b -> b[i] = step(-12f + f * 24f, 1f) }) }
                }
            }
        }
    }

    Group("Tone") {
        Slider("Bass boost", if (p.bass == 0f) "Off" else "+%.0f dB".format(p.bass), p.bass / 12f) { f -> Effects.update { it.copy(bass = step(f * 12f, 1f)) } }
        Slider("Treble", if (p.treble == 0f) "Off" else "%+.0f dB".format(p.treble), (p.treble + 6f) / 15f) { f -> Effects.update { it.copy(treble = step(-6f + f * 15f, 1f)) } }
    }

    Group("Space") {
        Slider("Stereo width", if (p.mono) "Mono" else "%.0f%%".format(p.width * 100), p.width / 2f) { f -> Effects.update { it.copy(width = step(f * 2f, 0.1f)) } }
        Slider("Balance", when { p.balance < 0f -> "L %.0f".format(-p.balance * 100); p.balance > 0f -> "R %.0f".format(p.balance * 100); else -> "Center" }, (p.balance + 1f) / 2f) { f ->
            Effects.update { it.copy(balance = step(-1f + f * 2f, 0.05f)) }
        }
        Slider("Reverb", if (p.reverb == 0f) "Off" else "%.0f%%".format(p.reverb * 100), p.reverb) { f -> Effects.update { it.copy(reverb = step(f, 0.05f)) } }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow("Mono", "Both ears hear the same mix (one earbud, or a single speaker)", p.mono) { on -> Effects.update { it.copy(mono = on) } }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow("8D audio", "The sound slowly circles around your head. Best with headphones.", p.eightD) { on -> Effects.update { it.copy(eightD = on) } }
        if (p.eightD) Slider("Circling speed", "%.0f s per turn".format(1f / p.eightDRate), (p.eightDRate - 0.04f) / 0.36f) { f ->
            Effects.update { it.copy(eightDRate = step(0.04f + f * 0.36f, 0.01f)) }
        }
    }

    Group("Transitions") {
        Slider("Fade between songs", if (p.fadeSec == 0f) "Off" else "%.0f s".format(p.fadeSec), p.fadeSec / 10f) { f -> Effects.update { it.copy(fadeSec = step(f * 10f, 1f)) } }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow("Skip silence", "Jumps over quiet gaps in songs", p.skipSilence) { on -> Effects.update { it.copy(skipSilence = on) } }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow("Sleep timer fades out", "Music fades over the timer's last 30 seconds instead of stopping dead", p.sleepFade) { on -> Effects.update { it.copy(sleepFade = on) } }
    }

    Group("Phone") {
        SettingRow("Phone's own equalizer", "Dolby, SoundAlive or the system equalizer, applied after WeTune's add-ons", onClick = {
            val i = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, Effects.sessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, ctx.packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            runCatching { ctx.startActivity(i) }.onFailure { toast("This phone has no system equalizer") }
        }) { Icon(Ic.Chevron, null, size = 20.dp, tint = WT.c.muted) }
    }

    Btn("Reset all add-ons", { Effects.reset() }, Modifier.fillMaxWidth().padding(top = 20.dp), kind = BtnKind.Secondary)
    Txt(
        "Everything here is applied on your phone as the music plays. With all add-ons off, the sound passes through untouched.",
        Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp), style = Type.caption, color = WT.c.muted,
    )
}

private fun step(v: Float, by: Float): Float = (Math.round(v / by) * by).let { if (kotlin.math.abs(it) < by / 2) 0f else it }

@Composable
private fun Slider(title: String, value: String, fraction: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(title, Modifier.weight(1f), style = Type.body)
            Txt(value, style = Type.caption.copy(fontFeatureSettings = "tnum"), color = WT.c.muted)
        }
        SeekSlider(fraction.coerceIn(0f, 1f), { onChange(it.coerceIn(0f, 1f)) }, {}, Modifier.fillMaxWidth().padding(top = 8.dp), color = WT.c.accent, thumb = true)
    }
}

@Composable
private fun PillRow(names: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { n -> Pill(n, n == selected, { onSelect(n) }) }
        Row(Modifier.width(4.dp)) {}
    }
}
