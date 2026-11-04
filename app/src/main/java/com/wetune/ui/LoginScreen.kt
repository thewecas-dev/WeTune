package com.wetune.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding


import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wetune.Settings
import com.wetune.io
import com.wetune.toast
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
fun LoginScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().background(WT.c.bg).statusBarsPadding()) {
        IconBtn(Ic.Back, "Back", back)
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->
                CookieManager.getInstance().removeAllCookies(null)
                WebView(ctx).apply {
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
                            io(then = { toast(Sync.account?.let { "Signed in as ${it.name}" } ?: "Sign-in didn't complete. Try again.") }) {
                                Sync.refresh(force = true)
                            }
                            back()
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            },
        )
    }
}
