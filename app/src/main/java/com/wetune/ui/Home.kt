package com.wetune.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wetune.Playback
import com.wetune.R
import com.wetune.Settings
import com.wetune.appScope
import com.wetune.sources.Card
import com.wetune.sources.Track
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Section
import com.wetune.youtube.Sync
import com.wetune.youtube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home feed kept for the whole session: the All feed and each mood chip's feed, each fetched once and kept, so
 * switching tabs or chips never reloads. Pull to refresh reloads the shown one. The All feed also carries the country's
 * charts (songs, artists) and, signed in, the account's YouTube Music history for "Jump back in".
 */
object HomeFeed {
    private class Feed(val sections: List<Section>, val token: String?)

    var sections by mutableStateOf<List<Section>>(emptyList()); private set
    /** No more pages will come for the shown feed (its last page arrived, or paging failed). */
    var done by mutableStateOf(false); private set
    /** The account's recently played songs on YouTube Music (signed in only), behind this device's own recents. */
    var history by mutableStateOf<List<Track>>(emptyList()); private set
    /** The country's chart songs: not personal, so they go at the very end of the page. */
    var trending by mutableStateOf<List<Track>>(emptyList()); private set
    /** The mood / genre chips YouTube Music offers above Home ("Energize", "Relax"…); kept from the All feed. */
    var chips by mutableStateOf<List<YouTube.Chip>>(emptyList()); private set
    /** The chip whose feed is shown; null = All. */
    var chip by mutableStateOf<YouTube.Chip?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var refreshing by mutableStateOf(false); private set
    /** Bumped each time a feed is shown: its rows animate in once, keyed by it. */
    var generation by mutableIntStateOf(0); private set
    /** When the current feed was shown (ms): rows only animate in shortly after, never when scrolled back into view. */
    var shownAt by mutableLongStateOf(0L); private set
    // by chip params ("" = All); state, so a mood's scene shows its art once its feed is in
    private val cache = androidx.compose.runtime.mutableStateMapOf<String, Feed>()
    private val preloading = HashSet<String>()
    private var cacheFor: String? = null // the account the cache belongs to
    private var job: Job? = null
    private var paging = false

    private fun key(c: YouTube.Chip?) = c?.params ?: ""

    private fun show(f: Feed) { sections = f.sections; done = f.token == null; generation++; shownAt = System.currentTimeMillis() }

