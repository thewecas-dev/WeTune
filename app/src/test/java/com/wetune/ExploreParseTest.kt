package com.wetune

import com.wetune.youtube.YouTube
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real WEB_REMIX Explore and charts pages (music.youtube.com, Nov 2026, signed out, gl=IN; tracking fields stripped). */
class ExploreParseTest {
    private fun fixture(name: String) = JSONObject(javaClass.getResource("/search/$name.json")!!.readText())

    @Test fun explorePage() {
        val x = YouTube.parseExplore(fixture("explore_in"), fixture("charts_in"))
        assertEquals("Midnight in Paris", x.newReleases.first().title)
        assertEquals(24, x.newReleases.size)
        assertTrue(x.trending.size >= 10)
        assertTrue(x.trending.first().title.startsWith("60 Lakh"))
        assertEquals("Jaadugari (AMAN)", x.videos.first().title)
        assertEquals(listOf("Chill", "Commute", "Energize", "Feel good"), x.moods.take(4).map { it.title })
        assertTrue(x.moods.none { it.title == "New releases" || it.title == "Charts" }) // the page's own buttons aren't moods
        assertEquals("Trending 20 India", x.charts.first().title)
        assertTrue(x.charts.all { it.subtitle.startsWith("Chart") })
        assertEquals("Alka Yagnik", x.artists.first().title)
    }

    @Test fun withoutCharts() {
        val x = YouTube.parseExplore(fixture("explore_in"), null)
        assertTrue(x.charts.isEmpty() && x.artists.isEmpty() && x.newReleases.isNotEmpty())
    }
}
