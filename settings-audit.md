# Settings audit (WeTune 0.7.x)

## 1. Current tree (before)

Settings (tab "You")
- [Account header row] → Account
- Account — signed in: name row + Sign out · signed out: "Sign in with Google", "Or paste a cookie" (field + button)
- Appearance
  - Theme: System / Light / Dark / Black (segmented)
  - Glass style: Frosted / Liquid (+ Reduce transparency, Liquid glass preview → glasslab, only when Liquid)
  - Accent color: Match the song (toggle)
  - Now Playing: Customize player (opens NpEditor)
  - Lyrics: Romanized inline lyrics, Word by word, Fill the one-line lyric too (toggles)
  - Lyrics panel: Moving between lines (Glide/Fade/Instant), Alignment (L/C/R), Text size (slider), Line height (slider), preview
  - Display: Auto-rotate
- Playback
  - Maximum quality: On Wi-Fi / On mobile data (Data / High / 320 / Lossless) + long explanation
  - Listening: Normalize volume, Autoplay
- Sources: source order list (add-ons, JioSaavn, YouTube), Subsonic/Navidrome form, "Also: This device" (static row)
- Backup: On-device library Back up / Restore
- About: version (hard-coded "0.6.0", app is 0.7.0), sources, built with, disclaimer

## 2. Problems

| # | Problem | Kind |
|---|---------|------|
| 1 | Appearance is a dumping ground: 7 groups, half of it is lyrics behaviour, not appearance | wrong grouping, buried |
| 2 | Lyrics settings (8 rows) have no page of their own; users look for "Lyrics" at the top level | buried |
| 3 | "Playback" mixes audio quality (a data/network decision) with listening behaviour | wrong grouping |
| 4 | `autoplayMix` (Radio vs Same artist) exists but is only reachable from the player's queue sheet | buried |
| 5 | `Settings.playFrom`, `wifiHigh`, `mobileHigh` are dead delegates (only read raw to seed `maxWifi`/`maxMobile` defaults) | dead |
| 6 | Recent searches can only be cleared from the Search screen; no privacy/data section at all | missing |
| 7 | No way to see or clear the 512 MB audio cache, or to change its size | missing |
| 8 | Backup saves the library but not the app's settings or the Now Playing style | missing |
| 9 | About shows "Version 0.6.0" (hard-coded, wrong) | bug |
| 10 | "Also → This device" is a non-interactive row that looks tappable-ish and says nothing useful | noise |
| 11 | No settings search | missing |
| 12 | Group titles are not announced as headings by TalkBack | a11y |
| 13 | Segmented labels sit in loose `Txt` above the chips with no row title/description pattern; inconsistent with toggle rows | inconsistent |
| 14 | Category summaries don't match contents ("Theme, accent color, rotation" hides lyrics + player) | confusing labels |
| 15 | Headphones-unplugged pause, swipe-away behaviour, skip-silence, sending plays to YouTube history are all hard-wired | missing control |

## 3. How others structure it (from memory of current apps)

- **Spotify**: Account · Content & display · Playback (gapless, crossfade, automix, mono, normalise, autoplay) · Audio quality (Wi-Fi / cellular / download, data saver) · Storage (cache, clear) · Privacy · Notifications · About. Top-level is a flat list of categories with search.
- **YouTube Music**: General (start page… ) · Playback & restrictions · Data saving (quality per network, stream via Wi-Fi only) · Downloads & storage · Privacy & location (pause history, clear history) · Recommendations · About.
- **Apple Music**: Playback (crossfade, Sound check, Dolby, lossless per network) · Downloads · Library · Privacy.
- **Poweramp / Symfonium**: very deep; top-level icons + one-line summaries, a search field, and "Look & feel" vs "Audio" vs "Library" vs "Lyrics" vs "Misc/backup settings to file".
- **Metrolist / InnerTune**: Appearance · Content · Player & audio (quality, skip silence, normalise, persistent queue, stop on task clear) · Storage (image/song cache size, clear) · Privacy (pause history, pause search history) · Backup & restore · About.

