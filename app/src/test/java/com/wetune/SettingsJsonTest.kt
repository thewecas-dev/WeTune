package com.wetune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsJsonTest {
    private val kinds = mapOf("theme" to 's', "normalize" to 'b', "lyrics_size" to 'i', "np_style" to 's')

    @Test fun roundTrip() {
        val values = mapOf("theme" to "black", "normalize" to false, "lyrics_size" to 34, "np_style" to """{"cover":"disc"}""")
        assertEquals(values, parseSettingsJson(settingsJson(values), kinds))
    }

    @Test fun skipsUnknownKeysAndWrongTypes() {
        val json = """{"wetune_settings":1,"values":{"theme":true,"normalize":"yes","lyrics_size":30.5,"cookie":"SAPISID=x","np_style":"{}"}}"""
        assertEquals(mapOf("np_style" to "{}"), parseSettingsJson(json, kinds))
    }

    @Test fun ignoresRemovedLiquidGlassKeys() {
        // files from before Liquid Glass was removed: its keys are no longer settings, so they're skipped
        val json = """{"wetune_settings":1,"values":{"glass_style":"liquid","glass_variant":"clear","glass_solid":true,"theme":"dark"}}"""
        assertEquals(mapOf("theme" to "dark"), parseSettingsJson(json, kinds))
    }

    @Test fun rejectsOtherFiles() {
        assertThrows(IllegalArgumentException::class.java) { parseSettingsJson("""{"playlists":[]}""", kinds) }
    }
}
