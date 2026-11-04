# WeTune — TODO (next phases)

Phase 1 (current) = Android, Compose + Media3, YouTube Music (cookie, two-way sync), local files, JioSaavn, Subsonic/Navidrome, LRCLIB lyrics, liquid-glass + Material theme. Everything below is deferred.

## Platforms
- [ ] Desktop (Windows/macOS/Linux) via Compose Multiplatform + libmpv (SimpMusic model)
- [ ] iOS (Compose Multiplatform + AVPlayer)

## Account
- [x] Google sign-in via WebView (pasted cookies die when the source browser rotates them)
- [ ] Keep the session alive by applying rotated cookies (Set-Cookie) from YouTube responses
- [ ] Multiple accounts / account switcher
- [ ] Auto-detect expired cookie and prompt re-login in-place

## Audio
- [ ] Crossfade (with curve options)
- [ ] Skip silence
- [ ] Equalizer (10–15 band) + AutoEq headphone profiles
- [ ] Tempo / pitch control
- [ ] Bit-perfect USB output (send untouched audio to an external USB DAC, Android 14+)

## Library
- [ ] Spotify playlist import
- [ ] Move local library from JSON to Room
- [ ] Smart downloads (auto-download liked songs on Wi-Fi)
- [ ] Download manager screen (progress, storage usage, delete)

## Integrations
- [ ] Last.fm / ListenBrainz scrobbling
- [ ] Discord Rich Presence
- [ ] SponsorBlock (skip non-music segments)
- [ ] Chromecast / DLNA
- [ ] Android Auto
- [ ] Home-screen widgets
- [ ] Song recognition (Shazam / SongRec)

## Social
- [ ] Listen Together (relay server or Nostr)
- [ ] Blend (merged taste playlist)

## Extras
- [ ] Wrapped-style yearly stats
- [ ] AI lyric translation / romanization
- [ ] Animated covers (Spotify Canvas style)
- [ ] Music videos
- [ ] Vocal removal / "Sing" mode
- [ ] DJ / automix with beat matching

## Lyrics
- [ ] More providers: BetterLyrics (word-by-word), KuGou, NetEase, YouTube
- [ ] Word-by-word karaoke animation
- [ ] Lyrics offset adjust + offline save

## Sources
- [ ] Stream-resolver fallback chain (NewPipe → innertubex → own client)
- [ ] Radio Browser (internet radio)
- [ ] Podcasts

## Theme
- [ ] Preset palettes
- [ ] Multiple player styles (Classic / Material Expressive / Apple-like)
- [ ] Custom fonts, corner/shape options, icon packs
- [ ] Shareable "Looks" (QR / file)
- [ ] Now Playing customization on the new glass design: cover style, background style, action-button style, seek bar style

## Ideas
- [ ] "Continue where you left off" card on Home
- [ ] Data-saver mode
- [ ] Quick queue gestures (swipe to play next / add to queue)
