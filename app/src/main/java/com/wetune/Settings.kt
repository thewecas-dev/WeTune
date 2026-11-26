package com.wetune

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.wetune.sources.Card
import com.wetune.sources.Track
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** SharedPreferences exposed as Compose state, so the UI updates when a setting changes. */
const val COVER_AUTO = Int.MIN_VALUE

object Settings {
    private val prefs by lazy { App.ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    // every setting by key (filled as the delegates below are created), so a settings file can be applied
    private val all = linkedMapOf<String, Pref>()

    // shortcut: cookie and Subsonic password sit in app-private prefs (unencrypted); move to Keystore-backed storage if needed.
    var cookie by Str("cookie", "")
    var subsonicUrl by Str("subsonic_url", "")
    var subsonicUser by Str("subsonic_user", "")
    var subsonicPass by Str("subsonic_pass", "")
    var spotifyDc by Str("spotify_dc", "") // the Spotify login cookie (sp_dc), only for Canvas; only ever sent to Spotify
    var theme by Str("theme", "system") // system | light | dark
    var libraryLayout by Str("library_layout", "list") // list | grid (2 across) | grid3 (3 across)
    var glassTint by IntPref("glass_tint", 62) // frosted surfaces: how much tint over the blur, 0..100 %
    var glassBlur by IntPref("glass_blur", 40) // frosted surfaces: blur radius in dp, 0..60
    var glassNoise by IntPref("glass_noise", 20) // every surface's grain, 0..100 % (50 = the original look)
    var bgNoise by IntPref("bg_noise", 50) // the app background's grain, 0..100 % (50 = the original look)
    var bgColor by IntPref("bg_color", 50) // how much of the app background's colour wash shows, 0..100 % (50 = the original look)
    var bgBlur by IntPref("bg_blur", 50) // the app background's colour wash: how soft and spread, 0..100 % (50 = the original look)
    var textScale by IntPref("text_scale", 100) // app text size, % of the system font size
    var startTab by Str("start_tab", "home") // the tab WeTune opens on: home | library
    var lyricsRoman by Bool("lyrics_roman", false) // show a Latin-letter reading under each lyric line
    var romanInline by Bool("roman_inline", false) // the one-line lyric above the seek bar in Latin letters
    var romanKeep by Str("roman_keep", "") // scripts the reader knows, left as written (comma-separated, see lyricScript): TAMIL,DEVANAGARI,…
    var lyricsWords by Bool("lyrics_words", true) // prefer word-by-word lyrics and light them up letter by letter
    var lyricsOrder by Str("lyrics_order", "") // lyric sources in the order to prefer, comma-separated; "" = automatic (best match)
    var songMenuPills by Str("song_menu_pills", "next,like,playlist") // the song menu's top buttons (≤3 action ids, see SongActions)
    var songMenuOrder by Str("song_menu_order", "") // its rows in the user's order (action ids); "" = the default order
    var inlineWords by Bool("inline_words", true) // the one-line lyric above the seek bar fills letter by letter
    var cacheV2 by Bool("cache_v2", false) // audio cache re-keyed by stream (see PlaybackService)
    var cacheMb by IntPref("cache_mb", 512) // audio cache limit; read when the cache opens (the next start)
    var radioSeed by Str("radio_seed", "") // YouTube id the current radio started from, so auto-added songs stay related
    var lastFrom by Str("last_from", "") // what the current queue was started from, kind + NUL + name
    var playerStyle by Str("player_style", "apple") // Now Playing only: apple | material (the rest of the app is always Apple-like)
    // Now Playing look (np2_ keys: the 0.x app left differently typed np_* keys on devices)
    var npHideStatus by Bool("np2_hide_status", true)
    var npGrabber by Bool("np2_grabber", true) // the drag handle at the top of Now Playing
    var npPlayingFrom by Bool("np2_playing_from", true) // "Playing from" (album, playlist, radio…) at the top of Now Playing
    var npShuffleRepeat by Bool("np2_shuffle_repeat", true) // Shuffle and Repeat beside the play controls (they stay in the queue either way)
    var npVideoCover by Bool("np2_video_cover", true) // the song's music video plays in place of the cover (muted, looping)
    var npVideoWifiOnly by Bool("np2_video_wifi_only", true) // ...only on Wi-Fi (saves mobile data)
    var npImmersiveAuto by Bool("np2_immersive_auto", false) // ...going in by itself when a song has a Canvas
    var npImmersiveStyle by Str("np2_immersive_style", "capsule") // capsule (floating frosted pill) | type (title on the Canvas)
    var immersiveLyrics by IntPref("immersive_lyrics", 0) // lyric lines over the Canvas in immersive view: 0 | 1 | 3
    var npBackground by Str("np2_background", "gradient") // flow | gradient | blur | tone | … (older choices map to the nearest; see backdrop)
    var coverSize by IntPref("np2_cover_size", 80) // % of the screen width, 55..120 (past 100 bleeds off the edges)
    var coverRadius by IntPref("np2_cover_radius", 10) // % of half the cover: 0 sharp .. 100 circle
    // each corner on its own (top left, top right, bottom right, bottom left; same % as coverRadius) instead of coverRadius
    var coverCornersSplit by Bool("np2_cover_corners_split", false)
    var coverCornerTL by IntPref("np2_cover_corner_tl", 10)
    var coverCornerTR by IntPref("np2_cover_corner_tr", 10)
    var coverCornerBR by IntPref("np2_cover_corner_br", 10)
    var coverCornerBL by IntPref("np2_cover_corner_bl", 10)
    var coverShadow by Bool("np2_cover_shadow", false)
    var coverShadowStrength by IntPref("np2_cover_shadow_strength", 50) // 0..100
    // where the cover sits between the screen's edges, 0..100: 0 = its top on the screen's top edge, 100 = its bottom on the
    // bottom edge. COVER_AUTO = halfway between the top of the screen and the controls card. (np2_cover_pos was dp from
    // the centre, left behind.)
    var coverPlace by IntPref("np2_cover_place", COVER_AUTO)
    // the Full width cover's own size and place (its own keys: the boxed cover's values would move it on update): its
    // height as % of its shape's (or of the space, for Fill the space), and where it sits like coverPlace (COVER_AUTO = the top)
    var edgeSize by IntPref("np2_edge_size", 100)
    var edgePlace by IntPref("np2_edge_place", COVER_AUTO)
    var coverAspect by Str("np2_cover_aspect", "9:16") // 1:1 | 4:5 | 3:4 | 9:16 | 16:9 | device (the phone screen's shape) | original (the artwork's own shape, uncropped)
    var coverStyle by Str("np2_cover_style", "edge") // plain | squircle | glow | tilt | polaroid | stack | disc | record | cue | edge (see CoverStyles)
    var sheetRadius by IntPref("np2_sheet_radius", -1) // controls sheet corners in dp, 0..48; -1 = match the screen's own corners
    var npCard by Bool("np2_card", true) // a frosted card behind the Now Playing controls
    var npCardInset by IntPref("np2_card_inset", 15) // dp from the screen edges, 0..24
    var npCardTint by Str("np2_card_tint", "black") // auto (frosted, as the navbar) | black | white | accent | dominant | neutral (cover tones)
    var npCardStrength by IntPref("np2_card_strength", 10) // tint strength for the non-frosted tints, 0..100 %
    var npCardBlur by IntPref("np2_card_blur", 25) // dp, 0 (no blur)..80
    var npCardShadow by Bool("np2_card_shadow", true) // the logo's depth under the one Now Playing surface (mini player, card, capsule)
    var npCardShadowStrength by IntPref("np2_card_shadow_strength", 50) // 10..100
    var inlineLyrics by IntPref("np2_inline_lyrics", 1) // lines of lyrics in the normal sheet: 0 (off) | 1 | 3
    var seekStyle by Str("np2_seek", "auto") // auto (follows the player style) | thin | thick | squiggly | wavy | waves
    var seekColor by Str("np2_seek_color", "theme") // theme (white / black) | cover (the cover's accent tone)
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
    var recentItems by Str("recent_items", "") // songs played and albums / playlists / artists opened, one recentJson per line, newest first
    var searchPicks by Str("search_picks", "") // the same, but only what was played or opened from Search's results (videos too)

    fun addRecentSearch(q: String) {
        recentSearches = pushRecent(recentSearches.lines().filter { it.isNotBlank() }, q, 6) { it.lowercase() }.joinToString("\n")
    }

    /** [searchPicks] read back: [Track]s, [Video]s and [Card]s. */
    fun picks(): List<Any> = searchPicks.lines().mapNotNull(::recentOf)

    fun addPick(x: Any) { searchPicks = pushRecent(picks(), x, 20, ::recentKey).joinToString("\n", transform = ::recentJson) }

    fun removeRecentSearch(q: String) { recentSearches = recentSearches.lines().filter { it.isNotBlank() && it != q }.joinToString("\n") }

    /** [recentItems] read back: [Track]s and [Card]s. */
    fun recents(): List<Any> = recentItems.lines().mapNotNull(::recentOf)

    fun addRecent(x: Any) { recentItems = pushRecent(recents(), x, 20, ::recentKey).joinToString("\n", transform = ::recentJson) }

    /** The value [key] has when the user never changed it (null for an unknown key). */
    fun defaultOf(key: String): Any? = all[key]?.default

    // never written to a settings file: sign-in secrets and what's playing / was searched
    private val PRIVATE = setOf("cookie", "subsonic_pass", "spotify_dc", "radio_seed", "last_from", "recent_searches", "recent_items", "search_picks", "cache_v2")

    /** Every setting the user changed (incl. the Now Playing style), minus sign-in secrets and passing state, as JSON. */
    fun exportJson(): String = settingsJson(all.keys.filter { it !in PRIVATE && prefs.contains(it) }.associateWith { prefs.all[it] })

    /** Applies a file from [exportJson]; unknown keys and wrong types are skipped. Returns how many settings were applied. */
    fun importJson(text: String): Int {
        val values = parseSettingsJson(text, all.mapValues { it.value.kind }).filterKeys { it !in PRIVATE }
        val e = prefs.edit()
        values.forEach { (k, v) -> when (v) { is String -> e.putString(k, v); is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v) } }
        e.apply()
        values.keys.forEach { all[it]?.reload() }
        migrate()
        return values.size
    }

