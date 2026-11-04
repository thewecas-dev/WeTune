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
import com.wetune.ui.AppUi
import com.wetune.ui.WeTuneTheme
import com.wetune.youtube.Sync

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            WeTuneTheme { AppUi() }
        }
    }

    override fun onResume() {
        super.onResume()
        io { Sync.refresh() } // picks up likes made on other devices
    }

    override fun onDestroy() {
        Playback.release()
        super.onDestroy()
    }
}
