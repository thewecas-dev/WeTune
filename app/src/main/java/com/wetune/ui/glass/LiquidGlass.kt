package com.wetune.ui.glass

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wetune.Settings
import com.wetune.ui.WT
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.min

/*
 * Liquid glass, after Apple's iOS 26 material: the backdrop is lensed like a thick drop (pulled in hard along the
 * rim with colour fringing, magnified in the middle), barely blurred, tinted just enough to keep text readable over
 * anything, with a specular rim lit from the top left (and a fainter inner one on the opposite edge), a top sheen,
 * a soft shadow; pressing squashes it like jelly with the light following the finger, and shapes in one
 * [LiquidGlassContainer] melt together when close.
 *
 * API 33+: one AGSL pass per glass element over a re-used capture of the screen behind it. All tuning is in [LiquidTuning].
 * API 31–32: blur + tint + highlight stroke (no lensing). Below 31: tint + highlight only.
 */

/** Every knob of the material in one place. */
object LiquidTuning {
    /** The lens bevel: the band along the rim where the backdrop bends, as a share of the shorter side, clamped. */
    const val BEVEL = 0.46f
    val BEVEL_MIN = 9.dp
    val BEVEL_MAX = 30.dp
    /** How far the backdrop is pulled in at the very rim, × the bevel (Regular / Clear). */
    const val BEND = 1.0f
    const val BEND_CLEAR = 1.2f
    /** Thick-lens zoom in the flat middle (0.08 ≈ 9% magnified). */
    const val MAGNIFY = 0.08f
    /** Colour fringing where it bends: red bends this much more, blue this much less. */
    const val ABERRATION = 0.22f
    /** Specular rim: width, strength on the lit edge, and the fainter inner highlight on the opposite edge. */
    val RIM = 2.6.dp
    const val RIM_LIGHT = 0.95f
    const val INNER_LIGHT = 0.42f
    /** Soft gloss over the top half. */
    const val SHEEN = 0.09f
    /** Backdrop blur under Regular glass (Clear has none): sharp enough that the bending reads. */
    val BLUR = 1.dp
    /** Press: the drop widens and flattens ([SWELL] + [SQUASH] × its shorter side, each side), then wobbles back. */
    val SWELL = 3.dp
    const val SQUASH = 0.08f
    const val JELLY_PRESS = 0.38f // spring damping; lower = wobblier
    const val JELLY_RELEASE = 0.3f
}

/** What the glass looks through. The content under the glass records itself here (a display-list reference, no pixel copy). */
@Stable
class LiquidBackdrop internal constructor(internal val layer: GraphicsLayer, internal val under: LiquidBackdrop?) {
    internal var origin by mutableStateOf(Offset.Zero)
}

/** [under]: a backdrop drawn beneath this one (the player background under a scrolling pane), so the glass sees both. */
@Composable
fun rememberLiquidBackdrop(under: LiquidBackdrop? = null): LiquidBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer, under) { LiquidBackdrop(layer, under) }
}

/** The backdrop liquid glass in this region looks through; null where glass is frosted. Never provide it to content that records into it. */
val LocalLiquid = compositionLocalOf<LiquidBackdrop?> { null }

/** Put on the content the glass floats over (not on anything that contains the glass itself). */
fun Modifier.liquidBackdrop(b: LiquidBackdrop): Modifier =
    onGloballyPositioned { b.origin = it.positionInWindow() }
        .drawWithContent {
            b.layer.record { this@drawWithContent.drawContent() }
            drawLayer(b.layer)
        }

/** Regular adapts to what's behind it (tint + contrast floor); Clear is see-through, for over media. */
enum class GlassVariant { Regular, Clear }

/**
 * One piece of liquid glass, drawn behind this element's content. [corner] Dp.Infinity = capsule (circle when square).
 * [interactive]: pressing squashes it like jelly, with the light following the finger.
 * [tint]: coloured / brighter glass (alpha = strength); text on it goes dark when the tint is light (luminance > 0.45).
 * [dark]: dark glass for white text whatever the app theme (the player).
 */
