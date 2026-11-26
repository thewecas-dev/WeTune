package com.wetune

import com.wetune.youtube.YouTube
import com.wetune.youtube.findFirst
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A real WEB_REMIX artist page (music.youtube.com, Nov 2026, signed out, gl=IN; tracking fields stripped). */
class ArtistParseTest {
    private val root = JSONObject(javaClass.getResource("/artist/arijit_singh.json")!!.readText())

    @Test fun aboutHasAudienceBioAndSource() {
        val a = YouTube.about(root)!!
        assertEquals("655M monthly audience", a.stat)
        assertTrue(a.bio.startsWith("Arijit Singh is an Indian singer"))
        assertFalse(a.bio.contains("Wikipedia"))
        assertFalse(a.bio.contains("Creative Commons"))
        assertEquals("From Wikipedia", a.source)
    }

    @Test fun followUsesTheButtonsChannelNotThePageId() {
        val sub = findFirst(root, "subscribeButtonRenderer") as JSONObject
        assertEquals("UCtFOW7jJXChfFNoucRFqRmw", sub.optString("channelId"))
        assertEquals(false, sub.optBoolean("subscribed"))
    }

    @Test fun noInfoIsNull() = assertNull(YouTube.about(JSONObject()))
}
