package com.wetune

import com.wetune.youtube.artistOf
import com.wetune.youtube.homeChips
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ParseTest {
    private fun run(text: String, page: String? = null) = JSONObject().put("text", text).apply {
        if (page != null) put("navigationEndpoint", JSONObject().put("browseEndpoint", JSONObject().put("browseEndpointContextSupportedConfigs",
            JSONObject().put("browseEndpointContextMusicConfig", JSONObject().put("pageType", page)))))
    }

    @Test fun keepsArtistsYouTubeDoesNotLink() {
        // "Tu Mil Jaaye" as YouTube Music sends it: only two of the four artists link to artist pages
        val runs = listOf(
            run("Sachin-Jigar", "MUSIC_PAGE_TYPE_ARTIST"), run(", "), run("Ed Sheeran"), run(", "),
            run("Arijit Singh", "MUSIC_PAGE_TYPE_ARTIST"), run(" & "), run("Madhubanti Bagchi"), run(" • "),
            run("KING", "MUSIC_PAGE_TYPE_ALBUM"), run(" • "), run("2026"),
        )
        assertEquals("Sachin-Jigar, Ed Sheeran, Arijit Singh, Madhubanti Bagchi", artistOf(runs))
    }

    @Test fun singleLinkedArtist() {
        assertEquals("Anuv Jain", artistOf(listOf(run("Anuv Jain", "MUSIC_PAGE_TYPE_ARTIST"), run(" • "), run("3:20"))))
    }

    @Test fun readsHomeChips() {
        // the chip cloud as YouTube Music's Home sends it (trimmed): a chip without params (the selected one) is skipped
        fun chip(text: String, params: String?) = JSONObject().put("chipCloudChipRenderer", JSONObject()
            .put("text", JSONObject().put("runs", org.json.JSONArray().put(JSONObject().put("text", text))))
            .apply { if (params != null) put("navigationEndpoint", JSONObject().put("browseEndpoint", JSONObject().put("browseId", "FEmusic_home").put("params", params))) })
        val root = JSONObject().put("header", JSONObject().put("chipCloudRenderer", JSONObject().put("chips",
            org.json.JSONArray().put(chip("All", null)).put(chip("Energize", "ggMPOg1")).put(chip("Relax", "ggMPOg2")).put(chip("Energize", "ggMPOg1")))))
        assertEquals(listOf("Energize" to "ggMPOg1", "Relax" to "ggMPOg2"), homeChips(root).map { it.title to it.params })
    }
}
