package com.wetune.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wetune.R
import com.wetune.Settings

/** Design spec §2. Neutral surfaces; the cover never tints UI, it only glows faintly behind Now Playing. */
@Immutable
data class WeColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val frost: Color, // tint over the blurred backdrop
    val frostSolid: Color, // devices without blur (Android 11 and older)
    val on: Color,
    val onSecondary: Color,
    val onTertiary: Color,
    val separator: Color,
    val chip: Color, // translucent circle behind small icon buttons
    val primary: Color, // play, selected pills / tab
    val onPrimary: Color,
    val liked: Color,
    val success: Color,
)

val Dark = WeColors(
    dark = true, bg = Color.Black, surface = Color(0xFF1C1C1E), surfaceHigh = Color(0xFF3A3A3C),
    frost = Color(0x9E1E1E20), frostSolid = Color(0xF21C1C1E), on = Color.White, onSecondary = Color(0xFF98989F),
    onTertiary = Color(0xFF636366), separator = Color(0x14FFFFFF), chip = Color(0x1FFFFFFF),
    primary = Color(0xFFF2F2F2), onPrimary = Color.Black, liked = Color(0xFFFF6B5E), success = Color(0xFF30D158),
)

val Light = WeColors(
    dark = false, bg = Color(0xFFF2F2F7), surface = Color.White, surfaceHigh = Color(0xFFE5E5EA),
    frost = Color(0xB8FFFFFF), frostSolid = Color(0xF7F9F9FB), on = Color.Black, onSecondary = Color(0xFF6C6C70),
    onTertiary = Color(0xFF8E8E93), separator = Color(0x1F3C3C43), chip = Color(0x14000000),
    primary = Color(0xFF1C1C1E), onPrimary = Color.White, liked = Color(0xFFFF3B30), success = Color(0xFF34C759),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int) = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/** Apple-like everywhere; Outfit only inside Now Playing's Material style. */
val Inter = variable(R.font.inter)
val Outfit = variable(R.font.outfit)

val LocalColors = staticCompositionLocalOf { Dark }

object WT {
    val c: WeColors @Composable get() = LocalColors.current
}

fun type(size: TextUnit, weight: Int, color: Color = Color.Unspecified, family: FontFamily = Inter, tracking: TextUnit = (-0.01).em) = TextStyle(
    fontFamily = family, fontSize = size, fontWeight = FontWeight(weight), color = color, letterSpacing = tracking,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** Spec §3, Apple-like column. */
object Type {
    val largeTitle = type(34.sp, 800, tracking = (-0.02).em)
    val title = type(21.sp, 700)
    val section = type(21.sp, 700)
    val row = type(15.sp, 500)
    val tile = type(14.sp, 500)
    val sub = type(13.sp, 400)
    val caption = type(11.sp, 600)
    val body = type(15.sp, 400)
    val button = type(15.sp, 600)
}

/** Spec §7: no bounce, no overshoot. */
object Motion {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f) // emphasized-decelerate
    const val SMALL = 200
    const val FOLD = 280
    const val PUSH = 400
    const val GLOW = 600
    fun <T> small(): FiniteAnimationSpec<T> = tween(SMALL, easing = Standard)
    fun <T> fold(): FiniteAnimationSpec<T> = tween(FOLD, easing = Decelerate)
    fun <T> push(): FiniteAnimationSpec<T> = tween(PUSH, easing = Decelerate)
}

@Composable
fun WeTheme(content: @Composable () -> Unit) {
    val dark = when (Settings.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val c = if (dark) Dark else Light
    CompositionLocalProvider(
        LocalColors provides c,
        LocalTextSelectionColors provides TextSelectionColors(c.on, c.on.copy(alpha = 0.25f)),
        content = content,
    )
}