    /** The shown feed: from memory unless [force] (or another account signed in), else fetched; placeholders meanwhile. */
    fun load(force: Boolean = false): Job {
        if (cacheFor != Settings.cookie) { cache.clear(); cacheFor = Settings.cookie; history = emptyList() }
        val c = chip
        val kept = cache[key(c)]
        if (kept != null && !force) { if (sections !== kept.sections) show(kept); return fill(c) }
        job?.cancel()
        return appScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    // charts and history are extras: the feed shows without them when they fail
                    // trending songs from Explore (the charts page has no songs in some countries, India among them)
                    val charts = async { if (c == null) runCatching { YouTube.explore().trending }.getOrNull() else null }
                    val played = async { if (c == null && InnerTube.signedIn) runCatching { Sync.history() }.getOrNull() else null }
                    // the first two pages at once: Home opens fuller, with fewer shelves arriving after
                    val h = YouTube.home(c?.params)
                    val next = h.token?.let { t -> runCatching { YouTube.homeMore(t) }.getOrNull() }
                    Triple(Feed(h.sections + next?.first.orEmpty(), if (next != null) next.second else h.token) to h.chips, charts.await(), played.await())
                }
            }
                .onSuccess { (fc, charts, played) ->
                    if (chip != c) return@onSuccess // the user picked another chip meanwhile
                    val (f, chipList) = fc
                    cache[key(c)] = f
                    if (c == null && chipList.isNotEmpty()) chips = chipList
                    if (played != null) history = played
                    if (charts != null) trending = charts
                    error = null
                    show(f)
                    fill(c)
                }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException && sections.isEmpty()) error = it.message ?: "Something went wrong" }
        }.also { job = it }
    }

    /**
     * YouTube sends Home a couple of sections at a time: fetch the rest straight away, so the page settles once
     * (shelves below Jump back in wait for the last page, so nothing above them ever moves).
     */
    private fun fill(c: YouTube.Chip?) = appScope.launch {
        var pages = 0
        while (chip == c && cache[key(c)]?.token != null && pages < 10) {
            if (paging) { delay(100); continue } // the feed shown before is still fetching a page: wait for it, then go on
            more().join()
            pages++
        }
        if (chip == c) done = true // all in (or enough pages): show the shelves held back
    }

    /** A mood's first page, fetched ahead (its scene is on screen): gives the scene its art and makes picking it instant. */
    fun preload(c: YouTube.Chip) {
        if (cache[key(c)] != null || !preloading.add(c.params)) return
        appScope.launch {
            runCatching { withContext(Dispatchers.IO) { YouTube.home(c.params) } }
                .onSuccess { h -> if (cache[key(c)] == null) cache[key(c)] = Feed(h.sections, h.token) }
            preloading.remove(c.params)
        }
    }

    /** A mood's picture: the first cover in its feed (null until fetched). */
    fun moodArt(c: YouTube.Chip): String? = chips.indexOf(c).let { i ->
        // the moods' feeds open on the same shelves (the account's own picks), so their first covers are all alike
        if (i >= 0) distinctCovers(chips.map(::covers))[i] else covers(c).firstOrNull()
    }

    private fun covers(c: YouTube.Chip): List<String> =
        cache[key(c)]?.sections.orEmpty().flatMap { s -> s.cards.mapNotNull { it.art } + s.tracks.mapNotNull { it.art } }.distinct()

    /** The first few artists a mood's feed plays. */
    fun moodArtists(c: YouTube.Chip): String = cache[key(c)]?.sections.orEmpty().asSequence().flatMap { it.tracks }
        .flatMap { it.artist.split(", ") }.filter { it.isNotBlank() }.distinct().take(3).joinToString(" · ")

    /** Show [c]'s feed (null = All): at once if it was opened before, else placeholders until it arrives. */
    fun pick(c: YouTube.Chip?) {
        if (c == chip) return
        chip = c; error = null
        if (cache[key(c)] == null) sections = emptyList() // placeholders while it loads, never the previous mood's rows
        load()
    }

    /** Pull to refresh: the shown feed, fresh; the spinner shows at least briefly so the gesture always feels answered. */
    fun refresh() = appScope.launch {
        if (refreshing) return@launch
        refreshing = true
        val started = System.currentTimeMillis()
        load(force = true).join()
        delay((700 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
        refreshing = false
    }

    fun more() = appScope.launch {
        val c = chip
        val f = cache[key(c)] ?: return@launch
        val t = f.token ?: return@launch
        if (paging) return@launch
        paging = true
        runCatching { withContext(Dispatchers.IO) { YouTube.homeMore(t) } }
            .onSuccess { (s, next) ->
                val grown = Feed(f.sections + s, next)
                cache[key(c)] = grown
                if (chip == c) { sections = grown.sections; done = next == null }
            }
            .onFailure { if (chip == c) done = true } // show what's held back rather than wait for a page that won't come
        paging = false
    }

    fun retry() { error = null; load(force = true) }
}

/**
 * One picture per mood from each mood's own covers ([candidates], in mood order): the first that no other mood's feed
 * has, else the first no earlier mood took, else its first; null while a mood's feed isn't in.
 */
internal fun distinctCovers(candidates: List<List<String>>): List<String?> {
    val seen = candidates.flatten().groupingBy { it }.eachCount()
    val used = HashSet<String>()
    return candidates.map { own ->
        (own.firstOrNull { seen[it] == 1 } ?: own.firstOrNull { it !in used } ?: own.firstOrNull())?.also { used += it }
    }
}

/**
 * The account's library artists, most played first: a play counts for each artist named on a song in [played]
 * (YouTube Music history and this device's recents); artists never played keep their library order after.
 */
internal fun rankArtists(artists: List<Card>, played: List<Track>): List<Card> {
    val plays = played.flatMap { t -> t.artist.split(", ").map { it.trim().lowercase() } }.groupingBy { it }.eachCount()
    return artists.sortedByDescending { plays[it.title.trim().lowercase()] ?: 0 }
}

private fun isArtist(c: Card) = c.subtitle.startsWith("Artist") || c.browseId.startsWith("UC")
private val MIX = Regex("mix|for you|radio|new release", RegexOption.IGNORE_CASE)
private val RANKED = Regex("trending|top|chart", RegexOption.IGNORE_CASE)
private val TOP_PICKS = Regex("quick picks", RegexOption.IGNORE_CASE)
private val LISTEN_AGAIN = Regex("listen again", RegexOption.IGNORE_CASE)

/** How a shelf is laid out; each kind of content gets its own, so the page has rhythm instead of rows of squares. */
private enum class Look { Songs, Ranked, Videos, Artists, Tiles, Feature, List }

private fun lookOf(s: Section): Look = when {
    s.cards.isEmpty() && s.wide -> Look.Videos
    s.cards.isEmpty() -> if (RANKED.containsMatchIn(s.title)) Look.Ranked else Look.Songs
    s.tracks.isEmpty() && s.cards.all(::isArtist) -> Look.Artists
    s.tracks.isEmpty() && MIX.containsMatchIn(s.title) -> Look.Feature
    else -> Look.Tiles
}

/** Our own words for the feed's shelves, by what they hold (YouTube's titles are matched, never shown as headings). */
private val NAMES = listOf(
    "quick picks" to "Top picks for you",
    "listen again" to "Played before",
    "mixed for you|my mix|supermix" to "Your mixes",
    "forgotten fav" to "Not heard in a while",
    "from your library" to "From your library",
    "new release" to "Just out",
    "trending songs" to "Trending songs for you",
    "trending albums" to "Trending albums",
    "trending" to "Trending now",
    "top songs|top 100|chart" to "Top songs",
    "top artists" to "Top artists",
    "video" to "Videos to watch",
    "albums for you" to "Albums to try",
    "community playlists" to "Made by listeners",
    "\\bcovers?\\b|remix" to "Covers & remixes", // not "Discover mix"
    "long listening" to "For a long stretch",
).map { (p, n) -> Regex(p, RegexOption.IGNORE_CASE) to n }

/**
 * A shelf's heading and the small line above it: our name when we know the shelf, else [fallback] (named by its
 * content) with YouTube's theme ("Hindi Hits") kept as the small line, since it says what the shelf is about.
 */
internal fun shelfName(title: String, strap: String, fallback: String): Pair<String, String> {
    if (strap.equals("similar to", ignoreCase = true)) return "More like $title" to ""
    NAMES.firstOrNull { it.first.containsMatchIn(title) }?.let { return it.second to "" }
    return fallback to title
}

private fun fallbackName(s: Section, look: Look) = when (look) {
    Look.Songs, Look.List -> "Songs for you"
    Look.Ranked -> "Top songs"
    Look.Videos -> "Videos to watch"
    Look.Artists -> "Artists to explore"
    else -> if (s.cards.isNotEmpty() && s.cards.all { it.browseId.startsWith("MPRE") }) "Albums to try" else "Playlists"
}

/**
 * [items] in feed order, except that one never follows another of the same [kind]: such an item waits until something
 * of another kind has come between (YouTube sends album and playlist shelves in long runs). What's still held back
 * comes at the end once [done].
 */
internal fun <T> interleave(items: List<T>, done: Boolean, kind: (T) -> Any): List<T> {
    val out = ArrayList<T>(items.size)
    val held = ArrayDeque<T>() // all of the same kind as out.last()
    for (x in items) {
        if (out.isNotEmpty() && kind(x) == kind(out.last())) { held += x; continue }
        out += x
        if (held.isNotEmpty()) out += held.removeFirst()
    }
    if (done) out += held
    return out
}

/** For each of [slots], the first item it matches (null if none), and the items no slot took, in order. */
internal fun <T> fillSlots(items: List<T>, slots: List<(T) -> Boolean>): Pair<List<T?>, List<T>> {
    val left = items.toMutableList()
    val taken = slots.map { match -> left.firstOrNull(match)?.also { left.remove(it) } }
    return taken to left
}

/** Home's fixed run below Jump back in, each shelf with its own look so no two neighbours are alike. */
private val SLOTS = listOf(
    Regex("trending songs", RegexOption.IGNORE_CASE) to Look.List,
    Regex("mixed for you|my mix|supermix", RegexOption.IGNORE_CASE) to Look.Tiles,
    Regex("forgotten", RegexOption.IGNORE_CASE) to Look.List,
    // (moods)
    Regex("album|new release", RegexOption.IGNORE_CASE) to Look.Tiles,
)
private const val MOODS_AFTER = 3 // the moods strip sits after the first three slots

private data class Shelf(val index: Int, val section: Section, val look: Look, val name: String, val line: String)

private fun shelf(i: Int, s: Section, look: Look) = shelfName(s.title, s.strap, fallbackName(s, look)).let { (n, l) -> Shelf(i, s, look, n, l) }

/** The rest of YouTube's shelves (or a mood's whole feed): in feed order, no two neighbours alike. */
private fun rest(items: List<IndexedValue<Section>>, after: Look?): List<Shelf> {
    var prev = after
    val kind = { s: Section -> lookOf(s).let { if (it == Look.Feature) Look.Tiles else it } }
    return interleave(items, true) { kind(it.value) }.map { (i, s) ->
        var look = lookOf(s)
        if (look == prev && s.tracks.isEmpty() && (look == Look.Tiles || look == Look.Feature)) look = if (look == Look.Tiles) Look.Feature else Look.Tiles
        prev = look
        shelf(i, s, look)
    }
}

@Composable
fun HomeScreen() {
    val state = rememberLazyListState()
    LaunchedEffect(Settings.cookie) { HomeFeed.load() }
    ReselectToTop(state)
    val sections = HomeFeed.sections
    val gen = HomeFeed.generation
    val chip = HomeFeed.chip
    val mood = chip != null
    val done = HomeFeed.done
    // songs played lately: this device's first, then the account's YouTube Music history
    val recent = remember(Settings.recentItems, HomeFeed.history) {
        (Settings.recents().filterIsInstance<Track>() + HomeFeed.history).distinctBy { it.id }.take(12)
    }
    val usable = remember(sections, recent) {
        val ids = recent.mapTo(HashSet()) { it.id }
        sections.withIndex()
            // Played before (Listen again) without the songs Jump back in already shows
            .map { (i, s) -> IndexedValue(i, if (LISTEN_AGAIN.containsMatchIn(s.title)) withoutSongs(s, ids) else s) }
            .filter { it.value.cards.isNotEmpty() || it.value.tracks.isNotEmpty() }
    }
    // Quick picks; failing that (YouTube renames shelves), the feed's first plain song shelf
    val topPicks = if (mood) null else usable.map { it.value }.let { all ->
        all.firstOrNull { it.tracks.isNotEmpty() && TOP_PICKS.containsMatchIn(it.title) }
            ?: all.firstOrNull { it.cards.isEmpty() && !it.wide && !RANKED.containsMatchIn(it.title) && it.tracks.size >= 4 }
    }
    // the All feed: fixed slots, then the rest; shown once every page is in, so nothing already on screen moves
    val (slots, others) = remember(usable, mood) {
        // a mood's shelves keep YouTube's own titles ("In the gym", "Cardio"): they say what the mood is
        if (mood) emptyList<Shelf?>() to rest(usable, null).map { x -> x.copy(name = x.section.title.ifEmpty { x.name }, line = "") }
        else {
            val (taken, left) = fillSlots(usable.filter { it.value !== topPicks }, SLOTS.map { (re, _) -> { x: IndexedValue<Section> -> re.containsMatchIn(x.value.title) } })
            taken.mapIndexed { k, x -> x?.let { shelf(it.index, it.value, SLOTS[k].second) } } to rest(left, Look.Artists)
        }
    }
    // the account's library artists, the ones played most first (empty when signed out)
    val yours = remember(Sync.artists, Settings.recentItems, HomeFeed.history) {
        rankArtists(Sync.artists, HomeFeed.history + Settings.recents().filterIsInstance<Track>()).take(20)
    }
    val scope = rememberCoroutineScope()
    // picking or dropping a mood re-flows the page from the top
    val pick = { m: YouTube.Chip? -> HomeFeed.pick(m); scope.launch { state.animateScrollToItem(0) }; Unit }
    // the backdrop takes the chosen mood's picture, else the focused pick's cover, while Home shows
    var focus by remember { mutableStateOf<String?>(null) }
    val backdrop = if (chip != null) HomeFeed.moodArt(chip) else focus
    SideEffect { AuraArt.art = backdrop }
    DisposableEffect(Unit) { onDispose { AuraArt.art = null } }
    Column(Modifier.fillMaxSize()) {
        // a chosen mood: the logo row and the mood's capsule hold their own space above the feed, which scrolls below them
        androidx.compose.animation.AnimatedVisibility(
            mood, enter = expandVertically(Motion.fold()) + fadeIn(Motion.small()), exit = shrinkVertically(Motion.fold()) + fadeOut(Motion.small()),
        ) {
            Column {
                TopBar()
                val shown = remember { mutableStateOf(chip) }
                if (chip != null) shown.value = chip // keep it while it folds away
                shown.value?.let { MoodCapsule(it, pick) }
            }
        }
        Box(Modifier.weight(1f)) {
            PullRefresh(HomeFeed.refreshing, onRefresh = { HomeFeed.refresh() }) {
                LazyColumn(
                    state = state, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Dock.bottomPadding(Playback.current != null)),
                ) {
                    if (!mood) {
                        item("top", "top") { TopBar() }
                        // placeholders only while the feed loads: a feed without songs has no Top picks, never an endless skeleton
                        if (topPicks != null) item("picks", "picks") { TopPicks(topPicks) { focus = it.art } }
                        else if (sections.isEmpty() && HomeFeed.error == null) item("picks-wait", "picks") { TopPicksSkeleton() }
                        if (recent.isNotEmpty()) {
                            item("recent-h", "header") { SectionHeader("Jump back in") }
                            item("recent", "recent") { JumpBackIn(recent) }
                        }
                    }
                    if (!done || sections.isEmpty()) item("state", "state") {
                        Crossfade(HomeFeed.error, label = "home") { e ->
                            if (e != null) Failed(e) { HomeFeed.retry() } else Column { SectionHeader("…"); repeat(3) { SkeletonRow() }; SectionHeader("…"); SkeletonTiles() }
                        }
                    } else {
                        var n = 0
                        slots.forEachIndexed { k, x ->
                            if (x != null) shelfItems(x, gen, n++)
                            if (k == MOODS_AFTER - 1 && HomeFeed.chips.isNotEmpty()) {
                                item("moods-h", "header") { SectionHeader("Moods") }
                                item("moods", "moods") { MoodScenes(pick) }
                            }
                        }
                        if (!mood && yours.isNotEmpty()) {
                            item("yours-h", "header") { SectionHeader("Your artists") }
                            item("yours", "yours") { Artists(yours, 72.dp) }
                        }
                        others.forEach { shelfItems(it, gen, n++) }
                        // the country's chart: not personal, so last
                        val trending = HomeFeed.trending
                        if (!mood && trending.isNotEmpty()) {
                            val name = "Trending in " + java.util.Locale.getDefault().displayCountry.ifEmpty { "your country" }
                            item("trend-h", "header") { SectionHeader(name, onMore = { Nav.open(Route.Shelf(name, trending, emptyList())) }) }
                            item("trend", Look.Ranked) { SongPages(Section(name, trending, emptyList()), name, ranked = true) }
                        }
                    }
                }
            }
            if (!mood) CollapsedBar("Home", state) // a soft fade behind the status bar once content scrolls under it
        }
    }
}

