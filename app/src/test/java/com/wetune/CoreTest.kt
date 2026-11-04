package com.wetune

import com.wetune.sources.JioSaavn
import com.wetune.sources.Subsonic
import com.wetune.sources.Track
import com.wetune.youtube.InnerTube
import com.wetune.youtube.items
import com.wetune.youtube.parseDuration
import com.wetune.youtube.parseRow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class CoreTest {
    @Test
    fun lrcParsing() {
        val lines = Lyrics.parseLrc("[ar:Someone]\n[00:01.50]Hello\n[00:00.00][00:03.00]Twice\nno tag")
        assertEquals(listOf(0L to "Twice", 1500L to "Hello", 3000L to "Twice"), lines.map { it.ms to it.text })
    }

    @Test
    fun sapisidHash() {
        assertEquals("SAPISIDHASH 1_fdd4a2889de4fd5c886eeae17a803a8c57f01113", InnerTube.sapisidHash("abc", 1))
        assertEquals("xyz", InnerTube.sapisid("HSID=a; SAPISID=xyz; SID=b"))
        assertEquals("p3", InnerTube.sapisid("a=b; __Secure-3PAPISID=p3"))
        assertNull(InnerTube.sapisid("a=b; SID=c"))
    }

    @Test
    fun subsonicToken() {
        // Example from the Subsonic API documentation.
        assertEquals("26719a1196d2a940705a59634eb18eab", Subsonic.token("sesame", "c19b2d"))
    }

    @Test
    fun jioSaavnDecrypt() {
        val url = "https://aac.saavncdn.com/123/abc_96.mp4"
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec("38346591".toByteArray(), "DES"))
        val encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(url.toByteArray()))
        assertEquals(url, JioSaavn.decrypt(encrypted))
        assertEquals("https://aac.saavncdn.com/123/abc_320.mp4", JioSaavn.streamUrl(encrypted, high = true))
        assertEquals("https://aac.saavncdn.com/123/abc_160.mp4", JioSaavn.streamUrl("$encrypted|no320", high = true))
    }

    @Test
    fun youtubeRowParsing() {
        val row = JSONObject(
            """
            {"thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://lh3.googleusercontent.com/x=w60-h60-l90"}]}}},
             "flexColumns":[
               {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Blinding Lights","navigationEndpoint":{"watchEndpoint":{"videoId":"4NRXx6U8ABQ"}}}]}}},
               {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                 {"text":"The Weeknd","navigationEndpoint":{"browseEndpoint":{"browseId":"UC1","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},
                 {"text":" • "},
                 {"text":"After Hours","navigationEndpoint":{"browseEndpoint":{"browseId":"MPRE1","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}},
                 {"text":" • "},{"text":"3:20"}]}}},
               {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"3.7B plays"}]}}}]}
            """
        )
        val t = parseRow(row) as Track
        assertEquals("4NRXx6U8ABQ", t.ref)
        assertEquals("Blinding Lights", t.title)
        assertEquals("The Weeknd", t.artist)
        assertEquals("After Hours", t.album)
        assertEquals(200_000L, t.durationMs)
        assertEquals("https://lh3.googleusercontent.com/x=w544-h544-l90", t.art)
        assertEquals(1, items(JSONObject().put("a", JSONObject().put("musicResponsiveListItemRenderer", row))).tracks.size)
        assertEquals(3_723_000L, parseDuration("1:02:03"))
    }
}
