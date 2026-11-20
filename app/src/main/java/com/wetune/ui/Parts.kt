package com.wetune.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wetune.sources.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- loading ------------------------------------------------------------------------------------------------------

/** A value fetched off the main thread; keeps the last good value while a retry or refresh runs. */
@Stable
class Loaded<T>(initial: T?) {
    var value by mutableStateOf(initial)
    var error by mutableStateOf<String?>(null)
    var tick by mutableIntStateOf(0)
    fun retry() { error = null; tick++ }
}

/** Fetches [fetch] on IO when [key] changes (or on retry). [initial] shows at once (a cache), then gets replaced. */
@Composable
fun <T> load(key: Any, initial: T? = null, fetch: () -> T): Loaded<T> {
    val l = remember(key) { Loaded(initial) }
    LaunchedEffect(key, l.tick) {
        runCatching { withContext(Dispatchers.IO) { fetch() } }
            .onSuccess { l.value = it; l.error = null }
            .onFailure { if (l.value == null) l.error = it.message ?: "Something went wrong" }
    }
    return l
}

/** Skeleton block: a soft sweep every 1.4 s, drawn in the draw phase only (no recomposition). */
@Composable
fun Modifier.shimmer(radius: Dp = 6.dp): Modifier {
    val c = WT.c
    val t = rememberInfiniteTransition(label = "shimmer")
    val x = t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "x")
    return drawBehind {
        val w = size.width
        drawRoundRect(
            Brush.linearGradient(listOf(c.surface, c.surfaceHigh.copy(alpha = 0.6f), c.surface), start = androidx.compose.ui.geometry.Offset(w * x.value - w, 0f), end = androidx.compose.ui.geometry.Offset(w * x.value, 0f)),
            cornerRadius = CornerRadius(radius.toPx()),
        )
    }
}

/** Skeleton of a song row at its exact final size. */
@Composable
fun SkeletonRow() = Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(48.dp).shimmer())
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth(0.6f).height(13.dp).shimmer(4.dp))
        Box(Modifier.fillMaxWidth(0.4f).height(11.dp).shimmer(4.dp))
    }
}

@Composable
fun SkeletonTiles() = Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    repeat(3) { Column { Box(Modifier.size(150.dp).shimmer(10.dp)); Spacer(Modifier.height(8.dp)); Box(Modifier.size(110.dp, 12.dp).shimmer(4.dp)) } }
}

/** Centered empty / offline message with an optional action (R11-A11). */
@Composable
fun EmptyState(ic: Ic, title: String, text: String, action: String? = null, modifier: Modifier = Modifier, onAction: () -> Unit = {}) {
    val c = WT.c
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(c.surface, CircleShape), contentAlignment = Alignment.Center) { Icon(ic, c.onSecondary, 30.dp) }
        Spacer(Modifier.height(16.dp))
        Txt(title, Type.title.copy(fontSize = Type.title.fontSize * 0.95f, textAlign = TextAlign.Center))
        Spacer(Modifier.height(6.dp))
        Txt(text, Type.body.copy(textAlign = TextAlign.Center), color = c.onSecondary, lines = 3)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.height(44.dp).clip(CircleShape).background(c.primary).press(action, onClick = onAction).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
                Txt(action, Type.button, color = c.onPrimary)
            }
        }
    }
}

@Composable
fun Failed(message: String, onRetry: () -> Unit) = EmptyState(Icons.Offline, "Couldn't load", message, "Try again", onAction = onRetry)

// ---- page chrome --------------------------------------------------------------------------------------------------

/** Large page title row (34 / 800) with optional trailing buttons. */
@Composable
fun PageTitle(title: String, trailing: @Composable RowScope.() -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, Type.largeTitle, Modifier.weight(1f))
        trailing()
    }

/**
 * Small centred title bar that fades in once the large title scrolls away (R11-A02). Alpha is read in the draw phase,
 * so scrolling never recomposes it. Near-solid: pages are the blur source, so a bar inside them can't blur them.
 */
@Composable
/** No page title when scrolled (the user doesn't want one): only a soft fade behind the status bar so its icons stay readable over content. [title] is unused, kept for call sites. */
@Suppress("UNUSED_PARAMETER")
fun CollapsedBar(title: String, state: LazyListState) {
    val c = WT.c
    val fade = remember(c) { Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.85f), c.bg.copy(alpha = 0f))) }
    Box(
        Modifier.fillMaxWidth()
            .graphicsLayer { alpha = if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / 120f).coerceIn(0f, 1f) }
            .background(fade).statusPad().height(16.dp),
    )
}

/** Scrolls [state] to the top when the current tab is tapped again (not when the page is merely re-shown). */
@Composable
fun ReselectToTop(state: LazyListState) {
    val start = remember { Nav.reselect }
    LaunchedEffect(Nav.reselect) { if (Nav.reselect != start) state.animateScrollToItem(0) }
}

