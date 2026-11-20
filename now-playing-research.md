# Now Playing rebuild: audit, research, recommendations

Date: 2026-11-12. Scope: the full-screen player's **background**, **cover** and **playback controls**, rebuilt from scratch so the user can customize them. Research and writing only; no app code was changed.

> Snapshot caveat: another agent was editing `GlassPlayer.kt`, `NowBar.kt`, `PlayerCore.kt`, `Settings.kt`, `SettingsScreen.kt` and the untracked `ui/glass/` package while this was written. The working tree still contains Liquid Glass code (`Settings.glassStyle`, `LocalLiquid`, `liquidGlass(...)`) even though commit `bba9d1d` removed it. Line numbers below are from the working tree on 2026-11-12 and may drift.

---

## TL;DR

- **Today:** `GlassPlayer.kt` (1085 lines) holds everything. Settings are stored as plain strings: 5 covers × 6 backgrounds × 5 layouts plus toggles. The cover and background are tangled together (the full-width cover is drawn inside the background), and nothing continuous can be adjusted (blur, size, corner radius, dim).
- **Performance:**
  - The whole player recomposes every 250 ms, because the playback position is read in composition (`PlayerCore.kt:157`).
  - The stretch background stacks about 11 full-screen layers, 5–6 of them blurred, then blurs them all again behind a framed cover, and doubles everything during the song-change crossfade.
  - The same artwork is decoded three times.
- **Blur:** `minSdk 26`, but `Modifier.blur` does nothing below API 31. On Android 8–11 every blur style renders sharp, with no fallback.
- **Accessibility:**
  - Hard-coded translucent white text over any artwork, with no contrast guarantee.
  - Several touch targets under 48 dp (grabber, quality tag, like).
  - TalkBack reads the seek bar as a percentage.
  - Reduce-motion is ignored by the player's animations.
- **Research:** mainstream apps (Apple Music, Spotify, YouTube Music, Tidal) offer one curated look on a contrast-safe background. Apps known for customization (Symfonium, Retro, Plexamp, Poweramp) all reduce to **presets made of a few independent axes**, and Symfonium stores them as versioned, importable style files.
- **Recommendation:**
  - One typed, serializable `NowPlayingStyle(background, cover, controls, layout, colors, motion)` using sealed types, with presets that are just values of it.
  - Pure layer composables `(style, colors, state)`, so the same code drives the player, a live Customize preview and screenshot tests.
  - A single custom `Layout`.
  - Position read only in the draw phase.
  - One downscaled blur that works on API 26+.
  - Automatic scrim and contrast solving that the user can't turn below WCAG.
  - Migration from today's `np*` keys.

---

## 1. Audit of the current implementation

### 1.1 Files and sizes

| File | Lines | Role |
|---|---|---|
| `ui/player/GlassPlayer.kt` | 1085 | The whole portrait + landscape player, 5 control layouts, seek bar, lyric line, menus, immersive lyrics/queue pane, shared-element plumbing |
| `ui/player/CoverFill.kt` | 324 | "Cover-made" backgrounds (stretch, mirror, blur), contrast shading, grain, `CoverSpace` layout math |
| `ui/player/NowStyles.kt` | 210 | Non-cover backgrounds (fade, ribbed, black) and framed covers (card, disc, strips, vinyl) |
| `ui/player/PlayerCore.kt` | 679 | `ArtPalette` extraction, `PlayerUi` state holder, `PlayerScreen` (status bar, swipe-to-close), lyrics view, queue view |
| `ui/player/Depth.kt` | 47 | Gravity-sensor tilt for the depth effect |
| `ui/player/NowBar.kt` | 327 | Mini player / in-player now bar with option trays |
| `ui/Theme.kt` | 129 | Own design system (no Material), `hueAccent()` |
| `Settings.kt` | 90 | SharedPreferences exposed as Compose state (`Str`, `Bool`, `IntPref` delegates) |
| `ui/SettingsScreen.kt` | 356 | Appearance page with the Now Playing groups |

Build facts that matter for the rebuild (`app/build.gradle.kts`): `minSdk = 26`, `targetSdk = 35`, Compose BOM `2024.12.01`, Kotlin 2.1.0 (strong skipping on by default), **no Material 3 dependency**, `androidx.palette:palette-ktx:1.0.0`, `dev.chrisbanes.haze:haze:1.2.2`, Coil 3.0.4. No screenshot tests, no `androidTest` source set.

### 1.2 Architecture today

**Entry.** `AppUi.kt:106` extracts the palette once per song (`NowColors.palette = artPalette(ctx, art)`). `AppUi.kt:178-189` shows `PlayerScreen` in an `AnimatedVisibility` (slide + scale from bottom). `PlayerScreen` (`PlayerCore.kt:141-179`) hides the status bar (`npHideStatus`), reads the playback position with `val pos by rememberPosition()` (`PlayerCore.kt:157`, polled every 250 ms in `Playback.kt:206`), builds a new `PlayerUi` object every time and wraps `GlassPlayer(ui)` in a swipe-down-to-close `draggable`.

**Layer stack in `GlassPlayer` (`GlassPlayer.kt:129-336`), back to front:**

1. Root `BoxWithConstraints` filled with `lerp(pal.sea, Black, 0.35f)` (`:160`). Landscape branches off to `LandscapePlayer` (`:161`, `:672-708`).
2. **Background** — a `Crossfade(ui.t, tween(650))` (`:193-213`) with a depth `graphicsLayer` (scale 1.08, tilt translation, `:195-200`) and `.blur(paneBlur or 40.dp ambient)` (`:201`). Inside it:
   - `StyledBackdrop(bg, …)` for `fade | ribbed | black` (`NowStyles.kt:75-105`), and/or
   - `CoverBackdrop(…)` for `stretch | mirror | blur` (`CoverFill.kt:92-182`). When the cover style is `edge`, **the cover itself is drawn inside the background layer** (`:210`), so cover and background are not separable.
3. Depth vignette `Canvas` (`:215-217`) and a pane dim box (`:218`).
4. `SharedTransitionLayout` + `AnimatedContent(paneOpen)` (`:225-332`): either `ImmersivePane` (lyrics/queue, `:970-1010`) or the normal layout:
   - Top column: header (grabber + "Playing from", measured into `headerH`), sleep badge, and an invisible gesture box over the cover area for swipe-to-skip and long-press menu (`:234-276`).
   - **Framed cover** (`!edge`): `Crossfade(ui.t, tween(500))` → `StyledCover(card|disc|strips|vinyl)` (`:279-293`).
   - Inline lyric line between the cover bottom and the title (`:296-299`).
   - **Controls** column pinned to the bottom with fixed height `blockH`, switching on `npLayout` to `Classic` (inline `else`), `DockControls`, `BigTitleControls`, `WaveControls`, `BesideControls` (`:301-328`, `:435-538`).
5. Sheets: `AudioOptionsSheet`, `PipelineSheet` (`:334-335`).

**Layout math (portrait).**

- `blockH = max(270 dp (classic) | 320 dp (others), 30 % of height)` (`:172`); `titleTop = maxHeight - blockH - navBottom - 8 dp` (`:175`); `controlsH = blockH + 124 dp + navBottom + 8 dp` (`:174`).
- `clearTop = headerTop + headerH` (`:141-142`), where `headerTop` tucks 8 dp into the status/cutout band.
- Lyrics reserve: 112 dp for `stack`, 48 dp for `line`, animated to 0 when there are no synced lyrics (`:179-180`).
- `CoverSpace(top, bottom, reserve, gaps)` (`CoverFill.kt:318-324`): `room = bottom - top - reserve - 10 dp × gaps`; `topFor(h)` shares the free height into `gaps` equal gaps (2 without lyrics, 3 with; fractional while animating — `:181`).
- Framed cover size = `min(base, CoverSpace(…,112 dp,3).room)` where `base` is a per-style magic fraction of width (`:280-282`).
- Full-width cover: `sideScale` 1.14 (crops ~6 % per side) or 1.0 with lyrics (`:183`, `CoverFill.kt:193`); shrinks to `room / ratio` if it doesn't fit (`CoverFill.kt:109-113`).
- The cover reports its bottom back up through `onCoverBottom` / `SideEffect { coverBottom = … }` (`:284`, `CoverFill.kt:115`) so the lyric line can be centred — a write-during-composition feedback loop that costs an extra frame.
- `lower` (`:169`) slides only the background down 56 dp when there are no lyrics.

