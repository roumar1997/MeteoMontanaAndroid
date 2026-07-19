package com.meteomontana.android.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.ui.screens.detail.CorrectionGhost
import com.meteomontana.android.ui.screens.detail.EditFace
import com.meteomontana.android.ui.screens.detail.initialEditFaces

/**
 * PUENTE mapa ↔ flujo de propuestas ("+ PROPONER").
 *
 * Antes esto eran 12 variables sueltas + 5 callbacks `var` repartidos por
 * SchoolMap (un event-bus manual): el flujo REGISTRA aquí sus callbacks y el
 * mapa los INVOCA vía los métodos handle*. Al ser una clase @Stable con
 * mutableStateOf, los listeners de MapLibre (registrados una sola vez en el
 * factory) leen los flags SIEMPRE frescos por referencia — fuera los 5
 * rememberUpdatedState que parcheaban eso.
 */
@Stable
internal class ProposalMapBridge {
    /** Diálogo del flujo "+ PROPONER" abierto. */
    var proposeOpen by mutableStateOf(false)
    /** Esperando a que el usuario toque el mapa para fijar coords. */
    var waitingMapTap by mutableStateOf(false)
    /** Modo "corregir posición": el siguiente tap elige el marker a mover. */
    var correctionMode by mutableStateOf(false)
    /** Marker fantasma con la posición candidata (corrección). */
    var correctionGhost by mutableStateOf<CorrectionGhost?>(null)
    /** Nombre del elemento que se está corrigiendo (para el banner). */
    var correctionTargetName by mutableStateOf<String?>(null)
    /** Trazado de muro del flujo CREAR activo. */
    var wallTracing by mutableStateOf(false)
    /** Polilínea del muro en construcción (preview). */
    var wallPreview by mutableStateOf<List<Pair<Double, Double>>>(emptyList())

    // Callbacks que registra ProposeContributionFlow (no dirigen recomposición).
    var mapTapCallback: ((Double, Double) -> Unit)? = null
    var markerTapForCorrection: ((Block) -> Unit)? = null
    var acceptCorrectionCallback: (() -> Unit)? = null
    var wallUndoCallback: (() -> Unit)? = null
    var wallDoneCallback: (() -> Unit)? = null

    /** Tap del usuario en el mapa durante el flujo. */
    fun handleMapTap(lat: Double, lon: Double) {
        mapTapCallback?.invoke(lat, lon)
        // No reseteamos waitingMapTap en corrección ni en trazado (siguen activos).
        if (!correctionMode && !wallTracing) waitingMapTap = false
    }

    /** Tap en un marker existente durante la corrección de posición. */
    fun handleMarkerTapForCorrection(block: Block) { markerTapForCorrection?.invoke(block) }

    fun acceptCorrection() { acceptCorrectionCallback?.invoke() }
    fun wallUndo() { wallUndoCallback?.invoke() }
    fun wallDone() { wallDoneCallback?.invoke() }

    /** Cancela/cierra el flujo entero: TODO el estado y los callbacks a cero. */
    fun reset() {
        proposeOpen = false
        waitingMapTap = false
        correctionMode = false
        correctionGhost = null
        correctionTargetName = null
        wallTracing = false
        wallPreview = emptyList()
        mapTapCallback = null
        markerTapForCorrection = null
        acceptCorrectionCallback = null
        wallUndoCallback = null
        wallDoneCallback = null
    }
}

/**
 * ESTADO del editor de piedra/muro ("+ AÑADIR VÍAS" / "✎ CORREGIR VÍA").
 *
 * Vive IZADO en SchoolMap (no dentro del mapa expandido) para que no se pierda
 * lo editado al plegar/expandir el mapa mientras se traza el muro. Antes eran
 * 9 variables sueltas.
 */
@Stable
internal class WallEditState {
    /** Piedra en edición ("+ AÑADIR VÍAS" abierto), o null. */
    var target by mutableStateOf<Block?>(null)
    var faces by mutableStateOf<List<EditFace>>(emptyList())
    var geometry by mutableStateOf("POINT")
    var direction by mutableStateOf("LTR")
    var selectedFace by mutableStateOf(0)
    /** Polilínea trazada confirmada (LISTO), o null si aún no hay. */
    var tracedPath by mutableStateOf<List<Pair<Double, Double>>?>(null)
    /** Trazando el muro sobre el mapa AHORA (el editor se oculta mientras). */
    var tracing by mutableStateOf(false)
    /** Polilínea en construcción durante el trazado. */
    var preview by mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    /** Vía concreta en corrección ("✎ CORREGIR VÍA"), o null. */
    var editingLine by mutableStateOf<Pair<Block, BlockLine>?>(null)

    /**
     * Abre el editor para [block] con el estado YA poblado (no en un
     * LaunchedEffect): así abre sin un frame vacío (el "salto").
     */
    fun openFor(block: Block) {
        faces = initialEditFaces(block)
        geometry = block.geometry.ifBlank { "POINT" }
        direction = block.direction.ifBlank { "LTR" }
        selectedFace = 0
        tracedPath = null
        tracing = false
        preview = emptyList()
        target = block
    }

    fun startTracing() { preview = emptyList(); tracing = true }
    fun addPoint(lat: Double, lon: Double) { preview = preview + (lat to lon) }
    fun undoPoint() { preview = preview.dropLast(1) }
    fun finishTracing() { tracedPath = preview; tracing = false }
    fun cancelTracing() { tracing = false }
}
