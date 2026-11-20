package com.wetune

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.LaunchedEffect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.wetune.ui.Root
import com.wetune.youtube.Sync

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // the launch screen (Theme.WeTune.Starting): once the app is ready the mark grows a little and fades while the
        // blue lifts away, revealing the first frame underneath
        installSplashScreen().setOnExitAnimationListener { splash ->
            val fade = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
            splash.iconView.animate().scaleX(1.35f).scaleY(1.35f).alpha(0f).setDuration(320).setInterpolator(fade).start()
            splash.view.animate().alpha(0f).setStartDelay(60).setDuration(320).setInterpolator(fade).withEndAction { splash.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        // see-through bars, no scrims: the app's own background shows under the status and navigation bars
        val clear = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = clear, navigationBarStyle = clear)
        if (Build.VERSION.SDK_INT >= 29) { window.isStatusBarContrastEnforced = false; window.isNavigationBarContrastEnforced = false }
        // draw into the camera cutout's band too, always: with the status bar hidden (Now Playing) the app keeps its
        // full height instead of being letterboxed below the cutout, so nothing moves when the bar hides or returns
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        Playback.connect(this)
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray())
        setContent {
            // Portrait unless the user opts in; "user" orientation then follows the phone's own auto-rotate toggle.
            LaunchedEffect(Settings.autoRotate) {
                requestedOrientation = if (Settings.autoRotate) ActivityInfo.SCREEN_ORIENTATION_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            Root()
        }
    }

    override fun onStart() {
        super.onStart()
        App.foreground = true
        Sync.pull() // picks up changes made on YouTube Music elsewhere
    }

    override fun onStop() {
        App.foreground = false
        super.onStop()
    }

    override fun onDestroy() {
        Playback.release()
        super.onDestroy()
    }
}
