package com.wetune

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMatchTest {
    @Test fun namesMatchAcrossEditions() {
        assertTrue(Motion.sameName("After Hours (Deluxe)", "After Hours"))
        assertTrue(Motion.sameName("Ez-Ez (From \"Dhurandhar\")", "Ez-Ez"))
        assertFalse(Motion.sameName("Starboy", "After Hours"))
    }

    @Test fun artistsMatchWhenOneIsShared() {
        assertTrue(Motion.sameArtist("The Weeknd", "The Weeknd"))
        assertTrue(Motion.sameArtist("Diljit Dosanjh, Hanumankind, Raj Ranjodh", "Diljit Dosanjh & Hanumankind"))
        assertFalse(Motion.sameArtist("Arijit Singh", "The Weeknd"))
    }
}
