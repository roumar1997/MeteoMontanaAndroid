package com.meteomontana.android.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Rumbo del móvil (0-360º, norte = 0) desde el sensor de rotación, cuantizado
 * a pasos de 15º para no re-pintar el marcador del mapa a 50 Hz. Null si el
 * dispositivo no tiene sensor. Se usa para el cono de dirección del punto
 * azul ("¿estoy mirando hacia la piedra?").
 */
@Composable
fun rememberDeviceHeading(): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val rot = FloatArray(9)
            private val orient = FloatArray(3)
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, event.values)
                SensorManager.getOrientation(rot, orient)
                val az = Math.toDegrees(orient[0].toDouble()).toFloat()
                val normalized = (az + 360f) % 360f
                val quantized = (Math.round(normalized / 15f) * 15f) % 360f
                if (quantized != heading) heading = quantized
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm.unregisterListener(listener) }
    }
    return heading
}
