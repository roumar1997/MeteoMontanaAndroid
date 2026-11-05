package com.meteomontana.android.ui.screens.detail

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * La piedra que se está proponiendo, mientras se rellena: vive por encima del formulario y del editor de líneas para
 * no perder nada al pasar de uno a otro. Una piedra puede tener VARIAS CARAS (fotos), cada una con sus vías.
 * Antes eran diez variables sueltas en ProposeContributionFlow (2026-11-05).
 */
@Stable
class BoulderDraft {
    /** Se está proponiendo una piedra (y no un parking o un sector). */
    var mode by mutableStateOf(false)
    var name by mutableStateOf("")
    var faces by mutableStateOf(listOf(BoulderFaceForm()))
    /** Cara cuyo editor de líneas está abierto. */
    var topoFaceIdx by mutableStateOf<Int?>(null)
    var sectorBlockId by mutableStateOf<String?>(null)
    /** BOULDER (bloque) / ROUTE (vía). */
    var discipline by mutableStateOf("BOULDER")
    /** Orientación de la piedra elegida por el autor (opcional; su primer voto). */
    var orientation by mutableStateOf<String?>(null)
    /** En el mapa: POINT (marcador) o LINE (muro = polilínea trazada). */
    var geometry by mutableStateOf("POINT")
    /** Sentido de numeración de las vías del muro: LTR (izq→der) / RTL (der→izq). */
    var direction by mutableStateOf("LTR")
    /** Polilínea del muro, puntos [lat, lon] (vacía con geometry = POINT). */
    var path by mutableStateOf<List<Pair<Double, Double>>>(emptyList())

    /** Sigue con un borrador guardado a medias. */
    fun restore(d: BoulderDraftStore.Draft) {
        mode = true; name = d.name; faces = d.faces; sectorBlockId = d.sectorBlockId; discipline = d.discipline
        geometry = d.geometry; direction = d.direction; orientation = d.orientation; path = d.path
    }

    /** Empieza una piedra nueva (la orientación y la cara abierta se conservan, como antes). */
    fun startNew() {
        mode = true; faces = listOf(BoulderFaceForm()); name = ""; sectorBlockId = null; discipline = "BOULDER"
        geometry = "POINT"; direction = "LTR"; path = emptyList()
    }
}
