# DJ-style features for WeTune: research report

Research date: 11 November 2026. Everything below was checked against the sources listed in section 6 unless it is marked **(unverified)**. Images are linked from their original hosts and were not downloaded.

**What WeTune already has (from the code)**, which the feasibility notes build on:

- One `ExoPlayer` (Media3 **1.5.1**) inside `PlaybackService`, with gapless playback ("the player buffers the next song near the end of the current one").
- A `CacheDataSource` under the resolver. A song that has played once is on disk and can be read again without the network. This is what makes later analysis (BPM, key, intro/outro) possible.
- Loudness normalisation, done by setting `player.volume` from YouTube's `loudnessDb` (`PlaybackService.normalize`). Any fade has to multiply with this gain, not replace it.
- Shuffle, a sleep timer, a speed read-out (`Playback.speed()`), and YouTube radio (`YouTube.radio(videoId)`) that keeps adding similar songs.
- `minSdk 26`, so Android's `VolumeShaper` (API 26) can be used.
- No local play history: plays are reported to YouTube only. Stats features need a new local play log.
- `todo.md` already lists Crossfade, Equalizer, Tempo/pitch, Wrapped-style stats and "DJ / automix with beat matching".

---

## 1. Summary: the most promising features for WeTune

Ranked by value for a *listening* app against effort. Effort: **Easy** is days, **Medium** is one to two weeks, **Hard** is weeks plus DSP or ML work.