/** One shelf: its heading, then its row in [Shelf.look]. */
private fun androidx.compose.foundation.lazy.LazyListScope.shelfItems(x: Shelf, gen: Int, n: Int) {
    val (i, s, look, name, line) = x
    val cards = s.cards
    item("$gen:h$i", "header") {
        Box(Modifier.arrive(n * 2)) {
            SectionHeader(name, strap = line, onMore = if (s.tracks.size > 4 || cards.size > 6 || (look == Look.List && s.tracks.size + cards.size > 5)) ({ Nav.open(Route.Shelf(name, s.tracks, s.cards)) }) else null)
        }
    }
    item("$gen:s$i", look) {
        Box(Modifier.arrive(n * 2 + 1)) {
            when (look) {
                Look.Songs -> SongPages(s, name, ranked = false)
                Look.Ranked -> SongPages(s, name, ranked = true)
                Look.List -> ShelfList(s, name)
                Look.Videos -> Videos(s, name)
                Look.Artists -> Artists(cards)
                Look.Feature -> Features(cards)
                Look.Tiles -> TileRow(cards, s.tracks, if (MIX.containsMatchIn(s.title)) 172.dp else 144.dp, name)
            }
        }
    }
}

/** "Good evening, Vikas": by the phone's clock, with the account's first name when signed in. */
internal fun greeting(name: String?, hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): String {
    val part = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night" }
    val first = name?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
    return if (first != null) "$part, $first" else part
}

