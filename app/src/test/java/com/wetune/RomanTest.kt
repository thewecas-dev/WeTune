package com.wetune

import org.junit.Assert.assertEquals
import org.junit.Test

class RomanTest {
    private fun r(s: String) = Roman.convert(s)

    @Test fun hindi() {
        assertEquals("dil", r("दिल"))
        assertEquals("dilbar", r("दिलबर"))
        assertEquals("samajhna", r("समझना"))
        assertEquals("dharkan", r("धड़कन"))
        assertEquals("zindagi", r("ज़िंदगी"))
        assertEquals("main hoon", r("मैं हूँ"))
        assertEquals("tera pyaar", r("तेरा प्यार"))
        assertEquals("kamal", r("कमल"))
        assertEquals("tum hi ho", r("तुम ही हो"))
        assertEquals("Tum (dil)", r("Tum (दिल)"))
    }

    @Test fun hindiWords() {
        assertEquals("hum tum", r("हम तुम"))
        assertEquals("tum hi ho, mein", r("तुम ही हो, में"))
        assertEquals("nahin", r("नहीं"))
    }

    @Test fun telugu() {
        assertEquals("prema", r("ప్రేమ"))
        assertEquals("nuvvu", r("నువ్వు"))
        assertEquals("naa manasu", r("నా మనసు"))
    }

    @Test fun kannada() {
        assertEquals("kannada", r("ಕನ್ನಡ"))
        assertEquals("neenu", r("ನೀನು"))
        assertEquals("hrudaya", r("ಹೃದಯ"))
    }

    @Test fun malayalam() {
        assertEquals("ente", r("എന്റെ"))
        assertEquals("aanu", r("ആണ്"))
        assertEquals("malayaalam", r("മലയാളം"))
        assertEquals("avan", r("അവൻ"))
        assertEquals("sneham", r("സ്നേഹം"))
    }

    @Test fun tamil() {
        assertEquals("kaadhal", r("காதல்"))
        assertEquals("anbu", r("அன்பு"))
        assertEquals("ennai", r("என்னை"))
        assertEquals("uyire", r("உயிரே"))
        assertEquals("kaatru", r("காற்று"))
        assertEquals("ondru", r("ஒன்று"))
        assertEquals("nenjam", r("நெஞ்சம்"))
        assertEquals("pattu", r("பட்டு"))
        assertEquals("muttham", r("முத்தம்"))
        assertEquals("pakkam", r("பக்கம்"))
        assertEquals("vaazhga", r("வாழ்க"))
    }
}
