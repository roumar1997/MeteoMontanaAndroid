package com.meteomontana.android.ui.screens.detail.guide

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Encuadre de una foto de satélite hecha a mano con las teselas del mapa (Web Mercator, 256 px por tesela).
 *
 * Sustituye a `MapSnapshotter` de MapLibre: en la 11.5 se cierra la app al pegar su logo en la foto (en Android 7+
 * el logo es un vector y el código solo sabe leer PNG). Aquí no hay logo: solo cálculo puro, sin Android, con tests.
 *
 * El área pedida se amplía para que tenga la proporción de la imagen ([width]×[height]) y se elige el zoom entero
 * más bajo cuyas teselas dan al menos esa resolución (tope [maxZoom]).
 */
class AerialFrame(
    north: Double, south: Double, east: Double, west: Double,
    val width: Int, val height: Int, maxZoom: Int = 19
) {
    /** Zoom de las teselas que se descargan. */
    val zoom: Int
    /** Rectángulo del área en píxeles del mundo a ese [zoom]. */
    val left: Double
    val top: Double
    val spanX: Double
    val spanY: Double

    init {
        val x0 = worldX(west, 0); val x1 = worldX(east, 0)
        val y0 = worldY(north, 0); val y1 = worldY(south, 0)
        var sx = max(x1 - x0, 1e-9); var sy = max(y1 - y0, 1e-9)
        val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2
        // Misma proporción que la imagen, ampliando el lado que se queda corto.
        val aspect = width.toDouble() / height
        if (sx / sy < aspect) sx = sy * aspect else sy = sx / aspect
        zoom = min(maxZoom, max(0, ceil(log2(width / sx)).toInt()))
        val k = (1 shl zoom).toDouble()
        spanX = sx * k; spanY = sy * k
        left = cx * k - spanX / 2; top = cy * k - spanY / 2
    }

    /** Punto de la imagen final ([width]×[height]) donde cae esa coordenada. */
    fun pixel(lat: Double, lon: Double): Pair<Float, Float> {
        val k = (1 shl zoom).toDouble()
        val x = (worldX(lon, 0) * k - left) / spanX * width
        val y = (worldY(lat, 0) * k - top) / spanY * height
        return x.toFloat() to y.toFloat()
    }

    /** Teselas (x, y) que cubren el área, ya con x dentro del mundo. */
    fun tiles(): List<Pair<Int, Int>> {
        val n = 1 shl zoom
        val tx0 = floor(left / TILE).toInt(); val tx1 = floor((left + spanX) / TILE).toInt()
        val ty0 = max(0, floor(top / TILE).toInt()); val ty1 = min(n - 1, floor((top + spanY) / TILE).toInt())
        return (ty0..ty1).flatMap { ty -> (tx0..tx1).map { tx -> tx to ty } }
    }

    /** Dónde va la tesela (x, y) en la imagen final: izquierda, arriba y lado (en px de la imagen). */
    fun tileRect(tx: Int, ty: Int): FloatArray {
        val sx = width / spanX; val sy = height / spanY
        return floatArrayOf(((tx * TILE - left) * sx).toFloat(), ((ty * TILE - top) * sy).toFloat(),
            (TILE * sx).toFloat(), (TILE * sy).toFloat())
    }

    companion object {
        const val TILE = 256.0
        fun worldX(lon: Double, z: Int) = (lon + 180.0) / 360.0 * TILE * (1 shl z)
        fun worldY(lat: Double, z: Int): Double {
            val r = lat.coerceIn(-85.0511, 85.0511) * PI / 180
            return (1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * TILE * (1 shl z)
        }
    }
}
