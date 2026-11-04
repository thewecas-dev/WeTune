package com.wetune

import com.wetune.sources.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceMatchTest {
    private fun saavn(title: String, artist: String, ms: Long) = Track("saavn", "x", title, artist, durationMs = ms)

    @Test fun cleansYouTubeTitles() {
        assertEquals("monica tamil", SourceMatch.clean("Monica (From \"Coolie\") (Tamil)"))
        assertEquals("kesariya", SourceMatch.clean("Kesariya (Official Video)"))
    }

    @Test fun matchesSameRecording() {
        assertTrue(SourceMatch.same("Kesariya (From \"Brahmastra\")", "Arijit Singh, Pritam", 268_000, saavn("Kesariya", "Pritam, Arijit Singh", 268_000 + 2_000)))
    }

    @Test fun acceptsLabelChannelWhenTitleAndLengthAgree() {
        // YouTube lists the label channel as the artist; title and length match closely
        assertTrue(SourceMatch.same("Kesariya (Official Video)", "Sony Music India", 268_000, saavn("Kesariya", "Arijit Singh", 269_000)))
        // ...but not when the length is only roughly the same
        assertFalse(SourceMatch.same("Kesariya", "Sony Music India", 268_000, saavn("Kesariya", "Arijit Singh", 271_500)))
        // composer listed first on YouTube, singer first on JioSaavn
        assertTrue(SourceMatch.same("Chaleya", "Anirudh Ravichander, Arijit Singh", 0, saavn("Chaleya", "Arijit Singh, Shilpa Rao", 0)))
    }

    @Test fun rejectsOtherVersionsArtistsAndLengths() {
        assertFalse(SourceMatch.same("Kesariya", "Arijit Singh", 268_000, saavn("Kesariya (Remix)", "Arijit Singh", 268_000)))
        assertFalse(SourceMatch.same("Kesariya", "Arijit Singh", 268_000, saavn("Kesariya", "Some Cover Band", 274_000)))
        assertFalse(SourceMatch.same("Kesariya", "Arijit Singh", 268_000, saavn("Kesariya (Cover)", "Some Cover Band", 268_000)))
        assertFalse(SourceMatch.same("Kesariya", "Arijit Singh", 268_000, saavn("Kesariya", "Arijit Singh", 300_000)))
        assertTrue(SourceMatch.same("Kesariya", "Arijit Singh", 268_000, saavn("Kesariya", "Arijit Singh", 274_000)))
        assertFalse(SourceMatch.same("Chaleya", "Arijit Singh", 0, saavn("Kesariya", "Arijit Singh", 0)))
    }
}

class CacheKeyTest {
    @Test fun youtubeByVideoAndFormat() {
        org.junit.Assert.assertEquals("gv:abc:251", streamCacheKey("https://rr1---sn-x.googlevideo.com/videoplayback?expire=1&id=abc&itag=251&sig=zz"))
    }

    @Test fun qobuzFilesSharingAPathStayApart() {
        val a = streamCacheKey("https://streaming-qobuz-std.akamaized.net/file?uid=1&eid=239301166&fmt=6&profile=raw&etsp=1793813582&hmac=x1")
        val b = streamCacheKey("https://streaming-qobuz-std.akamaized.net/file?uid=1&eid=460477109&fmt=6&profile=raw&etsp=1793813999&hmac=y2")
        val a2 = streamCacheKey("https://streaming-qobuz-std.akamaized.net/file?uid=1&eid=239301166&fmt=6&profile=raw&etsp=1799999999&hmac=other")
        org.junit.Assert.assertNotEquals(a, b)
        org.junit.Assert.assertEquals("same file, new signature", a, a2)
    }

    @Test fun plainPathsUnchanged() {
        org.junit.Assert.assertEquals("https://aac.saavncdn.com/123/abc_320.mp4", streamCacheKey("https://aac.saavncdn.com/123/abc_320.mp4"))
    }
}
