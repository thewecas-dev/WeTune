package com.wetune

import com.wetune.lyrics.Query
import com.wetune.lyrics.kugouLines
import com.wetune.lyrics.neteaseLines
import com.wetune.lyrics.parseYtLyrics
import com.wetune.sources.Track
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsers for the lyrics providers, run on responses saved from the live services (trimmed). */
class LyricsProvidersTest {
    private fun res(name: String) = javaClass.getResource("/lyrics/$name")!!.readText()

    @Test fun kugouDropsHeaderAndCredits() {
        val lines = kugouLines(res("kugou_download.json"), "Blinding Lights")
        assertEquals("Yeah", lines.first().text)
        assertEquals(14_070L, lines.first().ms)
        assertEquals("I've been tryna call", lines[1].text)
    }

    @Test fun neteaseWordTimed() {
        val (lines, words) = neteaseLines(res("netease_lyric.json"), "Blinding Lights")
        assertTrue(words)
        assertEquals("Yeah", lines[0].text)
        assertEquals("I've been tryna call", lines[1].text)
        assertEquals(27_360L, lines[1].ms)
        assertEquals(28_650L, lines[1].endMs)
        assertEquals(listOf("I've ", "been ", "tryna ", "call"), lines[1].words!!.map { it.text })
        assertEquals(27_600L, lines[1].words!![1].ms)
    }

    @Test fun neteaseLrcWithoutCredits() {
        val (lines, words) = neteaseLines(res("netease_lyric_lrc_only.json"), "Blinding Lights")
        assertFalse(words)
        assertEquals("Yeah", lines.first().text)
        assertEquals(13_564L, lines.first().ms)
    }

    @Test fun lrcRedTtml() {
        val lines = Lyrics.parseTtml(res("lrcred.ttml"))
        assertEquals("I been tryna call", lines.single().text)
        assertEquals(27_395L, lines.single().ms)
        assertEquals(4, lines.single().words!!.size)
    }

    @Test fun youtubeMusicPlain() {
        val (lines, source) = parseYtLyrics(JSONObject(res("ytm_web.json")))!!
        assertEquals("Musixmatch", source)
        assertEquals("Yeah", lines.first().text)
        assertEquals(-1L, lines.first().ms)
        val (static, src) = parseYtLyrics(JSONObject(res("ytm_android_static.json")))!!
        assertEquals("Musixmatch", src)
        assertEquals(4, static.size)
        assertTrue(static.all { it.ms == -1L })
    }

    @Test fun youtubeMusicTimed() {
        val page = JSONObject("""{"contents":{"elementRenderer":{"newElement":{"type":{"componentType":{"model":{"timedLyricsModel":{"lyricsData":{
            "timedLyricsData":[{"lyricLine":"Yeah","cueRange":{"startTimeMilliseconds":"14000","endTimeMilliseconds":"15000","metadata":{"id":"1"}}},
            {"lyricLine":"I've been tryna call","cueRange":{"startTimeMilliseconds":"27360","endTimeMilliseconds":"29000","metadata":{"id":"2"}}}],
            "sourceMessage":"Source: LyricFind"}}}}}}}}}""")
        val (lines, source) = parseYtLyrics(page)!!
        assertEquals("LyricFind", source)
        assertEquals(27_360L, lines[1].ms)
        assertEquals(29_000L, lines[1].endMs)
    }

    @Test fun queryCleanup() {
        fun q(title: String, artist: String, album: String = "") = Query.of(Track("yt", "x", title, artist, album, durationMs = 200_000))
        q("The Weeknd - Blinding Lights (Official Video)", "TheWeekndVEVO").let { assertEquals("Blinding Lights", it.title); assertEquals("The Weeknd", it.artist) }
        q("Blinding Lights (Official Audio)", "The Weeknd").let { assertEquals("Blinding Lights", it.title) }
        q("Kesariya (From \"Brahmastra\")", "Pritam, Arijit Singh", "Brahmastra").let {
            assertEquals("Kesariya (From \"Brahmastra\")", it.title); assertEquals("Kesariya", it.bare); assertEquals("Pritam", it.artist)
        }
        q("Peaches (feat. Daniel Caesar & Giveon)", "Justin Bieber", "Justice").let { assertEquals("Peaches", it.title) }
        q("Let It Be - Remastered 2009", "The Beatles", "Let It Be").let { assertEquals("Let It Be", it.title) }
        q("Song Title | Movie Name | Singer", "T-Series").let { assertEquals("Song Title", it.title) }
    }

    @Test fun rankWordsThenSyncedThenPlainByLength() {
        fun v(src: String, synced: Boolean, words: Boolean, d: Long, text: String = src) =
            Lyrics.Version(-1, "", "", "", synced, listOf(Lyrics.Line(if (synced) 0 else -1, text)), source = src, words = words, durationMs = d)
        val ranked = Lyrics.rank(
            listOf(v("plain", false, false, 200_000), v("far", true, false, 260_000), v("near", true, false, 201_000),
                v("word", true, true, 230_000), v("dup", true, false, 201_000, "near")),
            200_000, words = true,
        )
        assertEquals(listOf("word", "near", "far", "plain"), ranked.map { it.source })
    }
}