**Colour.** `artPalette()` (`PlayerCore.kt:104-117`) decodes a 160 px bitmap, runs `Palette.from(bitmap).generate()`, keeps `band` (vibrant), `sea` (dark muted, forced to luminance ≤ 0.12) and `hue` (most populous saturated swatch). `hueAccent(hue, dark)` (`Theme.kt:100`) fixes S/L so every accent is equally strong; the play button uses it (`GlassPlayer.kt:304`), and so does the app-wide accent when `matchAccent` is on (`Theme.kt:110-114`).

### 1.3 Every existing option

| Setting (`Settings.kt`) | Key | Values | Where it bites |
|---|---|---|---|
| `npCover` | `np_cover` | `edge` (Full width), `card`, `disc`, `strips`, `vinyl` | `GlassPlayer.kt:187, 279-293`; `NowStyles.kt:62, 115-210` |
| `npBackground` | `np_fill` | `stretch`, `mirror` (Reflection), `blur`, `fade`, `ribbed`, `black` | `GlassPlayer.kt:186-211`; `NowStyles.kt:65-66`; `CoverFill.kt:92-182` |
| `npLayout` | `np_layout` | `classic`, `dock` (Glass dock), `bigtitle`, `wave` (Waveform), `beside` | `GlassPlayer.kt:124, 308-326` |
| `npHeader` | `np_header` | bool | grabber + "Playing from" (`:236-255`) |
| `npHideStatus` | `np_hide_status` | bool (default **true**) | `PlayerCore.kt:144-152` |
| `npDepth`, `depthIntensity` (0–100), `depthInvert` | | | `GlassPlayer.kt:152-154, 195-217, 287-290`; `Depth.kt` |
| `showVolume` | `show_volume` | bool | Classic only (`:320`) — silently ignored by the other four layouts |
| `showInline`, `inlineStyle` (`line`/`stack`), `inlineWords`, `romanInline` | | | lyric line on Now Playing (`:715-771`, `:1043-1068`) |
| `matchAccent` | `match_accent` | bool | app accent from cover hue; **the player's play button ignores it** (`:304`) |
| `glassStyle`, `glassSolid` | | `frosted` / `liquid` | every control has an `if (lq != null) … else …` branch |
| Lyrics panel: `lyricsMotion`, `lyricsAlign`, `lyricsSize`, `lyricsLeading`, `lyricsWords`, `lyricsRoman` | | | `PlayerCore.kt:231-293` |

So the user can combine 5 covers × 6 backgrounds × 5 layouts (= 150 combinations) plus toggles, but **cannot** change anything continuous (blur amount, corner radius, cover size, dim/scrim strength, button sizes, accent source, seek-bar style independent of layout, which buttons appear where).

### 1.4 Transitions

- **Song change:** two independent `Crossfade`s — background 650 ms (`:193`), framed cover 500 ms (`:285`). During the fade every background layer exists twice. Swipe-to-skip moves only the cover by `swipe × 0.5` (`:210, :292`) and then the crossfade plays; there is no carousel/peek of the next cover.
- **To lyrics / queue:** `SharedTransitionLayout` + `AnimatedContent(paneOpen)` (`:225-230`). Shared keys: `title`, `artist` (ScaleToBounds text), `play`, `seek` (RemeasureToBounds) — `Modifier.shared()` at `:1018-1029`. The cover is **not** a shared element: it fades out while the background jumps to a 44 dp blur (`:135`, not animated on purpose — comment says animating the radius re-blurs every frame) plus an animated 45 % dim.
- **Open/close player:** slide + scale from the bottom in `AppUi.kt:178-183`, no shared element from the mini player's cover.
- **Vinyl/strips:** infinite animations while playing (`NowStyles.kt:157, 179-183`).

### 1.5 Performance hotspots

