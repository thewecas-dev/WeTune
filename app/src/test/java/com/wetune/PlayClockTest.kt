package com.wetune

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayClockTest {
    @Test fun advancesWhilePlaying() {
        val k = PlayClock(pos = 10_000, atMs = 1_000, playing = true, speed = 1f, duration = 200_000)
        assertEquals(10_000, k.at(1_000))
        assertEquals(10_016, k.at(1_016))
        assertEquals(12_500, k.at(3_500))
        assertEquals(10_000, k.at(900)) // a frame stamped just before the anchor never goes back
    }

    @Test fun stopsWhenPaused() {
        val k = PlayClock(42_000, 1_000, playing = false, speed = 1f, duration = 200_000)
        assertEquals(42_000, k.at(60_000))
    }

    @Test fun followsSpeed() {
        assertEquals(13_000, PlayClock(10_000, 0, true, 1.5f, 0).at(2_000))
        assertEquals(10_500, PlayClock(10_000, 0, true, 0.5f, 0).at(1_000))
    }

    @Test fun clampsToSong() {
        assertEquals(200_000, PlayClock(199_000, 0, true, 1f, 200_000).at(5_000))
        assertEquals(500_000, PlayClock(499_000, 0, true, 1f, 0).at(1_000)) // length unknown: no ceiling
        assertEquals(0, PlayClock(-50, 0, false, 1f, 200_000).at(0))
    }
}
