package com.wetune

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** SharedPreferences exposed as Compose state, so the UI updates when a setting changes. */
object Settings {
    private val prefs by lazy { App.ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    // shortcut: cookie and Subsonic password sit in app-private prefs (unencrypted); move to Keystore-backed storage if needed.
    var cookie by Str("cookie", "")
    var subsonicUrl by Str("subsonic_url", "")
    var subsonicUser by Str("subsonic_user", "")
    var subsonicPass by Str("subsonic_pass", "")
    var theme by Str("theme", "system") // system | light | dark | black
    var matchAccent by Bool("match_accent", true)
    var lyricsRoman by Bool("lyrics_roman", false) // show a Latin-letter reading under each lyric line
    var romanInline by Bool("roman_inline", false) // the one-line lyric above the seek bar in Latin letters
    var lyricsWords by Bool("lyrics_words", true) // prefer word-by-word lyrics and light them up letter by letter
    var inlineWords by Bool("inline_words", true) // the one-line lyric above the seek bar fills letter by letter
    var playFrom by Str("play_from", "yt") // where YouTube songs are streamed from: yt | saavn (320 kbps when matched)
    var cacheV2 by Bool("cache_v2", false) // audio cache re-keyed by stream (see PlaybackService)
    var radioSeed by Str("radio_seed", "") // YouTube id the current radio started from, so auto-added songs stay related
    var lastFrom by Str("last_from", "") // what the current queue was started from, kind + NUL + name
    var showVolume by Bool("show_volume", true) // volume slider in Now Playing
    var npStyle by Str("np_style", "bleed") // Now Playing look: bleed (Bleeding edge) | classic | strips | disc
    var npFill by Str("np_fill", "stretch") // how the player fills around the cover: stretch | mirror | blur
    var inlineStyle by Str("inline_style", "line") // lyric above the seek bar: line (one line) | stack (previous · current · next)
    // the full lyrics panel
    var lyricsMotion by Str("lyrics_motion", "glide") // how it moves between lines: glide | fade | instant
    var lyricsAlign by Str("lyrics_align", "start") // start | center | end
    var lyricsSize by IntPref("lyrics_size", 28) // sp
    var lyricsLeading by IntPref("lyrics_leading", 120) // line height, % of the font size
    var wifiHigh by Bool("wifi_high", true)
    var mobileHigh by Bool("mobile_high", false)
    // the best quality to reach per network: data (≈70 kbps) | high (best YouTube, ≈160 kbps) | max (JioSaavn 320 kbps when found)
    // | lossless (FLAC from the user's add-ons when found).
    // Defaults carry over the older "Play songs from" + high-quality switches.
    var maxWifi by Str("max_wifi", if (prefs.getString("play_from", "yt") == "saavn") "max" else if (prefs.getBoolean("wifi_high", true)) "high" else "data")
    var maxMobile by Str("max_mobile", if (prefs.getBoolean("mobile_high", false)) (if (prefs.getString("play_from", "yt") == "saavn") "max" else "high") else "data")
    var saavnOn by Bool("saavn_on", true) // JioSaavn 320 kbps as a step on the quality ladder (Settings → Sources)
    var saavnRank by IntPref("saavn_rank", 99) // JioSaavn's place among the add-ons in Settings → Sources (0 = first; past the end = after all of them)
    var normalize by Bool("normalize", true)
    var autoplay by Bool("autoplay", true)
    var autoRotate by Bool("auto_rotate", false)
    var recentSearches by Str("recent_searches", "") // newline-separated, newest first

    fun addRecentSearch(q: String) {
        recentSearches = (listOf(q) + recentSearches.lines().filter { it.isNotBlank() && !it.equals(q, ignoreCase = true) }).take(10).joinToString("\n")
    }

    private class Str(val key: String, val default: String) : ReadWriteProperty<Any?, String> {
        private val state by lazy { mutableStateOf(prefs.getString(key, default) ?: default) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            state.value = value
            prefs.edit().putString(key, value).apply()
        }
    }

    private class IntPref(val key: String, val default: Int) : ReadWriteProperty<Any?, Int> {
        private val state by lazy { mutableStateOf(prefs.getInt(key, default)) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state.value = value
            prefs.edit().putInt(key, value).apply()
        }
    }

    private class Bool(val key: String, val default: Boolean) : ReadWriteProperty<Any?, Boolean> {
        private val state by lazy { mutableStateOf(prefs.getBoolean(key, default)) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }
}
