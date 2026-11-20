package com.wetune

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsRankTest {
    private fun v(source: String, title: String = "Hukum", artist: String = "Anirudh Ravichander", synced: Boolean = true, words: Boolean = false, text: String = source) =
        Lyrics.Version(-1, title, artist, "", synced, listOf(Lyrics.Line(if (synced) 0 else -1, text)), source = source, words = words, durationMs = 200_000)

    @Test fun matchingSongComesFirst() {
        val wrong = v("BetterLyrics", title = "Hukum Remix Party", artist = "Someone Else", words = true)
        val right = v("LRCLIB")
        assertEquals("LRCLIB", Lyrics.rank(listOf(wrong, right), 200_000, true, "Hukum", "Anirudh Ravichander").first().source)
    }

    @Test fun automaticPrefersWordByWord() {
        val ranked = Lyrics.rank(listOf(v("LRCLIB"), v("BetterLyrics", words = true)), 200_000, true, "Hukum", "Anirudh Ravichander")
        assertEquals("BetterLyrics", ranked.first().source)
    }

    @Test fun userOrderWins() {
        val ranked = Lyrics.rank(listOf(v("BetterLyrics", words = true), v("KuGou"), v("YouTube Music · Musixmatch")), 200_000, true,
            "Hukum", "Anirudh Ravichander", order = listOf("YouTube Music", "KuGou", "BetterLyrics"))
        assertEquals(listOf("YouTube Music · Musixmatch", "KuGou", "BetterLyrics"), ranked.map { it.source })
    }
}