    /** Brings settings saved by older versions up to date (at start, and after a settings file is applied). */
    fun migrate() {
        val (b, s) = fullWidthMigration(npBackground, coverStyle)
        if (b != npBackground) { npBackground = b; coverStyle = s }
    }

    private abstract class Pref(val key: String, val kind: Char) {
        init { all[key] = this }
        abstract fun reload()
        abstract val default: Any
    }

    private class Str(key: String, override val default: String) : Pref(key, 's'), ReadWriteProperty<Any?, String> {
        private val state by lazy { mutableStateOf(prefs.getString(key, default) ?: default) }
        override fun reload() { state.value = prefs.getString(key, default) ?: default }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            state.value = value
            prefs.edit().putString(key, value).apply()
        }
    }

    private class IntPref(key: String, override val default: Int) : Pref(key, 'i'), ReadWriteProperty<Any?, Int> {
        private val state by lazy { mutableStateOf(prefs.getInt(key, default)) }
        override fun reload() { state.value = prefs.getInt(key, default) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state.value = value
            prefs.edit().putInt(key, value).apply()
        }
    }

    private class Bool(key: String, override val default: Boolean) : Pref(key, 'b'), ReadWriteProperty<Any?, Boolean> {
        private val state by lazy { mutableStateOf(prefs.getBoolean(key, default)) }
        override fun reload() { state.value = prefs.getBoolean(key, default) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }
}

