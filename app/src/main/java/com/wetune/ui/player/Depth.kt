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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp

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

/**
 * Depth style "3D box": the screen as a box seen from the front. The back wall is where the background shows; four
 * walls angle in from the screen's edges, tinted with the cover's color (ceiling lightest, sides darker into the
 * corners, floor darkest under the controls), with faint corner edges and shadow where the walls meet the back.
 * Tilting slides the back wall the other way, so the walls stretch and squash like a real box. [k] = intensity.
 */
@Composable
fun DepthBox(band: androidx.compose.ui.graphics.Color, tilt: MutableState<Offset>, k: Float, backBottom: androidx.compose.ui.unit.Dp) {
    val black = androidx.compose.ui.graphics.Color.Black
    fun shade(f: Float) = androidx.compose.ui.graphics.lerp(band, black, f)
    androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val side = 30.dp.toPx() * k.coerceIn(0.4f, 1.6f); val top = 64.dp.toPx() * k.coerceIn(0.4f, 1.6f)
        val bx = -tilt.value.x * 16.dp.toPx() * k; val by = tilt.value.y * 12.dp.toPx() * k
        val l = side + bx; val r = w - side + bx; val t = top + by; val b = backBottom.toPx() + by
        fun wall(p: List<Offset>, brush: androidx.compose.ui.graphics.Brush) {
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(p[0].x, p[0].y); p.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            drawPath(path, brush)
        }
        val tl = Offset(l, t); val tr = Offset(r, t); val bl = Offset(l, b); val br = Offset(r, b)
        wall(listOf(Offset(0f, 0f), Offset(w, 0f), tr, tl), brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(shade(0.55f), shade(0.82f)), 0f, t))
        wall(listOf(Offset(0f, 0f), tl, bl, Offset(0f, h)), brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(shade(0.7f), shade(0.9f)), 0f, l.coerceAtLeast(1f)))
        wall(listOf(Offset(w, 0f), tr, br, Offset(w, h)), brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(shade(0.9f), shade(0.7f)), r, w))
        wall(listOf(Offset(0f, h), Offset(w, h), br, bl), brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(shade(0.93f), shade(0.9f)), b, h))
        // shadow where the walls meet the back wall
        val s = 40.dp.toPx(); val dark = black.copy(alpha = 0.7f); val clear = black.copy(alpha = 0f)
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(dark, clear), t, t + s), tl, androidx.compose.ui.geometry.Size(r - l, s))
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(clear, dark), b - s, b), Offset(l, b - s), androidx.compose.ui.geometry.Size(r - l, s))
        drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(dark, clear), l, l + s), tl, androidx.compose.ui.geometry.Size(s, b - t))
        drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(clear, dark), r - s, r), Offset(r - s, t), androidx.compose.ui.geometry.Size(s, b - t))
        // the box's edges
        val edge = androidx.compose.ui.graphics.Color.White
        drawLine(edge.copy(alpha = 0.14f), Offset(0f, 0f), tl); drawLine(edge.copy(alpha = 0.14f), Offset(w, 0f), tr)
        drawLine(edge.copy(alpha = 0.08f), Offset(0f, h), bl); drawLine(edge.copy(alpha = 0.08f), Offset(w, h), br)
        drawRect(edge.copy(alpha = 0.1f), tl, androidx.compose.ui.geometry.Size(r - l, b - t), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
    }
}

