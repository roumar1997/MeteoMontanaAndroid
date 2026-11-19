package com.meteomontana.android.domain.model.gym

import kotlin.math.floor
import kotlin.math.pow

/**
 * Elemento del mapa: un rocodromo suelto ([isCluster] false) o un grupo de ellos con un numero encima.
 * [id] es estable entre llamadas con el mismo zoom, para que la animacion no parpadee.
 */
data class GymMapItem(val id: String, val lat: Double, val lon: Double, val gyms: List<Gym>) {
    val isCluster: Boolean get() = gyms.size > 1
    val count: Int get() = gyms.size
}

/**
 * Agrupacion por rejilla segun el zoom. Vive en shared para que iOS (MapKit) y Android (Google Maps)
 * agrupen EXACTAMENTE igual y las capturas de paridad coincidan.
 *
 * Con zoom de mapa web (256 px por tesela) una celda de [CELL_PX] px mide `360 * CELL_PX / (256 * 2^zoom)`
 * grados; por encima de [MAX_CLUSTER_ZOOM] ya no se agrupa.
 */
object GymClusterer {

    const val CELL_PX = 40.0
    const val MAX_CLUSTER_ZOOM = 11.0

    fun cellSizeDegrees(zoom: Double): Double = 360.0 * CELL_PX / (256.0 * 2.0.pow(zoom))

    fun cluster(gyms: List<Gym>, zoom: Double): List<GymMapItem> {
        if (gyms.isEmpty()) return emptyList()
        if (zoom >= MAX_CLUSTER_ZOOM) return gyms.sortedBy { it.id }.map { single(it) }
        val cell = cellSizeDegrees(zoom)
        val groups = gyms.groupBy { floor(it.lon / cell).toLong() to floor(it.lat / cell).toLong() }
        return groups.map { (key, members) ->
            val sorted = members.sortedBy { it.id }
            if (sorted.size == 1) single(sorted[0])
            else GymMapItem(
                id = "c:${key.first}:${key.second}",
                lat = sorted.map { it.lat }.average(),
                lon = sorted.map { it.lon }.average(),
                gyms = sorted
            )
        }.sortedBy { it.id }
    }

    private fun single(g: Gym) = GymMapItem("g:${g.id}", g.lat, g.lon, listOf(g))
}