/** The top of Home: WeTune's mark at the start, the profile at the end. */
@Composable
private fun TopBar() = Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = statusTop() + 6.dp), verticalAlignment = Alignment.CenterVertically) {
    AppMark()
    // the greeting takes all the room between (one line, ellipsized), so the profile always sits at the end
    Txt(greeting(Sync.account?.name), type(19.sp, 600, family = Outfit), Modifier.padding(start = 6.dp, end = 8.dp).weight(1f))
    ProfileButton()
}

private val DISC = 208.dp
private const val AUTO_MS = 4000L
private val STEP = 116.dp // centre to centre: neighbours tuck in behind the focused disc

/**
 * Top picks for you (YouTube Music's Quick picks for this account) as records flicked sideways: the focused one big,
 * ringed with its progress when it's the one playing; its neighbours smaller and dimmer, behind it. Sizes, fades and
 * positions follow the swipe in the draw phase only. Tap a record to play the picks from it. [onFocus]: the record
 * that settled in the middle (Home tints its backdrop with it).
 */
@Composable
private fun TopPicks(s: Section, onFocus: (Track) -> Unit) {
    val picks = remember(s) { s.tracks.take(20) }
    val pager = rememberPagerState { picks.size }
    LaunchedEffect(pager, picks) { snapshotFlow { pager.settledPage }.collect { picks.getOrNull(it)?.let(onFocus) } }
    // a carousel: the next record every 4 s with the swipe's own glide, back to the first after the last; it rests while
    // a finger is on it and for 4 s after, and while the app is in the background (off screen, this effect isn't running)
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    var touchedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragged) { touchedAt = if (dragged) Long.MAX_VALUE else System.currentTimeMillis() }
    LaunchedEffect(pager, picks) {
        while (picks.size > 1) {
            delay(AUTO_MS)
            val idle = touchedAt != Long.MAX_VALUE && System.currentTimeMillis() - touchedAt >= AUTO_MS
            if (idle && com.wetune.App.foreground && !com.wetune.ui.player.NowPlaying.isOpen && !pager.isScrollInProgress)
                pager.animateScrollToPage((pager.currentPage + 1) % picks.size, animationSpec = tween(700, easing = Motion.Standard))
        }
    }
    val side = (LocalConfiguration.current.screenWidthDp.dp - STEP) / 2
    val play = { i: Int -> Playback.play(picks, i, from = "Home" to "Top picks for you") }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            val cur = pager.currentPage
            // drawn farthest first, so the focused record sits on top
            (cur - 2..cur + 2).filter { it in picks.indices }.sortedByDescending { kotlin.math.abs(it - cur) }.forEach { i ->
                androidx.compose.runtime.key(picks[i].id) {
                    Disc(picks[i], Modifier.size(DISC).graphicsLayer {
                        val rel = i - pager.currentPage - pager.currentPageOffsetFraction
                        val d = kotlin.math.abs(rel).coerceAtMost(2.5f)
                        val k = if (d <= 1f) 1f - 0.23f * d else 0.77f - 0.145f * (d - 1f)
                        translationX = rel * STEP.toPx()
                        scaleX = k; scaleY = k
                        alpha = if (d <= 1f) 1f - 0.2f * d else (0.8f - 0.2f * (d - 1f)).coerceAtLeast(0f)
                    })
                }
            }
            // the swipe and the taps: one invisible page per record, over the drawn ones
            HorizontalPager(
                pager, Modifier.matchParentSize(), contentPadding = PaddingValues(horizontal = side),
                pageSize = PageSize.Fixed(STEP), key = { picks[it].id },
            ) { i -> Box(Modifier.fillMaxSize().press(picks[i].title, onLongClick = { TrackMenu.show(picks[i]) }, scale = 1f) { play(i) }) }
        }
        val t = picks.getOrNull(pager.currentPage)
        Txt(t?.title.orEmpty(), Type.title.copy(textAlign = TextAlign.Center), Modifier.padding(horizontal = 32.dp, vertical = 2.dp).fillMaxWidth())
        // the reason, when YouTube gives one, would go here; it doesn't for Quick picks, so: who and from what
        Txt(listOfNotNull(t?.artist, t?.album).filter { it.isNotEmpty() }.joinToString(" · "), Type.sub.copy(textAlign = TextAlign.Center), Modifier.padding(horizontal = 32.dp).fillMaxWidth(), color = WT.c.onSecondary)
        Dots(picks.size, pager.currentPage)
    }
}

