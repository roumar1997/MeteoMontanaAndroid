package com.meteomontana.android.ui.screens.schools

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que arrastra una foto desde que la eliges hasta que se abre el flujo de
 * proponer piedra dentro de su escuela.
 *
 * Va por aquí y no por la ruta de navegación porque un `Uri` de foto no cabe
 * decentemente en una URL —hay que escaparlo, y los permisos de lectura van
 * pegados al Uri original—, y porque la ruta es un contrato que ya usan los
 * enlaces compartidos: meterle campos de un flujo interno lo ensuciaría.
 *
 * Se consume UNA vez ([take]): si se quedara puesto, volver a entrar en la
 * escuela reabriría el flujo de proponer sin que nadie lo haya pedido.
 */
@Singleton
class PhotoProposalSeed @Inject constructor() {

    data class Seed(
        val schoolId: String,
        /** Uri de la foto, como texto (se reconstruye al usarla). */
        val photoUri: String,
        /** Dónde se hizo la foto, según su EXIF. */
        val lat: Double,
        val lon: Double,
        /** Orientación sugerida por el rumbo de la cámara, si la foto lo traía. */
        val aspect: String?
    )

    private var pendiente: Seed? = null

    fun put(seed: Seed) {
        pendiente = seed
    }

    /** Devuelve y BORRA la semilla si es de esta escuela. */
    fun take(schoolId: String): Seed? {
        val s = pendiente ?: return null
        if (s.schoolId != schoolId) return null
        pendiente = null
        return s
    }

    fun clear() {
        pendiente = null
    }
}
