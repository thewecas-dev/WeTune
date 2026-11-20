package com.wetune.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The frosted family: every glass surface is one of these. [tint] is how opaque the surface colour lies over the
 * blurred backdrop: enough that primary text keeps 4.5:1 and muted text 3:1 even over pure white or pure black
 * (GlassContrastTest). No rim, no outline, no shadow: blur, tint and a little grain are the whole look.
 */
enum class Frost(val blur: Dp, val tint: Float) {
    /** Floating bars and pills: nav bar, mini player and its tray, the status-bar fade. */
    Bar(28.dp, 0.72f),
    /** In-flow cards over the page (settings groups): no blur of their own, the page under them is already soft. */
    Card(0.dp, 0.72f),
    /** Menus, dialogs, sheets: the screen behind blurs, the panel is nearly solid. */
    Menu(32.dp, 0.88f),
}

/** Without RenderEffect (Android 11 and older) there is no blur: glass becomes a near-solid surface. */
const val FROST_FALLBACK = 0.95f

val blurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Frosted surface colour: the palette's surface nudged a touch toward the accent (the cover's hue), so glass isn't plain grey. */
fun frostColor(c: Palette): Color = lerp(if (c.dark) c.surfaceHigh else c.surface, c.accent, 0.06f)

/** [kind]'s tint over [c]'s frost colour; the near-solid fallback when this device can't blur. */
fun frostFill(c: Palette, kind: Frost): Color = frostColor(c).copy(alpha = if (blurSupported) kind.tint else FROST_FALLBACK)

/** Real blur under a translucent tint with a little grain, so what scrolls underneath shows through softly. */
@Composable
fun glassStyle(kind: Frost = Frost.Bar): HazeStyle {
    val c = WT.c
    val base = frostColor(c)
    return HazeStyle(
        backgroundColor = c.bg, tint = HazeTint(base.copy(alpha = kind.tint)), blurRadius = kind.blur,
        noiseFactor = 0.12f, fallbackTint = HazeTint(base.copy(alpha = FROST_FALLBACK)),
    )
}

/**
 * Floating glass (nav, mini player): blurred backdrop under the tint. No edge, no shadow.
 * Blur runs at reduced resolution (looks the same at this radius, much cheaper per frame).
 */
@Composable
fun Modifier.glass(state: HazeState, style: HazeStyle, shape: Shape): Modifier =
    clip(shape).hazeEffect(state, style) { inputScale = HazeInputScale.Auto }

/**
 * Popups, dialogs, sheets: they live in their own window, so the window behind is blurred ([frostBehind]) and the
 * panel is the [Frost.Menu] tint over it.
 */
@Composable
fun Modifier.frost(shape: Shape): Modifier = clip(shape).background(frostFill(WT.c, Frost.Menu))

/** In-flow card (settings groups): the [Frost.Card] tint over the page. */
@Composable
fun Modifier.frostCard(shape: Shape): Modifier = clip(shape).background(frostFill(WT.c, Frost.Card))

/** Chip/row fill: a flat tonal wash of the text color. */
@Composable
fun Modifier.glassChip(shape: Shape): Modifier = clip(shape).background(WT.c.text.copy(alpha = if (WT.c.dark) 0.08f else 0.05f))

/** Blur whatever is behind this dialog's window (Android 12+, when the system allows cross-window blur). Call inside a Dialog. */
@Composable
fun FrostBehind() {
    if (!blurSupported) return
    val view = androidx.compose.ui.platform.LocalView.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { Frost.Menu.blur.roundToPx() }
    androidx.compose.runtime.SideEffect {
        val w = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: return@SideEffect
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        w.attributes = w.attributes.apply { blurBehindRadius = px }
    }
}
