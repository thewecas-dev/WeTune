package com.wetune

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** SharedPreferences exposed as Compose state, so the UI updates when a setting changes. */
object Settings {
    private val prefs by lazy { App.ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    // every setting by key (filled as the delegates below are created), so a settings file can be applied
    private val all = linkedMapOf<String, Pref>()

    // shortcut: cookie and Subsonic password sit in app-private prefs (unencrypted); move to Keystore-backed storage if needed.
    var cookie by Str("cookie", "")
    var subsonicUrl by Str("subsonic_url", "")
    var subsonicUser by Str("subsonic_user", "")
    var subsonicPass by Str("subsonic_pass", "")
    var theme by Str("theme", "system") // system | light | dark | black
    var matchAccent by Bool("match_accent", true)
    var accentHue by IntPref("accent_hue", -1) // fixed accent hue (0–359) when not matching the song; -1 = WeTune's own
    var textScale by IntPref("text_scale", 100) // app text size, % of the system font size
    var startTab by Str("start_tab", "home") // the tab WeTune opens on: home | library
    var lyricsRoman by Bool("lyrics_roman", false) // show a Latin-letter reading under each lyric line
    var romanInline by Bool("roman_inline", false) // the one-line lyric above the seek bar in Latin letters
    var lyricsWords by Bool("lyrics_words", true) // prefer word-by-word lyrics and light them up letter by letter
    var inlineWords by Bool("inline_words", true) // the one-line lyric above the seek bar fills letter by letter
    var cacheV2 by Bool("cache_v2", false) // audio cache re-keyed by stream (see PlaybackService)
    var cacheMb by IntPref("cache_mb", 512) // audio cache limit; read when the cache opens (the next start)
    var radioSeed by Str("radio_seed", "") // YouTube id the current radio started from, so auto-added songs stay related
    var lastFrom by Str("last_from", "") // what the current queue was started from, kind + NUL + name
    // Now Playing's whole look (NowPlayingStyle as JSON). Empty until first run, which builds it from the pre-2.0 keys
    // (np_cover, np_fill, np_layout, np_header, np_hide_status, np_depth, depth_*, show_volume, show_inline,
    // inline_style); those are left in place and only read for that migration.
    var npStyle by Str("np_style", "")

    /** A raw preference (null when never set); only for migrating old keys. */
    fun legacy(key: String): Any? = prefs.all[key]
    // the full lyrics panel
    var lyricsMotion by Str("lyrics_motion", "glide") // how it moves between lines: glide | fade | instant
    var lyricsAlign by Str("lyrics_align", "start") // start | center | end
    var lyricsSize by IntPref("lyrics_size", 30) // sp
    var lyricsLeading by IntPref("lyrics_leading", 120) // line height, % of the font size
    // the best quality to reach per network: data (≈70 kbps) | high (best YouTube, ≈160 kbps) | max (JioSaavn 320 kbps when found)
    // | lossless (FLAC from the user's add-ons when found).
    // Defaults carry over the older "Play songs from" (play_from) + high-quality switches (wifi_high, mobile_high).
    var maxWifi by Str("max_wifi", if (prefs.getString("play_from", "yt") == "saavn") "max" else if (prefs.getBoolean("wifi_high", true)) "high" else "data")
    var maxMobile by Str("max_mobile", if (prefs.getBoolean("mobile_high", false)) (if (prefs.getString("play_from", "yt") == "saavn") "max" else "high") else "data")
    var saavnOn by Bool("saavn_on", true) // JioSaavn 320 kbps as a step on the quality ladder (Settings → Sources)
    var saavnRank by IntPref("saavn_rank", 99) // JioSaavn's place among the add-ons in Settings → Sources (0 = first; past the end = after all of them)
    var normalize by Bool("normalize", true)
    var skipSilence by Bool("skip_silence", false)
    var autoplay by Bool("autoplay", true)
    var autoplayMix by Str("autoplay_mix", "radio") // what autoplay adds: radio (songs like the seed) | artist (more by this artist)
    var pauseOnUnplug by Bool("pause_on_unplug", true) // pause when headphones or Bluetooth audio disconnect
    var stopOnSwipe by Bool("stop_on_swipe", false) // swiping WeTune out of recents stops playback too
    var sendHistory by Bool("send_history", true) // report plays to YouTube Music (history and recommendations)
    var autoRotate by Bool("auto_rotate", false)
    var recentSearches by Str("recent_searches", "") // newline-separated, newest first

    fun addRecentSearch(q: String) {
        recentSearches = (listOf(q) + recentSearches.lines().filter { it.isNotBlank() && !it.equals(q, ignoreCase = true) }).take(10).joinToString("\n")
    }

    // never written to a settings file: sign-in secrets and what's playing / was searched
    private val PRIVATE = setOf("cookie", "subsonic_pass", "radio_seed", "last_from", "recent_searches", "cache_v2")

    /** Every setting the user changed (incl. the Now Playing style), minus sign-in secrets and passing state, as JSON. */
    fun exportJson(): String = settingsJson(all.keys.filter { it !in PRIVATE && prefs.contains(it) }.associateWith { prefs.all[it] })

    /** Applies a file from [exportJson]; unknown keys and wrong types are skipped. Returns how many settings were applied. */
    fun importJson(text: String): Int {
        val values = parseSettingsJson(text, all.mapValues { it.value.kind }).filterKeys { it !in PRIVATE }
        val e = prefs.edit()
        values.forEach { (k, v) -> when (v) { is String -> e.putString(k, v); is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v) } }
        e.apply()
        values.keys.forEach { all[it]?.reload() }
        return values.size
    }