/** A record: the cover as a round disc with a small hole, and the progress ring round it while it's the song playing. */
@Composable
private fun Disc(t: Track, modifier: Modifier) {
    val c = WT.c
    val playing = Playback.current?.id == t.id
    Box(modifier, contentAlignment = Alignment.Center) {
        if (playing) {
            val pos = com.wetune.rememberPosition()
            Spacer(Modifier.matchParentSize().drawBehind {
                val sw = 3.dp.toPx()
                val r = size.minDimension / 2f + 11.dp.toPx() // just outside the record
                drawCircle(c.on.copy(alpha = 0.18f), r, style = Stroke(sw))
                val d = Playback.duration()
                if (d > 0) drawArc(c.on, -90f, 360f * (pos.value.toFloat() / d).coerceIn(0f, 1f), false,
                    Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
            })
        }
        Cover(t.art, DISC, DISC / 2)
        Box(Modifier.size(16.dp).background(c.bg, CircleShape))
    }
}

/** Page dots under the records: the focused one a short bar. */
@Composable
private fun Dots(count: Int, on: Int) = Row(Modifier.padding(top = 12.dp).height(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    repeat(count) { i ->
        val w by animateDpAsState(if (i == on) 18.dp else 6.dp, Motion.small(), label = "dot")
        Box(Modifier.size(w, 6.dp).background(WT.c.on.copy(alpha = if (i == on) 1f else 0.35f), CircleShape))
    }
}

/** Top picks while the feed loads: the same height, so nothing moves when the records arrive. */
@Composable
private fun TopPicksSkeleton() = Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.height(230.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(DISC).clip(CircleShape).shimmer(104.dp)) }
    Box(Modifier.padding(vertical = 6.dp).size(160.dp, 18.dp).shimmer(4.dp))
    Box(Modifier.size(200.dp, 13.dp).shimmer(4.dp))
    Spacer(Modifier.height(18.dp))
}

