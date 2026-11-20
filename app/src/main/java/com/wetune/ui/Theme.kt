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
        text = Color(0xFF1C1C1E), muted = Color(0xBF3C3C43) /* 4.9:1 on bg, 5.2:1 on white */, accent = Color(0xFFB4572F), onAccent = Color.White,
        danger = Color(0xFFD93A3A), outline = Color(0x1F3C3C43), dark = false,
    )

    val RadiusTrackArt = 8.dp
    val RadiusCard = 14.dp
    val RadiusField = 14.dp
    val RadiusLarge = 18.dp
}

/** For lyrics on Now Playing: Space Grotesk (variable), a sans with a little character that sets them apart from Geist. */
@OptIn(ExperimentalTextApi::class)
val LyricFont = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold).map { w ->
        Font(R.font.space_grotesk, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    }
)

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

/** The accent for a cover: only its [hue] changes; saturation and lightness stay fixed so every song's accent feels equally strong. */
fun hueAccent(hue: Float, dark: Boolean): Color = Color.hsl(hue, if (dark) 0.6f else 0.55f, if (dark) 0.7f else 0.45f)

/** The accent for [base]: the playing cover's hue, or the user's chosen hue, or the palette's own. */
private fun accentFor(base: Palette): Color =
    NowColors.palette?.hue?.takeIf { Settings.matchAccent }?.let { hueAccent(it, base.dark) }
        ?: Settings.accentHue.takeIf { it >= 0 }?.let { hueAccent(it.toFloat(), base.dark) } ?: base.accent

private fun Palette.withAccent(a: Color) = copy(accent = a, onAccent = if (a.luminance() > 0.5f) Color(0xFF15100C) else Color.White)

/** This palette, or the dark one (with the accent made for it) when the app is light. */
fun Palette.asDark(): Palette = if (dark) this else Tokens.Dark.withAccent(accentFor(Tokens.Dark))

/** Menus and sheets use this palette when set: Now Playing (always dark) sets the dark one, so they match the player. */
val LocalOverlayPalette = staticCompositionLocalOf<Palette?> { null }

@Composable
fun WeTuneTheme(content: @Composable () -> Unit) {
    val base = when (Settings.theme) {
        "light" -> Tokens.Light
        "dark" -> Tokens.Dark
        "black" -> Tokens.Black
        else -> if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    }
    val accent by animateColorAsState(accentFor(base), tween(600), label = "accent")
    val palette = base.withAccent(accent)
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !palette.dark
            isAppearanceLightNavigationBars = !palette.dark
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(
        // the app's own text size on top of the system's
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale * Settings.textScale / 100f),
        LocalPalette provides palette,
        LocalContentColor provides palette.text,
        LocalTextStyle provides Type.body,
        content = content,
    )
}
