package com.wetune

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Audio add-ons: equalizer, bass/treble, stereo width, balance, mono, reverb, 8D, speed/pitch, skip silence and fades.
 * The sound settings run in [EffectsProcessor] inside the player; speed, pitch, silence and fades are applied by the
 * playback service. With everything off the audio passes through untouched.
 */
object Effects {
    data class Params(
        val eqOn: Boolean = false,
        val bands: List<Float> = List(BANDS.size) { 0f }, // dB, −12…12
        val preamp: Float = 0f, // dB, −12…6
        val bass: Float = 0f, // dB, 0…12 (low shelf)
        val treble: Float = 0f, // dB, −6…9 (high shelf)
        val width: Float = 1f, // 0 = mono … 1 = as recorded … 2 = extra wide
        val balance: Float = 0f, // −1 left … 1 right
        val mono: Boolean = false,
        val reverb: Float = 0f, // 0…1
        val eightD: Boolean = false,
        val eightDRate: Float = 0.12f, // turns per second
        val speed: Float = 1f,
        val pitch: Float = 1f,
        val skipSilence: Boolean = false,
        val fadeSec: Float = 0f, // fade out / in between songs, 0 = off
        val sleepFade: Boolean = true, // the sleep timer fades out over its last 30 s
    ) {
        /** True when the processor has nothing to do. */
        val soundFlat: Boolean
            get() = (!eqOn || (bands.all { it == 0f } && preamp == 0f)) && bass == 0f && treble == 0f && width == 1f &&
                balance == 0f && !mono && reverb == 0f && !eightD
    }

    /** Equalizer band centers in Hz (ISO octaves). */
    val BANDS = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    val PRESETS: List<Pair<String, List<Float>>> = listOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Warm" to listOf(3f, 3f, 2f, 1f, 0f, 0f, -1f, -1f, -2f, -2f),
        "Vocal" to listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f),
        "Bright" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 3f, 4f, 5f),
        "Pop" to listOf(-1f, 1f, 3f, 4f, 2f, 0f, -1f, -1f, 1f, 2f),
        "Rock" to listOf(4f, 3f, 2f, 0f, -1f, -1f, 1f, 3f, 4f, 4f),
        "Electronic" to listOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 1f, 4f, 5f),
        "Classical" to listOf(3f, 2f, 1f, 1f, 0f, 0f, 0f, 1f, 2f, 3f),
        "Loudness" to listOf(5f, 3f, 0f, 0f, -1f, 0f, 0f, 1f, 3f, 4f),
    )

    /** Remix presets: speed, pitch and reverb together. */
    val REMIXES = listOf(
        Triple("Normal", Triple(1f, 1f, 0f), "As recorded"),
        Triple("Slowed + reverb", Triple(0.85f, 0.85f, 0.45f), "Slower, deeper, roomy"),
        Triple("Nightcore", Triple(1.25f, 1.25f, 0f), "Faster and higher"),
        Triple("Sped up", Triple(1.15f, 1.15f, 0f), "A little faster"),
        Triple("Daycore", Triple(0.8f, 0.8f, 0.2f), "Slow and low"),
    )

    private val prefs by lazy { App.ctx.getSharedPreferences("effects", Context.MODE_PRIVATE) }

    /** The settings as the UI sees them. */
    private fun saved(): Params = runCatching { fromJson(JSONObject(prefs.getString("p", "{}")!!)) }.getOrDefault(Params())

    // loaded on first use by whoever touches it first (settings screen or playback service)
    var params by mutableStateOf(saved())
        private set

    /** Same settings for the audio thread (a plain volatile read per buffer). */
    @Volatile var current = params
        private set

    /** The player's audio session, so the system equalizer / Dolby panel can attach to it. */
    @Volatile var sessionId = C.AUDIO_SESSION_ID_UNSET

    /** Applies [p] without saving it (tests; [update] saves). */
    internal fun use(p: Params) { params = p; current = p }

    fun update(f: (Params) -> Params) {
        val p = f(params)
        use(p)
        prefs.edit().putString("p", toJson(p).toString()).apply()
    }

    fun reset() = update { Params() }

    /** One line for the audio pipeline sheet. */
    fun summary(p: Params = params): String = buildList {
        if (p.eqOn && !(p.bands.all { it == 0f } && p.preamp == 0f)) add("EQ " + (PRESETS.firstOrNull { it.second == p.bands }?.first ?: "custom"))
        if (p.bass > 0f) add("bass +%.0f dB".format(p.bass))
        if (p.treble != 0f) add("treble %+.0f dB".format(p.treble))
        if (p.mono) add("mono") else if (p.width != 1f) add("width %.0f%%".format(p.width * 100))
        if (p.balance != 0f) add("balance %+.0f".format(p.balance * 100))
        if (p.reverb > 0f) add("reverb %.0f%%".format(p.reverb * 100))
        if (p.eightD) add("8D")
        if (p.speed != 1f || p.pitch != 1f) add("speed %.2f× pitch %.2f×".format(p.speed, p.pitch))
        if (p.skipSilence) add("skip silence")
        if (p.fadeSec > 0f) add("fades %.0f s".format(p.fadeSec))
    }.joinToString(" · ").ifEmpty { "Off" }

    private fun toJson(p: Params) = JSONObject()
        .put("eqOn", p.eqOn).put("bands", JSONArray(p.bands.map { it.toDouble() })).put("preamp", p.preamp.toDouble())
        .put("bass", p.bass.toDouble()).put("treble", p.treble.toDouble()).put("width", p.width.toDouble())
        .put("balance", p.balance.toDouble()).put("mono", p.mono).put("reverb", p.reverb.toDouble())
        .put("eightD", p.eightD).put("eightDRate", p.eightDRate.toDouble()).put("speed", p.speed.toDouble())
        .put("pitch", p.pitch.toDouble()).put("skipSilence", p.skipSilence).put("fadeSec", p.fadeSec.toDouble())
        .put("sleepFade", p.sleepFade)

    private fun fromJson(o: JSONObject): Params {
        val d = Params()
        fun f(k: String, def: Float) = o.optDouble(k, def.toDouble()).toFloat()
        val bands = o.optJSONArray("bands")?.let { a -> List(BANDS.size) { i -> a.optDouble(i, 0.0).toFloat() } } ?: d.bands
        return Params(
            o.optBoolean("eqOn", d.eqOn), bands, f("preamp", d.preamp), f("bass", d.bass), f("treble", d.treble),
            f("width", d.width), f("balance", d.balance), o.optBoolean("mono", d.mono), f("reverb", d.reverb),
            o.optBoolean("eightD", d.eightD), f("eightDRate", d.eightDRate), f("speed", d.speed), f("pitch", d.pitch),
            o.optBoolean("skipSilence", d.skipSilence), f("fadeSec", d.fadeSec), o.optBoolean("sleepFade", d.sleepFade),
        )
    }
}