/** [x] first, then [list] without any earlier entry with the same [key], at most [cap] long. */
fun <T> pushRecent(list: List<T>, x: T, cap: Int, key: (T) -> Any): List<T> = (listOf(x) + list.filter { key(it) != key(x) }).take(cap)

/** A music video picked from Search: a [Track] that lists and plays as a video (16:9 frame). */
data class Video(val track: Track)

fun recentKey(x: Any): Any = when (x) { is Track -> x.id; is Video -> x.track.id; is Card -> x.browseId; else -> x }

/** A recent song or album / playlist / artist as one line of JSON; [recentOf] reads it back (null if unreadable). */
fun recentJson(x: Any): String = when (x) {
    is Track -> JSONObject().put("t", x.toJson())
    is Video -> JSONObject().put("v", x.track.toJson())
    is Card -> JSONObject().put("c", JSONObject().put("b", x.browseId).put("t", x.title).put("s", x.subtitle).put("a", x.art))
    else -> throw IllegalArgumentException("not a song or page: $x")
}.toString()

fun recentOf(line: String): Any? = runCatching {
    val o = JSONObject(line)
    o.optJSONObject("t")?.let(Track::fromJson)
        ?: o.optJSONObject("v")?.let { Video(Track.fromJson(it)) }
        ?: o.getJSONObject("c").let { Card(it.getString("b"), it.optString("t"), it.optString("s"), if (it.isNull("a")) null else it.optString("a").ifEmpty { null }) }
}.getOrNull()

/** Full-width cover was a background ("edge"); it's now a cover style ("edge") over the same cover-edge colour ("tone"). */
fun fullWidthMigration(background: String, coverStyle: String): Pair<String, String> =
    if (background == "edge") "tone" to "edge" else background to coverStyle

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
