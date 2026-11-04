package com.wetune.ui

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.wetune.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.wetune.Settings

// WeTune's own design system (no Material). Tokens mirror the "WeTune Design System" artifact.

@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val outline: Color,
    val dark: Boolean,
)

object Tokens {
    val Accent = Color(0xFFE3A582)
    val Dark = Palette(
        bg = Color(0xFF0E0E10), surface = Color(0xFF1A1A1D), surfaceHigh = Color(0xFF222226),
        text = Color(0xFFEDEDEF), muted = Color(0xFF8A8A93), accent = Accent, onAccent = Color(0xFF1A1210),
        danger = Color(0xFFFF5A4F), outline = Color.White.copy(alpha = 0.06f), dark = true,
    )
    val Black = Dark.copy(bg = Color.Black, surface = Color(0xFF0A0A0A), surfaceHigh = Color(0xFF17171C))
    val Light = Palette(
        bg = Color(0xFFF5F5F7), surface = Color.White, surfaceHigh = Color(0xFFECECF0),
        text = Color(0xFF1C1C1E), muted = Color(0x993C3C43), accent = Color(0xFFB4572F), onAccent = Color.White,
        danger = Color(0xFFD93A3A), outline = Color(0x1F3C3C43), dark = false,
    )

    val RadiusTrackArt = 8.dp
    val RadiusCard = 14.dp
    val RadiusField = 14.dp
    val RadiusLarge = 18.dp
}

/** For lyrics on Now Playing: a slim display serif that sets them apart from the Geist interface text. */
val LyricFont = FontFamily(Font(R.font.instrument_serif))

/** Geist, bundled as one variable font; each weight is a variation of the same file. */
@OptIn(ExperimentalTextApi::class)
val Geist = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { w ->
        Font(R.font.geist, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    }
)

object Type {
    val display = TextStyle(fontFamily = Geist, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
    val title = TextStyle(fontFamily = Geist, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    val headline = TextStyle(fontFamily = Geist, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontFamily = Geist, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.Medium)
    val label = TextStyle(fontFamily = Geist, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontFamily = Geist, fontSize = 13.sp, lineHeight = 18.sp)
    val overline = TextStyle(fontFamily = Geist, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
}

val LocalPalette = staticCompositionLocalOf { Tokens.Dark }

object WT {
    val c: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

/** The cover color nudged until it reads as text and fill on this theme's background. */
fun readableAccent(c: Color, dark: Boolean): Color {
    var out = c
    repeat(6) {
        val l = out.luminance()
        if (dark && l < 0.3f) out = androidx.compose.ui.graphics.lerp(out, Color.White, 0.2f)
        else if (!dark && l > 0.25f) out = androidx.compose.ui.graphics.lerp(out, Color.Black, 0.2f)
    }
    return out
}

@Composable
fun WeTuneTheme(content: @Composable () -> Unit) {
    val base = when (Settings.theme) {
        "light" -> Tokens.Light
        "dark" -> Tokens.Dark
        "black" -> Tokens.Black
        else -> if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    }
    val art = NowColors.palette
    val accent by animateColorAsState(
        if (Settings.matchAccent && art != null) readableAccent(art.band, base.dark) else base.accent, tween(600), label = "accent",
    )
    val palette = base.copy(accent = accent, onAccent = if (accent.luminance() > 0.5f) Color(0xFF15100C) else Color.White)
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !palette.dark
            isAppearanceLightNavigationBars = !palette.dark
        }
    }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalContentColor provides palette.text,
        LocalTextStyle provides Type.body,
        content = content,
    )
}
