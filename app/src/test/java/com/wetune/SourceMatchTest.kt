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
