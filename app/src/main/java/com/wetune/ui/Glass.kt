package com.wetune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Liquid glass: a light tint over a real blur, so whatever scrolls underneath shows through. */
@Composable
fun glassStyle(): HazeStyle {
    val dark = WT.c.dark
    val base = if (dark) Color(0xFF16161A) else Color(0xFFF8F8FA)
    return HazeStyle(backgroundColor = WT.c.bg, tint = HazeTint(base.copy(alpha = if (dark) 0.45f else 0.5f)), blurRadius = 22.dp, noiseFactor = 0.03f)
}

/**
 * The glass edge. Dark: a bright rim catching light top-left and bottom-right.
 * Light: white on top fading to a faint grey underneath, because a white rim vanishes on a light page.
 */
@Composable
fun rimBrush(strength: Float = if (WT.c.dark) 0.45f else 1f): Brush =
    if (WT.c.dark) Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.9f * strength),
        0.3f to Color.White.copy(alpha = 0.1f * strength),
        0.7f to Color.White.copy(alpha = 0.06f * strength),
        1f to Color.White.copy(alpha = 0.55f * strength),
    ) else Brush.verticalGradient(
        0f to Color.White.copy(alpha = strength),
        0.5f to Color(0x1A3C3C43).copy(alpha = 0.06f * strength),
        1f to Color(0x2E3C3C43).copy(alpha = 0.18f * strength),
    )

/** Floating glass: blurred backdrop, tint, edge and a soft shadow that lifts it off the page. */
@Composable
fun Modifier.glass(state: HazeState, style: HazeStyle, shape: Shape): Modifier =
    shadow(if (WT.c.dark) 18.dp else 14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = if (WT.c.dark) 0.5f else 0.22f))
        .clip(shape).hazeEffect(state, style).border(1.2.dp, rimBrush(), shape)

/**
 * Glass for popups and dialogs: a near-solid tint plus the rim.
 * shortcut: popups live in their own window, so they can't blur what's behind them; real backdrop blur needs a shared haze source.
 */
@Composable
fun Modifier.frost(shape: Shape): Modifier {
    val tint = if (WT.c.dark) Color(0xF01E1A1C) else Color(0xF5F8F8FA)
    return clip(shape).background(tint).border(1.2.dp, rimBrush(), shape)
}

/** Glass chip/row fill: a faint wash of the text color plus the rim. */
@Composable
fun Modifier.glassChip(shape: Shape): Modifier =
    clip(shape).background(WT.c.text.copy(alpha = if (WT.c.dark) 0.08f else 0.05f)).border(1.dp, rimBrush(if (WT.c.dark) 0.35f else 0.9f), shape)
