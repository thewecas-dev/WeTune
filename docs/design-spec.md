# WeTune design spec (rebuild)

Source of truth for the look. Mockups: canvas "WeTune Identity Directions", pages **Round 6** (Now Playing behaviour), **Round 8** (both styles), **Round 10 option E** (navbar), **Round 11** (final screens).

## 1. Principles

- **Apple-like everywhere; Now Playing has a style choice.** The whole app (Home, Library, Search, You, album/artist/playlist, settings, navbar, mini player) uses the Apple-like style only. **Now Playing** — its normal, Lyrics, Lyrics-full-screen, Audio and Queue states — can be switched in Settings › Appearance › Player style: **Apple-like** (default) or **Material**. Both player styles share colours, layout, behaviour and motion; only shapes, type, icons and a few controls differ (§3–5). One Now Playing component tree, styled by a `PlayerStyle` object (see §9).
- **Neutral surfaces, cover as a hint.** The cover's colour never tints UI surfaces, text or controls. It only appears as the cover image itself and a faint, desaturated glow behind Now Playing.
- **Frost, not glass.** Real backdrop blur with a neutral tint. No rim highlight, no shadow, no gradient edge, no Liquid Glass.
- **No layout shift.** Things glide, crossfade or grow; nothing jumps. Elements present in two states move between them (shared-element transitions).
- **High density, FHD+.** Designed at 393 × 873 dp (1080 × 2400 @ ~2.75×). Rows 52–58 dp, body 14–15 sp, minimum touch target 44 dp.

## 2. Colour (shared by both styles)

| Token | Dark | Light |
|---|---|---|
| `bg` | `#000000` | `#F2F2F7` (Apple) / `#F5F5F7` (Material) |
| `surface` | `#1C1C1E` | `#FFFFFF` |
| `surfaceHigh` / tonal | `#3A3A3C` | `#E5E5EA` |
| `frost` (bars, sheets, mini player) | `rgba(30,30,32,0.62)` + blur 40 + saturate 1.8 | `rgba(255,255,255,0.72)` + same blur |
| `onBg` | `#FFFFFF` | `#000000` / `#1C1C1E` |
| `onBgSecondary` | `#98989F` | `#6C6C70` |
| `outline` / separators | `#636366` / `rgba(255,255,255,0.08)` | `#C7C7CC` / `rgba(60,60,67,0.12)` |
| `primary` (play, selected chip/pill/tab) | `#F2F2F2` on `#000` content | `#1C1C1E` on `#FFF` content |
| `liked` | `#FF6B5E` | `#FF3B30` |
| `success` (synced) | `#30D158` | `#34C759` |

- **Selected toggles** (Shuffle / Repeat / Automix, filter chips, active tab): solid `primary` with inverted content.
- **Cover glow:** the current cover drawn at ~1.5–1.8× size, blur 70–80 dp, saturation 0.7–0.8, opacity 0.30–0.38, behind Now Playing only. Cross-fades over 600 ms when the song changes.

## 3. Type

| Role | Apple-like (Inter) | Material (Outfit) |
|---|---|---|
| Large title | 34 / 800, −0.02em | 28 / 600 |
| Now Playing title | 21–22 / 700 | 24 / 600 |
| Section header | 20–21 / 700 | 20 / 600 |
| Row title | 15 / 500 | 16 / 500 |
| Secondary | 13 / 400 | 13 / 400 |
| Caption / time | 11 / 600 tabular | 12 / 500 tabular |
| Lyrics (in sheet / full) | 20 / 700 · 27 / 800 | 18–20 / 600 · 25–29 / 600 |

Fonts are bundled (no network font loading). Indic scripts fall back to Noto Sans for Telugu/Tamil/Kannada/Devanagari.

## 4. Shape

| Thing | Apple-like | Material |
|---|---|---|
| Sheet / dock corners | 30 | 32 |
| Mini player | capsule (radius = height/2), 60 dp | capsule 64 dp |
| Cards / tiles | 10–12 | 14–28 (carousel 28) |
| Thumbnails | 6–8 | 12 |
| Play button | glyph only, 64 dp target | rounded rect 96–100 × 66–70, radius 24–26 |
| Prev / Next | double-chevron glyph | 56–58 dp tonal circle |
| Seek bar | 7 dp capsule, white fill | wavy (played) + flat (remaining), 3.5 dp, 4 × 16 dp handle |
| Lyrics · Audio · Queue row | three plain icon buttons, active = white-ish tint | connected button group, active = white segment |
| Lists | inset grouped, 12 dp corners, inset separators | stepped groups: 22 dp outer corners, 6 dp inner, 2 dp gaps |

## 5. Icons

