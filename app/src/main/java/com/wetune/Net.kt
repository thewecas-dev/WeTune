package com.wetune

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
    val b = Request.Builder().url(url).header("User-Agent", UA)
    headers.forEach { (k, v) -> b.header(k, v) }
    http.newCall(b.build()).execute().use { r ->
        if (!r.isSuccessful) throw IOException("Request failed (${r.code}) for ${url.substringBefore('?')}")
        return r.body!!.string()
    }
}

fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

fun String.hash(algorithm: String): String =
    MessageDigest.getInstance(algorithm).digest(toByteArray()).joinToString("") { "%02x".format(it) }

fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/** org.json returns "null" for JSON null; this returns a real null. */
fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

fun toast(msg: String) {
    Handler(Looper.getMainLooper()).post { Toast.makeText(App.ctx, msg, Toast.LENGTH_SHORT).show() }
}