fun Modifier.liquidGlass(
    backdrop: LiquidBackdrop,
    corner: Dp = Dp.Infinity,
    variant: GlassVariant = GlassVariant.Regular,
    interactive: Boolean = true,
    tint: Color? = null,
    dark: Boolean? = null,
): Modifier = glassSurface(backdrop, variant, null, corner, interactive, 0.dp, tint, dark)

/** Shapes marked with [glassShape] inside the container are one sheet of glass: within [spacing] of each other they merge. */
class LiquidGlassScope internal constructor(internal val group: GlassGroup) {
    fun Modifier.glassShape(corner: Dp = Dp.Infinity): Modifier = composed {
        val key = remember { Any() }
        val px = with(LocalDensity.current) { if (corner == Dp.Infinity) CAPSULE else corner.toPx() }
        DisposableEffect(key) { onDispose { group.remove(key) } }
        onGloballyPositioned { group.put(key, it, px) }
    }
}

/** Apple's GlassEffectContainer: up to 4 shapes rendered in one pass, blending together when they come close. */
@Composable
fun LiquidGlassContainer(
    backdrop: LiquidBackdrop,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    variant: GlassVariant = GlassVariant.Regular,
    dark: Boolean? = null,
    content: @Composable LiquidGlassScope.() -> Unit,
) {
    val group = remember { GlassGroup() }
    val scope = remember(group) { LiquidGlassScope(group) }
    Box(modifier.glassSurface(backdrop, variant, group, Dp.Infinity, true, spacing, null, dark)) { scope.content() }
}

private const val CAPSULE = 1e4f
private const val MAX_SHAPES = 4

internal data class GlassRect(val rect: Rect, val corner: Float)

internal class GlassGroup {
    var coords: LayoutCoordinates? = null
    private val members = LinkedHashMap<Any, Pair<LayoutCoordinates, Float>>()
    var shapes by mutableStateOf(emptyList<GlassRect>())

    fun put(key: Any, c: LayoutCoordinates, corner: Float) { members[key] = c to corner; refresh() }
    fun remove(key: Any) { members.remove(key); refresh() }

    /** Member rects relative to the container; written only when they change, so moving the whole bar costs nothing. */
    fun refresh() {
        val g = coords?.takeIf { it.isAttached } ?: return
        val next = members.values.filter { it.first.isAttached }.take(MAX_SHAPES)
            .map { (c, r) -> GlassRect(g.localBoundingBoxOf(c, clipBounds = false), r) }
        if (next != shapes) shapes = next
    }
}