/** "‹ Back" link row used on pushed pages (R11-A10). */
@Composable
fun BackLink(label: String) = Row(
    Modifier.padding(start = 6.dp, top = 6.dp).height(44.dp).press("Back to $label") { Nav.back() },
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(Icons.Back, WT.c.on, 20.dp)
    Txt(label, Type.body.copy(fontSize = Type.body.fontSize * 1.13f))
}

/** Album / playlist / artist as a list row (Library, search results). */
@Composable
fun CardRow(card: Card, onClick: () -> Unit) {
    val round = card.subtitle.startsWith("Artist") || card.browseId.startsWith("UC")
    Row(
        Modifier.fillMaxWidth().height(66.dp).press(card.title, scale = 0.985f, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(card.art, 56.dp, if (round) 28.dp else 6.dp)
        Column(Modifier.weight(1f)) {
            Txt(card.title, Type.row.copy(fontSize = Type.row.fontSize * 1.07f))
            if (card.subtitle.isNotEmpty()) Txt(card.subtitle, Type.sub, color = WT.c.onSecondary)
        }
    }
}

/** Segmented control (R11-A03): selected segment solid primary. */
@Composable
fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = WT.c
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp).glass(RoundedCornerShape(10.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(8.dp)).background(if (on) c.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .semantics { role = Role.Tab; this.selected = on }.press(o, scale = 0.97f) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Txt(o, Type.sub.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = if (on) c.onPrimary else c.on) }
        }
    }
}

/** Grey caption above a settings group. */
@Composable
fun Caption(text: String) = Txt(text.uppercase(), Type.sub, Modifier.padding(start = 32.dp, top = 24.dp, bottom = 6.dp), color = WT.c.onSecondary)

/** A radio group as a grouped list: tick on the chosen row (R11-A10). [options]: value to (label, detail). */
@Composable
fun Radio(options: List<Pair<String, Pair<String, String?>>>, value: String, onSelect: (String) -> Unit) = Group {
    options.forEachIndexed { i, (v, label) ->
        GroupRow(
            label.first, value = label.second, chevron = false, last = i == options.lastIndex,
            trailing = { Box(Modifier.padding(start = 8.dp).width(18.dp)) { if (v == value) Icon(Icons.Check, WT.c.on, 18.dp) } },
        ) { onSelect(v) }
    }
}

/** Single-line text field for sheets and settings. */
@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, password: Boolean = false, keyboard: KeyboardOptions = KeyboardOptions.Default, actions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default) {
    val c = WT.c
    BasicTextField(
        value, onChange, singleLine = true, textStyle = Type.body.copy(color = c.on, fontSize = Type.body.fontSize * 1.07f),
        cursorBrush = SolidColor(c.on),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (password) keyboard.copy(keyboardType = KeyboardType.Password) else keyboard,
        keyboardActions = actions,
        modifier = modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceHigh.copy(alpha = if (c.dark) 0.6f else 1f)),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Txt(placeholder, Type.body, color = c.onSecondary)
                inner()
            }
        },
    )
}

/** Sheet title + rows. */
@Composable
fun SheetTitle(text: String) = Txt(text, Type.title.copy(fontSize = Type.title.fontSize * 0.86f), Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))

@Composable
fun MenuRow(ic: Ic, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = WT.c
    Row(
        Modifier.fillMaxWidth().height(52.dp).then(if (enabled) Modifier.press(text, scale = 0.985f) { Sheet.hide(); onClick() } else Modifier.graphicsLayer { alpha = 0.45f })
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(ic, c.on, 22.dp)
        Txt(text, Type.body)
    }
}

/** The account's photo (or the You icon when signed out) for a page title's right end; opens the You tab. */
@Composable
fun ProfileButton() {
    val c = WT.c
    val a = com.wetune.youtube.Sync.account
    Box(Modifier.size(44.dp).press(if (a != null) "Profile: ${a.name}" else "Profile") { Nav.select(Tab.You) }, contentAlignment = Alignment.Center) {
        if (a?.photo != null) Cover(a.photo, 34.dp, 17.dp, description = null)
        else Box(Modifier.size(34.dp).clip(CircleShape).background(c.chip), contentAlignment = Alignment.Center) { Icon(Icons.You, c.on, 18.dp) }
    }
}

/** A settings page's top bar: back, then this page's own title (not the page you came from). */
@Composable
fun BackTitle(title: String) = Row(Modifier.padding(start = 6.dp, top = 6.dp, end = 16.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(44.dp).press("Back") { Nav.back() }, contentAlignment = Alignment.Center) { Icon(Icons.Back, WT.c.on, 20.dp) }
    Txt(title, Type.title, Modifier.padding(start = 2.dp))
}

/**
 * Room for the status bar, measured as if it were always shown: Now Playing can hide the bar without anything behind
 * it moving.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Modifier.statusPad(): Modifier = windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility)

/** The status bar's height (always-shown measure), for a list's top content padding so the list scrolls under the bar. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun statusTop(): androidx.compose.ui.unit.Dp =
    androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
