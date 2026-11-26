package com.wetune

import com.wetune.sources.Card
import com.wetune.sources.Track
import com.wetune.youtube.YouTube
import com.wetune.youtube.correctedQuery
import com.wetune.youtube.findAll
import com.wetune.youtube.items
import com.wetune.youtube.text
import com.wetune.youtube.topResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Real WEB_REMIX search responses (music.youtube.com, Nov 2026, signed out, gl=IN; tracking fields stripped). */
class SearchParseTest {
    private fun fixture(name: String) = JSONObject(javaClass.getResource("/search/$name.json")!!.readText())

    @Test fun songQueryTopResultIsTheSong() {
        val root = fixture("all_birds_of_a_feather")
        val top = topResult(root) as Track
        assertEquals("V9PVRfjEBTI", top.ref)
        assertEquals("BIRDS OF A FEATHER", top.title)
        assertEquals("Billie Eilish", top.artist)
        assertEquals("1B views", top.views)
        assertNull(correctedQuery(root))
    }

    @Test fun artistQueryTopResultIsTheArtist() {
        val top = topResult(fixture("all_arijit_singh")) as Card
        assertEquals("UCDxKh1gFWeYsqePvgVzmPoQ", top.browseId)
        assertEquals("Arijit Singh", top.title)
        assertEquals(true, top.subtitle.startsWith("Artist"))
    }

    @Test fun spellingFixIsReported() {
        val root = fixture("all_birds_of_a_fether")
        assertEquals("birds of a feather", correctedQuery(root))
        assertNull(topResult(root))
    }

    @Test fun songsFilterKeepsYouTubesOrder() {
        val songs = items(fixture("songs_birds_of_a_feather")).tracks
        assertEquals("BIRDS OF A FEATHER", songs[0].title)
        assertEquals("Billie Eilish", songs[0].artist)
        assertEquals(listOf("birds of a feather", "Birds Of A Feather x Midnight City"), songs.drop(1).take(2).map { it.title })
    }

    @Test fun filterParamsMatchTheChipsYouTubeSends() {
        val chips = findAll(fixture("all_birds_of_a_feather"), setOf("chipCloudChipRenderer")).associate { (_, c) ->
            c.optJSONObject("text").text() to java.net.URLDecoder.decode(c.getJSONObject("navigationEndpoint").getJSONObject("searchEndpoint").getString("params"), "UTF-8")
        }
        val names = mapOf("songs" to "Songs", "videos" to "Videos", "albums" to "Albums", "artists" to "Artists", "playlists" to "Community playlists")
        names.forEach { (k, chip) -> assertEquals(k, chips[chip], YouTube.SearchFilters[k]) }
    }
}
