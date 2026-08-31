package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.Block

/** Un sector visto DESDE un parking: lo lejos que queda y cuántas piedras tiene. */
data class SectorCercano(
    val sector: Block,
    val metros: Int,
    val piedras: Int
) {
    /** "350 m" / "1,2 km" — la unidad que se lee de un vistazo. */
    val distanciaTexto: String
        get() = if (metros < 1000) "$metros m"
                else "${(metros / 100) / 10.0}".replace('.', ',') + " km"
}

/**
 * Los sectores de una escuela ordenados por cercanía A UN PARKING.
 *
 * El parking es la puerta de entrada: aparcas y desde ahí decides a qué zona
 * subes. Por eso la distancia se mide desde el parking y no desde el usuario —
 * lo que importa en ese momento es cuánto queda por andar.
 *
 * En la capa compartida para que Android e iOS ordenen y midan igual.
 */
object SectoresDesdeParking {

    fun calcular(parking: Block, todos: List<Block>): List<SectorCercano> =
        todos.asSequence()
            .filter { it.type == "ZONE" }
            .map { sector ->
                SectorCercano(
                    sector = sector,
                    metros = (Geo.haversineKm(parking.lat, parking.lon, sector.lat, sector.lon) * 1000)
                        .toInt(),
                    piedras = todos.count { it.sectorBlockId == sector.id }
                )
            }
            .sortedBy { it.metros }
            .toList()
}
