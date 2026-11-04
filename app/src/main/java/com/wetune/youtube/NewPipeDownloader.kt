package com.wetune.youtube

import com.wetune.UA
import com.wetune.http
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** Bridges NewPipe Extractor's HTTP calls to our OkHttp client. */
object NewPipeDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val b = okhttp3.Request.Builder().url(request.url()).header("User-Agent", UA)
        request.headers().forEach { (name, values) ->
            b.removeHeader(name)
            values.forEach { b.addHeader(name, it) }
        }
        val body = request.dataToSend()?.toRequestBody()
            ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null
        b.method(request.httpMethod(), body)
        http.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }
}
