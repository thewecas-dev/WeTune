package com.wetune

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.wetune.ui.Frost
import com.wetune.ui.Tokens
import com.wetune.ui.contrast
import com.wetune.ui.frostColor
import com.wetune.ui.minGlassTint
import com.wetune.ui.over
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassContrastTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val darkGlass = 0xFF121212.toInt()
    private val lightGlass = 0xFFFAFAFA.toInt()
    private val darkText = 0xFFEDEDEF.toInt() // Tokens.Dark.text
    private val lightText = 0xFF1C1C1E.toInt() // Tokens.Light.text

    private fun passes(glass: Int, text: Int, textAlpha: Float, a: Float, min: Double) =
        listOf(white, black).all { b -> val g = over(glass, a, b); contrast(over(text, textAlpha, g), g) >= min }

    @Test fun tintIsTheLeastThatPassesOverWhiteAndBlack() {
        for ((glass, text) in listOf(darkGlass to darkText, lightGlass to lightText)) {
            for ((ta, min) in listOf(0.72f to 4.5, 1f to 4.5, 0.75f to 3.0)) {
                val a = minGlassTint(glass, text, ta, min)
                assertTrue(passes(glass, text, ta, a, min))
                assertTrue(a == 0f || !passes(glass, text, ta, a - 0.01f, min))
            }
        }
    }

    @Test fun frostTintsKeepTextReadableOverWhiteAndBlack() {
        // every frosted surface, every theme, with the accent nudge at its extremes
        for (p in listOf(Tokens.Dark, Tokens.Black, Tokens.Light)) for (accent in listOf(p.accent, Color.White, Color.Black, Color.Red, Color.Blue)) {
            val glass = frostColor(p.copy(accent = accent)).toArgb()
            val text = p.text.toArgb()
            val muted = p.muted.copy(alpha = 1f).toArgb()
            for (kind in Frost.entries) {
                val what = "$kind dark=${p.dark} accent=${accent.toArgb().toUInt().toString(16)}"
                assertTrue("primary $what", minGlassTint(glass, text, 1f, 4.5) <= kind.tint)
                assertTrue("muted $what", minGlassTint(glass, muted, p.muted.alpha, 3.0) <= kind.tint)
                assertTrue("secondary (72 %) $what", minGlassTint(glass, text, 0.72f, 3.0) <= kind.tint)
            }
        }
    }

    @Test fun knownValues() {
        // secondary text (72 %) at 4.5:1: about three quarters opaque; icons at 3:1 need much less
        assertEquals(0.75f, minGlassTint(darkGlass, darkText, 0.72f, 4.5), 0.011f)
        assertEquals(0.73f, minGlassTint(lightGlass, lightText, 0.72f, 4.5), 0.011f)
        assertTrue(minGlassTint(darkGlass, darkText, 0.75f, 3.0) < 0.65f)
        // text that can never pass on this glass asks for a full tint
        assertEquals(1f, minGlassTint(lightGlass, 0xFFEEEEEE.toInt(), 1f, 4.5))
    }
}
