package com.wetune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Frosted glass: real blur under a translucent tint, so what scrolls underneath shows through softly. */
@Composable
fun glassStyle(): HazeStyle {
    val c = WT.c
    val base = if (c.dark) c.surfaceHigh else c.surface
    return HazeStyle(backgroundColor = c.bg, tint = HazeTint(base.copy(alpha = if (c.dark) 0.6f else 0.62f)), blurRadius = 24.dp, noiseFactor = 0f)
}

/** Hairline edge for floating surfaces and chips. */
@Composable
fun rimBrush(strength: Float = 1f): Brush = SolidColor(WT.c.outline.copy(alpha = WT.c.outline.alpha * strength))

/**
 * Floating glass (nav, mini player): blurred backdrop, tint, hairline edge.
 * No shadow: it shows through the translucent glass as a grey smudge.
 * Blur runs at reduced resolution (looks the same at this radius, much cheaper per frame).
 */
@Composable
fun Modifier.glass(state: HazeState, style: HazeStyle, shape: Shape): Modifier =
    clip(shape).hazeEffect(state, style) { inputScale = HazeInputScale.Auto }.border(1.dp, rimBrush(2.5f), shape)

/**
 * Popups and dialogs: a near-solid surface.
 * shortcut: popups live in their own window, so they can't blur what's behind them; real backdrop blur needs a shared haze source.
 */
@Composable
fun Modifier.frost(shape: Shape): Modifier =
    clip(shape).background((if (WT.c.dark) WT.c.surfaceHigh else WT.c.surface).copy(alpha = 0.96f)).border(1.dp, rimBrush(), shape)

/** Chip/row fill: a flat tonal wash of the text color. */
@Composable
fun Modifier.glassChip(shape: Shape): Modifier =
    clip(shape).background(WT.c.text.copy(alpha = if (WT.c.dark) 0.08f else 0.05f))
