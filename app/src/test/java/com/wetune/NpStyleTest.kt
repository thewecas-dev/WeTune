package com.wetune

import com.wetune.ui.ActionArrange
import com.wetune.ui.BgKind
import com.wetune.ui.ControlsStyle
import com.wetune.ui.NpAction
import com.wetune.ui.ControlsKind
import com.wetune.ui.CoverKind
import com.wetune.ui.InlineLyrics
import com.wetune.ui.NowPlayingStyle
import com.wetune.ui.Progress
import com.wetune.ui.Texture
import com.wetune.ui.contrast
import com.wetune.ui.ensureContrast
import com.wetune.ui.minDim
import com.wetune.ui.npGeometry
import com.wetune.ui.over
import com.wetune.ui.textIconContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpStyleTest {
    @Test fun jsonRoundTripsEveryPreset() {
        NowPlayingStyle.Presets.forEach { (name, s) -> assertEquals(name, s, NowPlayingStyle.fromJson(s.toJson())) }
        val d = NowPlayingStyle()
        assertEquals(d, NowPlayingStyle.fromJson(d.toJson()))
    }

    @Test fun presetsAreDistinctAndNamed() {
        assertEquals(6, NowPlayingStyle.Presets.map { it.second }.toSet().size)
        assertEquals("Aurora", NowPlayingStyle.nameOf(NowPlayingStyle.Aurora))
        assertEquals("Custom", NowPlayingStyle.nameOf(NowPlayingStyle.Aurora.copy(cover = NowPlayingStyle.Aurora.cover.copy(corner = 3))))
    }

    @Test fun pastedValuesAreClampedAndUnknownsFallBack() {
        val s = NowPlayingStyle.fromJson(
            """{"version":9,"background":{"kind":"hologram","blur":500,"dim":-1,"zoom":9},"cover":{"kind":"disc","size":3,"corner":-5,"ringWidth":40},
               "controls":{"kind":"expressive","playSize":10,"progress":"wavy"},"layout":{"lyrics":"stack"},"colors":{"customHue":999}}""",
        )!!
        assertEquals(BgKind.Blur, s.background.kind) // unknown kind → default
        assertEquals(100, s.background.blur)
        assertEquals(0f, s.background.dim)
        assertEquals(2f, s.background.zoom)
        assertEquals(CoverKind.Disc, s.cover.kind)
        assertEquals(1f, s.cover.size)
        assertEquals(0, s.cover.corner)
        assertEquals(8, s.cover.ringWidth)
        assertEquals(ControlsKind.Expressive, s.controls.kind)
        assertEquals(56, s.controls.playSize)
        assertEquals(Progress.Wavy, s.controls.progress)
        assertEquals(InlineLyrics.Stack, s.layout.lyrics)
        assertEquals(359, s.colors.customHue)
    }

    @Test fun rejectsWhatIsNotAStyle() {
        assertNull(NowPlayingStyle.fromJson("hello"))
        assertNull(NowPlayingStyle.fromJson("{}"))
        assertNull(NowPlayingStyle.fromJson("""{"title":"x"}"""))
        assertNull(NowPlayingStyle.fromJson(null))
        assertNotNull(NowPlayingStyle.fromJson("""{"cover":{}}"""))
    }

    @Test fun migratesTheOldDefaultsToTheEdgeLook() {
        val s = NowPlayingStyle.fromLegacy { null }
        assertEquals(CoverKind.FullWidth, s.cover.kind)
        assertEquals(BgKind.Extend, s.background.kind)
        assertEquals(ControlsKind.Classic, s.controls.kind)
        assertTrue(s.controls.volume)
        assertTrue(s.layout.header)
        assertTrue(s.layout.hideStatus) // the old default hid the status bar; keep what the user had
        assertEquals(InlineLyrics.Line, s.layout.lyrics)
    }

    @Test fun migratesEveryOldOption() {
        fun from(vararg kv: Pair<String, Any>) = NowPlayingStyle.fromLegacy { k -> kv.toMap()[k] }
        assertEquals(CoverKind.Card, from("np_cover" to "card").cover.kind)
        assertEquals(CoverKind.Card, from("np_cover" to "strips").cover.kind)
        assertEquals(CoverKind.Disc, from("np_cover" to "disc").cover.kind)
        assertEquals(CoverKind.Vinyl, from("np_cover" to "vinyl").cover.kind)
        assertTrue(from("np_fill" to "mirror").background.reflection)
        assertEquals(BgKind.Blur, from("np_fill" to "blur").background.kind)
        assertEquals(BgKind.Blur, from("np_fill" to "fade").background.kind)
        assertEquals(Texture.Ribbed, from("np_fill" to "ribbed").background.texture)
        assertEquals(BgKind.Solid, from("np_fill" to "black").background.kind)
        assertEquals(ControlsKind.Dock, from("np_layout" to "dock").controls.kind)
        assertEquals(ControlsKind.Expressive, from("np_layout" to "bigtitle").controls.kind)
        assertEquals(Progress.Bars, from("np_layout" to "wave").controls.progress)
        assertEquals(ControlsKind.Minimal, from("np_layout" to "beside").controls.kind)
        val m = from("np_header" to false, "np_hide_status" to false, "show_volume" to false, "show_inline" to true, "inline_style" to "stack",
            "np_depth" to true, "depth_intensity" to 80, "depth_invert" to true)
        assertEquals(false, m.layout.header)
        assertEquals(false, m.layout.hideStatus)
        assertEquals(false, m.controls.volume)
        assertEquals(InlineLyrics.Stack, m.layout.lyrics)
        assertTrue(m.motion.depth); assertEquals(80, m.motion.depthIntensity); assertTrue(m.motion.depthInvert)
        assertEquals(InlineLyrics.Off, from("show_inline" to false).layout.lyrics)
    }

    @Test fun autoDimKeepsTextAndIconsReadableOverAnyColour() {
        val ink = 0xFF08080A.toInt()
        listOf(0xFFFFFFFF.toInt(), 0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(), 0xFF808080.toInt(), 0xFF000000.toInt()).forEach { bg ->
            val a = minDim(bg, 0.3f)
            val (text, icons) = textIconContrast(over(ink, a + 0.3f * (1 - a), bg))
            assertTrue("text over ${Integer.toHexString(bg)}: $text", text >= 4.5)
            assertTrue("icons over ${Integer.toHexString(bg)}: $icons", icons >= 3.0)
        }
        assertEquals(0f, minDim(0xFF000000.toInt(), 0.3f)) // a black background needs no dim
    }

    @Test fun accentIsLiftedToThreeToOne() {
        val bg = 0xFF101010.toInt()
        val dark = 0xFF301010.toInt()
        assertTrue(contrast(ensureContrast(dark, bg), bg) >= 3.0)
    }

    @Test fun geometryKeepsTheCoverBetweenHeaderAndControls() {
        NowPlayingStyle.Presets.forEach { (name, s) ->
            listOf(844f, 1113f, 640f).forEach { h ->
                val g = npGeometry(s, 390f, h, 40f, 24f)
                assertTrue("$name controls inside the screen", g.controlsTop + g.controlsH <= h)
                assertTrue("$name cover above the controls", g.coverBottom <= g.controlsTop)
                if (s.cover.kind != CoverKind.FullWidth) assertTrue("$name cover under the header", g.coverTop >= g.headerTop + g.headerH)
            }
        }
    }

    @Test fun scrimCurveIsSmoothAndMonotone() {
        val pts = listOf(100f to 0f, 300f to 0.3f, 500f to 0.62f, 900f to 0.84f)
        pts.forEach { (y, a) -> assertEquals(a, com.wetune.ui.scrimCurve(pts, y), 1e-4f) }
        var last = 0f
        for (y in 0..1000) {
            val a = com.wetune.ui.scrimCurve(pts, y.toFloat())
            assertTrue("monotone at $y", a >= last - 1e-5f); last = a
        }
        // flat where it starts and ends: no kink for the eye to catch as an edge
        assertTrue(com.wetune.ui.scrimCurve(pts, 102f) < 0.001f)
        assertTrue(0.84f - com.wetune.ui.scrimCurve(pts, 896f) < 0.001f)
    }

    @Test fun actionRowRoundTripsAndDefaultsEverywhere() {
        val s = NowPlayingStyle().copy(
            controls = ControlsStyle(actions = listOf(NpAction.Audio, NpAction.Like, NpAction.Download), actionLabels = true, actionArrange = ActionArrange.Even),
        )
        assertEquals(s, NowPlayingStyle.fromJson(s.toJson()))
        NowPlayingStyle.Presets.forEach { (name, p) ->
            assertEquals(name, listOf(NpAction.Lyrics, NpAction.Queue, NpAction.Like, NpAction.Sleep, NpAction.Output), p.controls.actions)
            assertEquals(name, false, p.controls.actionLabels)
            assertEquals(name, ActionArrange.Spread, p.controls.actionArrange)
            assertTrue(name, p.layout.hideStatus)
        }
        // an empty row stays empty; unknown and repeated names are dropped
        assertEquals(emptyList<NpAction>(), NowPlayingStyle.fromJson("""{"version":2,"controls":{"actions":[]}}""")!!.controls.actions)
        assertEquals(listOf(NpAction.Queue), NowPlayingStyle.fromJson("""{"version":2,"controls":{"actions":["hologram","queue","Queue"]}}""")!!.controls.actions)
    }

    @Test fun readsVersionOneStyles() {
        // what 0.7 saved: like / sleep / output flags, no action row, the status bar showing
        val old = NowPlayingStyle.fromJson(
            """{"version":1,"controls":{"kind":"Dock","like":true,"sleep":false,"output":false},"layout":{"header":true,"hideStatus":false,"lyrics":"Line"}}""",
        )!!
        assertEquals(ControlsKind.Dock, old.controls.kind)
        assertEquals(NpAction.Defaults, old.controls.actions)
        assertEquals(false, old.controls.actionLabels)
        assertEquals(ActionArrange.Spread, old.controls.actionArrange)
        assertTrue(old.layout.hideStatus) // version 1's default showed it by mistake
        assertTrue(old.toJson().contains("\"version\":2"))
        // from version 2 on, showing the status bar is a choice and sticks
        assertEquals(false, NowPlayingStyle.fromJson("""{"version":2,"layout":{"hideStatus":false}}""")!!.layout.hideStatus)
    }
}
