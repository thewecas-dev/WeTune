# Liquid Glass on Android: Kyant0 vs QWEA0 vs WeTune

Research only. No app code was changed. Both repos were shallow-cloned into the session scratchpad and read, not built.
Snapshots: Kyant0/AndroidLiquidGlass `kmp` @ `eb4a0ea` (2026-11-12), QWEA0/Liquid-Glass-Android `main` @ `16afe4d` (2026-09-29).
WeTune: uncommitted working tree of `ui/glass/LiquidGlass.kt`, `ui/glass/GlassKit.kt`, `ui/Glass.kt`.

Shorthand for links:
- **K** = `https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/`
- **Q** = `https://github.com/QWEA0/Liquid-Glass-Android/blob/16afe4d8b5d08a370d3f8c50b08d7f5c6eb5da4d/`

---

## Summary

- **Kyant0/AndroidLiquidGlass ("Backdrop")** is a small, well-built **Compose Multiplatform** library (Apache-2.0, Maven Central `io.github.kyant0:backdrop:2.0.1`, minSdk 21, about 4.6k stars, pushed today). It records the backdrop into a `GraphicsLayer` and runs a composable `RenderEffect` chain (`vibrancy()`, `blur()`, `lens()`, or a custom AGSL effect) on a per-element layer. Highlight, outer shadow and inner shadow are separate modifier nodes. Its lens is very simple: one SDF, an **analytic gradient**, and a circular height profile. That profile is **the same `1 - sqrt(1 - x²)` curve WeTune uses**. It has no shape merging; press, stretch and drag are left to the app (the demos use `layerBlock`).
- **QWEA0/Liquid-Glass-Android** is a **View-system** library (`FrameLayout` subclass, MIT, JitPack, minSdk 24, about 300 stars). It has three pipelines: an AGSL lens on 33+, RenderNode blur on 31–32, and a C++/NEON CPU path on 24–30. Its single AGSL pass is the most complete of the three implementations: inverse-power bevel profile, per-channel dispersion scaled by slope, a two-lobe normal-lit rim, a vibrancy curve, per-pixel adaptive tint, a tinted-medium model, a touch bulge, a 2-shape smin merge, and sample clamping. It is also the heaviest (13.5k lines with demo widgets), it uses a `com.example` package, and it is not suited to a Compose app.
- **WeTune's own glass** (one AGSL pass, 4-shape smooth union, thick-lens magnify, 3-tap aberration, luminance floor/ceiling, scroll-driven activity, jelly squash done in the SDF, cross-window overlay via screen coordinates) already matches or beats both libraries on the features we want. **Keep our own code.** The useful things to borrow are performance and robustness details, not a new architecture:
  1. Shrink the 24 dp layer margin and skip the shadow SDF while the shadow is 0. The shader currently runs for about 3–4× more pixels than needed on small buttons.
  2. Use a single-shape shader variant with Kyant's analytic gradient and `radius × 1.5` gradient smoothing. Group glass keeps the smin version.
  3. Stop rebuilding the `RenderEffect` on every finger move while glow and rim are off. Quantize inputs the way QWEA does.
  4. Clamp sample coordinates to the recorded backdrop, as QWEA's `sampleLo/Hi` does.
  5. Optionally add QWEA's vibrancy curve (a saturation lift with highlight protection).

---

## Kyant0/AndroidLiquidGlass