Common pattern: **Account → Playback → Audio quality → (Lyrics) → Appearance → Library/Sources → Storage & privacy → Backup → About**, each with an icon and summary, plus a search field.

## 4. New structure (implemented)

Settings
- [Search settings] field — filters every row across pages by title, description and keywords; results jump to the page
- [Account header row] → Account (unchanged)
- **Playback** — Autoplay, Autoplay picks (Similar songs / Same artist), Normalize volume, Skip silence; Behaviour: Pause when headphones disconnect, Stop when swiped away
- **Audio quality** — Maximum on Wi-Fi / on mobile data (same keys), short explanation, link to Sources
- **Lyrics** — Romanized inline, Word by word, Fill the one-line lyric; Panel: motion, alignment, size, line height, preview; Reset lyric timing for all songs
- **Appearance** — Theme, Accent (match song + fixed colour swatches), Text size, Glass style (+ liquid extras), Now Playing → Customize player, Open on (start tab), Auto-rotate
- **Sources** — unchanged list + Subsonic (dead "This device" row removed)
- **Storage & privacy** — Audio cache size limit, cache in use + Clear cache, Send plays to YouTube history, Clear search history
- **Backup** — On-device library (unchanged) + App settings export/import (JSON, includes Now Playing style; excludes sign-in cookie and Subsonic password)
- **About** — version from the package (fixes 0.6.0)

Page ids kept (`account`, `appearance`, `playback`, `sources`, `backup`, `about`, `glasslab`); added `quality`, `lyrics`, `storage`. All stored keys kept; three dead delegates dropped (their raw values still seed the quality defaults).

## 5. New options — value / effort / status

| Option | Value | Effort | Status | Wired in |
|--------|-------|--------|--------|----------|
| Settings search | high | S | now | SettingsScreen.kt |
| Skip silence | med | XS | now | PlaybackService (`skipSilenceEnabled`) |
| Pause when headphones disconnect (was always on) | med | XS | now | PlaybackService (`setHandleAudioBecomingNoisy`) |
| Stop playback when swiped away | med | XS | now | PlaybackService.onTaskRemoved |
| Autoplay picks (radio / artist) surfaced | med | XS | now | existing key `autoplay_mix` |
| Fixed accent colour swatches | med | S | now | Theme.kt |
| App text size (90–130 %) | high (a11y) | S | now | Theme.kt (LocalDensity fontScale) |
| Start tab (Home / Library) | low-med | XS | now | AppUi.kt |
| Audio cache size limit (256 MB–4 GB) | med | XS | now (applies on next start) | App.kt |
| Clear audio + image cache | high | S | now | SettingsScreen.kt |
| Send plays to YouTube history (pause history) | high (privacy) | XS | now | PlaybackService.maybeRegister |
| Clear search history | med | XS | now | existing key |
| Reset lyric timing for all songs | low-med | XS | now | Lyrics.resetOffsets |
| Export / import settings JSON incl. `np_style` | high | S | now | Settings.kt (+ unit test) |
| Version from package | bug | XS | now | SettingsScreen.kt |
| Crossfade | med | L (ExoPlayer has no crossfade; needs two players) | later | — |
| Resume playing on launch / on Bluetooth connect | med | M (receiver + foreground-start rules) | later | — |
| Lyrics provider on/off + priority | med | M | later | Lyrics.kt job list |
| Default repeat / shuffle | low | S | later | Playback.kt |
| Notification buttons (shuffle / repeat beside like) | med | M | later | PlaybackService custom layout |
| Download quality / location | med | M | later | Downloads.kt |
| Home sections on/off | med | M (YouTube feed titles vary) | later | HomeSections.kt |
| Alternate app icons | low | M (activity-alias per icon) | later | Manifest |
| Mix with other apps (no audio focus) | low | XS | later (rarely wanted) | PlaybackService |

## 6. Accessibility

- Group titles now have heading semantics (Kit.Group) so TalkBack can jump between sections.
- Toggle rows use `toggleable(role = Switch)` (TalkBack announces On/Off); rows ≥ 56 dp; swatches are 48 dp radio buttons with colour names; the search field has a label.