/** Second-order filter (RBJ cookbook), transposed direct form II. */
class Biquad {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
    private var z1 = 0f; private var z2 = 0f
    var active = false
        private set

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() { z1 = 0f; z2 = 0f }
    fun off() { active = false }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = (nb0 / na0).toFloat(); b1 = (nb1 / na0).toFloat(); b2 = (nb2 / na0).toFloat()
        a1 = (na1 / na0).toFloat(); a2 = (na2 / na0).toFloat(); active = true
    }

    fun peaking(rate: Int, f0: Float, db: Float, q: Double = 1.41) {
        if (db == 0f || f0 >= rate / 2f) return off()
        val a = 10.0.pow(db / 40.0); val w = 2 * PI * f0 / rate; val alpha = sin(w) / (2 * q)
        set(1 + alpha * a, -2 * cos(w), 1 - alpha * a, 1 + alpha / a, -2 * cos(w), 1 - alpha / a)
    }

    fun shelf(rate: Int, f0: Float, db: Float, high: Boolean) {
        if (db == 0f || f0 >= rate / 2f) return off()
        val a = 10.0.pow(db / 40.0); val w = 2 * PI * f0 / rate; val cw = cos(w)
        val alpha = sin(w) / 2 * sqrt(2.0) // shelf slope 1
        val s = 2 * sqrt(a) * alpha
        if (!high) set(a * ((a + 1) - (a - 1) * cw + s), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - s),
            (a + 1) + (a - 1) * cw + s, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - s)
        else set(a * ((a + 1) + (a - 1) * cw + s), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - s),
            (a + 1) - (a - 1) * cw + s, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - s)
    }
}

/** Small Freeverb-style room: four damped combs and two all-passes per channel. */
class Room(rate: Int, spread: Int) {
    private val scale = rate / 44100.0
    private val combs = intArrayOf(1116, 1188, 1277, 1356).map { FloatArray(((it + spread) * scale).toInt().coerceAtLeast(1)) }
    private val combPos = IntArray(4); private val combLow = FloatArray(4)
    private val passes = intArrayOf(556, 441).map { FloatArray(((it + spread) * scale).toInt().coerceAtLeast(1)) }
    private val passPos = IntArray(2)

    fun process(x: Float): Float {
        var out = 0f
        val input = x * 0.2f
        for (i in 0 until 4) {
            val buf = combs[i]; val p = combPos[i]
            val y = buf[p]
            combLow[i] = y * 0.8f + combLow[i] * 0.2f // damping
            buf[p] = input + combLow[i] * 0.84f
            combPos[i] = if (p + 1 == buf.size) 0 else p + 1
            out += y
        }
        for (i in 0 until 2) {
            val buf = passes[i]; val p = passPos[i]
            val b = buf[p]
            buf[p] = out + b * 0.5f
            out = b - out
            passPos[i] = if (p + 1 == buf.size) 0 else p + 1
        }
        return out
    }
}

