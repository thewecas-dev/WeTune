package com.wetune.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Pull down at the top of a list to refresh. The list follows the finger (with resistance); past the threshold a light
 * tick says "let go now"; on release it holds open with a spinning ring until [refreshing] turns false, then settles.
 * The pull is read only in layer and draw lambdas, so dragging never recomposes the list.
 */
@Composable
fun PullRefresh(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = WT.c
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val hold = threshold * 0.8f
    var pull by remember { mutableFloatStateOf(0f) }
    val busy by rememberUpdatedState(refreshing)
    val refresh by rememberUpdatedState(onRefresh)

    fun settle(to: Float) = scope.launch { animate(pull, to, animationSpec = tween(Motion.FOLD, easing = Motion.Standard)) { v, _ -> pull = v } }
    LaunchedEffect(refreshing) { if (refreshing) { if (pull < hold) settle(hold) } else if (pull > 0f) settle(0f) }

    val connection = remember {
        object : NestedScrollConnection {
            // pushing back up while pulled: shrink the pull before the list scrolls
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f || pull <= 0f || busy) return Offset.Zero
                val take = maxOf(available.y, -pull)
                pull += take
                return Offset(0f, take)
            }

            // the list is at its top and the finger keeps pulling down: that's the refresh pull
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f || busy) return Offset.Zero
                val before = pull
                pull += available.y * 0.5f * (1f - (pull / (threshold * 2.2f)).coerceIn(0f, 0.85f)) // stiffer the further it goes
                if (before < threshold && pull >= threshold) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f || busy) return Velocity.Zero
                if (pull >= threshold) refresh() else settle(0f)
                return available // the release belongs to the pull, not a fling of the list
            }
        }
    }

    Box(modifier.fillMaxSize().nestedScroll(connection)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = pull }) { content() }
        // the ring: fills as you pull, spins while refreshing
        // runs only while refreshing, so an idle list costs no frames
        val spin by if (refreshing) rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spin")
        else remember { mutableFloatStateOf(0f) }
        Canvas(
            Modifier.align(Alignment.TopCenter).statusPad().size(28.dp)
                .graphicsLayer {
                    val p = (pull / threshold).coerceIn(0f, 1f)
                    alpha = p
                    translationY = pull / 2f - 14.dp.toPx()
                    scaleX = 0.6f + 0.4f * p; scaleY = scaleX
                    rotationZ = if (busy) spin else pull * 0.9f
                }
                .semantics { contentDescription = if (refreshing) "Refreshing" else "Pull down to refresh" },
        ) {
            val p = if (busy) 0.75f else (pull / threshold).coerceIn(0f, 1f) * 0.85f
            val w = 2.6.dp.toPx()
            drawArc(c.on, -90f, 360f * p, false, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}
