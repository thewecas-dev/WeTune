package com.wetune

import org.junit.Assert.assertEquals
import org.junit.Test

class FullWidthMigrationTest {
    @Test fun oldFullWidthBackgroundBecomesTheCoverStyle() {
        // the cover's look moves to the style; the background stays the cover-edge colour it drew
        assertEquals("tone" to "edge", fullWidthMigration("edge", "glow"))
    }

    @Test fun otherSettingsAreLeftAlone() {
        assertEquals("flow" to "glow", fullWidthMigration("flow", "glow"))
        assertEquals("tone" to "plain", fullWidthMigration("tone", "plain")) // the new edge-colour background on its own
        assertEquals("tone" to "edge", fullWidthMigration("tone", "edge")) // already migrated: runs again harmlessly
    }
}
