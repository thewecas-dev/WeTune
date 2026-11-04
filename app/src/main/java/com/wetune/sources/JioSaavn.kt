package com.wetune.sources

import com.wetune.httpGet
import com.wetune.objects
import com.wetune.urlEncode
import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** JioSaavn via its unofficial web API. Stream URLs come DES-encrypted with a public key. */
object JioSaavn : Source {
    override val label = "JioSaavn"
    private const val NO_320 = "|no320"

    /** Whether this song has a 320 kbps file. */
    fun has320(ref: String) = !ref.endsWith(NO_320)

    override fun search(query: String): List<Track> {
        val json = JSONObject(
            httpGet("https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=30&p=1&q=${query.urlEncode()}")
        )
        return json.optJSONArray("results").objects().mapNotNull { r ->
            val m = r.optJSONObject("more_info") ?: return@mapNotNull null
            val enc = m.optString("encrypted_media_url").ifEmpty { return@mapNotNull null }
            val artists = m.optJSONObject("artistMap")?.optJSONArray("primary_artists").objects().map { it.optString("name") }
            Track(
                source = "saavn",
                ref = if (m.optString("320kbps") == "true") enc else enc + NO_320,
                title = unescape(r.optString("title")),
                artist = unescape(artists.joinToString(", ").ifEmpty { r.optString("subtitle") }),
                album = unescape(m.optString("album")),
                art = r.optString("image").ifEmpty { null }?.replace("150x150", "500x500")?.replace("http://", "https://"),
                durationMs = (m.optString("duration").toLongOrNull() ?: 0) * 1000,
            )
        }
    }

    fun decrypt(encrypted: String): String {
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("38346591".toByteArray(), "DES"))
        return String(cipher.doFinal(Base64.getMimeDecoder().decode(encrypted.trim())))
    }

    /** The decrypted URL points at the 96 kbps file; the 160/320 kbps files sit beside it. */
    fun withQuality(url: String, high: Boolean): String =
        url.replace("_96.", if (high) "_320." else "_160.").replace("http://", "https://")

    override fun streamUrl(ref: String, high: Boolean): String {
        val no320 = ref.endsWith(NO_320)
        return withQuality(decrypt(ref.removeSuffix(NO_320)), high && !no320)
    }

    private fun unescape(s: String) =
        s.replace("&quot;", "\"").replace("&#039;", "'").replace("&apos;", "'").replace("&amp;", "&")
}
