package com.meteomontana.android.domain.util

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utilidades geográficas puras, compartidas entre Android e iOS (KMP).
 *
 * Solo usa `kotlin.math` — nada de `java.lang.Math` (que no existe en iOS),
 * por eso convertimos grados a radianes a mano con [toRadians] en vez de
 * `Math.toRadians`.
 */
object Geo {

    private const val EARTH_RADIUS_KM = 6371.0

    /** Distancia en km entre dos puntos (lat/lon en grados) por Haversine. */
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1).toRadians()
        val dLon = (lon2 - lon1).toRadians()
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1.toRadians()) * cos(lat2.toRadians()) *
                sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
}
