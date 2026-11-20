package com.wetune.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.wetune.Settings
import com.wetune.youtube.InnerTube
import com.wetune.youtube.Sync

private const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
    "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

/**
 * Google sign-in in a WebView. The captured cookie belongs to WeTune alone, so no browser rotates it
 * out from under us (the reason a cookie pasted from a desktop browser stops working).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen() {
    val bg = WT.c.bg
    Column(Modifier.fillMaxSize().background(bg).statusPad().navigationBarsPadding()) {
        BackLink("Back")
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->
                CookieManager.getInstance().removeAllCookies(null)
                WebView(ctx).apply {
                    setBackgroundColor(bg.toArgb()) // no white flash between Google's pages in dark mode
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        private var done = false
                        override fun onPageFinished(view: WebView, url: String) {
                            if (done || !url.startsWith(InnerTube.ORIGIN)) return
                            val cookie = CookieManager.getInstance().getCookie(InnerTube.ORIGIN).orEmpty()
                            if (InnerTube.sapisid(cookie) == null) return
                            done = true
                            Settings.cookie = cookie
                            Sync.pull(force = true)
                            Nav.back()
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            },
        )
    }
}

/**
 * Spotify sign-in in a WebView, only to show Spotify Canvas on the cover. Once Spotify's web player is reached
 * with the login cookie (sp_dc) set, the cookie is kept in WeTune's settings and this closes. It's only ever sent
 * to Spotify, and never written to a settings file.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen() {
    val bg = WT.c.bg
    Column(Modifier.fillMaxSize().background(bg).statusPad().navigationBarsPadding()) {
        BackLink("Back")
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(bg.toArgb())
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        private var done = false
                        override fun onPageFinished(view: WebView, url: String) {
                            if (done || !url.startsWith("https://open.spotify.com")) return
                            val dc = CookieManager.getInstance().getCookie("https://open.spotify.com").orEmpty()
                                .split(';').map { it.trim() }.firstOrNull { it.startsWith("sp_dc=") }?.removePrefix("sp_dc=")
                            if (dc.isNullOrBlank()) return
                            done = true
                            Settings.spotifyDc = dc
                            com.wetune.toast("Signed in to Spotify: Canvas will show on covers that have one")
                            Nav.back()
                        }
                    }
                    loadUrl("https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
                }
            },
        )
    }
}