1. **Whole-screen recomposition 4×/s.** `PlayerScreen` reads `pos` in composition (`PlayerCore.kt:157`) and passes a fresh `PlayerUi` (a plain class with lambdas, i.e. unstable) into `GlassPlayer`. Every 250 ms the entire `GlassPlayer` body re-runs; lambdas that capture `ui` (e.g. `onCoverBottom = { if (t.id == ui.t.id) … }` at `:210`) are re-created, so the comment in `CoverFill.kt:97` ("only stable inputs, so the backdrop skips…") most likely does not hold. *Unverified — confirm with Layout Inspector recomposition counts.*
2. **Per-frame recomposition for karaoke.** `smoothPosition()` (`Karaoke.kt:24-33`) writes a state every frame via `withFrameNanos`, and the inline `KaraokeText` reads it in composition.
3. **Many full-screen blurred layers.** In `stretch` mode `CoverBackdrop` draws up to: blurred cover (60 dp, when it doesn't fit) + far-edge canvas (18 dp) + far-edge canvas (30 dp) + 2 near-edge canvases (10/16 dp, offscreen + DstIn mask) + reflection (4 dp, offscreen) + cover image (offscreen) + 2 shades + floor gradient + grain (offscreen) ≈ **11 full-screen layers, 5–6 of them RenderEffect blurs** (`CoverFill.kt:117-181`). With a framed cover, the whole stack is blurred **again** by 40 dp (`GlassPlayer.kt:188, 201`). During the 650 ms crossfade, double it. `BlurBackdrop` uses 70 dp and 40 dp blurs on 1.6×/1.15× scaled images (`CoverFill.kt:265-266`).
4. **Blur radius is not resolution-aware.** Radii are in dp on full-resolution layers; RenderEffect cost grows with radius × pixels. A downscaled (e.g. 1/8) source would look identical for a 40–70 dp blur at a fraction of the cost.
5. **API 26–30 get no blur at all.** `Modifier.blur` is a no-op below Android 12 (API 31). With `minSdk = 26`, "Blur", "Fade", "Ribbed", stretch's softening and the pane blur all render **sharp** on those devices. Nothing in the code falls back.
6. **Triple decode of the same artwork.** Coil `AsyncImage` (hardware bitmap) for `Cover`, `rememberCover()` (software 900 px, `CoverFill.kt:75-85`) for the backdrop, and a 160 px decode for `Palette` (`PlayerCore.kt:106`). `averagedEdges()` and `edgeLuminance()` (`CoverFill.kt:199-213, 298-311`) then run on the main thread inside `remember(img)`; `edgeLuminance` uses `getPixel` per sample.
7. **`fan()` draws 28 slices × 2 per canvas** (`CoverFill.kt:240-251`), and `extend()` is called by three canvases → ~170 `drawImage` calls per frame whenever those layers invalidate (e.g. during the tilt animation, since the parent `graphicsLayer` is shared but the crossfade/blur layers are re-rendered).
8. **Depth effect** updates `tilt` at `SENSOR_DELAY_GAME` (~50 Hz) — correctly read only inside `graphicsLayer` (`:195-200`), good — but the layer it moves is the giant blurred stack, so every sensor tick re-composites it.
9. **Infinite animations without lifecycle/reduce-motion guards:** `MusicBreak`, `QualityTag` pulse, `PlayBtn` buffer pulse (the transition runs even when not buffering, `:389`), strips drift, vinyl spin.

What is already done well: tilt and swipe offsets are read in `graphicsLayer` lambdas; the disc ring reads position only in draw (`NowStyles.kt:133-147`); "blur switches on at once" avoids animating radius (`:134-135`); lyrics panel avoids per-line blur (`PlayerCore.kt:277`).

### 1.6 Accessibility gaps

- **Touch targets below 48 dp:** grabber (~13 dp tall, `:238-242`), `QualityTag` (~19 dp, `:949-951`), `LikeBtn` 44 dp (`:376`), `IconBtn` default 44 dp (`Kit.kt:187`), `SoftCircle`/`PaneAction` 46 dp, `LabelAction` 44 dp tall.
- **Contrast over arbitrary artwork:** text and icons are hard-coded `Color.White` with alpha 0.55–0.75 (times at 0.6, `:880`; "PLAYING FROM" 0.65, `:249`; artist 0.7, `:367`). Only `CoverBackdrop` computes edge luminance and adds a shade (`CoverFill.kt:156-172`), and only for the top/bottom 15 % of the cover; `StyledBackdrop` uses a fixed gradient. No check guarantees 4.5:1 for text or 3:1 for icons. The text shadow (`:355`) helps but is not a guarantee.
- **TalkBack:** the seek slider says "Seek" with a 0–1 `progressBarRangeInfo` (`Kit.kt:315-319`) → reads "34 percent" instead of "1:12 of 3:30"; no `stateDescription`. Volume slider same. Disc ring says "Song progress" without a value (`NowStyles.kt:136`). Swipe-to-skip and long-press on the cover have no `customActions` (`:257-275`). The cover image has no description (fine as decorative, but nothing announces the track change). Title/artist are clickable without an `onClickLabel` ("Go to album" / "Go to artist").
- **Reduce motion:** only `LiquidGlass.kt:240` reads `ANIMATOR_DURATION_SCALE`. Vinyl spin, strips drift, depth tilt, marquee titles, lyric drum-roll (`:747-756`) and karaoke ignore it.
- **Status bar hidden by default** (`npHideStatus = true`) hides time/battery on the screen people look at most.

### 1.7 Code smells

- **Giant composables.** `GlassPlayer()` is ~200 lines with 6 levels of nesting and ~20 local states; the file is 1085 lines and mixes layout, gestures, menus, lyrics, seek bar and sheets.
- **Stringly-typed everything.** Styles (`"edge"`, `"stretch"`…), panes (`"art"`, `"lyrics"`, `"queue"`), seek kinds (`"thin" | "thick" | "inline" | "wave"`, `:871`). Typos compile; `when` has `else` fallbacks that hide missing cases.
- **Options defined in three places:** ids+labels in `GlassPlayer.kt:124` / `NowStyles.kt:62-66`, descriptions in `SettingsScreen.kt:215-253`, behaviour scattered in `when`s.
- **Global singletons read deep in leaves** (`Settings.npCover`, `Settings.inlineStyle`, `Settings.showVolume`, `Playback.playing`…). You cannot render two styles side by side (needed for a live preview editor or screenshot tests) because every composable reads the same global.
- **Coupled axes.** `CoverFills` membership changes what a background *means* (`GlassPlayer.kt:186-211`); `edge` makes the cover part of the background; the header knows about the dock layout (`:244, :253`); `showVolume` only applies to Classic.
- **Magic numbers:** 270/320 dp, 124 dp, 56 dp, 112/48 dp, 0.8/0.84 width fractions, `SideScale = 1.14`, 14/7 dp depth, 1.08 zoom, 40/44/55/60/70 dp blurs, 0.35/0.45/0.6/0.72 lerps.
- **Duplicated frosted/liquid branches** (`if (lq != null) Modifier.liquidGlass(…) else Modifier.clip().background()`) in ~12 places.
- Dead/odd bits: `coverAlpha = 1f` constant threaded through everything (`:132`); duplicated KDoc (`:865-869`); About still says 0.6.0 (`SettingsScreen.kt:336`).

### 1.8 Why customization is hard today

1. There is no style *model*: options are independent global strings, so "presets", "reset to default", "export/share a look" or "preview before applying" have nothing to hold on to.
2. Cover and background are entangled (full-width cover lives in the background; three backgrounds behave differently depending on the cover).
3. Controls are five hand-written layouts rather than one layout built from parts, so adding a single option (e.g. "show volume") has to be wired into five functions — and today isn't.
4. Layout math is implicit (magic numbers, feedback through `SideEffect`), so a size slider would ripple unpredictably.
5. No preview harness or screenshot tests, so each new option multiplies untested combinations (already 150 + toggles).

---

## 2. Research

Tags: **[unverified]** = I couldn't confirm it from a primary or reputable source; **[secondary]** = the only source is a forum, blog or community post. Web pages were fetched on 2026-11-12. m3.material.io pages render with JavaScript and couldn't be read, so M3 size specs below come from release notes and blog posts, not the spec site.

### 2.1 How other players do it

| App | Background | Cover | Controls | What the user can customize |
|---|---|---|---|---|
| **Apple Music (iOS 26)** | A flowing gradient sampled from the artwork; heavy blur behind lyrics ([blakecrosley](https://blakecrosley.com/guides/design/apple-music) [secondary]) | Animated "motion art" covers since iOS 14.3, filling about half the screen ([9to5mac](https://9to5mac.com/2020/07/07/ios-14-animated-apple-music-art/)). In iOS 26 the Lock Screen art can expand to full screen and animate ([MacRumors](https://www.macrumors.com/2025/06/11/ios-26-animated-lock-screen-album-art/), [9to5mac](https://9to5mac.com/2025/06/09/ios-26-lock-screen-liquid-glass-animations/)) | Liquid Glass tab bar and mini player as a floating "tab accessory" ([MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3)); swipe the mini player to skip in 26.1 ([RouteNote](https://routenote.com/blog/apple-music-is-making-it-easier-to-skip-songs-in-ios-26/)). Android 7.0 got Liquid Glass bars but no big Now Playing change ([9to5google](https://9to5google.com/2026/09/30/apple-music-7-0-redesign-liquid-glass/)) | Almost nothing. A curated single look. The lyrics transition where the cover shrinks into a corner is well known but I found no write-up [unverified] |
| **Spotify** | A solid or gradient colour from the cover. The algorithm isn't public; community guesses (k-means) are [unverified] ([eggradients](https://eggradients.com/blog/spotify-colors)) | **Canvas**: a 3–8 s looping 9:16 video that replaces the cover; Spotify advises no flashing or rapid cuts ([Spotify](https://support.spotify.com/fj/artists/article/canvas-guidelines/)) | Lyrics moved onto Now Playing, line by line, in January 2026 ([Android Authority](https://www.androidauthority.com/spotify-now-playing-interface-lyrics-update-3636643/)). The DJ button sits bottom-right: tap for a new segment, hold to speak ([TechCrunch](https://techcrunch.com/2023/02/22/spotify-launches-dj-a-new-personalized-music-lineup-that-includes-ai-powered-commentary/)) | Nothing visual; Canvas can only be turned on or off |
| **YouTube Music** | A blurred artwork background is confirmed on the web player ([9to5google](https://9to5google.com/2024/03/04/youtube-music-web-player-redesign/)); Android is [unverified] | Swipe-to-skip carousel; testing full-screen cover art ([RouteNote](https://routenote.com/blog/youtube-music-tests-fullscreen-cover-art-bringing-a-more-immersive-now-playing-experience/)) | April 2026 split view: Up Next fills the lower half, Lyrics moved into the action row, swipe up to half-raise the queue ([Android Authority](https://www.androidauthority.com/youtube-music-now-playing-redesign-split-screen-3657648/), [9to5google](https://9to5google.com/2026/04/23/youtube-music-split-now-playing-redesign/)) | None |
| **Tidal** | A solid dominant colour per track, replacing an earlier blur ([Symfonium style that recreates it](https://styles.symfonium.app/styles/stellar-tidal-music) [secondary]) | Card | Quality badges (FLAC, Hi-Res, Atmos) | None |
| **Deezer** | The player tints itself to the cover colour ([Deezer 2019](https://newsroom-deezer.com/2019/05/deezer-debuts-new-look-and-updated-app-2/)) | — | — | 14 colour themes and icon choices since November 2025 ([Deezer](https://newsroom-deezer.com/2025/11/deezer-now-lets-you-choose-look-and-layout-of-the-app/)); whether they reach the player is [unverified] |
| **Amazon Music, Musicolet** | Nothing documented [unverified] | | | |
| **Poweramp** | Global background sliders: blur, detail, intensity, saturation ([forum](https://forum.powerampapp.com/topic/16186-background-blur-and-details) [secondary]) | A cover aspect-ratio option, including Rectangle (build 878) | Wave or simple seekbar, a "Static Seekbar" option, "Pro Buttons" ([APKMirror notes](https://www.apkmirror.com/apk/max-mp/poweramp/poweramp-v3-build-827-play-release/)) | **Skins** are installable APKs (an SDK and sample exist in [powerampapi](https://github.com/maxmpz/powerampapi)), each with its own options page, plus global background sliders |
| **Symfonium** (the best documented) | Background style, overlay, transparency | Thumbnail style, corner radius, rotation, "prefer animated artwork", tap cover to play/pause | Pick the visible buttons, seek amounts, button colour, progress style and colour, left-handed mode, swipe to skip; text lines driven by templates | **Named, saveable, importable, versioned "Now Playing Styles"**, separate for portrait, landscape and compact, shared in a gallery ([docs](https://docs.symfonium.app/wiki/settings/settings-interface-now-playing-screens/), [gallery](https://styles.symfonium.app)). The closest model to what WeTune wants |
| **Retro Music Player** (GPL-3.0) | Depends on the theme: blur, colour, gradient, flat… | `AlbumCoverStyle` is a separate axis (Normal, Card, Full, FullCard, Flat, Circle) | One fragment per theme | **18 `NowPlayingScreen` presets**: Adaptive, Blur, BlurCard, Card, Circle, Classic, Color, Fit, Flat, Full, Gradient, Material, MD3, Normal, Peek, Plain, Simple, Tiny ([source](https://raw.githubusercontent.com/RetroMusicPlayer/RetroMusicPlayer/dev/app/src/main/java/code/name/monkey/retromusic/fragments/NowPlayingScreen.kt)), plus a carousel effect, AMOLED mode and an accent picker ([repo](https://github.com/RetroMusicPlayer/RetroMusicPlayer)). Warning: one fragment per preset is exactly how a code base ends up with 18 near-duplicate screens |
| **Gramophone / Metrolist / InnerTune** | Monet / dynamic colour from the cover; Metrolist also has 19 preset palettes and a Black mode | Material 3 | Material 3 | Theme mode plus palette only ([Gramophone](https://github.com/FoedusProgramme/Gramophone), [Metrolist](https://github.com/mostafaalagamy/Metrolist), [InnerTune](https://github.com/z-huang/InnerTune)). Metrolist's player style enums are [unverified]. OuterTune is no longer maintained ([repo](https://github.com/OuterTune/OuterTune)) |
| **Namida** (Flutter) | Dynamic theme from the artwork | Animated thumbnail | Waveform seekbar ([repo](https://github.com/namidaco/namida)) | — |
| **Plexamp** | Three background styles: **Flat** (one colour), **Smooth** (a vertical gradient), **UltraBlur** (a gradient between several extracted colours, "artwork seen through smoked glass") ([Plex forum guide](https://forums.plex.tv/t/how-to-plexamp-4-6-1-settings/724870)) | Card | Optionally tinted with the UltraBlur colours ("Colorize Player Elements") | Theme (Dark or Light from the cover, or static Black or White) × background style × freeze colours or follow the album × colorize elements. Users report poor legibility on light palettes ([forum](https://forums.plex.tv/t/sometimes-the-backgrounds-color-of-the-menus-are-really-difficult-to-read/874424)). The idea that UltraBlur samples **four corners** is [unverified] |
| **Android system media controls (13+)** | Full-bleed artwork with a scrim | — | Up to 5 slots derived from `Player` state, set with `setMediaButtonPreferences` ([docs](https://developer.android.com/media/implement/surfaces/mobile)). The **squiggly progress bar** animates only while playing and not scrubbing ([AOSP](https://gitlab.e.foundation/e/os/android_frameworks_base/-/commit/8fa6c83d9532c95243e61af2e6ef1fe5de2cc7ff)) and goes flat when "Remove animations" is on ([AOSP](https://gitlab.e.foundation/e/os/android_frameworks_base/-/commit/22b40cc4e0b1699e4d977cbdadf527649b338632)). Samsung One UI didn't adopt it ([SamMobile](https://www.sammobile.com/?p=2564683)) | None |

**What this means for WeTune.** The mainstream apps give users almost no control and get away with it by having one strong look and a contrast-safe background (a solid colour or a heavy blur). Apps that are known for customization (Symfonium, Poweramp, Retro, Plexamp) all end up with the same structure: **a preset is a bundle of a few independent axes** (background, cover, controls, colour), and **a style is a saveable, shareable object**. Symfonium's versioned style files are the closest match to the typed `NowPlayingStyle` proposed below.

### 2.2 Techniques

**Colour extraction**
- **androidx Palette.** Six targets (Vibrant, Light Vibrant, Dark Vibrant, Muted, Light Muted, Dark Muted). `maximumColorCount` defaults to 16. `setRegion()` samples part of the image, useful for picking the colour under the controls. Each swatch exposes `titleTextColor` and `bodyTextColor`. Google's guidance is to build palettes off the main thread and cache them in an `LruCache` ([docs](https://developer.android.com/develop/ui/views/graphics/palette-colors)). WeTune already does this off-thread, but without a cache.
- **Material Color Utilities.** Quantizes with `QuantizerCelebi` (Wu, then WSMeans), ranks colours with `Score`, and builds a `DynamicScheme` from them. The **Content** and **Fidelity** variants keep the source hue, so they are the right ones for album art ([repo](https://github.com/material-foundation/material-color-utilities), [variants](https://api.flutter.dev/flutter/package-material_color_utilities_dynamiccolor_variant/Variant.html)). Its HCT colour space is the natural way to fix an accent's contrast: keep the hue and move only the tone.
- **MaterialKolor** (`com.materialkolor:material-kolor`). Wraps the utilities for Compose: `rememberDynamicColorScheme(seed, isDark, style, contrastLevel)` and animated scheme changes ([repo](https://github.com/jordond/MaterialKolor)). It is useful even without Material3 because you can read individual tones out of the scheme.
- **Cost.** No published timings. Common practice is to downscale to 64–128 px, run on `Dispatchers.Default` and cache by art URL [unverified heuristic].

**Backgrounds**
- **`Modifier.blur` needs API 31+ and is ignored below that.** It renders into its own clipped layer ([docs](https://developer.android.com/reference/kotlin/androidx/compose/ui/draw/blur.modifier)). Compose UI 1.13 adds a `blur { blurRadiusSpec }` block with gradient or shader masks, and reading the radius inside that block makes animating it a draw-only change. WeTune's BOM `2024.12.01` predates this API.
- **Layer cost.** A `graphicsLayer` with alpha < 1 or a `RenderEffect` forces an offscreen buffer. `CompositingStrategy.ModulateAlpha` avoids that for plain alpha. Cache brushes and shaders in `drawWithCache` ([docs](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)).
- **Blur before API 31.** The RenderScript Toolkit replacement is archived (since 2024-12) ([repo](https://github.com/android/renderscript-intrinsics-replacement-toolkit)). Cloudy falls back to a CPU blur ([repo](https://github.com/skydoves/cloudy)). The simplest option: blur a tiny bitmap once per track and draw it scaled up. A 64 px image upscaled with bilinear filtering is already soft, and a cheap box or stack blur on it costs microseconds [unverified heuristic, but standard].
- **Haze** (already a dependency, 1.2.2). Full features on API 33+. On 31–32 there are differences and it re-captures during animations. On ≤ 30 it shows a translucent scrim by default ([platforms](https://chrisbanes.github.io/haze/latest/blur/platforms/)). Good for frosted *controls* over the background, not for the background itself.
- **AGSL `RuntimeShader`** (API 33+). Draw it through `ShaderBrush` with an animated `iTime` uniform for mesh gradients, noise and grain. `RenderEffect.createRuntimeShaderEffect` costs more than drawing the shader directly ([docs](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)). For API 26–32 a mesh can be faked with 3–4 large radial gradients in a `Canvas`.

**Cover**
- **Shared elements.** Use `SharedTransitionLayout` with `sharedElement` / `sharedBounds`. Prefer data-class keys over strings, and note that modifier order matters. `ContentScale` and shape clipping don't animate automatically, and there is no support inside Dialogs or ModalBottomSheets ([docs](https://developer.android.com/develop/ui/compose/animation/shared-elements)). This makes possible a mini player → full player container transform and a cover → lyrics header morph.
- **Swipe to skip.** Use `HorizontalPager` with `beyondViewportPageCount = 1`. Read `currentPageOffsetFraction` in `graphicsLayer` for scale, alpha and tilt, and commit the skip on `settledPage` ([docs](https://developer.android.com/develop/ui/compose/layouts/pager)).
- **Spin, tilt and parallax.** Use `graphicsLayer` `rotationZ`, `rotationX`/`rotationY` and `cameraDistance`. These are draw-only. Tinted shadows: newer Compose has `Modifier.dropShadow(shape, Shadow(radius, color))` (needs a newer BOM than WeTune's); `shadow(ambientColor, spotColor)` works today on API 28+.

**Controls**
- **Material 3 Expressive status.** `compose-material3` 1.4.0 is stable and 1.5.0-rc01 came on 2026-11-04. During the 1.5 alphas these became stable: `ButtonGroup`, the wavy progress indicators, toggle buttons, `MotionScheme`, and the stateful `Slider(SliderState)` ([release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)). Google's guidance says buttons should "transform shape and size… more visual contrast between states such as play and pause", and shows a wavy progress ring around play/pause ([Android Developers blog](https://android-developers.googleblog.com/2025/08/introducing-material-3-expressive-for-wear-os.html)). There is no stable wavy *slider*. Community options are [saket/squiggly-slider](https://awesome.ecosyste.ms/projects/github.com%2Fsaket%2Fsquiggly-slider) and [mahozad/wavy-slider](https://github.com/mahozad/wavy-slider). WeTune has no Material dependency, so copying the *behaviour* in a custom `Canvas` is cheaper than adopting M3.
- **Media3 Compose state holders.** `media3-ui-compose` (since 1.6) provides `rememberPlayPauseButtonState(player)` and `rememberProgressStateWithTickInterval(...)` ([reference](https://developer.android.com/reference/kotlin/androidx/media3/ui/compose/state/package-summary)). WeTune is on Media3 1.5.1; these would replace the hand-rolled 250 ms polling.
- **Haptics.** Use `performHapticFeedback(HapticFeedbackConstants.*)`, which respects user settings. Use CONFIRM/REJECT for outcomes ([docs](https://developer.android.com/develop/ui/views/haptics/haptic-feedback)). WeTune already uses CONTEXT_CLICK and CLOCK_TICK.

**Performance**
- **Read fast-changing values late.** Pass `() -> T` lambdas and read them in `offset {}`, `graphicsLayer {}` or `drawBehind {}`. Use `derivedStateOf` for thresholds. **Never write state backwards during composition**, which is exactly what WeTune's `coverBottom` `SideEffect` does ([best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)).
- **Stability.** Strong skipping is the default since Kotlin 2.0.20, and unstable parameters are compared by instance. A fresh `PlayerUi` every tick therefore defeats skipping ([stability](https://developer.android.com/develop/ui/compose/performance/stability/fix)).
- **Baseline Profiles.** About 30 % faster code from the first launch. Include the "open player" and "skip" journeys ([docs](https://developer.android.com/topic/performance/baselineprofiles/overview)).
- **TalkBack.** Semantics that change every frame (a seek value) can freeze TalkBack. Throttle them ([gist](https://gist.github.com/Digipom/d1dc1aef7623d8f58ff6b57cb6935d5b) [secondary]).

**Accessibility**
- **Contrast.** WCAG 2.2 requires 4.5:1 for text (3:1 for large text) under SC 1.4.3 ([W3C](https://w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)), and 3:1 for UI components and graphics under SC 1.4.11. Over images, measure against the actual pixels, which in practice means a scrim ([WebAIM](https://webaim.org/articles/contrast/)).
- **Touch targets.** 48 dp minimum. Use `Modifier.minimumInteractiveComponentSize()` for custom controls ([docs](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)). Note that this modifier lives in material3; with foundation only, pad the touch area by hand.
- **Semantics.** Use `stateDescription` for play state and shuffle/repeat, `progressBarRangeInfo` plus `stateDescription` for seek, and `customActions` for gestures. Use `liveRegion` sparingly ([semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)).
- **Reduced motion.** Read `Settings.Global.ANIMATOR_DURATION_SCALE` (0 means "Remove animations"). There is no phone equivalent of Wear's `LocalReduceMotion`, so gate infinite animations by hand ([dev.to](https://dev.to/eevajonnapanula/android-animations-and-reduced-motion-2em2) [secondary]).

### 2.3 Customization systems

- **Patterns:**
  - Presets as enum × axis (Retro).
  - A style object you can save, import and version, with a gallery (Symfonium).
  - Skin packages plus per-skin options (Poweramp).
  - Theme × background × "colorize elements" (Plexamp).
  - Accent presets (Deezer).
- **Theme file formats:** Kustom's `.kwgt` is a ZIP of `preset.json` plus assets ([fileinfo](https://fileinfo.com/extension/kwgt)). Telegram's `.attheme` is `key=colour` lines plus an optional wallpaper ([docs.rs](https://docs.rs/attheme)). For WeTune, **plain JSON text** (clipboard or share sheet) is enough while backgrounds come from the cover. A ZIP is only needed if custom images are ever allowed.
- **Compose practice:**
  - Hoist the style. Pass it explicitly into layers.
  - Use a `CompositionLocal` only for tree-wide values with a sensible default ([docs](https://developer.android.com/develop/ui/compose/compositionlocal)). `staticCompositionLocalOf` recomposes everything below it when it changes; `compositionLocalOf` recomposes only the readers. A `LocalNowPlayingColors` is reasonable; the style itself is better as a parameter.
  - Persist it in DataStore with a JSON `Serializer<T>` and a `defaultValue`, use a `ReplaceFileCorruptionHandler`, and keep `T` immutable ([docs](https://developer.android.com/topic/libraries/architecture/datastore)). Sealed hierarchies serialize with a class discriminator ([polymorphism](https://kotlinlang.org/docs/serialization-polymorphism.html)). Use `ignoreUnknownKeys` and defaults so the schema can evolve (the details weren't re-read, so [unverified]).
- **Screenshot tests:**
  - Google's Compose Preview Screenshot Testing needs AGP 9 or newer and Kotlin 2.2.10 ([docs](https://developer.android.com/studio/preview/compose-screenshot-testing)). That is a toolchain upgrade for WeTune (Kotlin 2.1.0).
  - Roborazzi (Robolectric) works with today's toolchain ([repo](https://github.com/takahirom/roborazzi)).
  - Paparazzi ([repo](https://github.com/cashapp/paparazzi)).
  - Recommendation: **Roborazzi** now.

---

## 3. Recommendations

### 3.1 Architecture

Keep WeTune's own design system (no Material dependency is needed; borrow M3 Expressive *ideas* — shapes, sizes, wavy progress — not the library, unless you decide the ~1 MB is worth it).

```
ui/nowplaying/
  NowPlayingScreen.kt     // reads state + style, lays out the four layers; < 150 lines
  style/
    NowPlayingStyle.kt    // the typed model below, defaults, presets
    StyleStore.kt         // DataStore<NowPlayingStyle> + migration from Settings np* keys
  state/
    NowPlayingState.kt    // @Stable holder: track, palette, isPlaying, pane; position exposed as () -> Long
    ArtColors.kt          // one decode → palette/scheme + edge luminance + downscaled blur source, cached per art URL
  layers/
    Background.kt         // BackgroundLayer(style.background, colors, art)
    Cover.kt              // CoverLayer(style.cover, art, progress) incl. pager
    Controls.kt           // ControlsLayer(style.controls, state, actions) built from slots
    Overlays.kt           // header, lyric line, sleep badge, menus, scrim
  layout/
    NowPlayingLayout.kt   // a custom Layout/MeasurePolicy: header, cover band, lyrics band, controls block
  customize/
    CustomizeScreen.kt    // live editor: preview + controls
```

Principles:

- **Layers never read `Settings`/`Playback` directly.** They take `(style, colors, state, actions)`. That makes them previewable, testable and usable in the editor's preview at the same time as the real player.
- **Position never enters composition.** Pass `progress: () -> Float` and read it in `drawBehind`/`graphicsLayer`/`Modifier.layout`; only the time labels recompose, and only once per second (`derivedStateOf { pos / 1000 }`).
- **One `Layout` instead of `SideEffect` feedback.** A custom `MeasurePolicy` measures the controls block and header first, then gives the cover the remaining band; the lyrics slot is placed between them. No `coverBottom` state, no extra frame. Cover style only declares its aspect (`1:1`, `cover ratio`, `disc`) and how it reacts to the band.
- **Background and cover are independent.** "Full width" becomes a *cover* style that is simply a cover whose width = screen width with edge treatment; the background layer always draws full screen behind it. The "stretch / mirror" effects become *background* styles that need the cover rect, passed in from layout (`coverRect: () -> Rect`).
- **Colours resolved once.** `ArtColors` produces a small token set (see below) from one software decode at ~128–256 px, on `Dispatchers.Default`, cached in an `LruCache` keyed by art URL. The same decode feeds: palette, edge luminance, and a tiny pre-blurred bitmap for backgrounds.

**Typed style model (sketch):**

```kotlin
@Immutable @Serializable
data class NowPlayingStyle(
    val version: Int = 1,
    val background: BackgroundStyle = BackgroundStyle.Blur(),
    val cover: CoverStyle = CoverStyle.Card(),
    val controls: ControlsStyle = ControlsStyle(),
    val layout: LayoutStyle = LayoutStyle(),
    val colors: ColorStyle = ColorStyle(),
    val motion: MotionStyle = MotionStyle(),
)

@Serializable sealed interface BackgroundStyle {
    @Serializable @SerialName("blur")    data class Blur(val radius: Int = 60, val zoom: Float = 1.4f, val dim: Float = 0.25f) : BackgroundStyle
    @Serializable @SerialName("stretch") data class Stretch(val softness: Int = 24, val reflection: Boolean = true) : BackgroundStyle
    @Serializable @SerialName("mirror")  data class Mirror(val softness: Int = 14) : BackgroundStyle
    @Serializable @SerialName("mesh")    data class Mesh(val speed: Float = 0.3f, val points: Int = 4) : BackgroundStyle   // palette-driven, animated
    @Serializable @SerialName("ultra")   data class UltraBlur(val dim: Float = 0.2f) : BackgroundStyle                       // Plexamp-style 4-corner gradient
    @Serializable @SerialName("solid")   data class Solid(val source: ColorPick = ColorPick.Dark) : BackgroundStyle
    @Serializable @SerialName("ribbed")  data class Ribbed(val ribs: Int = 20) : BackgroundStyle
    // common to all, applied by the layer: grain (0..1), vignette (0..1), depth (tilt) on/off
}

@Serializable sealed interface CoverStyle {
    @Serializable @SerialName("card")  data class Card(val corner: Int = 22, val size: Float = 0.86f, val shadow: Shadow = Shadow.Tinted) : CoverStyle
    @Serializable @SerialName("edge")  data class FullWidth(val fadeEdges: Boolean = true, val crop: Float = 0.06f) : CoverStyle
    @Serializable @SerialName("disc")  data class Disc(val ring: Boolean = true, val spin: Boolean = false) : CoverStyle
    @Serializable @SerialName("vinyl") data class Vinyl(val spin: Boolean = true) : CoverStyle
    @Serializable @SerialName("strips") data class Strips(val count: Int = 5) : CoverStyle
    // common: swipe = Off | Nudge | Pager; breathe (scale down when paused) on/off
}

@Immutable @Serializable data class ControlsStyle(
    val arrangement: Arrangement = Arrangement.Classic,      // Classic | Dock | BigTitle | Beside
    val seek: SeekStyle = SeekStyle.Line,                    // Line | Thick | Wavy | Waveform | Inline
    val playShape: PlayShape = PlayShape.Circle,             // Circle | Pill | Squircle | Morph (circle↔squircle on play/pause)
    val playFill: ColorPick = ColorPick.Accent,              // Accent | White | Glass
    val scale: Float = 1f,                                   // 0.85..1.2, clamped so targets stay ≥ 48 dp
    val showShuffleRepeat: Boolean = true,
    val showVolume: Boolean = true,
    val bottomRow: List<Action> = listOf(Action.Lyrics, Action.Sleep, Action.Queue),
    val haptics: Boolean = true,
)

@Immutable @Serializable data class LayoutStyle(val header: Boolean = true, val hideStatusBar: Boolean = false, val inlineLyrics: InlineLyrics = InlineLyrics.Line, val density: Density = Density.Comfortable)
@Immutable @Serializable data class ColorStyle(val source: ColorSource = ColorSource.CoverHue, val scrim: Scrim = Scrim.Auto)
@Immutable @Serializable data class MotionStyle(val songChange: SongChange = SongChange.Crossfade, val depth: Depth? = null, val ambient: Boolean = true) // ambient animations off when system "Remove animations" is on
```

`ColorSource`: `CoverHue` (today's `hueAccent`), `CoverScheme` (Material Color Utilities content scheme), `Vibrant`, `Muted`, `Monochrome`, `Custom(argb)`. All resolve to one `NowColors` token set: `backgroundBase`, `backgroundTint`, `onBackground`, `onBackgroundMuted`, `accent`, `onAccent`, `scrim`.

Why sealed types + per-variant parameters: only parameters that make sense for a variant exist (no "corner radius" on Disc), `when` is exhaustive, and kotlinx.serialization gives JSON with a `type` discriminator for free — which is also the export/share format.

**Persistence and migration.** Store the whole object as one JSON string in Jetpack DataStore (or, smallest change, one SharedPreferences string key `np_style` alongside today's `Settings` delegate pattern — the codebase has no DataStore yet; either is fine because the model is one value). On first run, a migration builds `NowPlayingStyle` from the old keys:

| old key | → new field |
|---|---|
| `np_cover` edge/card/disc/strips/vinyl | `cover` = FullWidth/Card/Disc/Strips/Vinyl |
| `np_fill` stretch/mirror/blur/fade/ribbed/black | `background` = Stretch/Mirror/Blur/Blur(fade preset)/Ribbed/Solid(black) |
| `np_layout` classic/dock/bigtitle/wave/beside | `controls.arrangement` + `controls.seek` (wave → Classic + Waveform) |
| `np_header`, `np_hide_status`, `show_volume`, `show_inline`, `inline_style` | `layout.*`, `controls.showVolume` |
| `np_depth`, `depth_intensity`, `depth_invert` | `motion.depth` |

Keep `version` and a `migrate(json)` step list; unknown `type`s decode to the default via `coerceInputValues`/a fallback so an imported newer theme never crashes an older app. Mark the old keys read-only after migration.

### 3.2 The Customize editor

Entry points: Settings → Appearance → "Now Playing" (opens editor), and long-press on the player background → "Customize". Layout: the live preview on top (the real `NowPlayingScreen` composable rendered with the current track and a *draft* style, scaled to ~55 % of screen height), editing controls in a bottom sheet with tabs.

- **Presets** (horizontal row of mini previews, rendered with the same composable at small scale, current cover): pick one → draft = preset.
- **Background** tab: style chips; per-style sliders (blur 0–100, zoom, dim 0–60 %, grain 0–1, vignette); toggles (depth effect + intensity, ambient motion).
- **Cover** tab: style chips; size 70–100 %, corner 0–40 dp (Card), shadow None/Soft/Tinted, swipe Off/Nudge/Pager, breathe on pause.
- **Controls** tab: arrangement chips; seek style; play shape + fill; control size slider (clamped); toggles for shuffle/repeat, volume, haptics; a reorderable list for the bottom row actions (Lyrics, Sleep, Queue, Output, Like, Up next).
- **Colours** tab: source (cover hue / cover scheme / vibrant / muted / mono / custom picker), contrast "Auto scrim" (on by default; showing a "text contrast: 6.2:1" readout is a nice touch).
- Bottom actions: Reset to preset · Cancel · Apply. "Shuffle the preview cover" button cycles a few bundled test covers (white, black, busy, saturated) so the user sees the worst cases.
- **Export / import:** "Copy style" puts the JSON on the clipboard / share sheet; "Paste style" validates and previews before applying.

All editing is on a draft `MutableState<NowPlayingStyle>`; apply writes once. Because layers are pure functions of `(style, colors, state)`, preview and player cannot drift apart.

### 3.3 Accessibility rules baked in (not options)

- **Auto scrim:** compute luminance of the artwork region under the text/control band from the downscaled bitmap; choose scrim alpha so `onBackground` reaches ≥ 4.5:1 for text and ≥ 3:1 for icons/track (WCAG 1.4.3 / 1.4.11). The user's "dim" slider can only add darkness, never drop below the computed minimum.
- **Accent contrast:** if `accent` vs. its background < 3:1, lighten/darken in HCT tone (keep hue) until it passes; `onAccent` picked by contrast, not by a luminance threshold.
- **Targets:** every tappable thing ≥ 48 dp (visual size may be smaller; pad the touch area). `ControlsStyle.scale` clamps to keep this.
- **Semantics:** seek slider `stateDescription = "1:12 of 3:30"`, `setProgress` in seconds, custom actions "Skip forward 10 s"/"back 10 s"; play button `stateDescription` playing/paused; cover gets `customActions` Next / Previous / Song options; announce track change via a polite live region on the title.
- **Reduce motion:** read `ANIMATOR_DURATION_SCALE == 0` (and expose `LocalReduceMotion`); when on, disable vinyl spin, strips drift, mesh motion, depth tilt, marquee, lyric roll; song change becomes a plain 150 ms fade.
- Status bar visible by default.

### 3.4 Performance budget

- Target: steady 60/90/120 fps on a mid-range device; ≤ 1 recomposition of the screen per song change; position updates cause 0 recompositions outside the time labels (≤ 1/s).
- Background: **at most one** blur pass, on a **downscaled** image (e.g. 64–128 px source drawn scaled up with `FilterQuality.Low`, or blur done once off-thread into a small bitmap and cached per art). That also gives API 26–30 identical output with no `RenderEffect`. Use `RenderEffect`/AGSL only as an enhancement on 31+/33+.
- ≤ 4 full-screen layers in steady state (background, optional grain/vignette, scrim, content). Song change: crossfade only the background image and the cover, not a whole subtree with blurs.
- Ambient animations (mesh, spin) run in `graphicsLayer`/draw only, pause when not playing, when the screen is covered (lyrics pane opaque) or when reduce-motion is on.
- Measure with Macrobenchmark `FrameTimingMetric` on open-player, song-change and lyrics-open journeys; add a Baseline Profile covering them.

### 3.5 Design directions

**Background (pick 1 per preset; all palette-aware):**

1. **Soft blur** — the cover, downscaled, blurred, zoomed 1.3–1.6×, dimmed. The universal default; cheap if pre-blurred once. (Apple Music classic, YouTube Music.)
2. **UltraBlur / mesh** — 3–5 colours extracted from the cover (Plexamp's exact sampling is [unverified]; sampling regions/corners with `Palette.setRegion` is a reasonable approach), blended into a smooth 2D gradient, optionally drifting slowly. No image at all, so text contrast is predictable and it costs almost nothing; looks premium. (Plexamp UltraBlur, Apple Music iOS 26 animated backgrounds.)
3. **Extend** — today's stretch/mirror: the cover's edge pixels continued to the screen edges. Signature WeTune look; keep it, but compute from the small bitmap with one blur.
4. **Solid tint** — a single deep colour from the palette (or true black for AMOLED). Maximum legibility, zero cost, best for low-end. (Spotify's flat colour, Musicolet.)
5. **Textured glass** — ribbed/fluted or grain over any of the above as a modifier, not its own background.

**Cover:**

1. **Card** — rounded square, size and corner sliders, palette-tinted soft shadow, scales down slightly when paused (Apple Music's "breathe").
2. **Full width** — edge-to-edge, top/bottom feathered into the background (today's `edge`), best paired with Extend or Mesh.
3. **Pager** — Card inside a `HorizontalPager` showing prev/next covers peeking at the sides; swipe to skip with real physics instead of the current nudge-and-crossfade. (YouTube Music, Spotify swipe-to-skip.)
4. **Disc / vinyl** — round cover with progress ring, optional spin (respects reduce motion). Keep strips as a fun extra.
5. **Motion art (later)** — looping video / animated artwork where a source provides it (YouTube Music video thumbnails), muted, behind or as the cover. Optional; data source is the constraint.

**Controls:**

1. **Classic** — title row, line seek, 5-button transport with an accent play circle, bottom action row. Default.
2. **Expressive** — big title, wavy (squiggly) progress line while playing that flattens on pause, play button morphs circle↔rounded-square, wide pill play with prev/next as half-pills (M3 Expressive button group look).
3. **Dock** — transport inside a frosted capsule; labelled action pills below.
4. **Minimal** — title + inline seek + play beside title (today's "beside"), lots of air for the cover.
5. **Waveform** — seek drawn as bars (real waveform if ever available), up-next card.

These are one `ControlsLayer` with slots (`Title`, `Seek`, `Transport`, `Extras`, `Actions`), not five functions.

**Presets (background × cover × controls):**

| Preset | Background | Cover | Controls | Colours | Notes |
|---|---|---|---|---|---|
| **Studio** (default) | Soft blur, dim 25 % | Card 86 %, corner 22, tinted shadow, pager | Classic, line seek | Cover hue | Closest to today's most-used look, much cheaper |
| **Edge** | Extend (stretch) + grain | Full width | Classic, thin seek | Cover hue | Today's signature WeTune look |
| **Aurora** | Mesh / UltraBlur, slow drift | Card 80 %, corner 28 | Expressive (wavy, morphing play) | Cover scheme | The "new" look; cheap and very legible |
| **Night** | Solid black | Card 78 %, no shadow | Minimal | Monochrome | AMOLED, low-end, max contrast |
| **Record** | Soft blur, heavy dim | Vinyl (spin) | Dock | Muted | Playful |
| **Glass** | Soft blur + ribbed | Disc with ring | Dock | Vibrant | Frosted-glass identity |

### 3.6 Testing

- Screenshot tests per preset × 4 test covers (white, black, busy, saturated) × light/large font, with Roborazzi or Paparazzi (JVM, no device) — or Google's Compose Preview Screenshot Testing plugin since layers are previewable. Fake `NowPlayingState` with a fixed position.
- Unit tests: style JSON round-trip, migration from every old-key combination, unknown-type fallback, contrast solver (`accent` always ≥ 3:1, text ≥ 4.5:1 for the test covers).
- A recomposition-count test (or a manual Layout Inspector pass) asserting position ticks don't recompose layers.

### 3.7 Phased plan

1. **Foundation (no visible change).** `NowPlayingStyle` + JSON + migration from `np*` keys; `ArtColors` single decode with cache, contrast solver, reduce-motion local; `NowPlayingState` with `progress: () -> Float`. Tests for migration/contrast.
2. **Layout + controls.** Custom `Layout` replacing `CoverSpace`/`SideEffect`; `ControlsLayer` with slots reproducing Classic/Dock/BigTitle/Beside/Waveform; seek slider semantics; 48 dp targets. Shared elements to lyrics/queue keep their keys (`title`, `artist`, `play`, `seek`) and add `cover`.
3. **Cover layer.** Card/FullWidth/Disc/Vinyl/Strips from the typed style; `HorizontalPager` option; tinted shadow; reduce-motion.
4. **Background layer.** Downscaled single-blur pipeline (works on API 26+), Extend, Solid, Ribbed/grain modifiers, auto scrim; then Mesh/UltraBlur (Canvas gradients first; AGSL on 33+ as an upgrade).
5. **Presets + Customize editor.** Draft/apply, preset strip, per-layer tabs, export/import JSON.
6. **Polish.** Expressive extras (wavy progress, morphing play), Baseline Profile + Macrobenchmark, screenshot test matrix, remove old code paths.

---

## Sources

Code references are to the WeTune working tree (2026-11-12). The web sources were collected by a research sub-agent. I didn't re-open them, but every claim in the report carries an inline link.

**Apps**
- Apple: https://www.macrumors.com/2025/06/11/ios-26-animated-lock-screen-album-art/ · https://9to5mac.com/2025/06/09/ios-26-lock-screen-liquid-glass-animations/ · https://9to5mac.com/2025/06/15/iphone-apple-music-lock-screen-animated-artwork-third-party-apps/ · https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3 · https://www.apple.com/newsroom/2025/06/apple-elevates-the-iphone-experience-with-ios-26/ · https://routenote.com/blog/apple-music-is-making-it-easier-to-skip-songs-in-ios-26/ · https://9to5google.com/2026/09/30/apple-music-7-0-redesign-liquid-glass/ · https://blakecrosley.com/guides/design/apple-music · https://9to5mac.com/2020/07/07/ios-14-animated-apple-music-art/ · https://9to5mac.com/2025/09/24/heres-everything-new-for-apple-music-in-ios-26/
- Spotify: https://support.spotify.com/fj/artists/article/canvas-guidelines/ · https://eggradients.com/blog/spotify-colors · https://www.androidauthority.com/spotify-now-playing-interface-lyrics-update-3636643/ · https://techcrunch.com/2023/02/22/spotify-launches-dj-a-new-personalized-music-lineup-that-includes-ai-powered-commentary/ · https://newsroom.spotify.com/2023-02-22/spotify-debuts-a-new-ai-dj-right-in-your-pocket/ · https://www.musicbusinessworldwide.com/spotify-adds-text-input-and-spanish-requests-to-ai-dj/
- YouTube Music: https://www.androidauthority.com/youtube-music-now-playing-redesign-split-screen-3657648/ · https://9to5google.com/2026/04/23/youtube-music-split-now-playing-redesign/ · https://9to5google.com/2025/09/11/youtube-now-playing-2025-redesign/ · https://routenote.com/blog/youtube-music-tests-fullscreen-cover-art-bringing-a-more-immersive-now-playing-experience/ · https://9to5google.com/2024/03/04/youtube-music-web-player-redesign/
- Tidal and Deezer: https://styles.symfonium.app/styles/stellar-tidal-music · https://newsroom-deezer.com/2019/05/deezer-debuts-new-look-and-updated-app-2/ · https://newsroom-deezer.com/2025/11/deezer-now-lets-you-choose-look-and-layout-of-the-app/
- Poweramp: https://www.apkmirror.com/apk/max-mp/poweramp/poweramp-v3-build-816-play-release/ · https://www.apkmirror.com/apk/max-mp/poweramp/poweramp-v3-build-827-play-release/ · https://forum.powerampapp.com/topic/16186-background-blur-and-details · https://github.com/maxmpz/powerampapi
- Symfonium: https://docs.symfonium.app/wiki/settings/settings-interface-now-playing-screens/ · https://styles.symfonium.app
- Open-source players: https://github.com/RetroMusicPlayer/RetroMusicPlayer · https://raw.githubusercontent.com/RetroMusicPlayer/RetroMusicPlayer/dev/app/src/main/java/code/name/monkey/retromusic/fragments/NowPlayingScreen.kt · https://github.com/FoedusProgramme/Gramophone · https://github.com/mostafaalagamy/Metrolist · https://github.com/z-huang/InnerTune · https://github.com/OuterTune/OuterTune · https://www.github.com/namidaco/namida
- Plexamp: https://forums.plex.tv/t/how-to-plexamp-4-6-1-settings/724870 · https://medium.com/@elanf/plexamp-v3-9af3b10063b4 · https://forums.plex.tv/t/issue-with-new-blurred-dimmed-backgrounds/583573 · https://forums.plex.tv/t/sometimes-the-backgrounds-color-of-the-menus-are-really-difficult-to-read/874424
- Android system media controls: https://developer.android.com/media/implement/surfaces/mobile · https://gitlab.e.foundation/e/os/android_frameworks_base/-/commit/8fa6c83d9532c95243e61af2e6ef1fe5de2cc7ff · https://gitlab.e.foundation/e/os/android_frameworks_base/-/commit/22b40cc4e0b1699e4d977cbdadf527649b338632 · https://www.sammobile.com/?p=2564683

**Techniques**
- Colour: https://developer.android.com/develop/ui/views/graphics/palette-colors · https://github.com/material-foundation/material-color-utilities · https://api.flutter.dev/flutter/package-material_color_utilities_dynamiccolor_variant/Variant.html · https://github.com/material-components/material-components-android/blob/master/docs/theming/Color.md · https://github.com/jordond/MaterialKolor
- Blur and drawing: https://developer.android.com/reference/kotlin/androidx/compose/ui/draw/blur.modifier · https://developer.android.com/develop/ui/compose/graphics/draw/modifiers · https://github.com/android/renderscript-intrinsics-replacement-toolkit · https://github.com/skydoves/cloudy · https://github.com/chrisbanes/haze · https://chrisbanes.github.io/haze/latest/blur/platforms/ · https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl
- Animation and layout: https://developer.android.com/develop/ui/compose/animation/shared-elements · https://developer.android.com/develop/ui/compose/layouts/pager
- Controls: https://developer.android.com/jetpack/androidx/releases/compose-material3 · https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-linear-wavy-progress-indicator.html · https://android-developers.googleblog.com/2025/08/introducing-material-3-expressive-for-wear-os.html · https://awesome.ecosyste.ms/projects/github.com%2Fsaket%2Fsquiggly-slider · https://github.com/mahozad/wavy-slider · https://developer.android.com/reference/kotlin/androidx/media3/ui/compose/state/package-summary · https://android-developers.googleblog.com/2026/08/media3-1-11-whats-new.html · https://developer.android.com/develop/ui/views/haptics/haptic-feedback
- Performance: https://developer.android.com/develop/ui/compose/performance/bestpractices · https://developer.android.com/develop/ui/compose/performance/stability/fix · https://developer.android.com/topic/performance/baselineprofiles/overview · https://gist.github.com/Digipom/d1dc1aef7623d8f58ff6b57cb6935d5b
- Accessibility: https://w3.org/WAI/WCAG22/Understanding/contrast-minimum.html · https://webaim.org/articles/contrast/ · https://developer.android.com/develop/ui/compose/accessibility/api-defaults · https://developer.android.com/develop/ui/compose/accessibility/semantics · https://dev.to/eevajonnapanula/android-animations-and-reduced-motion-2em2

**Customization**
- Theme files: https://fileinfo.com/extension/kwgt · https://docs.rs/attheme · https://core.telegram.org/api/themes.md
- Compose state and persistence: https://developer.android.com/develop/ui/compose/compositionlocal · https://developer.android.com/topic/libraries/architecture/datastore · https://kotlinlang.org/docs/serialization-polymorphism.html
- Screenshot testing: https://developer.android.com/studio/preview/compose-screenshot-testing · https://github.com/takahirom/roborazzi · https://github.com/cashapp/paparazzi