private fun Modifier.glassSurface(
    b: LiquidBackdrop,
    variant: GlassVariant,
    group: GlassGroup?,
    corner: Dp,
    interactive: Boolean,
    spacing: Dp,
    tint: Color?,
    darkGlass: Boolean?,
): Modifier = composed {
    val dark = darkGlass ?: WT.c.dark
    val solidBase = if (!dark) WT.c.surface else if (WT.c.dark) WT.c.surfaceHigh else Color(0xFF1E1E22)
    val solid = Settings.glassSolid // "Reduce transparency"
    val ctx = LocalContext.current
    // system "Remove animations": no jelly or glow on press
    val still = remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val press = remember { Animatable(0f) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    var self by remember { mutableStateOf(Offset.Zero) }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    onGloballyPositioned {
        self = it.positionInWindow()
        if (group != null) { group.coords = it; group.refresh() }
    }.then(
        if (!interactive || still) Modifier else Modifier.pointerInput(Unit) {
            // watches the touch without consuming it, so the buttons inside still get their clicks and swipes
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touch = down.position
                scope.launch { press.animateTo(1f, spring(dampingRatio = LiquidTuning.JELLY_PRESS, stiffness = Spring.StiffnessMediumLow)) }
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull() ?: break
                    if (!c.pressed) break
                    touch = c.position
                }
                scope.launch { press.animateTo(0f, spring(dampingRatio = LiquidTuning.JELLY_RELEASE, stiffness = Spring.StiffnessLow)) }
            }
        },
    ).drawWithCache {
        val d = density
        val m = (24 * d).toInt() // room around the shape for the shadow, the jelly and blur edges
        val p = press.value // overshoots both ways: wider and flatter, then narrower and taller, settling like jelly
        val lit = p.coerceIn(0f, 1f)
        val cornerPx = if (corner == Dp.Infinity) CAPSULE else corner.toPx()
        val base = group?.shapes ?: listOf(GlassRect(size.toRect(), cornerPx))
        if (base.isEmpty()) return@drawWithCache onDrawBehind {}
        val swell = LiquidTuning.SWELL.toPx()
        val shapes = if (p == 0f) base else base.map {
            if (group != null && !it.rect.inflate(8 * d).contains(touch)) return@map it
            val s = min(it.rect.width, it.rect.height)
            val ex = p * (swell + LiquidTuning.SQUASH * s)
            val ey = p * (swell * 0.25f - LiquidTuning.SQUASH * 0.5f * s)
            it.copy(rect = Rect(it.rect.left - ex, it.rect.top - ey, it.rect.right + ex, it.rect.bottom + ey))
        }
        val clear = variant == GlassVariant.Clear
        val glassTint = when {
            solid -> tint?.copy(alpha = 0.92f) ?: solidBase.copy(alpha = 0.85f)
            tint != null -> tint
            dark -> Color.Black.copy(alpha = if (clear) 0.04f else 0.14f)
            else -> Color.White.copy(alpha = if (clear) 0.08f else 0.3f)
        }
        // contrast floor / ceiling: dark text needs light glass, white text dark glass (≥ 4.5:1 with the sheen on top)
        val lightTint = tint != null && tint.luminance() > 0.45f
        val lumMin = if (tint != null) (if (lightTint) 0.3f else 0f) else if (dark) 0f else (if (clear) 0.3f else 0.42f)
        val lumMax = if (tint != null) (if (lightTint) 1f else 0.08f) else if (dark) (if (clear) 0.16f else 0.07f) else 1f
        val blur = if (solid) 12 * d else if (clear) 0f else LiquidTuning.BLUR.toPx()
        val lens = Lens
        val effect: RenderEffect? = when {
            lens != null -> {
                val minDim = shapes.minOf { min(it.rect.width, it.rect.height) }
                val bevel = (minDim * LiquidTuning.BEVEL).coerceIn(LiquidTuning.BEVEL_MIN.toPx(), LiquidTuning.BEVEL_MAX.toPx())
                lens.effect(
                    m.toFloat(), shapes, spacing.toPx().coerceAtLeast(0.5f), bevel,
                    bevel * (if (clear) LiquidTuning.BEND_CLEAR else LiquidTuning.BEND), if (solid) 0f else LiquidTuning.MAGNIFY,
                    LiquidTuning.ABERRATION, glassTint, lumMin, lumMax,
                    LiquidTuning.RIM.toPx(), LiquidTuning.RIM_LIGHT * (0.7f + 0.3f * lit), LiquidTuning.INNER_LIGHT, LiquidTuning.SHEEN,
                    touch, lit, 30 * d, if (dark) 0.22f else 0.1f, 6 * d, 2 * d, blur,
                )
            }
            Build.VERSION.SDK_INT >= 31 -> BlurEffect(blur * 3 + 1f, blur * 3 + 1f, TileMode.Clamp) // no lens: frost more
            else -> null
        }
        // pre-33 fallback: draw the shapes as rounded rects with a tint and a highlight stroke
        val path = if (lens == null) Path().apply {
            shapes.forEach { addRoundRect(RoundRect(it.rect, CornerRadius(min(it.corner, min(it.rect.width, it.rect.height) / 2)))) }
        } else null
        val fallbackTint = if (solid || tint != null) glassTint else if (dark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.62f)
        val shine = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.45f)),
            Offset.Zero, Offset(size.width, size.height),
        )
        val layerSize = IntSize(ceil(size.width).toInt() + 2 * m, ceil(size.height).toInt() + 2 * m)
        onDrawBehind {
            layer.topLeft = IntOffset(-m, -m)
            layer.renderEffect = effect
            layer.record(layerSize) { drawBackdrop(b, self, m.toFloat()) }
            if (path == null) drawLayer(layer)
            else {
                clipPath(path) {
                    drawLayer(layer)
                    drawRect(fallbackTint)
                    if (lit > 0f) drawCircle(Color.White.copy(alpha = 0.18f * lit), 30 * d, touch)
                }
                drawPath(path, shine, style = Stroke(1.2f * d))
            }
        }
    }
}

