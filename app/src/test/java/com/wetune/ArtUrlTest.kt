package com.wetune

import com.wetune.youtube.hiRes
import com.wetune.youtube.isVideoArt
import com.wetune.youtube.smallerYtimg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtUrlTest {
    private val frame = "https://i.ytimg.com/vi/V9PVRfjEBTI/hqdefault.jpg?sqp=-oaymwEXCNACELwBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLA"
    private val cover = "https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"

    @Test fun videoFrameGoesToTheLargestSize() = assertEquals("https://i.ytimg.com/vi/V9PVRfjEBTI/maxresdefault.jpg", hiRes(frame))

    @Test fun coverGoesTo1200() = assertEquals("https://lh3.googleusercontent.com/abc=w1200-h1200-l90-rj", hiRes(cover))

    @Test fun missingSizesStepDown() {
        val max = hiRes(frame)!!
        val hq720 = smallerYtimg(max)!!
        assertTrue(hq720.endsWith("/hq720.jpg"))
        val sd = smallerYtimg(hq720)!!
        assertTrue(sd.endsWith("/sddefault.jpg"))
        assertTrue(smallerYtimg(sd)!!.endsWith("/hqdefault.jpg"))
        assertNull(smallerYtimg(smallerYtimg(sd)!!))
        assertNull(smallerYtimg(cover))
    }

    @Test fun onlyYtimgIsVideoArt() {
        assertTrue(isVideoArt(frame))
        assertTrue(isVideoArt("https://i9.ytimg.com/vi_webp/abc/mqdefault.webp"))
        assertEquals("https://i.ytimg.com/vi/abc/maxresdefault.jpg", hiRes("https://i9.ytimg.com/vi_webp/abc/mqdefault.webp"))
        assertFalse(isVideoArt(cover))
        assertFalse(isVideoArt(null))
        assertNull(hiRes(null))
    }
}
