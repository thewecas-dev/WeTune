package com.wetune

import com.wetune.youtube.shortTotals
import org.junit.Assert.assertEquals
import org.junit.Test

class LengthLineTest {
    @Test fun shortensHeaderTotals() {
        assertEquals("14 songs · 56 min", shortTotals("14 songs • 56 minutes"))
        assertEquals("24 songs · 1 h 22 min", shortTotals("24 songs • 1 hour, 22 minutes"))
        assertEquals("1,000+ songs · 70+ h", shortTotals("1,000+ songs • 70+ hours"))
        assertEquals("1 song · 1 min", shortTotals("1 song • 1 minute"))
    }
}