/**
 * Moods as tall scenes, each the mood's own picture (the first cover in its feed, fetched once its scene is on screen)
 * with its word set big. Tap to tune Home to it.
 */
@Composable
private fun MoodScenes(pick: (YouTube.Chip?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(HomeFeed.chips, key = { it.title }, contentType = { "scene" }) { m ->
            LaunchedEffect(m) { HomeFeed.preload(m) }
            Box(Modifier.size(170.dp, 228.dp).clip(RoundedCornerShape(18.dp)).background(WT.c.surface).press(m.title, scale = 0.97f) { pick(m) }) {
                val art = HomeFeed.moodArt(m)
                AsyncImage(art, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                // legibility: a scrim rising from the bottom in the picture's own darkest colour, under white type
                val dark = com.wetune.ui.player.coverColors(art)?.minByOrNull { it.luminance() }?.let { androidx.compose.ui.graphics.lerp(it, Color.Black, 0.55f) } ?: Color.Black
                val scrim by androidx.compose.animation.animateColorAsState(dark, Motion.small(), label = "scrim")
                Box(Modifier.matchParentSize().drawBehind {
                    drawRect(Color.Black.copy(alpha = 0.12f))
                    drawRect(Brush.verticalGradient(0.3f to scrim.copy(alpha = 0f), 0.72f to scrim.copy(alpha = 0.78f), 1f to scrim.copy(alpha = 0.95f)))
                })
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    Txt(m.title, MoodWord, color = Color.White, lines = 2)
                    val who = HomeFeed.moodArtists(m)
                    Txt(who, Type.sub, Modifier.padding(top = 4.dp), color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
    }
}

private val MoodWord = type(32.sp, 700, family = Outfit).copy(lineHeight = 32.sp)

/**
 * The chosen mood, pinned under the logo row (the feed scrolls below it, never under it): one glass capsule with
 * the mood's picture and name, a quick switch to the next ones, and × to go back to all of Home.
 */
@Composable
private fun MoodCapsule(m: YouTube.Chip, pick: (YouTube.Chip?) -> Unit) {
    val c = WT.c
    val chips = HomeFeed.chips
    val next = chips.indexOf(m).let { i -> (1..2).mapNotNull { chips.getOrNull((i + it) % chips.size) }.filter { it != m } }
    Row(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp).fillMaxWidth().height(64.dp)
            .glass(CircleShape).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(HomeFeed.moodArt(m), 48.dp, 24.dp)
        Column(Modifier.weight(1f)) {
            Txt("MOOD", Type.caption, color = c.onSecondary)
            Txt(m.title, type(23.sp, 600, family = Outfit))
        }
        if (next.isNotEmpty()) Box(Modifier.height(44.dp).press("Switch to ${next.first().title}") { pick(next.first()) }, contentAlignment = Alignment.Center) {
            Txt(next.joinToString(" · ") { it.title } + " ›", Type.sub, color = c.onSecondary)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF2F2F2)).press("Turn off ${m.title}") { pick(null) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Close, Color.Black, 14.dp)
        }
    }
}

/** Songs played lately as small squares in a row: tap starts the song's radio. */
@Composable
private fun JumpBackIn(recent: List<Track>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(recent, key = { it.id }, contentType = { "recent" }) { t ->
            Column(Modifier.width(96.dp).press(t.title, onLongClick = { TrackMenu.show(t) }) { Playback.startRadio(t) }) {
                Cover(t.art, 96.dp, 8.dp)
                Spacer(Modifier.height(6.dp))
                Txt(t.title, Type.sub.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
                Txt(t.artist, Type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = WT.c.onSecondary)
            }
        }
    }
}

/** A shelf as a short list: songs play in place, albums and playlists open. */
@Composable
private fun ShelfList(s: Section, from: String) = Column {
    listRows(s).forEach { x ->
        if (x is Track) TrackRow(x) { Playback.play(s.tracks, s.tracks.indexOf(x), from = "Home" to from) }
        else if (x is Card) CardRow(x) { Nav.open(Route.Collection(x)) }
    }
}

/**
 * The rows a list shelf shows: its songs and its albums / playlists both (Not heard in a while mixes them; showing
 * only the songs once left it a single row), at most [max].
 */
internal fun listRows(s: Section, max: Int = 5): List<Any> = (s.tracks + s.cards).take(max)

/** [s] without the songs in [ids], unless that would leave it under [min] items: a de-dupe never guts a shelf. */
internal fun withoutSongs(s: Section, ids: Set<String>, min: Int = 4): Section {
    val kept = s.tracks.filter { it.id !in ids }
    return if (kept.size + s.cards.size >= min) s.copy(tracks = kept) else s
}

/**
 * Songs four to a page, pages swiped sideways and snapping into place; the next page peeks in at the edge. [ranked]:
 * a chart, so each row carries its place.
 */
@Composable
private fun SongPages(s: Section, from: String, ranked: Boolean) {
    val pages = s.tracks.take(20).chunked(4)
    val row = rememberLazyListState()
    // the screen's width rather than BoxWithConstraints: no subcomposition for a row that scrolls with the page
    val pageW = LocalConfiguration.current.screenWidthDp.dp - 40.dp
    LazyRow(state = row, flingBehavior = rememberSnapFlingBehavior(row)) {
        items(pages.size, key = { it }, contentType = { "page" }) { p ->
            Column(Modifier.width(pageW)) {
                pages[p].forEachIndexed { j, t ->
                    val play = { Playback.play(s.tracks, p * 4 + j, from = "Home" to from) }
                    // swipe off: a sideways slide here pages the shelf
                    if (!ranked) TrackRow(t, swipe = false, onClick = play)
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt("${p * 4 + j + 1}", Type.title.copy(textAlign = TextAlign.End), Modifier.padding(start = 12.dp).width(26.dp), color = WT.c.onSecondary)
                        TrackRow(t, Modifier.weight(1f), swipe = false, onClick = play)
                    }
                }
            }
        }
    }
}

