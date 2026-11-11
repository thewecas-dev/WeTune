package com.wetune.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wetune.Playback
import com.wetune.ui.Art
import com.wetune.ui.Ic
import com.wetune.ui.Icon
import com.wetune.ui.Segmented
import com.wetune.ui.Txt
import com.wetune.ui.Type
import com.wetune.ui.WT
import com.wetune.ui.pressable
import kotlin.math.roundToInt

/** Round glass button. */
@Composable
fun GlassButton(backdrop: LiquidBackdrop, icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp, variant: GlassVariant = GlassVariant.Regular) {
    Box(
        modifier.size(size).liquidGlass(backdrop, variant = variant).semantics { contentDescription = desc }.pressable(scale = 0.94f, onClick = onClick),
        Alignment.Center,
    ) { Icon(icon, null, size = size * 0.42f, tint = WT.c.text) }
}

/** Glass capsule with an optional icon and a label. */
@Composable
fun GlassPill(backdrop: LiquidBackdrop, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, variant: GlassVariant = GlassVariant.Regular) {
    Row(
        modifier.height(44.dp).liquidGlass(backdrop, variant = variant).pressable(scale = 0.96f, onClick = onClick).padding(horizontal = 16.dp),
        Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, size = 18.dp, tint = WT.c.text)
        Txt(label, style = Type.label.copy(fontSize = 14.sp), color = WT.c.text, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Glass toggle chip: on = glass tinted with the accent (text dark or white, whichever the tint keeps readable). */
@Composable
fun GlassChip(backdrop: LiquidBackdrop, label: String, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = WT.c
    val fg = if (!on) c.text else if (c.accent.luminance() > 0.45f) Color(0xFF141414) else Color.White
    Row(
        modifier.height(40.dp).liquidGlass(backdrop, tint = if (on) c.accent.copy(alpha = 0.85f) else null)
            .semantics { toggleableState = ToggleableState(on) }
            .pressable(scale = 0.95f, role = Role.Switch) { onChange(!on) }.padding(horizontal = 14.dp),
        Arrangement.spacedBy(6.dp), Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, size = 16.dp, tint = fg)
        Txt(label, style = Type.caption.copy(fontSize = 13.sp), color = fg, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * Settings → Appearance → Liquid glass preview: every element over a busy, scrolling backdrop.
 * Scroll the stripes under the glass, press the pieces, drag the loose drop into the bar to see them merge.
 */
@Composable
fun LiquidGlassLab() {
    var variant by remember { mutableStateOf(GlassVariant.Regular) }
    var chip by remember { mutableStateOf(true) }
    val bd = rememberLiquidBackdrop()
    Segmented(listOf("Regular" to "Regular", "Clear" to "Clear"), variant.name, { variant = GlassVariant.valueOf(it) }, Modifier.padding(vertical = 12.dp))
    Txt(
        "Bevel ${(LiquidTuning.BEVEL * 100).roundToInt()}% of the short side (${LiquidTuning.BEVEL_MIN.value.roundToInt()}–${LiquidTuning.BEVEL_MAX.value.roundToInt()} dp) · bend ${LiquidTuning.BEND}× · " +
            "zoom ${(LiquidTuning.MAGNIFY * 100).roundToInt()}% · fringe ±${(LiquidTuning.ABERRATION * 100).roundToInt()}% · rim ${LiquidTuning.RIM.value} dp · blur ${LiquidTuning.BLUR.value} dp",
        Modifier.padding(bottom = 10.dp), style = Type.caption, color = WT.c.muted,
    )
    Box(Modifier.fillMaxWidth().height(800.dp).clip(RoundedCornerShape(24.dp))) {
        BusyBackdrop(Modifier.fillMaxSize().liquidBackdrop(bd))
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassButton(bd, Ic.Back, "Back", {}, variant = variant)
                GlassPill(bd, "Glass pill", {}, icon = Ic.Shuffle, variant = variant)
                GlassButton(bd, Ic.Search, "Search", {}, size = 44.dp, variant = variant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassChip(bd, "Toggle chip", chip, { chip = it }, icon = Ic.Repeat)
                GlassChip(bd, "Off", false, {})
                // the play orb: glass tinted with the accent
                val accent = WT.c.accent
                Box(Modifier.size(64.dp).liquidGlass(bd, tint = accent.copy(alpha = 0.8f)).pressable(scale = 0.94f) {}, Alignment.Center) {
                    Icon(Ic.Play, "Play", size = 28.dp, tint = if (accent.luminance() > 0.45f) Color(0xFF111111) else Color.White)
                }
            }
            // pills in one sheet: press one and it bulges into its neighbours
            LiquidGlassContainer(bd, Modifier.fillMaxWidth(), spacing = 12.dp, variant = variant) {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    listOf(Ic.LyricsQuote to "Lyrics", Ic.Moon to "Sleep", Ic.QueueList to "Queue").forEach { (ic, label) ->
                        Row(
                            Modifier.weight(1f).height(44.dp).glassShape().pressable(scale = 0.96f) {},
                            Arrangement.Center, Alignment.CenterVertically,
                        ) {
                            Icon(ic, null, Modifier.padding(end = 8.dp), size = 17.dp, tint = WT.c.text)
                            Txt(label, style = Type.caption.copy(fontSize = 13.sp), color = WT.c.text, weight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
            // the mini player's tray: tap the button and the pills well up out of it, melting through the bar
            var tray by remember { mutableStateOf(false) }
            LiquidGlassContainer(bd, Modifier.fillMaxWidth(), spacing = 12.dp, variant = variant) {
                Column {
                    AnimatedVisibility(
                        tray,
                        enter = fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 420f), 0.25f, TransformOrigin(1f, 1f)) + slideInVertically(spring(dampingRatio = 0.6f, stiffness = 420f)) { it },
                        exit = fadeOut(tween(200)) + scaleOut(tween(240), 0.25f, TransformOrigin(1f, 1f)) + slideOutVertically(tween(240)) { it },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            listOf("Shuffle", "Repeat", "Autoplay").forEach {
                                Box(Modifier.height(36.dp).glassShape().padding(horizontal = 14.dp), Alignment.Center) {
                                    Txt(it, style = Type.caption.copy(fontSize = 12.sp), color = WT.c.text, weight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(56.dp).glassShape().padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt("Mini player", Modifier.weight(1f), style = Type.bodyStrong, color = WT.c.text)
                        Box(
                            Modifier.size(44.dp).liquidGlass(bd, tint = if (tray) WT.c.text.copy(alpha = 0.85f) else null)
                                .semantics { contentDescription = "Options" }.pressable(scale = 0.9f) { tray = !tray },
                            Alignment.Center,
                        ) { Icon(Ic.Tune, null, size = 20.dp, tint = if (tray) WT.c.bg else WT.c.text) }
                    }
                }
            }
            // a tab bar made of two shapes in one container; drag the loose drop toward the bar and it melts in
            val startY = with(androidx.compose.ui.platform.LocalDensity.current) { 130.dp.toPx() }
            var drop by remember { mutableStateOf(Offset(0f, startY)) }
            LiquidGlassContainer(bd, Modifier.fillMaxWidth().height(220.dp), spacing = 22.dp, variant = variant) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.height(62.dp).glassShape().padding(horizontal = 22.dp), Arrangement.spacedBy(22.dp), Alignment.CenterVertically) {
                        Icon(Ic.Home, "Home", size = 22.dp, tint = WT.c.text)
                        Icon(Ic.Library, "Library", size = 22.dp, tint = WT.c.text)
                        Icon(Ic.Person, "You", size = 22.dp, tint = WT.c.text)
                    }
                    Box(Modifier.size(62.dp).glassShape(), Alignment.Center) { Icon(Ic.Search, "Search", size = 22.dp, tint = WT.c.text) }
                }
                Box(
                    Modifier.offset { IntOffset(drop.x.roundToInt(), drop.y.roundToInt()) }.size(64.dp).glassShape()
                        .pointerInput(Unit) { detectDragGestures { ch, d -> ch.consume(); drop += d } },
                    Alignment.Center,
                ) { Icon(Ic.Add, "Drag me", size = 22.dp, tint = WT.c.text) }
            }
        }
    }
}

/** Stripes, blobs, type and the current cover: something with edges for the lens to bend. */
@Composable
private fun BusyBackdrop(modifier: Modifier) {
    val colors = listOf(Color(0xFFFF375F), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF30D158), Color(0xFF64D2FF), Color(0xFF5E5CE6), Color(0xFFBF5AF2), Color.White, Color.Black)
    Column(modifier.verticalScroll(rememberScrollState())) {
        repeat(4) { block ->
            Art(Playback.current?.art, Modifier.fillMaxWidth().height(180.dp), RoundedCornerShape(0.dp))
            colors.forEachIndexed { i, col ->
                Box(
                    Modifier.fillMaxWidth().height(56.dp)
                        .background(Brush.horizontalGradient(listOf(col, colors[(i + block + 3) % colors.size]))),
                    Alignment.CenterStart,
                ) {
                    Txt(
                        "LIQUID GLASS  ${i + 1}  — the quick brown fox",
                        Modifier.padding(start = 12.dp),
                        style = Type.bodyStrong.copy(fontSize = 22.sp), weight = FontWeight.Black,
                        color = if (i % 2 == 0) Color.Black else Color.White, maxLines = 1,
                    )
                }
            }
        }
    }
}
