package com.wetune

import com.wetune.sources.Track

/**
 * Finds the same song on another source (JioSaavn) for a YouTube track, strictly enough that a remix,
 * live or cover version doesn't get swapped in. Pure logic so it can be unit-tested.
 */
object SourceMatch {
    private val noise = Regex(
        "\\((?:from|official|lyric|lyrics|audio|video|full|feat\\.?|ft\\.?)[^)]*\\)|\\[[^]]*]|\\b(?:official (?:music )?video|lyric(?:al)? video|full (?:video|song)|audio)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** "Monica (From "Coolie") (Tamil)" → "monica tamil"; letters and digits only, lowercase. */
    fun clean(title: String): String =
        title.replace(noise, " ").lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun words(s: String) = clean(s).split(' ').filter { it.isNotBlank() }.toSet()

    /** The words that set a version apart; if one side has them and the other doesn't, it's a different recording. */
    private val versionWords = setOf("remix", "live", "cover", "acoustic", "unplugged", "reprise", "lofi", "slowed", "reverb", "instrumental", "karaoke", "mashup", "sped")

    fun firstArtist(artist: String) = artist.split(',', '&', '|').first().trim()

    /** True when [candidate] is the same recording as the YouTube song described by [title], [artist], [durationMs]. */
    fun same(title: String, artist: String, durationMs: Long, candidate: Track): Boolean {
        val a = words(title); val b = words(candidate.title)
        if (a.isEmpty() || b.isEmpty()) return false
        if ((a intersect versionWords) != (b intersect versionWords)) return false
        val overlap = (a intersect b).size.toFloat() / minOf(a.size, b.size)
        if (overlap < 0.75f) return false
        val gap = if (durationMs > 0 && candidate.durationMs > 0) kotlin.math.abs(durationMs - candidate.durationMs) else null
        if (gap != null && gap > 8_000) return false
        // any of the YouTube artists among the JioSaavn ones (YouTube often lists the composer first); with a shared
        // artist the masters may differ by a few seconds (8 s), otherwise they must agree closely (below)
        val artistWords = words(artist) - setOf("the", "and", "feat", "ft", "x")
        if (artistWords.isEmpty() || (artistWords intersect words(candidate.artist)).isNotEmpty()) return true
        // no artist in common: often the YouTube "artist" is a label channel (T-Series, Sony Music India…);
        // accept only when the title matches almost exactly and the lengths agree closely
        return gap != null && gap <= 2_500 && overlap >= 0.9f
    }

    /** Search texts for the other source, tried in order: cleaned title + first artist, then the title alone. */
    fun queries(title: String, artist: String) = listOf("${clean(title)} ${firstArtist(artist)}".trim(), clean(title)).distinct().filter { it.isNotBlank() }
}