/** Music videos: wide 16:9 frames, played in place. */
@Composable
private fun Videos(s: Section, from: String) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(s.tracks, key = { _, t -> t.id }, contentType = { _, _ -> "video" }) { i, t ->
            Column(Modifier.width(240.dp).press(t.title, onLongClick = { TrackMenu.show(t) }) { Playback.play(s.tracks, i, from = "Home" to from) }) {
                AsyncImage(t.art, null, Modifier.size(240.dp, 135.dp).clip(RoundedCornerShape(10.dp)).background(WT.c.surface), contentScale = ContentScale.Crop)
                Spacer(Modifier.height(6.dp))
                Txt(t.title, Type.tile)
                Txt(t.artist, Type.sub, color = WT.c.onSecondary)
            }
        }
    }
}

/** Artists: round portraits of [size] with the name centred under them. */
@Composable
private fun Artists(cards: List<Card>, size: Dp = 96.dp) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(cards, key = { it.browseId }, contentType = { "artist" }) { a ->
            Column(Modifier.width(size).press(a.title) { Nav.open(Route.Collection(a)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(a.art, size, size / 2)
                Spacer(Modifier.height(8.dp))
                Txt(a.title, Type.tile.copy(textAlign = TextAlign.Center), Modifier.fillMaxWidth())
            }
        }
    }
}

