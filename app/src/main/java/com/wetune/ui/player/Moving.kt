package com.wetune.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember

// Slow motion for backgrounds and covers that stops where it is when music pauses and carries on from there when it
// plays (an infinite transition restarts from its start each time: the picture jumped on every pause and play).

/** Degrees, one turn per [ms] while [on]; starts at [start]. */
@Composable
fun turning(ms: Int, on: Boolean, start: Float = 0f): State<Float> {
    val a = remember { Animatable(start) }
    LaunchedEffect(on) { if (on) while (true) a.animateTo(a.value + 360f, tween(ms, easing = LinearEasing)) }
    return a.asState()
}

/** 0..1 and back, [ms] each way while [on]; starts at [start]. */
@Composable
fun swinging(ms: Int, on: Boolean, start: Float = 0.5f, easing: Easing = LinearEasing): State<Float> {
    val a = remember { Animatable(start) }
    LaunchedEffect(on) {
        if (!on) return@LaunchedEffect
        var up = a.value < 1f
        while (true) {
            val to = if (up) 1f else 0f
            a.animateTo(to, tween((ms * kotlin.math.abs(to - a.value)).toInt().coerceAtLeast(1), easing = easing)) // the rest of this sweep
            up = !up
        }
    }
    return a.asState()
}
