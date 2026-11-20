package com.wetune

import com.wetune.ui.player.lyricScript
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricScriptTest {
    @Test fun detectsScripts() {
        assertEquals("TAMIL", lyricScript("Oh baby உன் பார்வை"))
        assertEquals("DEVANAGARI", lyricScript("तेरी (ez-ez) बातें"))
        assertEquals("JAPANESE", lyricScript("夜に駆ける かな"))
        assertEquals("CHINESE", lyricScript("月亮代表我的心"))
        assertEquals("HANGUL", lyricScript("사랑해 love"))
        assertEquals(null, lyricScript("Plain English, café"))
    }
}
