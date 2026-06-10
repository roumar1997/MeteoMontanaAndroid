package com.meteomontana.android.data.map

import android.content.Context
import co.touchlab.kermit.Logger
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Helper para descargar y eliminar regiones offline de MapLibre por escuela.
 * Cada región se asocia por metadata a un schoolId.
 *
 * - Bounding box: ~2 km cuadrados alrededor del centro.
 * - Zoom: 10..16.
 * - Tile source: OpenTopoMap (raster) — el mismo que usa SchoolMap en modo TOPO.
 *
 * Implementación con callbacks "fire-and-forget" — no bloquea coroutines.
 * Si la descarga falla solo se loguea y la app sigue funcionando con tiles online.
 */
class OfflineTileManager(private val context: Context) {

    private val log = Logger.withTag("OfflineTiles")

    init {
        runCatching { MapLibre.getInstance(context) }
    }

    private val manager get() = OfflineManager.getInstance(context)

    fun downloadFor(schoolId: String, lat: Double, lon: Double) {
        val deltaLat = 0.018
        val deltaLon = 0.024
        val bounds = LatLngBounds.from(lat + deltaLat, lon + deltaLon, lat - deltaLat, lon - deltaLon)
        val definition = OfflineTilePyramidRegionDefinition(
            TOPO_STYLE_JSON, bounds, 10.0, 16.0, context.resources.displayMetrics.density
        )
        val metadata = "school:$schoolId".toByteArray(Charsets.UTF_8)

        log.i("Creando región offline para $schoolId @($lat,$lon)")
        manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                log.i("Descarga iniciada en background para $schoolId")
            }
            override fun onError(error: String) {
                log.w("createOfflineRegion error: $error")
            }
        })
    }

    fun removeFor(schoolId: String) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                offlineRegions?.forEach { r ->
                    val tag = String(r.metadata, Charsets.UTF_8)
                    if (tag == "school:$schoolId") {
                        r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() { log.i("Región offline borrada para $schoolId") }
                            override fun onError(error: String) { log.w("delete error: $error") }
                        })
                    }
                }
            }
            override fun onError(error: String) {
                log.w("listOfflineRegions error: $error")
            }
        })
    }

    companion object {
        private const val TOPO_STYLE_JSON = """{"version":8,"sources":{"topo":{"type":"raster","tiles":["https://a.tile.opentopomap.org/{z}/{x}/{y}.png","https://b.tile.opentopomap.org/{z}/{x}/{y}.png","https://c.tile.opentopomap.org/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenTopoMap (CC-BY-SA)"}},"layers":[{"id":"topo","type":"raster","source":"topo"}]}"""
    }
}
