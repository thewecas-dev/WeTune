package com.wetune.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

// WeTune UI kit: every control is drawn here on top of Compose foundation.

val LocalContentColor = compositionLocalOf { Color.Black }
val LocalTextStyle = compositionLocalOf { Type.body }

private val PillShape = RoundedCornerShape(50)

/** Press feedback: a soft spring shrink instead of a ripple. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    enabled: Boolean = true,
    scale: Float = 0.94f,
    role: Role = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scale else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    graphicsLayer { scaleX = s; scaleY = s; alpha = if (pressed) 0.85f else 1f }.then(
        if (onLongClick == null) Modifier.clickable(source, null, enabled = enabled, role = role, onClick = onClick)
        else Modifier.combinedClickable(source, null, enabled = enabled, role = role, onLongClickLabel = "More options", onLongClick = onLongClick, onClick = onClick)
    )
}

/** Moving highlight over placeholder shapes while content loads. */
fun Modifier.shimmer(): Modifier = composed {
    val base = WT.c.surfaceHigh
    val shine = lerp(base, WT.c.text, 0.08f)
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    drawWithCache {
        val w = size.width
        val brush = Brush.linearGradient(listOf(base, shine, base), start = Offset(w * x - w * 0.5f, 0f), end = Offset(w * x + w * 0.5f, size.height))
        onDrawBehind { drawRect(brush) }
    }
}

/** Three bouncing bars marking the playing row; still when paused. */
@Composable
fun Equalizer(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "eq").animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "t")
    Canvas(modifier.size(16.dp).semantics { contentDescription = if (playing) "Playing" else "Paused" }) {
        val bw = size.width / 5
        for (i in 0..2) {
            val h = if (playing) 0.35f + 0.65f * ((kotlin.math.sin(t + i * 2.1f) + 1f) / 2f) else 0.35f + i * 0.15f
            drawRoundRect(color, Offset(i * bw * 2, size.height * (1 - h)), Size(bw, size.height * h), CornerRadius(bw / 2))
        }
    }
}

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Unspecified,
    weight: FontWeight? = null,
) {
    val c = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    // merged over the theme style so ad-hoc TextStyles still get Geist unless they pick their own family
    BasicText(text, modifier, LocalTextStyle.current.merge(style).merge(TextStyle(color = c, textAlign = align, fontWeight = weight)), overflow = TextOverflow.Ellipsis, maxLines = maxLines)
}

