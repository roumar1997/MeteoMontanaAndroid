package com.meteomontana.android.ui.screens.gyms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.maplibre.android.camera.CameraPosition

/** Estado del mapa que tiene que sobrevivir a que el MapView se recree (cambiar a Lista y volver). */
class GymsMapState {
    var savedCamera: CameraPosition? = null
    var fittedIds: Set<Long>? = null
}

@Composable
fun rememberGymsMapState(): GymsMapState = remember { GymsMapState() }
