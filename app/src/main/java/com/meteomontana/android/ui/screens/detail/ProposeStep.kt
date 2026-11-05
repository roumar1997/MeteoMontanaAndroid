package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.util.AppText
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.outbox.toQueued
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

// Pasos del flujo de proponer (qué se enseña en cada momento), el fantasma de una corrección y la semilla de foto.

/** Datos del marker fantasma que SchoolMap renderiza durante una corrección. */
data class CorrectionGhost(
    val originalId: String?,   // id del marker original a "fantasmear". null=escuela.
    val newLat: Double?,
    val newLon: Double?
)

/** Foto de "Enviar piedra" con lo que se pudo leer de su EXIF. */
data class PhotoSeed(
    val photoUri: android.net.Uri,
    val lat: Double,
    val lon: Double,
    /** Orientación sugerida por el rumbo de la cámara, si la foto lo traía. */
    val aspect: String?
)

internal sealed interface ProposeStep {
    data object TypePicker    : ProposeStep
    data object WaitingMapTap : ProposeStep
    data class  Form(val lat: Double, val lon: Double)        : ProposeStep  // PARKING
    data class  BoulderForm(val lat: Double, val lon: Double) : ProposeStep  // BOULDER
    /** Foto ya colocada donde se hizo: solo falta confirmar el sitio. */
    data class  PhotoConfirm(val lat: Double, val lon: Double) : ProposeStep
    data class  WallTracing(val lat: Double, val lon: Double) : ProposeStep  // trazar muro en el mapa
    data class  SectorForm(val lat: Double, val lon: Double)  : ProposeStep  // SECTOR
    data object SchoolNameCorrection  : ProposeStep  // SCHOOL_NAME_CORRECTION, sin tocar el mapa
    data object SchoolStyleCorrection : ProposeStep  // SCHOOL_STYLE_CORRECTION, sin tocar el mapa
    data object CorrectionPickTarget : ProposeStep                          // espera tap en marker existente
    data class  CorrectionMoving(
        val targetId: String?,    // null = mover escuela entera
        val targetName: String,
        val oldLat: Double,
        val oldLon: Double,
        val targetType: String,   // BLOCK / PARKING / ZONE / SCHOOL
        val newLat: Double? = null,  // null = aún no fijada
        val newLon: Double? = null
    ) : ProposeStep
    data class  Success(val queued: Boolean = false) : ProposeStep
}

// ─── Punto de entrada ────────────────────────────────────────────────────────