@Composable
fun Icon(icon: ImageVector, desc: String?, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = LocalContentColor.current) {
    Image(rememberVectorPainter(icon), desc, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

@Composable
fun IconBtn(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
    haptic: Boolean = false,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    Box(
        modifier.size(size).clip(CircleShape).background(background).pressable(enabled) {
            if (haptic) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        },
        Alignment.Center,
    ) { Icon(icon, desc, size = iconSize, tint = if (enabled) tint else tint.copy(alpha = 0.35f)) }
}

enum class BtnKind { Primary, Secondary, Ghost, Danger }

@Composable
fun Btn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.Primary, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = WT.c
    val (bg, fg) = when (kind) {
        BtnKind.Primary -> c.text to c.bg
        BtnKind.Secondary -> c.text.copy(alpha = 0.1f) to c.text
        BtnKind.Ghost -> Color.Transparent to c.accent
        BtnKind.Danger -> c.danger.copy(alpha = 0.14f) to c.danger
    }
    Row(
        modifier.heightIn(min = 46.dp).clip(PillShape).background(bg)
            .pressable(enabled, scale = 0.96f, onClick = onClick).graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            if (icon != null) {
                Icon(icon, null, size = 20.dp)
                Spacer(Modifier.width(8.dp))
            }
            Txt(text, style = Type.label.copy(fontSize = 15.sp), weight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Selectable glass chip. Selected chips invert (solid text color) for a clear, color-blind-safe state. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = WT.c
    val fg = if (selected) c.bg else c.text
    Row(
        modifier.heightIn(min = 36.dp).then(if (selected) Modifier.clip(PillShape).background(c.text) else Modifier.glassChip(PillShape))
            .semantics { this.selected = selected }
            .pressable(scale = 0.95f, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, size = 16.dp, tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Txt(text, style = Type.label, color = fg, weight = if (selected) FontWeight.SemiBold else null, maxLines = 1)
    }
}

/** iOS-style segmented control. */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = WT.c
    Row(modifier.fillMaxWidth().glassChip(PillShape).padding(3.dp)) {
        options.forEach { (key, label) ->
            val on = key == selected
            Txt(
                label,
                Modifier.weight(1f).clip(PillShape)
                    .then(if (on) Modifier.background(if (c.dark) Color(0xFF2A2A2F) else Color.White).border(1.dp, rimBrush(), PillShape) else Modifier)
                    .semantics { this.selected = on }
                    .pressable(scale = 0.97f, role = Role.Tab) { onSelect(key) }.padding(vertical = 9.dp),
                style = Type.label, align = TextAlign.Center, maxLines = 1,
                weight = if (on) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

/** Visual-only switch; put `toggleable` on the surrounding row so the whole row is the touch target. */
@Composable
fun SwitchKnob(checked: Boolean) {
    val c = WT.c
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, spring(dampingRatio = 0.7f), label = "knob")
    val track by animateColorAsState(if (checked) c.accent else c.muted.copy(alpha = 0.35f), label = "track")
    Box(Modifier.size(51.dp, 31.dp).clip(PillShape).background(track).padding(2.dp)) {
        Box(Modifier.offset(x = x).size(27.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

@Composable
fun CheckMark(checked: Boolean) {
    val c = WT.c
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(if (checked) c.accent else Color.Transparent)
            .border(1.5.dp, if (checked) c.accent else c.muted.copy(alpha = 0.5f), CircleShape),
        Alignment.Center,
    ) { if (checked) Icon(Ic.Check, null, size = 16.dp, tint = c.onAccent) }
}

/** Seek bar: tap or drag anywhere; grows while dragging. */
@Composable
fun SeekSlider(
    value: Float, onChange: (Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalContentColor.current,
    thickness: Dp = 5.dp, thumb: Boolean = false,
) {
    var width by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    var lastTick by remember { mutableIntStateOf(-1) }
    val onChangeLatest by rememberUpdatedState(onChange)
    // a soft tick every 5% while dragging
    val change: (Float) -> Unit = { f ->
        val bucket = (f * 20).toInt()
        if (dragging && bucket != lastTick) { if (lastTick >= 0) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); lastTick = bucket }
        onChangeLatest(f)
    }
    val done by rememberUpdatedState(onDone)
    val h by animateDpAsState(if (dragging) thickness * 2 else thickness, label = "seek")
    Box(
        modifier.fillMaxWidth().height(32.dp).onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { change((it.x / width).coerceIn(0f, 1f)); done() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; change((it.x / width).coerceIn(0f, 1f)) },
                    onDragEnd = { dragging = false; done() },
                    onDragCancel = { dragging = false; done() },
                ) { ch, _ -> change((ch.position.x / width).coerceIn(0f, 1f)) }
            }
            .semantics {
                contentDescription = "Seek"
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { change(it); done(); true }
            },
        Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(h).clip(PillShape).background(color.copy(alpha = 0.22f)))
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(h).clip(PillShape).background(color))
        if (thumb) {
            val dot = if (dragging) 16.dp else 12.dp
            Box(Modifier.offset { IntOffset((value.coerceIn(0f, 1f) * width - dot.toPx() / 2).roundToInt(), 0) }.size(dot).background(color, CircleShape))
        }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    leading: ImageVector? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    actions: KeyboardActions = KeyboardActions.Default,
    onClear: (() -> Unit)? = null, // shows a ✕ while there's text
) {
    val c = WT.c
    Column(modifier) {
        if (label != null) Txt(label, Modifier.padding(start = 4.dp, bottom = 6.dp), style = Type.caption, color = c.muted)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).glassChip(if (singleLine) PillShape else RoundedCornerShape(Tokens.RadiusField))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            if (leading != null) {
                Icon(leading, null, size = 20.dp, tint = c.muted)
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Txt(placeholder, color = c.muted, maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label ?: placeholder },
                    textStyle = Type.body.copy(color = c.text),
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = if (singleLine) 1 else 6,
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = keyboard,
                    keyboardActions = actions,
                    cursorBrush = SolidColor(c.accent),
                )
            }
            if (onClear != null && value.isNotEmpty()) Box(
                Modifier.padding(start = 8.dp).size(24.dp).clip(CircleShape).background(c.text.copy(alpha = 0.12f))
                    .pressable(scale = 0.9f, onClick = onClear).semantics { contentDescription = "Clear" },
                Alignment.Center,
            ) { Icon(Ic.Close, null, size = 12.dp, tint = c.text) }
        }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 28.dp, color: Color = WT.c.accent) {
    val angle by rememberInfiniteTransition(label = "spin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Canvas(modifier.size(size).rotate(angle).semantics { contentDescription = "Loading" }) {
        drawArc(color, 0f, 270f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(WT.c.outline))
}

/** Dropdown anchored to the top-end of its parent. With a [title] it gets a header with a visible close (✕) button. */
@Composable
fun Menu(open: Boolean, onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (!open) return
    val c = WT.c
    Popup(alignment = Alignment.TopEnd, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(visible, enter = fadeIn(tween(120)) + scaleIn(tween(160), 0.85f, TransformOrigin(1f, 0f))) {
            val shape = RoundedCornerShape(28.dp)
            CompositionLocalProvider(LocalContentColor provides c.text) {
                Column(
                    Modifier.padding(8.dp).widthIn(min = 230.dp, max = 290.dp).shadow(28.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                        .frost(shape).padding(8.dp),
                ) {
                    if (title != null) Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(title.uppercase(), Modifier.weight(1f), style = Type.overline, color = c.muted, maxLines = 1)
                        IconBtn(Ic.Close, "Close", onDismiss, size = 36.dp, iconSize = 18.dp, tint = c.muted)
                    }
                    content()
                }
            }
        }
    }
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit, icon: ImageVector? = null, danger: Boolean = false, enabled: Boolean = true) {
    val c = WT.c
    val color = if (danger) c.danger else c.text
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(20.dp)).pressable(enabled, scale = 0.98f, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, size = 21.dp, tint = color.copy(alpha = 0.82f))
            Spacer(Modifier.width(14.dp))
        }
        Txt(text, color = color, style = Type.body, maxLines = 1)
    }
}

/** Modal card. [actions] sit bottom-right. */
@Composable
fun Sheet(title: String, onDismiss: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val c = WT.c
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalContentColor provides c.text) {
            Column(Modifier.fillMaxWidth().frost(RoundedCornerShape(30.dp)).padding(22.dp)) {
                Txt(title, style = Type.title)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), Arrangement.spacedBy(8.dp, Alignment.End), content = actions)
            }
        }
    }
}

/** Rounded container for settings-style rows, separated by hairlines. */
@Composable
fun Group(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = WT.c
    Column(modifier.fillMaxWidth()) {
        if (title != null) Txt(title.uppercase(), Modifier.padding(start = 16.dp, bottom = 8.dp, top = 20.dp), style = Type.overline, color = c.muted)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface), content = content)
    }
}

@Composable
fun SettingRow(title: String, detail: String? = null, icon: ImageVector? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    val c = WT.c
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(scale = 0.98f, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(c.accent.copy(alpha = 0.15f)), Alignment.Center) {
                Icon(icon, null, size = 18.dp, tint = c.accent)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(title, style = Type.bodyStrong)
            if (detail != null) Txt(detail, style = Type.caption, color = c.muted)
        }
        trailing()
    }
}

@Composable
fun ToggleRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = WT.c
    Row(
        Modifier.fillMaxWidth().toggleable(checked, remember { MutableInteractionSource() }, null, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Txt(title, style = Type.bodyStrong)
            if (detail != null) Txt(detail, style = Type.caption, color = c.muted)
        }
        SwitchKnob(checked)
    }
}