@OptIn(UnstableApi::class)
class EffectsProcessor : BaseAudioProcessor() {
    private var rate = 44100
    private var channels = 2
    private var applied: Effects.Params? = null
    private val eq = Array(2) { Array(Effects.BANDS.size) { Biquad() } }
    private val bass = Array(2) { Biquad() }
    private val treble = Array(2) { Biquad() }
    private var rooms: Array<Room>? = null
    private var phase = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // 16-bit mono/stereo is what the player decodes to; anything else passes by untouched
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) return AudioProcessor.AudioFormat.NOT_SET
        rate = inputAudioFormat.sampleRate; channels = inputAudioFormat.channelCount
        applied = null; rooms = null
        return inputAudioFormat
    }

    private fun setup(p: Effects.Params) {
        for (c in 0 until 2) {
            Effects.BANDS.forEachIndexed { i, f -> eq[c][i].peaking(rate, f, if (p.eqOn) p.bands[i] else 0f) }
            bass[c].shelf(rate, 90f, p.bass, high = false)
            treble[c].shelf(rate, 7500f, p.treble, high = true)
        }
        if (p.reverb > 0f && rooms == null) rooms = arrayOf(Room(rate, 0), Room(rate, 23))
        if (p.reverb == 0f) rooms = null
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val p = Effects.current
        if (p !== applied) { setup(p); applied = p }
        val out = replaceOutputBuffer(size)
        if (p.soundFlat) { out.put(inputBuffer); out.flip(); return }

        val pre = if (p.eqOn) 10f.pow(p.preamp / 20f) else 1f
        // EQ and bass boosts add level; pull the input down by part of the largest boost so the limiter rarely works hard
        val boost = maxOf(0f, if (p.eqOn) (p.bands.maxOrNull() ?: 0f) + p.preamp else 0f, p.bass, p.treble)
        val headroom = pre * 10f.pow(-boost * 0.35f / 20f)
        val width = if (p.mono) 0f else p.width
        val balL = if (p.balance > 0f) 1f - p.balance else 1f
        val balR = if (p.balance < 0f) 1f + p.balance else 1f
        val wet = p.reverb * 0.9f
        val dry = 1f - p.reverb * 0.35f
        val step = 2 * PI * p.eightDRate / rate
        val room = rooms
        val eqL = eq[0]; val eqR = eq[1]

        val frames = size / (2 * channels)
        for (n in 0 until frames) {
            var l = inputBuffer.getShort() / 32768f
            var r = if (channels == 2) inputBuffer.getShort() / 32768f else l
            l *= headroom; r *= headroom
            for (i in eqL.indices) if (eqL[i].active) { l = eqL[i].process(l); r = eqR[i].process(r) }
            if (bass[0].active) { l = bass[0].process(l); r = bass[1].process(r) }
            if (treble[0].active) { l = treble[0].process(l); r = treble[1].process(r) }
            if (width != 1f) { val m = (l + r) * 0.5f; val s = (l - r) * 0.5f * width; l = m + s; r = m - s }
            if (p.eightD) {
                // the sound circles the head: equal-power pan swinging left ↔ right, slightly narrowed so it reads as movement
                phase += step; if (phase > 2 * PI) phase -= 2 * PI
                val pan = sin(phase).toFloat()
                val a = (pan + 1f) * (PI / 4).toFloat()
                val m = (l + r) * 0.5f
                l = (m * 0.6f + l * 0.4f) * cos(a) * 1.41f
                r = (m * 0.6f + r * 0.4f) * sin(a) * 1.41f
            }
            if (room != null) { l = l * dry + room[0].process(l) * wet; r = r * dry + room[1].process(r) * wet }
            l *= balL; r *= balR
            if (channels == 1) l = (l + r) * 0.5f
            out.putShort(toPcm(limit(l)))
            if (channels == 2) out.putShort(toPcm(limit(r)))
        }
        out.flip()
    }

    override fun onFlush() {
        eq.forEach { ch -> ch.forEach { it.reset() } }; bass.forEach { it.reset() }; treble.forEach { it.reset() }
        rooms = null; applied = null
    }

    private companion object {
        /** Soft limiter: clean below 0.85, then a smooth knee up to 0.98 so boosts never crackle. */
        fun limit(x: Float): Float {
            val a = abs(x)
            if (a <= 0.85f) return x
            val y = 0.85f + 0.13f * tanh((a - 0.85f) / 0.13f)
            return if (x < 0) -y else y
        }

        fun toPcm(x: Float): Short = (x * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }
}
