package com.meteomontana.android.domain.usecase.map

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.util.Geo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Punto geográfico neutro (sin depender de MapLibre/CoreLocation). */
data class GeoPoint(val lat: Double, val lon: Double)

/** Rectángulo geográfico neutro: esquina suroeste (south, west) y noreste (north, east). */
data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    /** Todos los puntos coinciden (una sola piedra): no se puede encuadrar un rectángulo sin área. */
    val isDegenerate: Boolean get() = south == north && west == east
}

/**
 * Geometría PURA de los mapas de escuela: parseo del trazado de un muro y orden
 * de pintado de los marcadores. Extraído de MarkerPlacer/SchoolMapView para
 * testearlo sin MapLibre y tener UNA sola definición del formato del muro (antes
 * duplicada en Android y iOS). Hexagonal: no conoce el framework de mapas.
 */
object MapGeometry {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parsea el `path` de un muro ("[[lat,lon],[lat,lon],...]") a puntos.
     *  Vacío si es nulo/blanco o no parsea (tolerante, nunca lanza). */
    fun parseWallPath(path: String?): List<GeoPoint> {
        if (path.isNullOrBlank()) return emptyList()
        return try {
            json.parseToJsonElement(path).jsonArray.mapNotNull { el ->
                // Por-elemento: un punto malformado se salta (no invalida el resto),
                // igual que el parser org.json original.
                try {
                    val pair = el.jsonArray
                    if (pair.size < 2) null
                    else GeoPoint(pair[0].jsonPrimitive.double, pair[1].jsonPrimitive.double)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Punto MEDIO del trazado de un muro (donde va su número/marcador), o null
     *  si no hay trazado válido (menos de 2 puntos). */
    fun wallMidpoint(path: String?): GeoPoint? {
        val pts = parseWallPath(path)
        return if (pts.size >= 2) pts[pts.size / 2] else null
    }

    /** Orden de pintado por tipo: piedras primero (0), luego parking (1), zona
     *  (2) y escuela/otros (3) — para que los sectores no queden tapados por las
     *  piedras. Menor = se pinta antes (debajo). */
    fun paintRank(type: String): Int = when (type.uppercase()) {
        "BLOCK" -> 0
        "PARKING" -> 1
        "ZONE" -> 2
        else -> 3
    }

    /** Centroide (media aritmética) de un conjunto de puntos; null si vacío. */
    fun centroid(points: List<GeoPoint>): GeoPoint? {
        if (points.isEmpty()) return null
        return GeoPoint(points.sumOf { it.lat } / points.size, points.sumOf { it.lon } / points.size)
    }

    /** Bloques dentro de [km] de (lat,lon), excluyendo opcionalmente [excludeId].
     *  Usado para "volar a la zona" de un parking (sectores/piedras cercanas). */
    fun blocksWithinKm(
        blocks: List<Block>, lat: Double, lon: Double, km: Double, excludeId: String? = null
    ): List<Block> = blocks.filter {
        it.id != excludeId && Geo.haversineKm(lat, lon, it.lat, it.lon) <= km
    }

    /**
     * Qué encuadra el mapa de una escuela: SOLO las piedras/muros (type BLOCK), nunca parkings, sectores ni
     * aproximaciones (una piedra lejana de un parking estiraba el zoom y dejaba las piedras diminutas).
     * [dimmedIds] = piedras que el filtro de grado deja atenuadas: no cuentan. null si no queda ninguna
     * (el que llama decide: al entrar cae a encuadrar todo; al filtrar no mueve la cámara).
     */
    fun boundsForVisibleBlocks(blocks: List<Block>, dimmedIds: Set<String> = emptySet()): GeoBounds? {
        val stones = blocks.filter { it.type.uppercase() == "BLOCK" && it.id !in dimmedIds }
        if (stones.isEmpty()) return null
        return GeoBounds(
            south = stones.minOf { it.lat }, west = stones.minOf { it.lon },
            north = stones.maxOf { it.lat }, east = stones.maxOf { it.lon }
        )
    }
}
