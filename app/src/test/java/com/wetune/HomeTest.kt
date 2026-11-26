package com.wetune

import com.wetune.sources.Card
import com.wetune.sources.Track
import com.wetune.ui.interleave
import com.wetune.ui.distinctCovers
import com.wetune.ui.fillSlots
import com.wetune.ui.greeting
import com.wetune.ui.listRows
import com.wetune.ui.withoutSongs
import com.wetune.ui.rankArtists
import com.wetune.ui.shelfName
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTest {
    private fun kind(s: String) = s.first() // "s1" = songs, "c2" = cards…

    @Test fun neverTwoOfAKindInARow() {
        val feed = listOf("s1", "c1", "c2", "c3", "a1", "c4", "s2")
        assertEquals(listOf("s1", "c1", "a1", "c2", "s2", "c3", "c4"), interleave(feed, done = true, ::kind))
    }

    @Test fun holdsBackUntilDoneAndOnlyAppends() {
        val page1 = listOf("s1", "c1", "c2")
        val first = interleave(page1, done = false, ::kind)
        assertEquals(listOf("s1", "c1"), first)
        val later = interleave(page1 + listOf("c3", "a1"), done = false, ::kind)
        assertEquals(first, later.take(first.size)) // what was shown stays where it was
        assertEquals(listOf("s1", "c1", "a1", "c2"), later)
    }

    @Test fun namesShelvesOurWay() {
        assertEquals("Top picks for you" to "", shelfName("Quick picks", "", "Songs for you"))
        assertEquals("More like Arijit Singh" to "", shelfName("Arijit Singh", "SIMILAR TO", "Playlists"))
        // unknown shelves: named by content, YouTube's theme kept as the small line
        assertEquals("Playlists" to "Hindi Hits", shelfName("Hindi Hits", "", "Playlists"))
        assertEquals("Playlists" to "Discover mix", shelfName("Discover mix", "", "Playlists"))
    }

    @Test fun slotsTakeTheirShelvesTheRestKeepOrder() {
        val feed = listOf("Quick picks", "Hindi Hits", "Forgotten favorites", "Trending songs for you", "Mixed for you", "Old school")
        val (taken, left) = fillSlots(feed, listOf<(String) -> Boolean>({ "Trending" in it }, { "Mixed" in it }, { "Forgotten" in it }, { "album" in it }))
        assertEquals(listOf("Trending songs for you", "Mixed for you", "Forgotten favorites", null), taken)
        assertEquals(listOf("Quick picks", "Hindi Hits", "Old school"), left)
    }

    @Test fun ranksArtistsByPlays() {
        fun a(n: String) = Card("UC$n", n, "Artist", null)
        var n = 0
        fun t(by: String) = Track("yt", "v${n++}", "song", by)
        val library = listOf(a("Alka Yagnik"), a("Anirudh Ravichander"), a("Lady Gaga"), a("Bruno Mars"))
        val played = listOf(t("Lady Gaga, Bruno Mars"), t("Anirudh Ravichander"), t("Anirudh Ravichander"), t("anirudh ravichander"), t("Lady Gaga"))
        // most played first; never-played ones keep their library order after
        assertEquals(listOf("Anirudh Ravichander", "Lady Gaga", "Bruno Mars", "Alka Yagnik"), rankArtists(library, played).map { it.title })
    }

    @Test fun eachMoodGetsItsOwnPicture() {
        // every mood's feed opens on the same shared shelf ("shared"); each should still get a cover of its own
        val romance = listOf("shared", "r1", "r2")
        val relax = listOf("shared", "x1")
        val feelGood = listOf("shared")
        assertEquals(listOf("r1", "x1", "shared", null), distinctCovers(listOf(romance, relax, feelGood, emptyList())))
    }

    @Test fun greetsByTimeOfDay() {
        assertEquals("Good evening, Vikas", greeting("Vikas M", hour = 19))
        assertEquals("Good morning", greeting(null, hour = 7))
        assertEquals("Good night", greeting("  ", hour = 2))
    }

    @Test fun moodFeedIsTheMoodsOwn() {
        // FEmusic_home with the Workout chip's own params: the request music.youtube.com makes when the chip is tapped
        // (signed out, gl=IN, trimmed). Its shelves are the mood's, not the general Home's
        val s = com.wetune.youtube.sections(JSONObject(javaClass.getResource("/home/mood_workout.json")!!.readText()))
        assertEquals(listOf("In the gym", "Cardio", "Outdoor"), s.map { it.title })
        assertEquals("Bollywood Workout", s[0].cards.first().title)
        assertTrue(s.all { it.cards.size == 10 })
    }

    @Test fun listShelvesShowSongsAndAlbumsAndDeDupeNeverGuts() {
        fun t(n: Int) = Track("yt", "v$n", "song $n", "someone")
        fun c(n: Int) = Card("MPRE$n", "album $n", "Album", null)
        // Not heard in a while: one song and four albums used to show as one row
        val forgotten = com.wetune.youtube.Section("Forgotten favorites", listOf(t(1)), listOf(c(1), c(2), c(3), c(4)))
        assertEquals(5, listRows(forgotten).size)
        // Played before minus Jump back in's songs: fine while 4+ items stay, skipped when it would leave fewer
        val again = com.wetune.youtube.Section("Listen again", (1..6).map(::t), emptyList())
        assertEquals(listOf("v3", "v4", "v5", "v6"), withoutSongs(again, setOf("yt:v1", "yt:v2")).tracks.map { it.ref })
        assertEquals(6, withoutSongs(again, setOf("yt:v1", "yt:v2", "yt:v3")).tracks.size)
    }
}
