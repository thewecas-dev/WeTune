package com.wetune.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.wetune.Lyrics
import com.wetune.Playback
import com.wetune.Settings

/**
 * The song position, advanced every frame between the player's updates (which come every 250 ms), so a lyric can
 * light up letter by letter smoothly. Never runs ahead of the real position by more than half a second.
 */
@Composable
fun smoothPosition(pos: Long): Long {
    val playing = Playback.playing
    val speed = Playback.speed()
    val at = remember(pos) { pos to System.nanoTime() }
    val now by produceState(pos, at, playing) {
        if (!playing) { value = at.first; return@produceState }
        while (true) androidx.compose.runtime.withFrameNanos { f -> value = at.first + ((f - at.second) / 1_000_000 * speed).toLong().coerceIn(0, 500) }
    }
    return now
}

/**
 * The words of [line] with their timing: the real word timing when the lyrics have it, otherwise (with
 * "Estimate word timing" on) the line's time shared out across its words by length. Null = show the line as a whole.
 */
fun wordsOf(line: Lyrics.Line, nextMs: Long, text: String = line.text): List<Lyrics.Word>? {
    if (text == line.text) line.words?.let { return it }
    if (!Settings.lyricsEstimate || line.ms < 0) return null
    val start = line.words?.firstOrNull()?.ms ?: line.ms
    // end of the line: its own end, else the next line — but never a long instrumental gap
    val natural = line.words?.lastOrNull()?.endMs ?: line.endMs.takeIf { it > start } ?: nextMs
    val end = minOf(if (natural > start) natural else start + 3_000, start + 250L + text.length * 110L)
    val parts = Regex("""\S+\s*""").findAll(text).map { it.value }.toList().ifEmpty { return null }
    val total = parts.sumOf { it.trim().length.coerceAtLeast(1) }.toFloat()
    var t = start.toFloat()
    return parts.map { w ->
        val d = (end - start) * w.trim().length.coerceAtLeast(1) / total
        Lyrics.Word(t.toLong(), (t + d).toLong(), w).also { t += d }
    }
}

/**
 * A lyric line that fills in letter by letter as it's sung: sung letters in [lit], letters still to come in [dim],
 * and the letter being sung fading between the two.
 */
@Composable
fun KaraokeText(words: List<Lyrics.Word>, now: Long, style: TextStyle, lit: Color, dim: Color, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, weight: FontWeight? = null) {
    val text = buildAnnotatedString {
        words.forEach { w ->
            val letters = w.text.trimEnd()
            val n = letters.length.coerceAtLeast(1)
            val span = (w.endMs - w.ms).coerceAtLeast(1)
            letters.forEachIndexed { i, ch ->
                val from = w.ms + span * i / n; val to = w.ms + span * (i + 1) / n
                val p = ((now - from).toFloat() / (to - from).coerceAtLeast(1)).coerceIn(0f, 1f)
                withStyle(SpanStyle(color = lerp(dim, lit, p))) { append(ch) }
            }
            val space = w.text.substring(letters.length)
            if (space.isNotEmpty()) withStyle(SpanStyle(color = if (now >= w.endMs) lit else dim)) { append(space) }
        }
    }
    androidx.compose.foundation.text.BasicText(text, modifier, style = if (weight != null) style.copy(fontWeight = weight) else style, maxLines = maxLines)
}
