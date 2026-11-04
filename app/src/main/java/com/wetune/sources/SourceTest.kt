package com.wetune.sources

import com.wetune.AudioInfo
import com.wetune.UA
import com.wetune.http
import com.wetune.youtube.YouTube
import okhttp3.Request
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Settings → Sources → Test: for each source (every add-on, JioSaavn, YouTube) search a song, take the top result,
 * get its stream and download the first 64 KB to prove it really plays — with timings and the quality it offers.
 */
object SourceTest {
    data class Result(
        val source: String,
        val ok: Boolean,
        val searchMs: Long = 0,
        val streamMs: Long = 0,
        val found: String? = null, // "Title · Artist"
        val quality: String? = null,
        val problem: String? = null,
    )

    private val pool = Executors.newCachedThreadPool()

    /** Blocking. Tests every source at once for [query]; results in Settings order (add-ons, JioSaavn, YouTube). */
    fun run(query: String): List<Result> {
        val jobs = Addons.list.map { a -> pool.submit<Result> { addon(a, query) } } +
            pool.submit<Result> { saavn(query) } + pool.submit<Result> { youtube(query) }
        return jobs.map { f -> runCatching { f.get(90, TimeUnit.SECONDS) }.getOrElse { Result("?", false, problem = "Timed out") } }
    }

    private inline fun <T> timed(block: () -> T): Pair<T, Long> { val t0 = System.nanoTime(); val r = block(); return r to (System.nanoTime() - t0) / 1_000_000 }

    /** Downloads the first 64 KB of [url]; returns a problem description, or null when it streams. */
    private fun probe(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", UA).header("Range", "bytes=0-65535").build()).execute().use { r ->
            if (r.code !in listOf(200, 206)) return "The file answered ${r.code}"
            val got = r.body?.byteStream()?.use { s -> val b = ByteArray(65_536); var n = 0; while (n < b.size) { val k = s.read(b, n, b.size - n); if (k < 0) break; n += k }; n } ?: 0
            if (got < 1024) "The file came back empty" else null
        }
    }.getOrElse { "Couldn't download the file (${it.message})" }

    private fun addon(a: Addons.Addon, q: String): Result {
        val name = a.name + if (a.enabled) "" else " (off)"
        val (tracks, searchMs) = timed { runCatching { Addons.search(a, q) } }
        val top = tracks.getOrElse { return Result(name, false, searchMs, problem = "Search failed: ${it.message}") }.firstOrNull()
            ?: return Result(name, false, searchMs, problem = "Online, but found nothing for this song")
        val (stream, streamMs) = timed { runCatching { Addons.stream(top.ref) } }
        val s = stream.getOrElse { return Result(name, false, searchMs, problem = "Found it, but no stream: ${it.message}", found = "${top.title} · ${top.artist}") }
            ?: return Result(name, false, searchMs, problem = "Found it, but the stream is missing or encrypted", found = "${top.title} · ${top.artist}")
        val quality = listOfNotNull(s.codec, s.bitDepth.takeIf { it > 0 }?.let { "$it-bit" }, s.sampleRate.takeIf { it > 0 }?.let { "${AudioInfo.formatKhz(it)} kHz" }).joinToString(" · ")
        val problem = probe(s.url)
        return Result(name, problem == null, searchMs, streamMs, "${top.title} · ${top.artist}", quality, problem)
    }

    private fun saavn(q: String): Result {
        val (tracks, searchMs) = timed { runCatching { JioSaavn.search(q) } }
        val top = tracks.getOrElse { return Result("JioSaavn", false, searchMs, problem = "Search failed: ${it.message}") }.firstOrNull()
            ?: return Result("JioSaavn", false, searchMs, problem = "Online, but found nothing for this song")
        val (url, streamMs) = timed { runCatching { JioSaavn.streamUrl(top.ref, true) } }
        val u = url.getOrElse { return Result("JioSaavn", false, searchMs, problem = "Found it, but no stream: ${it.message}", found = "${top.title} · ${top.artist}") }
        val problem = probe(u)
        return Result("JioSaavn", problem == null, searchMs, streamMs, "${top.title} · ${top.artist}", if (JioSaavn.has320(top.ref)) "AAC · 320 kbps" else "AAC · 160 kbps", problem)
    }

    private fun youtube(q: String): Result {
        val (tracks, searchMs) = timed { runCatching { YouTube.search(q) } }
        val top = tracks.getOrElse { return Result("YouTube", false, searchMs, problem = "Search failed: ${it.message}") }.firstOrNull()
            ?: return Result("YouTube", false, searchMs, problem = "Found nothing for this song")
        val (url, streamMs) = timed { runCatching { YouTube.streamUrl(top.ref, true) } }
        val u = url.getOrElse { return Result("YouTube", false, searchMs, problem = "Found it, but no stream: ${it.message}", found = "${top.title} · ${top.artist}") }
        val itag = android.net.Uri.parse(u).getQueryParameter("itag")?.toIntOrNull()
        val quality = itag?.let { "${AudioInfo.ItagFormat[it] ?: "audio"} · ${AudioInfo.ItagKbps[it] ?: "?"} kbps" }
        val problem = probe(u)
        return Result("YouTube", problem == null, searchMs, streamMs, "${top.title} · ${top.artist}", quality, problem)
    }
}
