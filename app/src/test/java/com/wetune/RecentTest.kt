package com.wetune

import com.wetune.sources.Card
import com.wetune.sources.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentTest {
    @Test fun newestFirstDedupedCapped() {
        val list = listOf("b", "A", "c")
        assertEquals(listOf("a", "b", "c"), pushRecent(list, "a", 10) { it.lowercase() })
        assertEquals(listOf("d", "b"), pushRecent(list, "d", 2) { it })
    }

    @Test fun songsAndPagesDedupeByIdAndRoundTrip() {
        val song = Track("yt", "abc", "Song", "Artist", art = "https://x/a.jpg")
        val album = Card("MPREb_1", "Album", "Album • Artist", null)
        val items = listOf(song, album).map { recentOf(recentJson(it)) }
        assertEquals(listOf(song, album), items)
        assertEquals(listOf<Any>("MPREb_1", "yt:abc"), pushRecent(listOf(song, album), album.copy(title = "Renamed"), 20, ::recentKey).map(::recentKey))
        assertNull(recentOf("not json"))
    }

    @Test fun videosKeepTheirKind() {
        val clip = Video(Track("yt", "vid", "Clip", "Channel"))
        assertEquals(clip, recentOf(recentJson(clip)))
        val song = Track("yt", "vid", "Clip", "Channel")
        // the same id picked as a song later replaces the video entry (one row per id), newest first
        assertEquals(listOf<Any>(song), pushRecent(listOf<Any>(clip), song, 20, ::recentKey))
    }
}