    private abstract class Pref(val key: String, val kind: Char) {
        init { all[key] = this }
        abstract fun reload()
    }

    private class Str(key: String, val default: String) : Pref(key, 's'), ReadWriteProperty<Any?, String> {
        private val state by lazy { mutableStateOf(prefs.getString(key, default) ?: default) }
        override fun reload() { state.value = prefs.getString(key, default) ?: default }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            state.value = value
            prefs.edit().putString(key, value).apply()
        }
    }

    private class IntPref(key: String, val default: Int) : Pref(key, 'i'), ReadWriteProperty<Any?, Int> {
        private val state by lazy { mutableStateOf(prefs.getInt(key, default)) }
        override fun reload() { state.value = prefs.getInt(key, default) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state.value = value
            prefs.edit().putInt(key, value).apply()
        }
    }

    private class Bool(key: String, val default: Boolean) : Pref(key, 'b'), ReadWriteProperty<Any?, Boolean> {
        private val state by lazy { mutableStateOf(prefs.getBoolean(key, default)) }
        override fun reload() { state.value = prefs.getBoolean(key, default) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }
}

/** The settings file: {"wetune_settings": 1, "values": {key: value}}. */
fun settingsJson(values: Map<String, Any?>): String =
    JSONObject().put("wetune_settings", 1).put("values", JSONObject().apply { values.forEach { (k, v) -> if (v != null) put(k, v) } }).toString(2)

/** The values in a settings file, only for keys in [kinds] whose type matches ('s' text, 'b' on/off, 'i' whole number). */
fun parseSettingsJson(text: String, kinds: Map<String, Char>): Map<String, Any> {
    val root = JSONObject(text)
    require(root.has("wetune_settings")) { "This isn't a WeTune settings file" }
    val v = root.getJSONObject("values")
    return kinds.mapNotNull { (k, kind) ->
        if (!v.has(k)) return@mapNotNull null
        when (val x = v.get(k)) {
            is String -> x.takeIf { kind == 's' }
            is Boolean -> x.takeIf { kind == 'b' }
            is Number -> x.toInt().takeIf { kind == 'i' && x.toDouble() == it.toDouble() }
            else -> null
        }?.let { k to it }
    }.toMap()
}