/** Mixes and new releases: one wide glass card at a time, cover beside its title, snapping into place. */
@Composable
private fun Features(cards: List<Card>) {
    val row = rememberLazyListState()
    val w = LocalConfiguration.current.screenWidthDp.dp - 56.dp
    LazyRow(state = row, flingBehavior = rememberSnapFlingBehavior(row), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(cards, key = { it.browseId }, contentType = { "feature" }) { card ->
            Row(Modifier.width(w).height(132.dp).glass(RoundedCornerShape(14.dp)).press(card.title, scale = 0.98f) { Nav.open(Route.Collection(card)) }) {
                Cover(card.art, 132.dp, 0.dp)
                Column(Modifier.padding(14.dp)) {
                    Txt(card.title, Type.title.copy(fontSize = Type.title.fontSize * 0.86f), lines = 2)
                    Spacer(Modifier.height(4.dp))
                    if (card.subtitle.isNotEmpty()) Txt(card.subtitle, Type.sub, color = WT.c.onSecondary, lines = 3)
                }
            }
        }
    }
}

/** A row of tiles: [cards] open their page; [tracks] (songs mixed into the row) play in place. */
@Composable
private fun TileRow(cards: List<Card>, tracks: List<Track>, size: Dp, from: String) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cards, key = { it.browseId }, contentType = { "tile" }) { c -> Tile(c, size = size) { Nav.open(Route.Collection(c)) } }
        items(tracks.size, key = { "t" + tracks[it].id }, contentType = { "song" }) { i ->
            val t = tracks[i]
            Column(Modifier.width(size).press(t.title, onLongClick = { TrackMenu.show(t) }) { Playback.play(tracks, i, from = "Home" to from) }) {
                Cover(t.art, size, 10.dp)
                Spacer(Modifier.height(6.dp))
                Txt(t.title, Type.tile)
                Txt(t.artist, Type.sub, color = WT.c.onSecondary)
            }
        }
    }
}

/** WeTune's mark, small, at the start of the header. */
@Composable
private fun AppMark() {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        // the launcher icon's own two layers, so the header badge always matches the app icon
        Box(Modifier.size(34.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.size(34.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), "WeTune", Modifier.size(34.dp))
        }
    }
}

/**
 * A row arriving with a fresh feed: rises 18dp and fades in, staggered by its place on screen. Rows composed later
 * (scrolled into view) are simply there, with no animation state at all; only the layer animates, never the layout.
 */
@Composable
private fun Modifier.arrive(order: Int): Modifier {
    val fresh = remember { System.currentTimeMillis() - HomeFeed.shownAt < 1200 && order < 14 }
    if (!fresh) return this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(order * 55L); a.animateTo(1f, tween(460, easing = Motion.Decelerate)) }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 18.dp.toPx() }
}
