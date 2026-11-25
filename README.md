<p align="center"><img src="docs/logo.svg" width="120" alt="WeTune logo"></p>

# WeTune

A music player for Android with true two-way YouTube Music sync, synced lyrics, and a Now Playing screen that changes shape.

<p align="center"><a href="https://github.com/thewecas-dev/WeTune/releases/latest">Download</a> · <a href="https://github.com/thewecas-dev/WeTune">Star on GitHub</a> · <a href="https://www.instagram.com/thewecas/">Instagram @thewecas</a></p>

## Features

### Listening and sync
- Sign in with Google inside the app for two-way YouTube Music library sync: likes, playlists and library stay in step with YouTube Music
- Home feed, Search and Library (list or grid)
- Play history sent to YouTube Music for better recommendations (can be turned off)
- Autoplay that keeps the music going with similar songs or more by the same artist
- Downloads for offline listening
- Back up and restore your library as a file

### Now Playing
- One player that changes shape: the mini player, Now Playing and the immersive bar are a single surface, with a drag that follows your finger
- Immersive Canvas: the song's Canvas full screen, as a floating bar or with the title over it, with optional lyrics; it can turn on by itself for songs that have one
- Animated covers from Spotify Canvas (sign in to Spotify) and Apple Music motion artwork, with a compact Cover / Canvas switch
- Inline lyrics above the seek bar and full-screen lyrics, with word-by-word highlighting and romanization
- Lyrics from BetterLyrics, LRCLIB, lrc.red, KuGou, NetEase and YouTube Music, matched automatically or in your own order
- Audio panel with output switching

### Queue and song menu
- Swipe a song right to add it to the queue, left to play it next
- In the queue: swipe to play next or remove, drag to reorder, long-press for the song menu
- Song menu with Play next, Like, Playlist, Share and Song info; choose its top buttons and reorder the rest in Settings

### Sources
- YouTube Music
- Songs on your phone
- JioSaavn (320 kbps step on the quality ladder)
- Subsonic / Navidrome servers
- Lossless add-ons: paste the link of a compatible add-on server (WeTune ships with none)
- Separate quality limits for Wi-Fi and mobile data

### Look and feel
- Two Now Playing styles: Clean (Apple-like) and Expressive (Material)
- Frosted glass surfaces with adjustable tint and blur
- Nine backgrounds: Colour flow, Gradient, Blurred art, Full-width cover, Halo, Aurora, Film, Spotlight, Sweep
- Eight cover styles: Plain, Squircle, Glow, Tilt, Polaroid, Stack, Disc, Sleeve and record, with adjustable size, shape and corners
- Light, dark or system theme

### Privacy
See [Privacy](#privacy) below.

## Download

Get the APK from the [latest release](https://github.com/thewecas-dev/WeTune/releases/latest). Older versions are on the [releases page](https://github.com/thewecas-dev/WeTune/releases).

- Requires Android 8.0 (API 26) or newer.
- Updates install over an older version signed with the same key, and your data is kept.

## Build from source

You need JDK 17 or newer (JDK 21 works) and the Android SDK (API 35).

```sh
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.

Release builds are signed with a keystore that is not in the repository. Add two entries to `local.properties` (which is not committed):

```properties
release.store=<path to your keystore>
release.password=<keystore and key password>
```

The key alias is `wetune`. Then run `./gradlew assembleRelease`.

## Privacy

- No analytics, crash reporting or tracking SDKs.
- Your Google sign-in cookie, Spotify sign-in cookie and Subsonic password stay on the device, in the app's private storage, and are each only sent to their own service. They are left out of settings backups.
- WeTune talks directly to the services you use: YouTube Music, JioSaavn, your Subsonic server or add-ons, the lyrics providers listed above, Spotify and Apple Music for animated covers. For Spotify Canvas it also reads a public GitHub gist that holds Spotify's current time-code secret.
- Reporting plays to YouTube Music history can be turned off in Settings.

## Tech stack

Kotlin, Jetpack Compose, Media3 (ExoPlayer and MediaSession), OkHttp, Coil 3, Haze, NewPipe Extractor.

## Credits

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [AndroidX Media3](https://github.com/androidx/media)
- [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)
- [OkHttp](https://github.com/square/okhttp)
- [Coil](https://github.com/coil-kt/coil)
- [Haze](https://github.com/chrisbanes/haze)
- Lyrics: [BetterLyrics](https://lyrics-api.boidu.dev), [LRCLIB](https://lrclib.net), [lrc.red](https://lrc.red), KuGou, NetEase Cloud Music, YouTube Music

## Disclaimer

WeTune is an independent project. It is not affiliated with, endorsed by or sponsored by YouTube, Google, Spotify, Apple, JioSaavn or any lyrics provider. All trademarks belong to their owners.

## Links

- [Releases](https://github.com/thewecas-dev/WeTune/releases)
- Instagram: [@thewecas](https://www.instagram.com/thewecas/)
