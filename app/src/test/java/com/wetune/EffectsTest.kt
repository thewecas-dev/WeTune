package com.wetune

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class EffectsTest {
    @After fun off() = Effects.use(Effects.Params())

    private fun sine(hz: Double, frames: Int = 44100, amp: Double = 0.5): ShortArray =
        ShortArray(frames * 2) { i -> (sin(2 * PI * hz * (i / 2) / 44100) * amp * 32767).toInt().toShort() }

    private fun run(input: ShortArray): ShortArray {
        val proc = EffectsProcessor()
        proc.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        proc.flush()
        val buf = ByteBuffer.allocateDirect(input.size * 2).order(ByteOrder.nativeOrder())
        input.forEach { buf.putShort(it) }; buf.flip()
        proc.queueInput(buf)
        val out = proc.output
        return ShortArray(out.remaining() / 2) { out.getShort() }
    }

    private fun rms(s: ShortArray, from: Int = s.size / 2) = sqrt(s.drop(from).sumOf { (it / 32768.0) * (it / 32768.0) } / (s.size - from))

    @Test fun flatPassesThroughUntouched() {
        val x = sine(440.0)
        assertArrayEquals(x, run(x))
    }

    @Test fun bassBoostRaisesLowsNotHighs() {
        Effects.use(Effects.Params(bass = 9f))
        val low = sine(60.0, amp = 0.2); val high = sine(5000.0, amp = 0.2)
        assertTrue("60 Hz louder", rms(run(low)) > rms(low) * 1.3)
        assertTrue("5 kHz about the same (headroom only)", rms(run(high)) in rms(high) * 0.6..rms(high) * 1.1)
    }

    @Test fun eqBandCutsItsFrequency() {
        Effects.use(Effects.Params(eqOn = true, bands = List(10) { if (it == 5) -12f else 0f }))
        val x = sine(1000.0, amp = 0.3)
        assertTrue(rms(run(x)) < rms(x) * 0.4)
    }

    @Test fun hugeBoostNeverClips() {
        Effects.use(Effects.Params(eqOn = true, bands = List(10) { 12f }, preamp = 6f, bass = 12f))
        val out = run(sine(100.0, amp = 0.95))
        assertTrue(out.all { abs(it.toInt()) < 32767 })
    }

    @Test fun monoMakesChannelsEqual() {
        Effects.use(Effects.Params(mono = true))
        val x = ShortArray(2000) { i -> if (i % 2 == 0) 10000 else -4000 }
        val out = run(x)
        for (i in out.indices step 2) assertEquals(out[i].toFloat(), out[i + 1].toFloat(), 1f)
    }

    @Test fun reverbAddsATail() {
        Effects.use(Effects.Params(reverb = 0.6f))
        val click = ShortArray(44100 * 2).also { it[0] = 20000; it[1] = 20000 }
        val out = run(click)
        assertTrue("energy after the click", out.drop(4000).any { abs(it.toInt()) > 20 })
    }
}