- **Apple-like:** in-house SF-style set: rounded, mostly filled, 1.9–2.2 stroke for line icons. No SF Symbols assets are copied.
- **Material:** Material Symbols (Apache 2.0) paths.
- **Fixed meanings in both:**
  - Lyrics = speech bubble (quote / lines).
  - **Audio = waveform / `graphic_eq`**, never a speaker.
  - Queue = list (+ note).
  - Liked = filled heart.
  - Open Now Playing = chevron up.
  - Full screen = arrows out; exit = arrows in.

## 6. Components

### Mini player (one instance, app root)
Cover-ring disc on the left, then title/artist, then the **action button**.
- **Cover-ring disc:** 40 dp cover inside a 52 dp progress ring; tap = play/pause.
- **Action button:** opens Now Playing (chevron up).
- **Rendering rule:** rendered once at the root and never re-created per screen, so it is pixel-identical everywhere.

### Navbar — option E (identical in both styles: same shapes, icons and type; it does not switch with `WeStyle`)
- **Tabs:** Home · Library · Search · You. The profile lives in the navbar as the **You** tab (account + settings); there is no avatar in page headers.
- **At rest:** the mini player sits above a full-width frosted tab capsule.
  - Active tab: `primary` pill with icon + label (Apple-like); Material uses a 64 × 32 indicator + label.
  - Inactive tabs: icon only.
- **Scrolled down (≥ 48 dp):** the tab capsule folds into one round button showing the current tab's icon, placed left of the mini player, which slides down to the bottom row. Tapping that button unfolds the tabs.
- **Unfolds again:** when scrolling up, at the top of a page, or when switching tabs.
- **Motion:** 280 ms emphasized-decelerate. The capsule's width and the active pill animate continuously; icons cross-fade.

### Now Playing (one controls sheet)
- **Normal:** cover full-bleed, vertically centred in the space above the sheet. The sheet is only as tall as its content: title / artist / like, seek + times + quality badge, transport, and the Lyrics · Audio · Queue row. No separator above that row.
- **Lyrics or Audio open:** the sheet grows to the cover's lower edge (top ≈ 353 dp) and the cover slides up to y = 0.
  - **Lyrics:** a full-screen button.
  - **Audio:** volume, output device, quality badge, Other versions, Audio pipeline.
- **Lyrics full screen:** the sheet fills the screen.
  - The cover shrinks into a 52–56 dp thumbnail left of the title; like stays.
  - The bottom keeps only seek + times + prev / play / next.
- **Queue:** goes straight to full screen with the same header.
  - Shuffle / Repeat / Automix pills.
  - A reorderable list.
  - Seek + prev / play / next.
- **Shared elements between all states:** cover, title, artist, like, seek, play / prev / next.

### Lists, pages, settings
- Album / artist / playlist pages, search results, sign-in, settings sub-pages, empty and loading states follow Round 11.
- **Sync status** is visible where it matters: "Synced" on Library, "Saved to library · synced" on albums, and the playlist badge.

## 7. Motion

- **Durations:** 200 ms (small), 280 ms (navbar fold, sheet grow), 400 ms (screen push), 600 ms (glow / colour cross-fade).
- **Easing:** standard / emphasized-decelerate. No bounce, no overshoot.
- **Screens:** push = slide + fade 24 dp. Tabs cross-fade in place.
- **Images:** fade in over 200 ms on a neutral placeholder of the final size, so there is no size jump.
- **Loading:** skeleton shimmer at 1.4 s. `prefers-reduced-motion` / Android "remove animations" turns shimmer and drift off.

## 8. Accessibility

- **Contrast:** text ≥ 4.5 : 1, large text and icons ≥ 3 : 1, in both styles and both themes.
- **Labels:** every icon button has a content description. Toggles expose on/off.
- **Font scale:** respected up to 200 %. Rows grow; nothing truncates in Settings pickers.

## 9. Implementation notes (Compose)

- **App style is fixed:** Apple-like shapes, Inter, the SF-style icon set, for every screen outside Now Playing. Colours come from `WeColors` (dark / light).
- **`PlayerStyle`:** a data class with `typography`, `icons`, `transport`, `seekBar`, `actionRow` and `listGroup` (Queue / Audio panels) slots. Two instances, `AppleLike` (default) and `Material`, provided through a `CompositionLocal` **inside Now Playing only**. Only those leaf components (`PlayButton`, `SeekBar`, `NowPlayingActionRow`, `QueuePills`, `AudioPanel`) switch on it; the sheet layout and states are shared.
- **Cover glow:** blur via `RenderEffect` on Android 12+, a pre-blurred downscaled bitmap below that. Frost on bars uses Haze.
- **Mini player + navbar:** live in one root `Scaffold` overlay driven by a single scroll-connection state (`navFolded`).
