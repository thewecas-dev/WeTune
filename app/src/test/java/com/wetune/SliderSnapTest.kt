package com.wetune

import com.wetune.ui.player.snapValue
import org.junit.Assert.assertEquals
import org.junit.Test

class SliderSnapTest {
    // 0..100 on a 300px track: 3px per unit; the default pulls within 14px (≈4.7 units), steps of 5 within 7px (≈2.3)
    private fun snap(raw: Float, mark: Int? = 10) = snapValue(raw, 0..100, mark, unitPx = 3f, markPx = 14f, stepPx = 7f)

    @Test fun pullsOntoTheDefault() {
        assertEquals(10, snap(13.9f))
        assertEquals(10, snap(6.2f))
    }

    @Test fun pullsOntoStepsOfFive() {
        assertEquals(35, snap(36.9f))
        assertEquals(35, snap(33.2f))
    }

    @Test fun otherwiseNearestWholeValue() {
        assertEquals(38, snap(37.6f))
        assertEquals(42, snap(42.4f, mark = null))
    }

    @Test fun staysInRange() {
        assertEquals(100, snap(100f))
        assertEquals(-50, snapValue(-50f, -50..50, null, 3f, 14f, 7f))
    }
}
