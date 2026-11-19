package com.meteomontana.android.ui.screens.gyms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.maplibre.android.camera.CameraPosition

/** Estado del mapa que tiene que sobrevivir a que el MapView se recree (cambiar a Lista y volver). */
class GymsMapState {
    var savedCamera: CameraPosition? = null
    var fittedIds: Set<Long>? = null

    /** Satélite por defecto, igual que el mapa de Escuelas (el botón de capas alterna con el topográfico). */
    var isSatellite by mutableStateOf(true)
}

@Composable
fun rememberGymsMapState(): GymsMapState = remember { GymsMapState() }
