package com.wetune.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import com.wetune.sources.Track
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.wetune.Playback
import com.wetune.ui.Cover
import com.wetune.ui.Icon
import com.wetune.ui.Icons
import com.wetune.ui.MaterialIcons
import com.wetune.ui.Txt
import com.wetune.ui.Type
import com.wetune.ui.WT
import com.wetune.ui.press
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The queue (full screen): Shuffle / Repeat / Automix pills, then every song. Drag a row's handle to reorder, swipe a
 * row left to remove it, tap to play it. The playing row is marked.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueuePanel(modifier: Modifier = Modifier) {
    val c = WT.c
    val mat = material
    val queue = Playback.queue
    val index = Playback.index
    Column(modifier.fillMaxSize()) {
        // R8-A5: pills, then "Playing Next · from …"; R8-M5: "Up next · N songs · M min", then chips
        val heading = @Composable {
            Row(Modifier.fillMaxWidth().padding(top = if (mat) 0.dp else 18.dp, bottom = if (mat) 10.dp else 4.dp), verticalAlignment = Alignment.Bottom) {
                Txt(if (mat) "Up next" else "Playing Next", pt(Type.title.copy(fontSize = 20.sp), 22, 600), Modifier.weight(1f))
                val sub = if (mat) {
                    val rest = queue.drop(index + 1)
                    "${rest.size} songs · ${rest.sumOf { it.durationMs } / 60_000} min"
                } else Playback.from?.second?.let { "from $it" }.orEmpty()
                Txt(sub, pt(Type.sub, 13, 400), color = c.onSecondary)
            }
        }
        if (mat) heading()
        QueuePills()
        if (!mat) heading() else Box(Modifier.height(6.dp))
        val state = rememberLazyListState()
        LaunchedEffect(Unit) { state.scrollToItem((index - 1).coerceAtLeast(0)) }
        val rowPx = with(LocalDensity.current) { 58.dp.toPx() }
        var dragging by remember { mutableIntStateOf(-1) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        // While a row is dragged the list reorders here only (instant, under the finger); the player gets one move on
        // release, and this order stays on screen until the player's queue matches it (moving the player on every
        // step made its slower replies redraw the list mid-drag: rows jumped about).
        var local by remember { mutableStateOf<List<Track>?>(null) }
        var dragFrom by remember { mutableIntStateOf(-1) }
        LaunchedEffect(queue, dragging) {
            if (local == null || dragging >= 0) return@LaunchedEffect
            if (queue.map { it.id } == local!!.map { it.id }) local = null
            else { delay(1500); local = null } // the player never confirmed: show what it has
        }
        val shown = local ?: queue
        val playing = queue.getOrNull(index)
        // keys survive moves so a row being dragged keeps its gesture; repeats of the same song get a counter
        val keys = remember(shown) { val seen = HashMap<String, Int>(); shown.map { t -> val n = seen.merge(t.id, 1, Int::plus)!!; "${t.id}#$n" } }
        LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(bottom = 12.dp)) {
            itemsIndexed(shown, key = { i, _ -> keys.getOrElse(i) { "$i" } }) { i, t ->
                val current = t === playing
                val isDragged = dragging == i
                val swipe = remember { Animatable(0f) }
                val scope = rememberCoroutineScope()
                val idx by rememberUpdatedState(i)
                Row(
                    Modifier.fillMaxWidth().height(58.dp)
                        .then(if (isDragged) Modifier.zIndex(1f) else Modifier.animateItem())
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f; translationX = swipe.value; alpha = 1f - (-swipe.value / size.width).coerceIn(0f, 1f) * 0.7f }
                        .clip(RoundedCornerShape(if (mat) 16.dp else 10.dp))
                        .background(if (current) c.chip else if (isDragged) c.surfaceHigh else Color.Transparent)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (swipe.value < -size.width * 0.4f) { swipe.animateTo(-size.width.toFloat()); Playback.remove(idx); swipe.snapTo(0f) }
                                        else swipe.animateTo(0f)
                                    }
                                },
                                onHorizontalDrag = { ch, dx -> ch.consume(); scope.launch { swipe.snapTo((swipe.value + dx).coerceAtMost(0f)) } },
                            )
                        }
                        .press(if (current) "${t.title}, playing" else "Play ${t.title}", scale = 0.985f) { Playback.skipTo(i) }
                        .semantics {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction("Remove from queue") { Playback.remove(idx); true },
                                if (idx > 0) CustomAccessibilityAction("Move up") { Playback.move(idx, idx - 1); true } else null,
                                if (idx < queue.lastIndex) CustomAccessibilityAction("Move down") { Playback.move(idx, idx + 1); true } else null,
                            )
                        }
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box {
                        Cover(t.art, 44.dp, if (mat) 12.dp else 6.dp)
                        if (current) Box(Modifier.size(44.dp).clip(RoundedCornerShape(if (mat) 12.dp else 6.dp)).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                            Icon(if (mat) MaterialIcons.Audio else Icons.Audio, Color.White, 18.dp)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Txt(t.title, pt(Type.row, 16, 500))
                        Txt(t.artist, pt(Type.sub, 13, 400), color = c.onSecondary)
                    }
                    Box(
                        Modifier.size(44.dp).pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { local = Playback.queue; dragFrom = idx; dragging = idx; dragOffset = 0f },
                                onDragEnd = {
                                    if (dragFrom >= 0 && dragging >= 0 && dragFrom != dragging) Playback.move(dragFrom, dragging) // one move, on release
                                    dragging = -1; dragOffset = 0f; dragFrom = -1
                                },
                                onDragCancel = { local = null; dragging = -1; dragOffset = 0f; dragFrom = -1 },
                                onDrag = { ch, d ->
                                    ch.consume()
                                    dragOffset += d.y
                                    val list = local ?: return@detectDragGestures
                                    val steps = (dragOffset / rowPx).roundToInt()
                                    val to = (dragging + steps).coerceIn(0, list.lastIndex)
                                    if (steps != 0 && to != dragging) {
                                        local = list.toMutableList().apply { add(to, removeAt(dragging)) } // on screen only
                                        dragOffset -= (to - dragging) * rowPx
                                        dragging = to
                                    }
                                },
                            )
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (mat) MaterialIcons.Reorder else Icons.Reorder, c.onSecondary, 18.dp) }
                }
            }
        }
    }
}