| # | Feature | Effort | Why |
|---|---|---|---|
| 1 | **Crossfade** (1 to 12 s, curve options, off for gapless albums) | Easy–Medium | The feature people most often ask for. Spotify, Apple, Poweramp, Metrolist and SimpMusic all have it; YouTube Music itself does not. Needs a second, short-lived ExoPlayer for the outgoing song plus volume ramps. |
| 2 | **Smart transitions: silence trim + "DJ filter" fade** | Medium | Skipping silent intros and outros (as Mixxx does at -60 dBFS) and sweeping a high-pass filter on the outgoing song (as SimpMusic's "DJ-styled" crossfade does) gets most of the "AutoMix" feel without beat-matching. |
| 3 | **Smart Shuffle** (mix recommendations into the user's own playlist) | Easy | `YouTube.radio()` already returns similar songs. Insert one after every N tracks and tag it as "suggested", like Spotify. |
| 4 | **Local listening stats** (weekly "mini Wrapped", yearly recap) | Easy–Medium | One play-log table (track, source, timestamp, ms listened) plus Compose screens. Works offline and keeps data private. Spotify's weekly Listening Stats (Nov 2025) is the model. |
| 5 | **Tempo / pitch control** ("nightcore", "slowed") | Easy | Media3 already ships Sonic: `setPlaybackParameters(speed, pitch)`. Mostly UI work. |
| 6 | **Equalizer + simple DJ filter knob** | Medium | Either the platform `Equalizer` on the audio session, or a custom Media3 `AudioProcessor` (biquad bands) that also gives the transition filter in #2. |
| 7 | **Daylist-style time-of-day mixes** | Medium | Needs the play log from #4. Group plays by daypart and seed YouTube radio or search from what the user plays at that time. Titles are generated from tags and need no LLM. |
| 8 | **BPM + key analysis of cached songs** | Medium–Hard | Decode the cached file to PCM in the background and run onset-envelope autocorrelation (tempo) and chroma/key-profile matching (key). Shows BPM/key like Spotify's Mix editor and feeds #9. Watch the licences: most libraries are GPL. |
| 9 | **Auto-DJ ordering** (energy/BPM/key-aware queue ordering, "harmonic shuffle") | Medium (after #8) | Reorder the upcoming queue so neighbours have close BPM and compatible Camelot keys. Cheap once #8 exists. |
| 10 | **Prompt-based radio** ("rainy-day lo-fi for coding") | Medium | Without a server or LLM: map the prompt to a YouTube Music search (playlists/songs) plus radio. Real natural-language understanding needs a cloud LLM or an on-device model. |
| 11 | **Beat-matched AutoMix** (tempo ramp + phase-aligned crossfade) | Hard | Needs #8 to be accurate (±0.02 BPM matters, per Mixxx), plus speed ramps on two players. Apple's own AutoMix skips beat-matching when tempos or genres don't fit. |
| 12 | **Listen-together (Jam-like)** | Hard | Needs a relay or sync server, or a local-network/Nearby protocol. WeTune has no backend. |

**Not worth it for WeTune:** an AI voice DJ with commentary (needs a licensed TTS voice and an LLM, which means a server), real-time stem separation (heavy ML on a phone, and YouTube streams are compressed Opus/AAC), scratching, hot cues and loops (performance tools, not listening features), and mix recording (legal grey area with streamed content).

---

## 2. Spotify, feature by feature

Spotify has many "the app picks or mixes for you" features. Most are **Premium only** and many are still labelled **beta**.

### 2.1 DJ (AI DJ with voice)
- **What it is:** A personalised, never-ending radio session in the "DJ" card. An AI voice introduces songs with short facts and explains why it picked them. You tap the DJ button to switch to a different vibe.
- **How it works:** Spotify's recommender picks the music. Commentary is written with OpenAI technology together with Spotify's music editors, and spoken by a voice platform from Spotify's acquisition of Sonantic. The first voice is modelled on Xavier "X" Jernigan.
- **Timeline and availability (Premium only, beta):**
  - Feb 22 2023: launch in English, US and Canada.
  - May 16 2023: UK and Ireland. Aug 8 2023: 46 more markets.
  - Jul 17 2024: Spanish voice "Livi".
  - **May 13 2025: voice requests.** Press and hold the DJ button and ask by genre, mood, artist or activity. English, 60+ markets, music only (no podcasts or audiobooks).
  - Sep 23 2025: "Talk to DJ" on Android Auto.
  - **Oct 15 2025:** Spanish requests, **typed requests**, and three personalised suggested prompts.
  - Mar 18 2026: more markets (Belgium, Czech Republic, Denmark, Finland, Japan, Netherlands, Norway, Poland).
  - **Apr 20 2026: "Ask DJ"**, a conversational beta for recommendations and facts. Premium users aged 18+ in the US, Ireland and Sweden, English.
  - **May 7 2026:** French, German, Italian and Brazilian Portuguese voices. 75+ markets in total. Spotify says 94 million Premium users have used DJ.
  - **Nov 10 2026 (yesterday):** seven new English voices and two new Spanish voices, plus a **commentary-off toggle** (music only, with brief acknowledgements).
- **Visuals:** [DJ launch header](https://storage.googleapis.com/pr-newsroom-wp/1/2023/02/FTR-Header-DJ-1-1-1440x733.png) · [Voice requests header](https://storage.googleapis.com/pr-newsroom-wp/1/2025/05/Copy-of-SPIDJ_Record_PR_Header_1440x550.jpg) · [Oct 2025 update header](https://storage.googleapis.com/pr-newsroom-wp/1/2025/10/DJ-Header-2-1920x733.png) · [2026 languages header](https://storage.googleapis.com/pr-newsroom-wp/1/2026/05/Header-2-DJ-1872498274597.jpg)
- **Sources:** [launch](https://newsroom.spotify.com/2023-02-22/spotify-debuts-a-new-ai-dj-right-in-your-pocket/), [voice requests](https://newsroom.spotify.com/2025-05-13/dj-voice-requests/), [Spanish/text](https://newsroom.spotify.com/2025-10-15/dj-spanish-text-requests-update/), [Ask DJ](https://newsroom.spotify.com/2026-04-20/deeper-music-features-about-the-song-dj-songdna/), [4 languages](https://newsroom.spotify.com/2026-05-07/dj-expansion-4-new-languages/), [new voices](https://newsroom.spotify.com/2026-11-10/dj-new-voices-controls/)
- **WeTune feasibility:**
  - **Voice commentary: Not feasible** without a server. It needs an LLM to write the lines and a licensed, natural TTS voice. Android's built-in `TextToSpeech` could read a template line ("Next up, a 2014 track from…"), but it sounds robotic. It could be an optional gimmick, rated Easy.
  - **The music part, "a DJ session": Easy.** It is essentially radio seeded from the user's recent plays. **Typed requests: Medium**, by mapping a prompt to a search plus radio (see 3.4).

### 2.2 Smart Shuffle (formerly Enhance)
- **What it is:** A third shuffle mode (Shuffle → Smart Shuffle) that mixes recommended songs that "match the vibe" into your own playlist. Suggestions are marked with a sparkle icon, and you can keep or remove each one.
- **History:** "Enhance" (2021, Premium) added up to 30 recommendations, one after every two tracks. Smart Shuffle replaced it in March 2023 (Stream On). It later reached free users, and on free mobile it is reportedly the default or always-on mode (regional help pages differ).
- **Sources:** [Support: shuffle](https://support.spotify.com/us/using_spotify/features/shuffle/), [Newsroom tag: Enhance → Smart Shuffle](https://newsroom.spotify.com/tag/enhance), [Engadget on Enhance](https://www.engadget.com/spotify-enhance-playlist-song-suggestions-180749023.html)
- **Visual:** see the support page linked above (it has an inline screenshot of the shuffle button states).
- **WeTune: Easy.** Take one seed per N playlist tracks, call `YouTube.radio(seed)`, dedupe against the playlist, insert with a "suggested" flag and an "add to playlist" action. Shuffle state already lives in `Playback.shuffle`. Make the third state opt-in: Spotify users dislike that it is hard to turn off ([community thread](https://community.spotify.com/t5/Your-Library/Introducing-Smart-Shuffle/td-p/5516566/page/54)).

### 2.3 Crossfade, Automix and gapless ("Transitions between tracks")
- **Crossfade:** a slider (0 to 12 s) under Settings → Playback. Free and Premium.
- **Automix (Premium):** "beat-matched seamless transitions between songs on select Spotify playlists", working even on shuffle. It applies only to playlists Spotify has prepared, not to every song.
- **Gapless:** removes the pauses between tracks.
- None of these can be set over Spotify Connect.
- **Source:** [Support: Transitions between tracks](https://support.spotify.com/us/article/tracks-transitions/)
- **WeTune:** gapless is **done**. Crossfade is **Easy–Medium** (see 5.1). Automix is **Hard** (see 3.1).

### 2.4 Mixed playlists ("Mix" button / custom transitions)
- **What it is:** Open any playlist and tap **Mix**. Spotify adds transitions automatically, or you edit each one: presets such as *Fade*, *Rise* and *Blend*, volume/EQ/effect curves, and the transition start chosen on a waveform with beat markers. **Each track's BPM and key is shown** to help with reordering. Mixes can be saved, shared and edited by collaborators. Spotify says pre-produced genres such as house and techno blend best.
- **Availability:** Aug 19 2025, mobile, beta, Premium; some APAC markets later. Desktop from **May 19 2026**. Free users hear mixed playlists as normal playlists, and over Spotify Connect they play without transitions.
- **Visuals:** [Playlist "Mixed by you"](https://techcrunch.com/wp-content/uploads/2025/08/Playlist-Mixed-by-You.png?w=1024) · [Enable Mix](https://techcrunch.com/wp-content/uploads/2025/08/Enable-Mix.png?w=680) · [Effects editor](https://techcrunch.com/wp-content/uploads/2025/08/Effect_1.png?w=680)
- **Sources:** [TechCrunch](https://techcrunch.com/2025/08/19/spotifys-latest-feature-lets-you-add-your-own-transitions-to-playlists), [Engadget](https://www.engadget.com/entertainment/music/spotify-now-lets-you-create-seamless-transitions-between-songs-on-your-custom-playlists-100051537.html), [Tom's Guide](https://www.tomsguide.com/entertainment/music-streaming/spotifys-new-mixing-feature-lets-you-dj-your-own-playlists-heres-how-it-works), [Newsroom tag "fade"](https://newsroom.spotify.com/tag/fade/). The support page `support.spotify.com/article/mixed-playlists/` returned 404 from here **(unverified)**.
- **WeTune:** **Medium** for per-transition presets (fade / filter-sweep / echo-out) stored per playlist, built on the crossfade engine. **Hard** for a waveform-and-beats editor.

### 2.5 Jam (group listening)
- **What it is:** A shared, real-time queue. Up to 32 people add songs, and recommendations blend everyone's taste. Joining works by QR code, link or tap-to-join with Bluetooth proximity. Only Premium users can start a Jam. Free users can join in person; Premium users can join remotely.
- **Timeline:** launched Sep 26 2023 worldwide. Later: "Request to Jam" through Spotify Messages, and Android Auto integration (Aug 2025).
- **Sources:** [TechCrunch launch](https://techcrunch.com/2023/09/26/spotify-launches-jam-a-real-time-collaborative-playlist-controlled-by-up-to-32-people), [Newsroom tag: Jam](https://newsroom.spotify.com/tag/spotify-jam)
- **WeTune: Hard.** A shared queue needs a relay. Options: a free realtime backend such as Firebase or Supabase (breaks "no backend"), WebRTC with a signalling server, or **same-room only** through Google Nearby Connections. Each guest phone resolves the stream itself; only track IDs and positions are synced.

### 2.6 daylist
- **What it is:** A personal playlist that changes several times a day to fit your listening at that time. It has quirky generated titles such as "90s rave rainforest late night", built from niche micro-genres. Free and Premium.
- **Timeline:** Sep 12 2023 (US, CA, UK, AU, NZ, IE). Global Sep 2024.
- **Visuals:** [TechCrunch daylist on 3 phones](https://techcrunch.com/2024/09/04/spotify-launches-its-evolving-playlist-daylist-globally) (header image on the page)
- **Sources:** [9to5Google](https://9to5google.com/2023/09/12/spotify-daylist-customized-playlist/), [TechCrunch global](https://techcrunch.com/2024/09/04/spotify-launches-its-evolving-playlist-daylist-globally)
- **WeTune: Medium.** Needs a local play log. Cluster plays by weekday × daypart and seed radio or search from the top artists in the current slot. Titles can be templates such as "<top genre/artist mood> <daypart>". Genre tags are not directly in InnerTune data **(assumption)**, so artists or the YouTube "mood" chips would be the vocabulary.

### 2.7 AI Playlist and Prompted Playlist
- **AI Playlist (beta, Premium):** type a prompt ("songs for a rainy Sunday") and get a 30-song playlist you can refine with more prompts. Launched April 2024 in the UK and Australia, then the US, Canada, Ireland and NZ, then 40+ more markets on Apr 24 2025 (50+ by summer 2025). English.
- **Prompted Playlist (beta, Premium):** a newer prompt-driven playlist that uses your whole listening history and can refresh on a schedule. Tested in NZ in Dec 2025, US/Canada in Jan 2026, UK/IE/AU/SE on Feb 23 2026, podcasts added Apr 2026 (single secondary source).
- **Visual:** [AI Playlist newsroom header](https://storage.googleapis.com/pr-newsroom-wp/1/2025/04/AI-Playlist-Newsroom-Header-1024x583.jpg)
- **Sources:** [Newsroom: 40+ markets](https://newsroom.spotify.com/?p=33837), [Newsroom: US/CA/IE/NZ](https://newsroom.spotify.com/?p=30737), [TechCrunch: Prompted Playlist](https://techcrunch.com/2026/02/23/spotify-ai-prompted-playlists-uk-markets), [PPC Land: podcasts](https://ppc.land/spotifys-prompted-playlist-adds-podcasts-for-premium-users-in-seven-markets/)
- **WeTune:** **Medium** for keyword → YouTube Music search (playlists and songs) → merge, dedupe and seed radio. **Needs a server or licensed LLM** for real language understanding. An on-device option (Gemini Nano through ML Kit GenAI on supported phones) is possible but limited to newer devices **(unverified for this use)**.

### 2.8 Blend
- **What it is:** A shared playlist that mixes the tastes of 2 to 10 people and updates daily. It shows a "taste match" percentage and a shareable data story. Artists can also be blended with. Free and Premium.
- **Timeline:** Aug 31 2021 (2 people). Up to 10 people from Mar 2022.
- **Sources:** [AppleInsider](https://appleinsider.com/articles/21/08/31/spotify-blends-creates-shared-playlists-mixing-the-musical-tastes-of-friends), [TechCrunch 10 people](https://techcrunch.com/2022/03/30/spotify-expands-its-music-matching-playlist-blend-to-work-with-up-to-10-people-including-select-artists), [Newsroom tag](https://newsroom.spotify.com/tag/taste-match-scores/)
- **WeTune: Medium**, without a server, through an **export/import "taste card"**: a QR code or share link carrying the top ~50 artists/tracks. The other phone computes overlap (Jaccard → "taste match %") and builds a playlist from shared plus each person's top songs plus radio. Live, auto-updating Blends need a backend.

### 2.9 Wrapped and Listening Stats
- **Wrapped:** a yearly story-style recap (top songs, artists, genres, minutes). Wrapped 2025 (Dec 3 2025) added "Listening Age", top 5 albums and a "Wrapped Party" to compete with friends.
- **Listening Stats (Nov 6 2025):** a weekly snapshot of top artists and songs (last 4 weeks), shareable to Instagram and WhatsApp. Free and Premium, 60+ markets.
- **Sources:** [Newsroom: Listening Stats](https://newsroom.spotify.com/2025-11-06/spotify-new-feature-listening-stats/), [9to5Google](https://9to5google.com/2025/11/06/spotify-listening-stats-rolling-out/), [Newsroom: Wrapped 2025](https://newsroom.spotify.com/?p=37865), [Tom's Guide](https://www.tomsguide.com/entertainment/music-streaming/spotify-wrapped-2025-has-landed-now-with-more-stats-more-fun-and-additional-social-features)
- **WeTune: Easy–Medium.** Log plays locally (count a play after 30 s or 50%), then aggregate per week, month and year. The share card is a rendered Compose bitmap. This is already in `todo.md`.

### 2.10 Other "Spotify picks for you" and DJ-adjacent features
| Feature | What it is | Availability | WeTune |
|---|---|---|---|
| **Radio / autoplay** | Endless similar songs after a song, album or playlist ends | All | **Done** (YouTube radio) |
| **Daily Mix / Discover Weekly / Release Radar** | Algorithmic playlists (genre clusters / weekly new-to-you / new releases from followed artists) | All | Easy–Medium: YouTube Music's own "Supermix"/"My Mix" home shelves through InnerTune; Release Radar from followed artists' latest releases |
| **Taste Profile** (beta) | View and edit what Spotify thinks you like; the edits shape the Home feed. NZ test Mar 2026, US Sep 23 2026, Premium, 18+ | [Newsroom](https://newsroom.spotify.com/2026-09-23/taste-profile-shape-home-feed/), [TechCrunch](https://techcrunch.com/2026/03/13/spotify-will-let-you-edit-your-taste-profile-to-control-your-recommendations/) | Medium: a local "never recommend this artist" / "more like this" list that filters radio |
| **Exclude from Taste Profile** | Keep a playlist (for example kids' music or sleep sounds) out of recommendations and Wrapped | All | Easy: a per-playlist flag that the play log ignores |
| **About the Song / SongDNA** (beta, Apr 2026) | Story cards about a track; a sample/cover/producer map powered by WhoSampled | Premium, selected markets | Hard (needs licensed data) |
| **DJ software integration** | Spotify catalogue inside rekordbox, Serato, djay (Sep 24 2025), rekordbox/djay mobile (Dec 2025), VirtualDJ, Cross DJ, edjing Mix (Sep 2026). 51 markets, Premium | [Newsroom](https://newsroom.spotify.com/2025-09-24/dj-software-integration-premium/) | Not applicable |
| **Music Quiz** (Oct 2026) | Seen only as a newsroom thumbnail **(unverified, not researched)** | n/a | n/a |

---

## 3. General DJ features, feature by feature

**Listening vs performance:** features marked 🎧 make sense in a listening app; ⚙️ are performance tools for DJs that would clutter a player.

### 3.1 Transitions

#### Crossfade 🎧
- **What:** the outgoing song fades out while the next fades in over N seconds.
- **Who:** Spotify (0–12 s), Apple Music (1–12 s; not with hi-res lossless), Poweramp, Symfonium, Metrolist, ArchiveTune, SimpMusic (up to 30 s), Mixxx Auto DJ ("Full Track" mode). **YouTube Music has no native crossfade** as of 2026 (third-party guides; no official statement found).
- **Visual:** [Apple: Song Transitions settings](https://cdsassets.apple.com/live/7WUAS350/images/apple-music/locale/en-gb/ios-27-iphone-17-pro-settings-apps-music-song-transitions.png) · [Apple: Up Next with Crossfade](https://cdsassets.apple.com/live/7WUAS350/images/apple-music/locale/en-gb/ios-27-iphone-17-pro-music-mini-player-up-next-crossfade.png)
- **Sources:** [Spotify support](https://support.spotify.com/us/article/tracks-transitions/), [Apple support 105067](https://support.apple.com/en-gb/105067), [Metrolist (GitHub)](https://github.com/metrolistgroup/metrolist), [SourceForge Metrolist features](https://sourceforge.net/projects/metrolist.mirror/)
- **WeTune: Easy–Medium.** Media3 has no built-in crossfade (the long-standing request is [ExoPlayer #3438](https://github.com/google/ExoPlayer/issues/3438); there is nothing on it in the Media3 release notes up to 1.12.0-alpha02). You need a second, temporary ExoPlayer plus volume ramps (see 5.1).

#### Smart / AI transitions ("AutoMix") 🎧
- **What:** the app picks the transition per song pair: trims silence, chooses the overlap point, and may time-stretch and beat-match.
- **Who:**
  - **Apple Music AutoMix** (iOS 26, 2025): "mixes one song into the next, just like a DJ", with time-stretching and beat-matching. It needs a subscription and only works on catalogue songs, not uploads or purchases. It may fall back to a plain transition for sequential albums or for "incompatible genres or tempos". iOS 27 (2026) reportedly adds more varied transitions and can loop or re-edit intros and outros **(reported, not Apple-confirmed)**.
  - **Spotify Automix:** selected playlists only.
  - **djay Automix AI:** detects intro/outro sections and adds transitions such as Dissolve, Riser and Echo.
  - **Mixxx Auto DJ:** intro/outro cues, silence detection at -60 dBFS, modes "Full Intro + Outro", "Fade At Outro Start", "Full Track" and "Skip Silence".
- **Visuals:** [Apple: Up Next with AutoMix](https://cdsassets.apple.com/live/7WUAS350/images/apple-music/locale/en-gb/ios-27-iphone-17-pro-music-mini-player-up-next-automix.png) · [9to5Mac AutoMix](https://9to5mac.com/wp-content/uploads/sites/6/2025/06/automix-ios-26.jpg?quality=82&strip=all&w=1600) · [Mixxx: AutoDJ aligning ends](https://mixxx.org/images/news/intro-outro/autodj-align-ends.png) · [Mixxx: intro/outro markers](https://mixxx.org/images/news/intro-outro/intro-outro-full.png)
- **Sources:** [Apple support](https://support.apple.com/en-gb/105067), [9to5Mac](https://9to5mac.com/2025/06/16/automix-apple-music-ios-26/), [RouteNote: iOS 27](https://routenote.com/radar/?p=2789), [heise: why AutoMix blends oddly](https://heise.de/-11139247), [Mixxx 2.3 intro/outro](https://mixxx.org/news/2020-07-09-intro-outro-sections), [MusicTech: djay Automix](https://musictech.net/news/gear/algoriddim-free-dj-software-djay-pro-ai-automix-and-neural-mix/)
- **WeTune:**
  - **Medium for "smart crossfade without beat-matching":** trim silence, start the fade where the outro energy drops, use a filter-sweep preset. It works on the cached file, so it applies only after a song has played once, or by analysing the next song's bytes as they buffer.
  - **Hard for true beat-matched AutoMix:** needs accurate BPM and beat phase plus a tempo ramp. Mixxx notes that a 0.02 BPM error already drifts on long overlaps.

#### "DJ-style" filter / echo transitions 🎧
- **What:** during the crossfade, a high-pass filter sweeps up on the outgoing song (bass fades first) or an echo/delay tail plays, which sounds like a DJ instead of a volume dip.
- **Who:** SimpMusic "DJ-styled crossfade" (Android YouTube Music client, Feb 28 2026, "low-cut / high-pass-style movement", no BPM matching), djay Automix (Echo, Riser, Dissolve), Spotify Mix presets (Fade, Rise, Blend with EQ/effect curves).
- **Sources:** [SimpMusic blog](https://www.simpmusic.org/blogs/en/crossfade-beta), [Spotify Mix (TechCrunch)](https://techcrunch.com/2025/08/19/spotifys-latest-feature-lets-you-add-your-own-transitions-to-playlists)
- **WeTune: Medium.** A custom Media3 `AudioProcessor` with a biquad high-pass whose cutoff ramps from about 20 Hz to about 1 kHz over the fade, installed on the outgoing (helper) player only. An echo is a feedback delay line in the same processor.

#### Gapless 🎧
- Already in WeTune through ExoPlayer's playlist handling. Crossfade must be **skipped for consecutive tracks of the same album**, as Apple and Symfonium do, so live albums and DJ mixes stay seamless.

### 3.2 Tempo and key

#### BPM detection / beat grid 🎧 (as info) ⚙️ (as a grid)
- **What:** estimate tempo (and beat positions) from the audio and show it as a tag; DJs use it to sync decks.
- **Who:** Mixxx (Queen Mary-based beat detector, "Fast Analysis" over the first minute, ×0.5/×2 fixes), rekordbox, Serato, djay, VirtualDJ, Spotify Mix editor (shows BPM).
- **Sources:** [Mixxx manual: Beat Detection](https://manual.mixxx.org/2.3/ca/chapters/preferences/beat_detection), [dev.to: on-device key & tempo DSP](https://dev.to/conkormedia/detecting-musical-key-and-tempo-on-device-the-dsp-behind-beatscope-31me), [TarsosDSP](https://github.com/st-h/TarsosDSP) (onsets, BeatRoot-based beat tracking; GPL-3.0)
- **WeTune: Medium.** Needs on-device analysis of decoded PCM: `MediaExtractor` + `MediaCodec` on the cached file, mono at 11 kHz, onset-strength envelope, autocorrelation in the 70–180 BPM range. That is a few hundred lines of Kotlin. YouTube streams can't be pre-analysed without fetching the audio, so analyse after the first play (from the cache) or while the next song pre-buffers.

#### Musical key detection + harmonic mixing (Camelot wheel) 🎧 (ordering) ⚙️ (manual)
- **What:** detect the key (for example A minor = 8A on the Camelot wheel) and play neighbouring keys (±1 or the same number) one after another for smooth transitions.
- **Who:** Mixxx (KeyFinder default since 2.3; Queen Mary as the alternative), rekordbox, Serato, djay, Mixed In Key, Spotify Mix editor (shows key).
- **Sources:** [Mixxx: KeyFinder support](https://mixxx.org/news/2021-04-08-new-in-2-3-keyfinder), [libkeyfinder (GPL-3)](https://github.com/mixxxdj/libkeyfinder), [Mixxx manual: key detection](https://manual.mixxx.org/2.5/ru/chapters/preferences/key_detection)
- **WeTune: Medium–Hard.** Chromagram + Krumhansl-Schmuckler key-profile correlation is simple to write yourself. libkeyfinder is more accurate but GPL-3 (it would force WeTune's source open) and needs NDK/JNI. Accuracy on pop songs is typically 60–80% **(general knowledge, unverified figure)**, so show the key as a hint, not a hard rule.

#### Tempo sync / beatmatching ⚙️ (manual) 🎧 (inside AutoMix only)
- **What:** stretch one song's tempo to match the other's during a transition.
- **Who:** every DJ app; Apple AutoMix; Spotify Automix.
- **WeTune: Hard.** `setPlaybackParameters(PlaybackParameters(speed, pitch = 1f))` on each player does Sonic time-stretching, but each change re-configures the audio sink. Smooth ramping and phase alignment are fiddly. Only worth it after BPM detection proves reliable.

#### Pitch / tempo control ("nightcore", "slowed + reverb") 🎧
- **Who:** Poweramp (tempo, reverb), Metrolist (tempo and pitch), djay/rekordbox (key lock, pitch fader).
- **Sources:** [Media3 SonicAudioProcessor](https://developer.android.com/reference/androidx/media3/common/audio/SonicAudioProcessor), [ExoPlayer: variable speed](https://medium.com/google-exoplayer/variable-speed-playback-with-exoplayer-e6e6a71e0343)
- **WeTune: Easy.** One call on the existing player, plus a sheet with speed and pitch sliders and presets (0.8× slowed, 1.25× nightcore). A "slowed + reverb" preset additionally needs a reverb `AudioProcessor`, rated Medium.

### 3.3 Effects and sound

#### Equalizer and filter knob 🎧 (EQ) ⚙️ (filter as performance)
- **Who:** Poweramp, Metrolist, Spotify (built-in EQ), djay/rekordbox (3-band EQ + filter per deck).
- **WeTune: Medium.**
  - **Option A:** platform `android.media.audiofx.Equalizer` on `player.audioSessionId`. Few bands, depends on the device, and some OEMs break it.
  - **Option B:** a custom `AudioProcessor` with peaking biquads (consistent everywhere, any number of bands, supports AutoEq presets). Option B can share code with the transition filter.

#### Effects (echo, reverb, flanger, gater, brake/spin-back) ⚙️
- **Who:** djay, rekordbox, Serato, VirtualDJ, edjing.
- **WeTune:** Medium per effect as an `AudioProcessor`. Only echo (for transitions) and reverb (for "slowed + reverb") fit a listening app.

#### Stems / vocal separation (karaoke mode) 🎧 (karaoke) ⚙️ (live stems)
- **What:** split a song into vocals, drums, bass and other in real time, for example to mute vocals.
- **Who:** djay Neural Mix (ARM64 Android), Serato Stems, rekordbox, VirtualDJ Stems 2.0 (5 stems), Apple Music Sing (karaoke vocal slider).
- **Sources:** [Algoriddim Neural Mix](https://www.algoriddim.com/neural-mix), [Digital DJ Tips: stems compared 2025](https://www.digitaldjtips.com/best-sounding-stems-2025/), [demucs-android (reference app)](https://github.com/sevagh/demucs-android), [MDX-Net TFLite models](https://huggingface.co/skytrix/kirexa-mdx-tflite)
- **Visual:** [Neural Mix hero](https://cdn.algoriddim.com/assets/home/neural-mix-hero-e98c600a.webp)
- **WeTune: Hard.**
  - Models are 30–100+ MB and real time on mid-range phones is doubtful, so offline per-song processing is more realistic.
  - A cheap "karaoke" alternative is mid/side cancellation (L−R), which removes centre-panned vocals. It sounds rough but is **Easy**, and would pair with the existing synced lyrics/Karaoke view.
  - Media3 **1.12.0-alpha** release notes mention a `SoundSeparationAudioProcessor` / `StudioSoundAudioProcessor` built on a Google Play Services "StudioSound" module. What it separates (voice vs background) and which devices it needs is **unverified**. Worth watching.

#### Loops, hot cues, scratching, jog wheels, mix recording ⚙️
- **Who:** all DJ apps (djay, rekordbox, Serato, VirtualDJ, Mixxx, edjing Mix, Cross DJ).
- **WeTune: Not recommended.** These are performance tools. A light version is an "A–B repeat" loop for practising an instrument, rated Easy (seek back at B). Mix recording of streamed content is legally risky.

### 3.4 Smart selection

| Feature | Who | WeTune |
|---|---|---|
| **Radio / autoplay** | Everyone | **Done** |
| **Prompt radio / AI playlists**: type what you want and get a station | YouTube Music **Ask Music** (Sep 2024, Premium, Android first; refine by follow-up prompts since Mar 2025) [9to5Google](https://9to5google.com/2024/09/10/youtube-music-ask-music), [refine](https://9to5google.com/2025/03/26/youtube-music-ai-radios-tune/); Amazon **Maestro** (Apr 2024, US beta) [About Amazon](https://www.aboutamazon.com/md/news/entertainment/amazon-music-maestro-ai-playlist-generator); **Alexa+ in Amazon Music** (Nov 2025, Early Access, "AI DJ") [TechRadar](https://techradar.com/audio/audio-streaming/alexa-is-coming-to-amazon-music-and-its-an-ai-dj-playlist-curator-and-music-encyclopedia-all-rolled-into-one); Deezer **Playlist with AI** (Jul 2024) [TechCrunch](https://techcrunch.com/2024/07/15/deezer-chases-spotify-and-amazon-music-with-its-own-ai-playlist-generator); Spotify AI/Prompted Playlist | **Medium** with search plus radio heuristics. **Needs a server or a licensed LLM** for real prompts |
| **Mood-steered radio** | Deezer **Flow Moods** (and Flow Tuner) [TechRadar](https://techradar.com/news/deezer-one-ups-spotify-with-playlist-mixes-that-match-your-mood); YouTube Music radio "tune" chips (Familiar / Discover, popularity, mood) | **Easy–Medium:** YouTube radio already accepts tuning params in InnerTune **(unverified in WeTune's client)** |
| **Smart Shuffle** | Spotify | **Easy** (see 2.2) |
| **Harmonic / energy ordering ("Auto-DJ sort")** | Mixed In Key, djay Automix browser, rekordbox "Track Suggestion" **(not separately verified)** | **Medium** once BPM/key exist |
| **Time-of-day mixes** | Spotify daylist | **Medium** (see 2.6) |
| **Taste controls** | Spotify Taste Profile, Exclude from Taste | **Easy–Medium** |

### 3.5 Social
| Feature | Who | WeTune |
|---|---|---|
| **Group session / shared queue** | Spotify Jam (32 people); Apple Music SharePlay (iOS) **(not separately verified)** | **Hard** (needs a relay; Nearby Connections for same-room) |
| **Taste blend playlist** | Spotify Blend | **Medium** (offline "taste card" exchange) |
| **Collaborative mixed playlists** | Spotify Mix collaborators | Hard |

### 3.6 Stats
| Feature | Who | WeTune |
|---|---|---|
| **Yearly recap** | Spotify Wrapped, Apple Music Replay, YouTube Music Recap, Deezer "My Deezer Year" **(the last three not separately verified this session)** | **Easy–Medium** with a local play log |
| **Weekly stats** | Spotify Listening Stats | **Easy** with the same log |
| **Per-song info: BPM, key, loudness** | Spotify Mix editor, DJ apps | **Easy** to show in the existing "Pipeline" sheet once analysed; loudness is already shown there |

---

## 4. Comparison table

✅ = has it, ◐ = partial or limited, ✗ = no, ? = not verified. "Others" = Poweramp, Metrolist/SimpMusic (Android YouTube Music clients), Mixxx, Deezer, Amazon Music.

| Feature | Spotify | Apple Music | YouTube Music | djay | Others | WeTune today | WeTune effort |
|---|---|---|---|---|---|---|---|
| Gapless | ✅ | ✅ | ✅ | n/a | ✅ Poweramp | ✅ | done |
| Crossfade | ✅ 0–12 s | ✅ 1–12 s | ✗ | ✅ | ✅ Poweramp, Metrolist, SimpMusic, Mixxx | ✗ | Easy–Med |
| DJ-style filter transitions | ✅ (Mix presets) | ◐ (inside AutoMix) | ✗ | ✅ Echo/Riser/Dissolve | ✅ SimpMusic | ✗ | Medium |
| Auto beat-matched mix | ◐ Automix (select playlists) | ✅ AutoMix (catalogue) | ✗ | ✅ Automix AI | ◐ Mixxx Auto DJ (no beat sync) | ✗ | Hard |
| Silence trim / intro-outro detect | ? | ✅ (part of AutoMix) | ✗ | ✅ | ✅ Mixxx | ✗ | Medium |
| BPM / key shown | ✅ (Mix editor) | ✗ | ✗ | ✅ | ✅ Mixxx | ✗ | Medium |
| Tempo / pitch | ✗ (music) | ✗ | ✗ | ✅ | ✅ Poweramp, Metrolist | ◐ (speed read-out) | Easy |
| Equalizer | ✅ | ✅ (presets) | ✗ (Android system only) | ✅ | ✅ Poweramp, Metrolist | ✗ | Medium |
| Stems / karaoke | ✗ | ✅ Apple Music Sing | ✗ | ✅ Neural Mix | ✅ Serato, VirtualDJ, rekordbox | ✗ (synced lyrics only) | Hard (Easy for L−R) |
| AI voice DJ | ✅ DJ | ✗ | ✗ | ✗ | ◐ Alexa+ (Amazon) | ✗ | Not feasible |
| Prompt playlists / radio | ✅ | ? | ✅ Ask Music | ✗ | ✅ Amazon Maestro, Deezer | ✗ | Medium (heuristic) |
| Smart Shuffle | ✅ | ✗ | ✗ | ✗ | ✗ | ✗ | Easy |
| Radio / autoplay | ✅ | ✅ | ✅ | ✗ | ✅ Deezer Flow | ✅ | done |
| Time-of-day playlist | ✅ daylist | ✗ | ✗ | ✗ | ◐ Deezer Flow moods | ✗ | Medium |
| Group listening | ✅ Jam | ✅ SharePlay ? | ✗ | ✗ | ✗ | ✗ | Hard |
| Taste blend | ✅ Blend | ✗ | ✗ | ✗ | ✗ | ✗ | Medium |
| Weekly / yearly stats | ✅ Listening Stats / Wrapped | ✅ Replay | ✅ Recap ? | ✗ | ✗ | ✗ | Easy–Med |

---

## 5. Implementation notes for the top picks

### 5.1 Crossfade (and the base for all transitions)
**Approach (recommended): a "tail player".** The `MediaSession` keeps owning the one main `ExoPlayer`, so the notification, lock screen, queue and Android Auto stay untouched. When the main player reaches `duration − fadeMs`:

1. Build a lightweight second `ExoPlayer` with the **same `MediaSourceFactory` (cache-backed)**. Give it `setAudioAttributes(attrs, handleAudioFocus = false)` so it doesn't steal audio focus from the main player.
2. Load the **current** item into it at `currentPosition` and start it at the current gain. The tail is almost always already in the disk cache, so it starts instantly.
3. Move the main player to the next item with `seekToNextMediaItem()` and ramp its volume from 0 to the normalisation gain. Ramp the tail player from its gain to 0 with an equal-power curve (`cos`/`sin`).
4. Release the tail player when its fade ends, or immediately on pause, seek, skip or stop.

**Details:**
- Ramps: drive `player.volume` from a coroutine every ~20 ms, or use `VolumeShaper` (API 26+). ExoPlayer doesn't expose its `AudioTrack`, so a coroutine on `volume` is the practical way.
- Multiply by the existing `normalize()` gain. Today `normalize()` sets `player.volume` directly, so the fade factor and the normalisation gain must be combined in one place.
- Don't crossfade when: the next item is the next track of the same album (gapless), the user skipped manually (do a quick 300 ms fade or none), the next song isn't ready (fall back to gapless), or a sleep timer is about to stop playback.
- Media3 1.11 adds `enablePerStreamMediaProgression()` and playlist context in `AudioProcessor.StreamMetadata`. Upgrading from 1.5.1 helps processors know which song they are on.

**Risks:**
- Two decoders running at the same time use more battery during each fade.
- Some Bluetooth stacks or OEMs glitch when a second `AudioTrack` opens.
- Android Auto, Cast and remote controllers only see the main player. That is fine because the tail player is invisible.
- A known Metrolist bug (crossfade timing at non-1× speed): divide the fade window by the playback speed.

**Prior art to read:** Metrolist (GitHub, crossfade with tempo/pitch), SimpMusic (DJ-styled crossfade), Symfonium's forum thread suggesting `VolumeShaper`.

### 5.2 DJ-style filter transition and EQ
- Write one `BiquadAudioProcessor : BaseAudioProcessor` that handles 16-bit/float PCM and has per-channel biquad state. Coefficients use the RBJ cookbook.
- Install it through a custom `DefaultRenderersFactory.buildAudioSink` → `DefaultAudioSink.Builder().setAudioProcessors(arrayOf(...))`.
- **Main player:** EQ bands (user settings).
- **Tail player:** a high-pass whose cutoff is animated from the fade progress (a `@Volatile` field read per buffer), with optional echo.
- Risk: keep the processing allocation-free in the audio thread, and smooth coefficient changes to avoid zipper noise.

### 5.3 Smart Shuffle
- In `Playback`: a `shuffleMode` of Off / On / Smart. In Smart mode, after every 2–3 user tracks, insert one result from `YouTube.radio(seed)` that isn't already in the playlist, tagged `suggested = true`. Show a sparkle and "Add to playlist / Not this" actions. Skipping a suggestion lowers that artist in later picks.
- Risk: only YouTube-sourced seeds get radio results. Local/Subsonic/JioSaavn seeds need `SourceMatch` to a YouTube ID first, which already exists for version matching.

### 5.4 Local play log → stats, daylist, blend
- Store a small table (Room or a JSON-lines file, whichever matches `Library`'s style): `trackId, source, artist, title, startedAt, listenedMs, skipped`. Write it from the existing `onMediaItemTransition` / `onIsPlayingChanged` hooks.
- **Stats:** top tracks, artists and hours by week, month and year, plus "listening age" fun facts. The share card is Compose → `Bitmap`.
- **Daylist:** bucket plays by weekday × {early morning, morning, afternoon, evening, late night}, take the top artists in the current bucket, seed radio, and refresh at bucket changes.
- Privacy: everything stays on the phone, which is a selling point against Spotify.

### 5.5 BPM and key analysis
- **When:** a background `WorkManager` job (charging/idle, or right after a song is fully cached). Read through `App.cache` with a `CacheDataSource`, or use local files directly.
- **Decode:** `MediaExtractor` + `MediaCodec` (Opus/AAC/MP3/FLAC are all supported from API 26 for Opus), downmix to mono and decimate to ~11 kHz. Analyse 30–60 s from the middle of the song, as Mixxx's "fast analysis" does.
- **Tempo:** spectral-flux onset envelope → autocorrelation → pick a peak in 70–180 BPM, with a prior toward 120. Store BPM and confidence.
- **Key:** STFT → 12-bin chroma → correlate with Krumhansl major/minor profiles → map to Camelot.
- **Intro/outro:** first and last frame above -60 dBFS (silence), plus an RMS energy envelope to find where the outro drops. This feeds smart crossfade timing in 3.1.
- **Licences:** TarsosDSP, libkeyfinder, aubio and Queen Mary DSP are GPL; Essentia is AGPL. WeTune has no licence file. Either write the small DSP yourself (a few hundred lines) or accept GPL for the whole app.
- **Risks:** half/double-tempo errors (offer ×2/÷2), weak accuracy on rubato or ambient music, and battery use. Store results keyed by `source:ref` so they survive re-resolves.

### 5.6 Beat-matched AutoMix (later)
Needs 5.1 + 5.5 with beat phase, not just BPM.

1. Only mix when |ΔBPM| ≤ ~6% and the keys are compatible.
2. Start the tail player time-stretched to the incoming tempo (`PlaybackParameters(speed = in/out, pitch = 1)`, key lock through Sonic).
3. Seek the incoming track so its first downbeat lands on an outgoing downbeat.
4. Run the crossfade + high-pass.
5. Ramp the speed back to 1.0 over a few bars after the fade.

Each `setPlaybackParameters` call is applied at a buffer boundary, so the ramp has to be stepped. Expect weeks of tuning. Fall back to the plain smart crossfade whenever confidence is low, which is exactly what Apple does.

### 5.7 What not to build
- **AI voice DJ:** needs an LLM plus a natural TTS voice, i.e. a server and licences.
- **Real-time stems:** model size and CPU cost, plus quality on compressed streams.
- **Jam:** needs a backend.
- **Performance DJ UI** (decks, jog wheels, hot cues, mix recording): a different product category, already well covered by djay and edjing on Android.

---

## 6. Sources

**Spotify**
- https://newsroom.spotify.com/2023-02-22/spotify-debuts-a-new-ai-dj-right-in-your-pocket/
- https://newsroom.spotify.com/2025-05-13/dj-voice-requests/
- https://newsroom.spotify.com/2025-10-15/dj-spanish-text-requests-update/
- https://newsroom.spotify.com/2026-04-20/deeper-music-features-about-the-song-dj-songdna/
- https://newsroom.spotify.com/2026-05-07/dj-expansion-4-new-languages/
- https://newsroom.spotify.com/2026-11-10/dj-new-voices-controls/
- https://newsroom.spotify.com/tag/dj
- https://newsroom.spotify.com/2025-09-24/dj-software-integration-premium/
- https://support.spotify.com/us/article/tracks-transitions/
- https://support.spotify.com/us/using_spotify/features/shuffle/
- https://newsroom.spotify.com/tag/enhance
- https://community.spotify.com/t5/Your-Library/Introducing-Smart-Shuffle/td-p/5516566/page/54
- https://www.engadget.com/spotify-enhance-playlist-song-suggestions-180749023.html
- https://techcrunch.com/2025/08/19/spotifys-latest-feature-lets-you-add-your-own-transitions-to-playlists
- https://www.engadget.com/entertainment/music/spotify-now-lets-you-create-seamless-transitions-between-songs-on-your-custom-playlists-100051537.html
- https://www.tomsguide.com/entertainment/music-streaming/spotifys-new-mixing-feature-lets-you-dj-your-own-playlists-heres-how-it-works
- https://newsroom.spotify.com/tag/fade/
- https://techcrunch.com/2023/09/26/spotify-launches-jam-a-real-time-collaborative-playlist-controlled-by-up-to-32-people
- https://newsroom.spotify.com/tag/spotify-jam
- https://9to5google.com/2023/09/12/spotify-daylist-customized-playlist/
- https://techcrunch.com/2024/09/04/spotify-launches-its-evolving-playlist-daylist-globally
- https://newsroom.spotify.com/?p=33837
- https://newsroom.spotify.com/?p=30737
- https://techcrunch.com/2026/02/23/spotify-ai-prompted-playlists-uk-markets
- https://ppc.land/spotifys-prompted-playlist-adds-podcasts-for-premium-users-in-seven-markets/
- https://appleinsider.com/articles/21/08/31/spotify-blends-creates-shared-playlists-mixing-the-musical-tastes-of-friends
- https://techcrunch.com/2022/03/30/spotify-expands-its-music-matching-playlist-blend-to-work-with-up-to-10-people-including-select-artists
- https://newsroom.spotify.com/tag/taste-match-scores/
- https://newsroom.spotify.com/2025-11-06/spotify-new-feature-listening-stats/
- https://9to5google.com/2025/11/06/spotify-listening-stats-rolling-out/
- https://newsroom.spotify.com/?p=37865
- https://www.tomsguide.com/entertainment/music-streaming/spotify-wrapped-2025-has-landed-now-with-more-stats-more-fun-and-additional-social-features
- https://newsroom.spotify.com/2026-09-23/taste-profile-shape-home-feed/
- https://techcrunch.com/2026/03/13/spotify-will-let-you-edit-your-taste-profile-to-control-your-recommendations/

**Apple Music**
- https://support.apple.com/en-gb/105067
- https://9to5mac.com/2025/06/16/automix-apple-music-ios-26/
- https://routenote.com/radar/?p=2789
- https://heise.de/-11139247

**YouTube Music, Amazon, Deezer**
- https://9to5google.com/2024/09/10/youtube-music-ask-music
- https://9to5google.com/2025/03/26/youtube-music-ai-radios-tune/
- https://www.audfree.com/youtube-music/crossfade-youtube-music.html (third-party: no native crossfade)
- https://www.aboutamazon.com/md/news/entertainment/amazon-music-maestro-ai-playlist-generator
- https://techradar.com/audio/audio-streaming/alexa-is-coming-to-amazon-music-and-its-an-ai-dj-playlist-curator-and-music-encyclopedia-all-rolled-into-one
- https://techcrunch.com/2024/07/15/deezer-chases-spotify-and-amazon-music-with-its-own-ai-playlist-generator
- https://techradar.com/news/deezer-one-ups-spotify-with-playlist-mixes-that-match-your-mood

**DJ software and Android apps**
- https://www.algoriddim.com/ · https://www.algoriddim.com/neural-mix · https://www.algoriddim.com/djay-android
- https://musictech.net/news/gear/algoriddim-free-dj-software-djay-pro-ai-automix-and-neural-mix/
- https://mixxx.org/news/2020-07-09-intro-outro-sections
- https://mixxx.org/news/2021-04-08-new-in-2-3-keyfinder
- https://manual.mixxx.org/2.3/ca/chapters/preferences/beat_detection
- https://manual.mixxx.org/2.5/ru/chapters/preferences/key_detection
- https://github.com/mixxxdj/libkeyfinder
- https://www.digitaldjtips.com/best-sounding-stems-2025/
- https://www.simpmusic.org/blogs/en/crossfade-beta
- https://github.com/metrolistgroup/metrolist · https://sourceforge.net/projects/metrolist.mirror/
- https://www.apkmirror.com/?p=595778 (Poweramp v3 changelog: crossfade, gapless, tempo, reverb)
- https://support.symfonium.app/t/crossfade-for-tracks/1206

**Android / Media3 / DSP**
- https://developer.android.com/jetpack/androidx/releases/media3
- https://github.com/google/ExoPlayer/issues/3438
- https://developer.android.com/reference/androidx/media3/common/audio/SonicAudioProcessor
- https://medium.com/google-exoplayer/variable-speed-playback-with-exoplayer-e6e6a71e0343
- https://developer.android.com/reference/android/media/VolumeShaper
- https://github.com/st-h/TarsosDSP · https://0110.be/releases/TarsosDSP/TarsosDSP-2.0/TarsosDSP-2.0-Readme.html
- https://dev.to/conkormedia/detecting-musical-key-and-tempo-on-device-the-dsp-behind-beatscope-31me
- https://github.com/sevagh/demucs-android
- https://huggingface.co/skytrix/kirexa-mdx-tflite