**License:** Apache-2.0 (`LICENSE`, and the header on every file). **Artifact:** `io.github.kyant0:backdrop:2.0.1` on Maven Central ([K backdrop/build.gradle.kts L84](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/build.gradle.kts#L84)). Depends on `io.github.kyant0:shapes` (G2 / capsule shapes). Compose Multiplatform: Android, desktop and iOS (Skiko). Docs: https://kyant.gitbook.io/backdrop. Default branch `kmp`. 47 open issues.

### Architecture

- **Backdrop capture.** `Modifier.layerBackdrop(backdrop)` goes on the content behind the glass. Its node draws the content and then records it into a shared `GraphicsLayer`: `recordLayer(backdrop.graphicsLayer) { backdrop.onDraw(this@draw) }` ([K LayerBackdropModifier.kt L57](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/commonMain/kotlin/com/kyant/backdrop/backdrops/LayerBackdropModifier.kt#L57)). This is a display-list reference, not a pixel copy, which is exactly WeTune's `liquidBackdrop`.
- **Drawing the backdrop in the glass.** `LayerBackdrop.drawBackdrop` translates by `layerCoordinates.localPositionOf(coordinates)` and calls `drawLayer(graphicsLayer)` ([K LayerBackdrop.kt L59–L66](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/commonMain/kotlin/com/kyant/backdrop/backdrops/LayerBackdrop.kt#L59)). It falls back to `positionInWindow` deltas when that throws, with a `// TODO: outer transformations lead to wrong position calculation` (L61). If the glass has its own `layerBlock` (scale or translation for press effects), `InverseLayerScope` applies the inverse matrix so the backdrop stays fixed while the glass moves.
- **Per-glass layer.** `DrawBackdropNode` ([K DrawBackdropModifier.kt L234–L388](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/commonMain/kotlin/com/kyant/backdrop/DrawBackdropModifier.kt#L234)) owns one `GraphicsLayer`. Each draw does `recordLayer(layer, size + 2*padding) { drawBackdrop() }`, then `layer.topLeft = -padding` and `drawLayer(layer)` (L285–L304). The effect chain is set as `graphicsLayer.renderEffect` and rebuilt only in `updateEffects()`, which runs under `observeReads`, so it re-runs whenever a state read inside `effects {}` changes (L350–L368). Clipping uses `placeWithLayer { clip = true; shape; compositingStrategy = Offscreen }` (L254–L258, L306–L314). The shader therefore does **not** compute coverage; the clip does.
- **Effect chain.** `BackdropEffectScope` accumulates `renderEffect = renderEffect.chain(effect)` via `createChainEffect(new, previous)`, so the order of calls is the order of passes ([K android internal/RenderEffect.kt](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/androidMain/kotlin/com/kyant/backdrop/internal/RenderEffect.kt)). `blur()` grows `padding` by the radius so edges sample real content ([K effects/Blur.kt L9–L29](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/commonMain/kotlin/com/kyant/backdrop/effects/Blur.kt#L9)). `lens()` shrinks it again by `refractionHeight`.
- **Composition helpers.** `rememberCombinedBackdrop(a, b, …)` draws several backdrops stacked; this is WeTune's `under`. The `exportedBackdrop` parameter lets a glass element record *itself* (backdrop plus surface) into another `LayerBackdrop`, so glass can sit over glass (used by the tab-bar thumb over the tabs). `rememberCanvasBackdrop { }` draws an arbitrary backdrop.
- **API levels.** `isRenderEffectSupported() = SDK >= 31`; `isRuntimeShaderSupported() = SDK >= 33` ([K android Platform.kt](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/androidMain/kotlin/com/kyant/backdrop/Platform.kt)). On 31–32, `lens()` and `vibrancy()` with AGSL are silent no-ops, while blur and color filters still apply. Below 31 every effect is a no-op: you get the raw, clipped backdrop plus the highlight stroke and the shadow. There is no tint fallback; the app supplies one via `onDrawSurface`. minSdk is 21.

### Shaders ([K internal/Shaders.kt](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt))

- **Rounded-rect SDF with per-corner radii** (L22–L48): `radiusAt()` picks the radius by quadrant, then the standard `sdRoundedRect`. The gradient is **analytic**, not finite-differenced:
  ```glsl
  float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
      float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
      if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) return sign(coord) * normalize(max(cornerCoord, 0.0));
      else { float gradX = step(cornerCoord.y, cornerCoord.x); return sign(coord) * float2(gradX, 1.0 - gradX); }
  }
  ```
- **Refraction** (L63–L84). The height profile is a quarter circle, `circleMap(x) = 1 - sqrt(1 - x*x)`. The displacement runs along the (inward) normal. It is not Snell's law, just a simplified bend:
  ```glsl
  if (-sd >= refractionHeight) return content.eval(coord);          // flat interior: untouched
  float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;   // refractionAmount passed negated (Lens.kt L49) → inward
  float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));      // smoother normals round corners
  float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
  return content.eval(coord + d * grad);
  ```
  `depthEffect` blends the edge normal with the radial direction from the centre, which gives a dome or "depth" feel. There is **no magnification of the flat middle**: the interior is an exact pass-through.
- **Dispersion** (L104–L158). Seven taps (red, orange, yellow, green, cyan, blue, purple) are summed with spectral weights. The intensity is `chromaticAberration * (x*y)/(hx*hy)`, so it is **strongest on the diagonals and corners and zero along the straight edges**. This is an artistic choice: it costs 7 `eval`s per bevel pixel.
- **Highlight** (L161–L204, [K highlight/HighlightModifier.kt](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/commonMain/kotlin/com/kyant/backdrop/highlight/HighlightModifier.kt)). A separate node draws the outline as a **stroke** (`width` 0.5 dp, blurred by `width/2`) into its own layer with `BlendMode.Plus`. The stroke's paint shader is `color * pow(abs(dot(grad, (cos a, sin a))), falloff)`, which lights both the lit and the opposite edge. `Ambient` lights only the facing side and draws it as white or black. `Plain` is a flat 38% white stroke.
- **Vibrancy and color.** `vibrancy()` is a fixed `ColorMatrix` with saturation 1.5 ([K effects/ColorFilter.kt L41–L45](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/commonMain/kotlin/com/kyant/backdrop/effects/ColorFilter.kt#L41)). `colorControls(brightness, contrast, saturation)` builds the classic Rec.709 saturation and contrast matrix (L47–L75). These are linear, so there is no highlight protection.
- **Tint.** The library has none; the demos draw it in `onDrawSurface`: `drawRect(tint, blendMode = Hue); drawRect(tint.copy(alpha = .75f))` ([K app LiquidButton.kt L91–L99](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidButton.kt#L91)).
- **Outer shadow** ([K shadow/ShadowModifier.kt L73–L111](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/commonMain/kotlin/com/kyant/backdrop/shadow/ShadowModifier.kt#L73)). A blurred `Paint` draws the outline offset, then a `BlendMode.Clear` outline punches out the shape so the shadow never shows *through* the glass. Defaults: radius 24 dp, 10% black.
- **Inner shadow** ([K shadow/InnerShadowModifier.kt L78–L133](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/commonMain/kotlin/com/kyant/backdrop/shadow/InnerShadowModifier.kt#L78)). Fill the shape, clear an offset copy, apply a Decal blur through the layer `RenderEffect`, and clip to the shape.
- **Lens magnification.** None in the library. The Magnifier demo builds it from a custom `runtimeShaderEffect`.
- **Shapes.** `lens()` requires a `RoundedRectangularShape` or a `CornerBasedShape`, and **throws** for any other shape ([K effects/Lens.kt](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/backdrop/src/commonMain/kotlin/com/kyant/backdrop/effects/Lens.kt)). The SDF is circular-arc even when the shape is a G2 superellipse, so the outline and the bend can differ very slightly.

### Interaction (demo app, not the library)

- `InteractiveHighlight` ([K app utils/InteractiveHighlight.kt](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/InteractiveHighlight.kt)) uses a `pressProgress` spring (0.5, 300) and a finger-position spring. It draws a radial `Plus` glow under the finger with a tiny AGSL `smoothstep(radius, radius*.5, dist)`.
- Press, stretch and squash go through `layerBlock` ([K LiquidButton.kt L63–L87](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidButton.kt#L63)): the scale grows by 4 dp/height, translation follows `maxOffset * tanh(0.05 * offset / maxOffset)` (rubber-band), and scaleX/scaleY stretch along the drag angle. The backdrop is inverse-transformed so it does not move with the glass.
- `DampedDragAnimation` ([K app utils/DampedDragAnimation.kt](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt)) is a velocity-tracked squash for sliders and tab thumbs: `scaleX /= 1 - clamp(v*.75, ±.2); scaleY *= 1 - clamp(v*.25, ±.2)`.
- The bottom-tabs thumb animates `lens(10dp*progress, 14dp*progress, chromaticAberration = true)`, the highlight and shadow alpha, and the inner shadow radius, all driven by `pressProgress` ([K LiquidBottomTabs.kt ~L241–L270](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt#L241)). The lens appears only while pressed, the opposite of WeTune's "lens at rest".
- **No smooth union or merging** anywhere in the library or the demos (grep for `smin` finds nothing).
- Adaptive luminance demo: the backdrop is downscaled to 5×5, and its average luminance drives `colorControls` and the blur over a 1 s tween ([K AdaptiveLuminanceGlassContent.kt](https://github.com/Kyant0/AndroidLiquidGlass/blob/eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/destinations/AdaptiveLuminanceGlassContent.kt)).

### Performance

- Each node has a `RuntimeShaderCache` keyed by name (`getOrPut(key) { RuntimeShader(src) }`), so a node never recompiles its shader.
- The effect chain is rebuilt only when observed state changes, not every frame. It *is* rebuilt every frame while a press spring runs, because `effects {}` reads `pressProgress`.
- Shadow and highlight nodes set `shouldAutoInvalidate = false` and cache `Paint`, `Path` and the clip.
- Layers are created in `onAttach` and released through `GraphicsContext.releaseGraphicsLayer` in `onDetach`.
- Interior pixels exit early (`if (-sd >= refractionHeight) return content.eval(coord)`), so the flat middle costs one sample.
- There is no downsampling; it relies on platform blur.

### API ergonomics

```kotlin
val backdrop = rememberLayerBackdrop()
Box(Modifier.layerBackdrop(backdrop)) { /* content */ }
Box(Modifier.drawBackdrop(backdrop, shape = { Capsule() },
    effects = { vibrancy(); blur(2.dp.toPx()); lens(12.dp.toPx(), 24.dp.toPx()) },
    highlight = { null }, shadow = { null }, innerShadow = null,
    onDrawSurface = { drawRect(tint) }))
```

The knobs are `lens(height, amount, depthEffect, chromaticAberration)`, `blur(radius, edgeTreatment)`, `vibrancy()`, `colorControls()`, `opacity()`, `colorFilter()`, custom `runtimeShaderEffect(key, agsl)`, `Highlight(width, blurRadius, alpha, style)`, `Shadow(radius, offset, color)`, `InnerShadow(...)`, `layerBlock`, `exportedBackdrop`, and the `onDrawBehind`, `onDrawSurface` and `onDrawFront` hooks.

### Strengths, weaknesses, maturity

- **Strengths:** clean modifier-node design; composable effects; analytic normals; correct backdrop inverse-transform for press scaling; glass-on-glass via `exportedBackdrop`; multiplatform; widely used; active.
- **Weaknesses:** one shape per element with no merging; no tint, contrast or adaptive logic in the library; lens limited to rounded rects (throws otherwise); dispersion is 7 taps and only at the corners; nothing on 31–32 beyond blur; nothing below 31 beyond a clip; shadows and highlight are separate offscreen layers, about 3 extra layers per glass when all are enabled.
- **Maturity:** high for a hobby library: v2.x on Maven Central, an extensive catalog app, docs site.

---

## QWEA0/Liquid-Glass-Android

**License:** MIT ("Copyright (c) 2025-2026 pandadog"). **Artifact:** JitPack `com.github.QWEA0:liquidglass:v2.0.11`, an AAR with a prebuilt `.so` for **arm64-v8a and armeabi-v7a only**. minSdk 24, NDK 27. Package `com.example.liquidglass`. The README explicitly recommends Kyant0's library for Compose apps. 3 open issues; last push 2026-09-29.

### Architecture

- **View-based.** `LiquidGlassView : FrameLayout` (2,336 lines) has three render tiers chosen at draw time ([Q LiquidGlassView.kt L1300–L1330](https://github.com/QWEA0/Liquid-Glass-Android/blob/16afe4d8b5d08a370d3f8c50b08d7f5c6eb5da4d/liquidglass/src/main/java/com/example/liquidglass/LiquidGlassView.kt#L1300)):
  1. **API 33+ lens pipeline**, `GlassLensRenderer`: the backdrop is recorded into a `RenderNode`, then `createChainEffect(lensShader, createBlurEffect(...))`.
  2. **API 31–32**, `HardwareBackdropBlur`: `RenderNode` plus blur, then an optional aberration shader and a saturation `ColorFilterEffect`.
  3. **API 24–30**: CPU path. It captures to a `Bitmap` (pooled), blurs with C++ box or IIR-Gaussian (NEON), applies chromatic aberration in C++, and checks an 8×8-sample content hash to skip work.
- **Backdrop capture.** `BackdropCapture` ([Q BackdropCapture.kt](https://github.com/QWEA0/Liquid-Glass-Android/blob/16afe4d8b5d08a370d3f8c50b08d7f5c6eb5da4d/liquidglass/src/main/java/com/example/liquidglass/BackdropCapture.kt)) draws the source view (the parent by default) into the `RenderNode` canvas using the public `View.draw()` at each level of the path. This avoids re-entering the recording of a `RenderNode` that is mid-record, and avoids reference cycles. The glass branch is hidden with `setTransitionVisibility(INVISIBLE)`, so no invalidate is triggered. **Nested sampling is limited to one level:** neighbouring glass is hidden while inside another glass's capture. Without that, close-together glasses re-record each other exponentially (the class comment cites "15 dynamic glasses → tens of thousands of records per frame").
- **Cross-window.** Positions are deltas from `getLocationOnScreen`, and `WindowStackBackdrop` draws every window of the activity below a toast or dialog, adding `FLAG_DIM_BEHIND` dim layers ([Q WindowStackBackdrop.kt](https://github.com/QWEA0/Liquid-Glass-Android/blob/16afe4d8b5d08a370d3f8c50b08d7f5c6eb5da4d/liquidglass/src/main/java/com/example/liquidglass/WindowStackBackdrop.kt)). This is the same idea as WeTune's `LocalLiquidOverlay` with screen coordinates.
- **Outward-refraction workaround.** `createRuntimeShaderEffect` exposes no `childSampleRadius`, so the child input is only valid inside the output clip. For outward sampling the effect node is drawn into an outer `RenderNode` with `setUseCompositingLayer(true)` covering the margin ([Q GlassLensRenderer.kt L319–L321, L408–L419](https://github.com/QWEA0/Liquid-Glass-Android/blob/16afe4d8b5d08a370d3f8c50b08d7f5c6eb5da4d/liquidglass/src/main/java/com/example/liquidglass/GlassLensRenderer.kt#L319)). This is a real-device pitfall worth knowing.
- **Dynamic backdrop.** `enableDynamicBackground = true` simply calls `invalidate()` from `onDraw` every frame (L1309, L1321). That is a continuous redraw, and a battery cost. The default `false` captures once. The README calls forgetting this "the #1 integration mistake".

### The lens shader ([Q GlassLensRenderer.kt L54–L273](https://github.com/QWEA0/Liquid-Glass-Android/blob/16afe4d8b5d08a370d3f8c50b08d7f5c6eb5da4d/liquidglass/src/main/java/com/example/liquidglass/GlassLensRenderer.kt#L54))

- **SDF.** Per-corner `sdRoundedBox4` plus a second shape merged with `sminPoly(d, d2, k)` (L84–L113). "Flat edges" extend the primary rect outside the view, so a side with no bevel can butt against a neighbour without a seam.
- **Coverage** is anti-aliased in-shader, so no clip path is needed: `cov = clamp(0.5 - d/1.5, 0, 1)` (L125).
- **Normal** by **central differences** (4 SDF calls) (L131–L140).
- **Height profile** (L143–L156). Inverse power, "gravitational lens": `slope = ((1+4t)^-p - 5^-p) / (1 - 5^-p)`, where `t = -d/bevel`. With p = 2 most of the bend packs into the outer few pixels and leaves a faint magnification tail inside. `p = 0` gives a quadratic bevel `(1-t)²`. A `refractionNoFold` option caps the displacement at `1/slope'(0)`, so the mapping stays monotonic and there is no mirrored compression ring.
- **Refraction** (L164–L165): `offset = n * (refractDir * slope * refractPx * (1 + 0.6*press))`. Inward is the default, matching iOS; outward is an option.
- **Touch bulge** (L168–L176): a Gaussian `bump = touchAmp * exp(-r²/(σ²*0.3))` pulls samples toward the finger.
- **Dispersion** (L179–L181), 3 taps scaled by slope, so it exists only where it bends: `cR = coord + offset*(1 - disp*slope)`, `cG = coord + offset`, `cB = coord + offset*(1 + disp*slope)`. The doc notes it fixed an old bug where a scalar offset added to both x and y produced a fixed 45° fringe.
- **Sample clamping** (L183–L188): `clamp(c, sampleLo, sampleHi)` with a safe rect (the view rect for inward sampling, the recorded area ∩ source for outward), quantized to 4 px. This avoids "transparent black" rims.
- **Rim softening** (L190–L204): optional, 3 extra taps per channel along the normal (9 evals).
- **Vibrancy curve** (L216–L225): saturation boost with protection.
  ```glsl
  float satNow = max3(col) - min3(col);
  float room = 1.0 - smoothstep(0.2, 0.85, satNow);   // already-saturated pixels get less
  float hl   = 1.0 - smoothstep(0.75, 0.98, lum);     // near-white pixels get less
  float amount = 1.0 + (satFactor - 1.0) * mix(0.3, 1.0, room * hl);
  ```
- **Adaptive tint, per pixel** (L228–L237): `e = smoothstep(.35,.75,lum); col = mix(col, 1-e, .14 + .08e)`. Bright areas are darkened slightly and dark areas lightened. This is a gentler cousin of WeTune's floor and ceiling.
- **Tinted glass as a medium** (L243–L248): `absorbed = col * mix(1, tint, .85)`, `scattered = tint * .38 * (1 - lum)`, then mixed by the tint's alpha. The hue stays visible over dark backdrops, while backdrop detail survives over light ones.
- **Rim light** (L256–L269). Two symmetric lobes, `pow(max(±dot(n,-L),0), 4.5)` (measured from iOS 26 Control Center screenshots, per the doc). Each lobe is a 2 px hairline one pixel inside the edge, plus a ≤6 px glow on the lit side only. There is no direction-independent term, so the sides are dark. The light direction can follow the gravity sensor (`LightSourceController`: low-pass filtered, ref-counted, rotation-aware).
- **Size-adaptive lens** ([Q LiquidGlassView.kt L1453–L1466](https://github.com/QWEA0/Liquid-Glass-Android/blob/16afe4d8b5d08a370d3f8c50b08d7f5c6eb5da4d/liquidglass/src/main/java/com/example/liquidglass/LiquidGlassView.kt#L1453)): bevel ≤ ratio × short side, and refraction ≤ 0.7 × short side × min(1, short/110 dp), so a 48 dp button gets about 38 px. WeTune's `BEVEL = 0.46 × short side, clamp 9–30 dp` does the same job.

### Interaction

- Press: a `ValueAnimator` (180 ms in, 320 ms out, decelerate) drives the `press` uniform: refraction +60%, specular −35%, touch bulge ([Q LiquidGlassView.kt L2150–L2170](https://github.com/QWEA0/Liquid-Glass-Android/blob/16afe4d8b5d08a370d3f8c50b08d7f5c6eb5da4d/liquidglass/src/main/java/com/example/liquidglass/LiquidGlassView.kt#L2150)). The whole view also scales via `View.scaleX/Y` (`pressScale` 0.95, plus an elastic stretch toward the drag), applied as a View transform so it can overflow its bounds.
- Merge: `setSecondaryShape(rect, corner, smoothing)` merges two shapes inside **one view**. Merging across views is listed as "not done".
- Accessibility: high-contrast text gives an opaque fallback; "remove animations" turns off press and sensor effects; battery saver slows luminance sampling and turns the sensor off ([Q GlassAccessibility.kt](https://github.com/QWEA0/Liquid-Glass-Android/blob/16afe4d8b5d08a370d3f8c50b08d7f5c6eb5da4d/liquidglass/src/main/java/com/example/liquidglass/GlassAccessibility.kt)).
- `BackdropLuminanceMeter` draws the parent into 24×24 every 350 ms (1000 ms in power-save), uses an EMA, and applies 0.60/0.45 hysteresis to fire `glassAppearanceListener(isOverLight)` for foreground colour.

### Performance

- **All uniforms are quantized** (light 0.005, blur 0.5 px, press 0.01, touch 0.5 px, margin rounded to 16 px). A `data class LensParams` is compared with the previous one, and the `RenderEffect` is rebuilt **only when it differs** ([Q GlassLensRenderer.kt L392–L406](https://github.com/QWEA0/Liquid-Glass-Android/blob/16afe4d8b5d08a370d3f8c50b08d7f5c6eb5da4d/liquidglass/src/main/java/com/example/liquidglass/GlassLensRenderer.kt#L392); [Q LiquidGlassView.kt L1509–L1536](https://github.com/QWEA0/Liquid-Glass-Android/blob/16afe4d8b5d08a370d3f8c50b08d7f5c6eb5da4d/liquidglass/src/main/java/com/example/liquidglass/LiquidGlassView.kt#L1509)). At rest there is one parent recording per frame and no effect allocation.
- A shader compile failure sets `shaderBroken` and the view permanently falls back to the next pipeline. WeTune does the same with `runCatching`.
- CPU path: `BitmapPool`, C++/NEON blur, downsampling (`downsampleScale = 2`), content hashing.
- `collectFrameStats` defaults to **true** in a library, which is minor waste.

### API ergonomics

XML attributes (`app:cornerRadius`, `app:bevelWidth`, `app:refractionHeight`, `app:dispersionStrength`, `app:glassMaterial`, …) plus about 40 Kotlin properties; ready widgets (Button, FAB, TabBar, TabLayout with ViewPager2 sync, Chip/ChipGroup, ListItem/ListGroup, Toast, Dialog builder); a `ScrollEdgeBlurView` (progressive blur). In Compose it would have to go through `AndroidView`, and the backdrop would be the hosting `ComposeView` drawn through `View.draw()`, which is exactly what the author warns against.

### Strengths, weaknesses, maturity

- **Strengths:** the most physically careful shader of the three (profile options, normal-lit two-lobe rim, slope-scaled dispersion, sample clamping, medium-style tint, vibrancy curve); strong engineering notes on real-device pitfalls; quantized effect rebuilds; real accessibility handling; it runs down to API 24.
- **Weaknesses:** View-only; `com.example` package; huge surface area (13.5k lines including widgets); native code with arm-only ABIs (no x86_64 emulator `.so`); JitPack only; `enableDynamicBackground` means redrawing every frame; two shapes at most; single maintainer with a few hundred stars; the doc is mostly in Chinese.
- **Maturity:** medium. Version 2.0.11, active through September 2026; the v2 doc lists several "never worked before" fixes (NDK not linked, XML attributes not parsed), which suggests the codebase is young.

---

## Side-by-side table

| | **Kyant0 Backdrop** | **QWEA0 LiquidGlass** | **WeTune** (`ui/glass/LiquidGlass.kt`) |
|---|---|---|---|
| Toolkit | Compose Multiplatform | Android View (`FrameLayout`) | Compose (Android) |
| License | Apache-2.0 | MIT | own |
| minSdk / lens API | 21 / 33 | 24 / 33 | 26 / 33 |
| < 33 fallback | 31–32 blur + color filters; < 31 clipped raw backdrop | 31–32 RenderNode blur; 24–30 C++/NEON CPU blur + aberration | 31–32 blur(×3+1) + tint + stroke; < 31 tint + stroke |
| Backdrop capture | `GraphicsLayer` record of content, `localPositionOf` | `RenderNode` + `View.draw()` of parent, screen coordinates | `GraphicsLayer` record of content, `positionOnScreen` |
| Glass-on-glass / stacked backdrops | `exportedBackdrop`, `CombinedBackdrop` | hides neighbour glass (1 nesting level) | `under` chain |
| Cross-window (dialogs) | no | `WindowStackBackdrop` | `LocalLiquidOverlay` (screen coordinates) |
| Coverage / clip | layer clip to `Shape` | in-shader AA (1.5 px) | in-shader AA (`0.5 - d`) |
| SDF | rounded rect, 4 radii | rounded rect, 4 radii + 1 extra | up to 4 rounded rects, 1 radius each |
| Normal | **analytic** (+ r×1.5 smoothing) | central difference (4 SDF calls) | forward difference (2 extra smin-of-4 calls) |
| Height profile | quarter circle `1-√(1-x²)` | inverse power `(1+4t)^-p` or quadratic | quarter circle (same as Kyant) |
| Middle magnification | no (exact pass-through) | faint tail of profile + touch bulge | yes, `MAGNIFY` 8% thick lens |
| Dispersion | 7 taps, corners only | 3 taps × slope (+ optional soft 9) | 3 taps, ±22% of bend |
| Blur | `RenderEffect` blur chain | `RenderEffect` blur chain (CPU below 31) | `RenderEffect` blur chain (1 dp Regular, 0 Clear) |
| Vibrancy | linear ColorMatrix sat 1.5 | protected saturation curve | none |
| Tint / contrast | app draws `onDrawSurface` | absorption+scatter tint; per-pixel adaptive | mix tint + **luminance floor/ceiling** (WCAG-oriented) |
| Rim / specular | separate stroke layer, `pow(|n·L|)` both sides | in-shader 2-lobe hairline + lit-side glow, sensor light | in-shader rim + inner + lens glint (all tuned to 0) |
| Inner shadow | yes (separate layer) | no | no |
| Outer shadow | yes (separate layer, clear-punched) | simple rect `drawPath` | in-shader `exp(-d/r)` (tuned to 0) |
| Merge / smooth union | none | 2 shapes, one view | **up to 4 shapes in a container, seamless weighted centre** |
| Press | app-level `layerBlock` scale/tanh-drag, glow | `press` uniform + bulge + View scale | spring "jelly" inflates SDF rect (content not scaled), light follows finger |
| Scroll-reactive | no | no | yes (`activity` via `NestedScrollConnection`) |
| Effect rebuilds | on observed state change (per frame during springs) | only when quantized params change | on any `drawWithCache` read change (incl. every touch move) |
| Accessibility | none | high-contrast → opaque, reduce motion, battery saver | "Reduce transparency" setting, animator-scale 0 → still |

---

## WeTune comparison

### What WeTune already does better or uniquely

- **Multi-shape merge with a seamless bridge.** Up to 4 rects are smin'd with per-pixel soft weights `w = exp(-(ds - dm)/k)`, which blend each shape's centre and box ([LiquidGlass.kt L453–L458](app/src/main/java/com/wetune/ui/glass/LiquidGlass.kt)), so the magnification centre slides smoothly across a merged neck. Kyant has no merge; QWEA has 2 shapes in one view and uses only shape 1's centre for its bulge.
- **Thick-lens magnification of the middle** (`at = fc + (c - p) * mag * (1 - lensK)`, L467). Neither library does this at rest. Kyant's interior is a pure pass-through, which is why its glass reads flat in the middle.
- **Contrast floor and ceiling in approximately linear luminance** (L476–L481). This guarantees text legibility over anything, a stronger guarantee than QWEA's adaptive mix and absent in Kyant.
- **Scroll-aware activity** (`LiquidBackdrop.scroll` NestedScrollConnection, L128–L138) plus `LENS_AT_REST`.
- **Jelly done in the SDF** (L294–L300). The rect is inflated in-shader, so text and icons are never scaled or blurred and no inverse backdrop transform is needed. Kyant must inverse-transform the backdrop; QWEA scales the whole View, content included.
- **One pass for everything.** Coverage, lens, tint, rim and shadow are one shader on one layer per element. Kyant needs up to 4 layers (backdrop, highlight, shadow, inner shadow).
- **Cross-window overlay** for menus and sheets (screen coordinates via `LocalLiquidOverlay`), which Kyant lacks.

### Concrete differences where they do better

1. **Wasted shader work in the margin, and a shadow computed while it is off.** WeTune records every glass layer with a fixed `m = 24dp` margin on every side (L286). The shader runs on every pixel of that layer, and **before** the `cover <= 0` early exit it evaluates `sdf(p - shadowOffset)`, 4 more `box()` calls (L448–L451), even though `LiquidTuning.SHADOW = 0`. For a 52 dp `GlassButton` the layer is (52+48)² ≈ 10,000 dp² against a 2,700 dp² shape, so about 73% of the shaded pixels only compute a shadow that is zero. Kyant sizes its padding from the actual blur radius (`Blur.kt L16–L20`); QWEA rounds margin = max(3×blur, refraction reach) to 16 px.
2. **Normal cost.** WeTune's `sdf()` always smins 4 boxes, and the forward-difference gradient calls it twice more. With the initial `ds` and the shadow that comes to **16 `box()` evaluations per pixel** even for a lone button whose 3 empty slots sit at −1e4. Kyant's analytic `gradSdRoundedRect` costs one extra branch, and its `gradRadius = min(r*1.5, …)` gives smoother corner normals on rounded-rect cards (corner `Group`, `frost` sheets).
3. **Effect churn.** `drawWithCache` reads `touch` (L258, used at L295 and L396), so **every pointer move rebuilds the shape list and two `RenderEffect`s** for that element. While `GLOW` and `RIM_LIGHT` are 0, the touch position only matters at press-down, to choose which group member inflates. QWEA quantizes and compares params before rebuilding.
4. **Sample clamping.** WeTune samples `at + offB * (1 ± aberr)` unclamped. Inward bending normally stays inside the shape. But on small or flattened shapes (e.g. 52 dp, bevel ≈ 24 dp, red at 1.22× ≈ 29 dp > half-width) and when glass sits at the edge of its backdrop's recorded area (nav and mini player near the screen bottom, overlays near a window edge), taps can land outside the recorded backdrop and fetch transparent pixels, which shows as dark or see-through fringes. QWEA clamps every tap to a safe rect.
5. **Vibrancy.** Both libraries raise saturation under glass (Kyant ×1.5 linear; QWEA protected curve). WeTune's Regular tint (white 30%) plus the floor can wash colours out. QWEA's curve is a 5-line drop-in.
6. **Slope-scaled dispersion.** QWEA scales dispersion by `slope`, so fringes vanish smoothly toward the flat interior. WeTune's `offB * (1 ± aberr)` already goes to 0 where `lensK → 0`, so it is equivalent. No change needed.

### Smaller notes

- WeTune shares **one `RuntimeShader` across all elements** (L367) and relies on `createRuntimeShaderEffect` snapshotting uniforms at creation. This holds on current Android (QWEA relies on the same thing for its colour filters), and the file comment states it. Kyant sidesteps the question with one shader per node. Keep ours, but if a device ever shows glass elements "swapping" shapes, this is the first suspect.
- Kyant's `LayerBackdrop` has a TODO about **ancestor transforms** breaking the position (L61). WeTune has the same limitation: a `liquidGlass` inside a parent that is being scaled (e.g. a `Sheet` scale-in) will see its backdrop scaled and shifted during the animation. Neither library solves it.
- The QWEA write-up on `childSampleRadius` explains why **outward** sampling needs a compositing outer node. WeTune only samples inward, so this does not apply, but it is the reason not to flip the bend outward casually.

---

## Recommendations

**Do not adopt either library as a dependency.**
- *Kyant (Apache-2.0, minSdk 21, active).* It is license-compatible (keep its NOTICE if code is copied) and would run on minSdk 26. It also supports "lens at rest, no rim": `lens(h, a)` plus `highlight = { null }, shadow = { null }`. But we would lose the 4-shape merge (`LiquidGlassContainer`, mini-player tray, tab-bar drop), the middle magnification, the contrast floor and the scroll activity. We would add the `kyant shapes` dependency, pull Compose Multiplatform artifacts, and take a lens that throws on non-rounded shapes. We would end up rewriting most of our shader as a custom `runtimeShaderEffect` anyway.
- *QWEA (MIT).* Wrong UI toolkit (View `FrameLayout`), a `com.example` package, a native `.so` without x86_64, JitPack only, per-frame `invalidate()` for live backdrops. Use it as a reference only.

**Borrow these into `app/src/main/java/com/wetune/ui/glass/LiquidGlass.kt`, in priority order:**

1. **Size the margin to what is actually drawn.** Replace `val m = (24 * d).toInt()` (L286) with the real reach: `swell + SQUASH * minDim + 3 * blur + (shadow > 0 ? shadowR*4 + shadowY : 0)`, rounded up to 4–8 px so it does not churn. With the shadow off, small buttons shade about 3–4× fewer pixels. *(Technique: Kyant `Blur.kt` padding; QWEA `computeLensMargin`.)*
2. **Skip the shadow SDF when it is off.** In `SRC`, guard it: `float sh = 0.0; if (shadow.x > 0.0) { float dsh = sdf(p - float2(0.0, shadow.z)); sh = shadow.x * exp(-max(dsh, 0.0) / shadow.y); }` (L448–L449). That removes 4 `box()` calls per pixel today.
3. **Single-shape fast path with an analytic normal.** Compile a second `LensShader` source for `group == null`: one `box()`, plus Kyant's `gradSdRoundedRect` with `gradRadius = min(r * 1.5, min(h.x, h.y))`, and `c = rect centre` (no weights). This drops from about 16 `box()` calls to 1 per pixel for every standalone pill, button, chip and card, and gives rounder bending at card corners. Keep the current smin source for `LiquidGlassContainer`. *(Kyant `Shaders.kt` L40–L48, L79–L80.)*
4. **Stop rebuilding on every finger move.** Read `touch` in the cache block only when `GLOW > 0 || RIM_LIGHT > 0`. Otherwise capture the down position once into a plain (non-state) field used for the group-member hit test. Optionally quantize `p` (press) to 1/100 and `act` to 1/50 before building, the way QWEA does. *(QWEA `LensParams` compare, `LiquidGlassView.kt` L1509–L1536.)*
5. **Clamp the three taps** to the recorded backdrop: pass `float4 safe` = (margin+1, margin+1, margin+w-1, margin+h-1), intersected with the backdrop's on-screen bounds when they are known, and wrap each `content.eval(clamp(x, safe.xy, safe.zw))`. This costs 3 clamps and removes edge fringes near screen and window edges. *(QWEA `GlassLensRenderer.kt` L183–L188, L463–L497.)*
6. **Optional look tweak: a vibrancy curve** after the aberration samples and before the tint, with `satFactor` about 1.3 for Regular and 1.0 for Clear. *(QWEA L216–L225.)* Check that it does not push accent-tinted chips past the luminance ceiling. It runs before the floor and ceiling, so it should not.
7. **Optional accessibility:** treat the system "High contrast text" setting like `Settings.glassSolid` (QWEA `GlassAccessibility.kt`), and read `ANIMATOR_DURATION_SCALE` reactively rather than once in `remember` (L249).

**Leave as is:** the quarter-circle profile (it is Kyant's too), inward-only bending, the in-SDF jelly, the shared shader, `under` stacking, and screen-coordinate overlays. Rim, inner light, sheen, glow and shadow stay at 0 to match the settled "lens at rest, no rim" look. If a rim is ever wanted again, QWEA's two-lobe `pow(±n·L, 4.5)` hairline with no constant term is the closest to iOS 26.

---

## Sources

- Kyant0/AndroidLiquidGlass: https://github.com/Kyant0/AndroidLiquidGlass (README, LICENSE, `backdrop/` module, catalog `app/`). Files cited: `backdrop/src/commonMain/kotlin/com/kyant/backdrop/{internal/Shaders.kt, effects/Lens.kt, effects/Blur.kt, effects/ColorFilter.kt, effects/RenderEffect.kt, DrawBackdropModifier.kt, BackdropEffectScope.kt, backdrops/LayerBackdrop.kt, backdrops/LayerBackdropModifier.kt, backdrops/CombinedBackdrop.kt, highlight/*.kt, shadow/*.kt, internal/InverseLayerScope.kt}`, `backdrop/src/androidMain/kotlin/com/kyant/backdrop/{Platform.kt, RuntimeShader.kt, internal/RenderEffect.kt}`, `app/src/commonMain/kotlin/com/kyant/backdrop/catalog/{components/LiquidButton.kt, components/LiquidBottomTabs.kt, utils/InteractiveHighlight.kt, utils/DampedDragAnimation.kt, destinations/AdaptiveLuminanceGlassContent.kt}`. Docs: https://kyant.gitbook.io/backdrop
- QWEA0/Liquid-Glass-Android: https://github.com/QWEA0/Liquid-Glass-Android (README, LICENSE, `docs/LIQUID_GLASS_V2.md`). Files cited: `liquidglass/src/main/java/com/example/liquidglass/{GlassLensRenderer.kt, LiquidGlassView.kt, BackdropCapture.kt, WindowStackBackdrop.kt, HardwareBackdropBlur.kt, BackdropLuminanceMeter.kt, LightSourceController.kt, GlassAccessibility.kt}`, `liquidglass/build.gradle.kts`.
- GitHub API repo metadata (stars, license, default branch, last push), fetched 2026-11-12.
- WeTune: `app/src/main/java/com/wetune/ui/glass/LiquidGlass.kt`, `app/src/main/java/com/wetune/ui/glass/GlassKit.kt`, `app/src/main/java/com/wetune/ui/Glass.kt`, `app/src/main/java/com/wetune/ui/Kit.kt` (`pressable`), `app/build.gradle.kts` (minSdk 26).