/** The backdrop (and whatever it sits on) as seen from [self], shifted by the layer margin. */
private fun DrawScope.drawBackdrop(b: LiquidBackdrop, self: Offset, m: Float) {
    b.under?.let { drawBackdrop(it, self, m) }
    val o = b.origin - self
    translate(o.x + m, o.y + m) { drawLayer(b.layer) }
}

/**
 * One compiled shader for every glass element: a RenderEffect snapshots the uniforms when it's made, so setting them
 * for the next element doesn't disturb the last. A driver that rejects the shader gets the API 31 look instead of a crash.
 */
private val Lens: LensShader? by lazy { if (Build.VERSION.SDK_INT >= 33) runCatching { LensShader() }.getOrNull() else null }

@RequiresApi(33)
private class LensShader {
    private val shader = RuntimeShader(SRC)

    fun effect(
        margin: Float, shapes: List<GlassRect>, k: Float, bevel: Float, bend: Float, mag: Float, aberr: Float, tint: Color,
        lumMin: Float, lumMax: Float, rimW: Float, rimLight: Float, innerLight: Float, sheen: Float,
        touch: Offset, touchK: Float, glowR: Float, shadowA: Float, shadowR: Float, shadowY: Float, blur: Float,
    ): RenderEffect {
        shader.setFloatUniform("origin", margin, margin)
        for (i in 0 until MAX_SHAPES) {
            val s = shapes.getOrNull(i)
            if (s == null) shader.setFloatUniform(RECTS[i], -1e4f, -1e4f, -1e4f + 1f, -1e4f + 1f)
            else shader.setFloatUniform(RECTS[i], s.rect.left, s.rect.top, s.rect.right, s.rect.bottom)
        }
        fun r(i: Int) = shapes.getOrNull(i)?.corner ?: 0f
        shader.setFloatUniform("radii", r(0), r(1), r(2), r(3))
        shader.setFloatUniform("k", k)
        shader.setFloatUniform("bevel", bevel)
        shader.setFloatUniform("bend", bend)
        shader.setFloatUniform("mag", mag)
        shader.setFloatUniform("aberr", aberr)
        shader.setFloatUniform("light", -0.5f, -0.866f)
        shader.setFloatUniform("rimL", rimW, rimLight, innerLight)
        shader.setFloatUniform("sheen", sheen)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
        shader.setFloatUniform("lum", lumMin, lumMax)
        shader.setFloatUniform("touch", touch.x, touch.y, touchK)
        shader.setFloatUniform("glowR", glowR)
        shader.setFloatUniform("shadow", shadowA, shadowR, shadowY)
        val lens = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        return (if (blur > 0f) android.graphics.RenderEffect.createChainEffect(lens, android.graphics.RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)) else lens)
            .asComposeRenderEffect()
    }

    private companion object {
        val RECTS = arrayOf("r0", "r1", "r2", "r3")

        // Rounded-rect SDFs, smooth-min'd together; the SDF gradient is the surface normal that drives the lens and the rim light.
        const val SRC = """
uniform shader content;
uniform float2 origin;
uniform float4 r0; uniform float4 r1; uniform float4 r2; uniform float4 r3;
uniform float4 radii;
uniform float k;
uniform float bevel;
uniform float bend;
uniform float mag;
uniform float aberr;
uniform float2 light;
uniform float3 rimL;
uniform float sheen;
uniform float4 tint;
uniform float2 lum;
uniform float3 touch;
uniform float glowR;
uniform float3 shadow;

float box(float2 p, float4 r, float rad) {
    float2 c = (r.xy + r.zw) * 0.5;
    float2 h = (r.zw - r.xy) * 0.5;
    rad = min(rad, min(h.x, h.y));
    float2 q = abs(p - c) - h + rad;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rad;
}

float smin(float a, float b) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float sdf(float2 p) {
    return smin(smin(box(p, r0, radii.x), box(p, r1, radii.y)), smin(box(p, r2, radii.z), box(p, r3, radii.w)));
}

half4 main(float2 fc) {
    float2 p = fc - origin;
    float4 ds = float4(box(p, r0, radii.x), box(p, r1, radii.y), box(p, r2, radii.z), box(p, r3, radii.w));
    float d = smin(smin(ds.x, ds.y), smin(ds.z, ds.w));
    float dsh = sdf(p - float2(0.0, shadow.z));
    float sh = shadow.x * exp(-max(dsh, 0.0) / shadow.y);
    float cover = clamp(0.5 - d, 0.0, 1.0);
    if (cover <= 0.0) return half4(0.0, 0.0, 0.0, half(sh));

    // the shape(s) this pixel belongs to, softly weighted so a merged bridge has no seam: their centre and box
    float dm = min(min(ds.x, ds.y), min(ds.z, ds.w));
    float4 w = exp(-(ds - dm) / k);
    w /= (w.x + w.y + w.z + w.w);
    float4 R = r0 * w.x + r1 * w.y + r2 * w.z + r3 * w.w;
    float2 c = (R.xy + R.zw) * 0.5;

    float2 g = float2(sdf(p + float2(1.0, 0.0)) - d, sdf(p + float2(0.0, 1.0)) - d);
    float2 n = g / max(length(g), 0.0001);
    float t = max(-d, 0.0);
    float x = clamp(t / bevel, 0.0, 1.0);
    float lensK = 1.0 - sqrt(1.0 - (1.0 - x) * (1.0 - x)); // circular bevel: steep at the rim, flat inside
    // thick lens: the rim pulls the backdrop in (red and blue apart), the flat middle magnifies it
    float2 offB = -n * bend * lensK;
    float2 at = fc + (c - p) * mag * (1.0 - lensK);
    float3 col = float3(
        content.eval(at + offB * (1.0 + aberr)).r,
        content.eval(at + offB).g,
        content.eval(at + offB * (1.0 - aberr)).b
    );

    col = mix(col, tint.rgb, tint.a);
    // contrast floor/ceiling (luminance ~ c^2): keeps text over the glass readable whatever passes behind
    float L = dot(col * col, float3(0.2126, 0.7152, 0.0722));
    if (L > lum.y) col *= sqrt(lum.y / L);
    if (L < lum.x) {
        float s = (1.0 - sqrt(lum.x)) / max(1.0 - sqrt(L), 0.001);
        col = 1.0 - (1.0 - col) * s;
    }

    // light from the top left, swinging toward the finger while pressed
    float2 tl = touch.xy - c;
    float2 lv = mix(light, tl / max(length(tl), 0.001), touch.z * 0.85);
    lv /= max(length(lv), 0.001);
    float facing = dot(n, lv);
    float rim = 1.0 - smoothstep(0.0, rimL.x, t);
    float ib = (t - rimL.x * 1.8) / rimL.x;
    float inner = exp(-ib * ib);
    float spec = rim * (0.28 + 0.9 * pow(max(facing, 0.0), 2.0)) * rimL.y
        + inner * pow(max(-facing, 0.0), 2.0) * rimL.z
        + lensK * pow(max(facing, 0.0), 4.0) * 0.14;
    float v = clamp((p.y - R.y) / max(R.w - R.y, 1.0), 0.0, 1.0);
    float gloss = sheen * (1.0 - smoothstep(0.0, 0.55, v));
    float2 dt = p - touch.xy;
    float glow = touch.z * 0.22 * exp(-dot(dt, dt) / (glowR * glowR));
    col = clamp(col + spec + gloss + glow, 0.0, 1.0);

    return half4(half3(col) * half(cover), half(cover)) + half4(0.0, 0.0, 0.0, half(sh * (1.0 - cover)));
}
"""
    }
}
