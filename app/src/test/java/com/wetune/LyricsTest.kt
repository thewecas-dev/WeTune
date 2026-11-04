package com.wetune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsTest {
    private val wordTtml = """<tt xmlns="http://www.w3.org/ns/ttml" itunes:timing="Word"><body dur="3:21.570"><div begin="27.395" end="48.621">""" +
        """<p begin="27.395" end="28.960" itunes:key="L1" ttm:agent="v1"><span begin="27.395" end="27.549">I</span> <span begin="27.549" end="27.740">been</span> <span begin="27.740" end="28.077">tryna</span> <span begin="28.077" end="28.960">call</span></p>""" +
        """<p begin="1:30.189" end="1:32.529"><span begin="1:31.839" end="1:31.996">e</span><span begin="1:31.996" end="1:32.529">nough &amp; more</span></p></div></body></tt>"""

    @Test fun wordTimedTtml() {
        val lines = Lyrics.parseTtml(wordTtml)
        assertEquals(2, lines.size)
        assertEquals("I been tryna call", lines[0].text)
        assertEquals(27_395L, lines[0].ms)
        assertEquals(28_960L, lines[0].endMs)
        assertEquals(listOf("I ", "been ", "tryna ", "call"), lines[0].words!!.map { it.text })
        assertEquals(27_740L, lines[0].words!![2].ms)
        // syllables without a space join into one word; minutes parse; entities decode
        assertEquals("enough & more", lines[1].text)
        assertEquals(91_839L, lines[1].words!![0].ms)
    }

    @Test fun lineTimedTtml() {
        val lines = Lyrics.parseTtml("""<tt itunes:timing="Line"><body><p begin="00:00:12.5" end="00:00:15">Tum hi ho</p></body></tt>""")
        assertEquals("Tum hi ho", lines.single().text)
        assertEquals(12_500L, lines.single().ms)
        assertNull(lines.single().words)
    }

    @Test fun times() {
        assertEquals(1_500L, Lyrics.ttmlTime("1.5s"))
        assertEquals(62_345L, Lyrics.ttmlTime("1:02.345"))
        assertEquals(3_723_000L, Lyrics.ttmlTime("01:02:03"))
    }
}
