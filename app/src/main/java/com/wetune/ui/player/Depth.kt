package com.wetune.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * How far the phone is tilted from the way it's being held, about −1…1 on each axis, smoothed. The resting angle
 * drifts in slowly, so holding the phone at any angle reads as centered; only movement makes the scene shift.
 * Listens only while [enabled] (Settings → Depth effect) and while the player is on screen.
 */
@Composable
fun rememberTilt(enabled: Boolean): MutableState<Offset> {
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val ctx = LocalContext.current
    DisposableEffect(enabled) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || sensor == null) { tilt.value = Offset.Zero; return@DisposableEffect onDispose { } }
        var restX = Float.NaN; var restY = 0f
        var x = 0f; var y = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val gx = e.values[0] / SensorManager.GRAVITY_EARTH; val gy = e.values[1] / SensorManager.GRAVITY_EARTH
                if (restX.isNaN()) { restX = gx; restY = gy }
                restX += (gx - restX) * 0.01f; restY += (gy - restY) * 0.01f // the resting angle follows slowly
                x += ((gx - restX) * 2.2f - x) * 0.18f; y += ((gy - restY) * 2.2f - y) * 0.18f // smooth the motion
                tilt.value = Offset(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener); tilt.value = Offset.Zero }
    }
    return tilt
}
