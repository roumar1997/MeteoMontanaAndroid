package com.meteomontana.android.data.map

/**
 * Fuente ÚNICA de los estilos raster de MapLibre (P1.5). Antes el JSON de estilo
 * y las URLs de tiles estaban copiados en ~6 ficheros (SchoolMapView,
 * SubmissionCard, SchoolsMapPanel, FullScreenMapDialog, MeetupsMapPanel,
 * OfflineTileManager). Aquí viven las URLs y un constructor del JSON; los presets
 * cubren las combinaciones usadas. Vive en data/map (infraestructura, no UI) para
 * que lo usen tanto las pantallas como OfflineTileManager sin violar capas.
 * MapLibre parsea el JSON sin importar el orden de las claves → los presets son
 * EQUIVALENTES a los literales previos.
 */
object MapStyles {

    // ── Fuentes de tiles (multi-host donde el proveedor lo permite) ──────────
    val TOPO = listOf(
        "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
        "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
        "https://c.tile.opentopomap.org/{z}/{x}/{y}.png",
    )
    val OSM = listOf(
        "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png",
        "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png",
        "https://c.tile.openstreetmap.org/{z}/{x}/{y}.png",
    )
    // Esri World Imagery — OJO orden {z}/{y}/{x} (distinto de OSM/Topo).
    val SATELLITE = listOf(
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
    )
    val DARK = listOf(
        "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
        "https://b.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
        "https://c.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
    )

    const val PAPER_BG = "#F4F1E9"
    const val DARK_BG = "#1C1B18"

    // ── Hasta qué nivel de zoom tiene teselas CADA proveedor ─────────────────
    // Sin esto, al pasarse de zoom MapLibre no encuentra tesela y pinta
    // "map data not yet available" (reportado por Rodrigo 2026-08-15). Con el
    // maxzoom declarado en la FUENTE, MapLibre ESTIRA la última que tiene
    // (se ve borroso, como cualquier mapa) y se puede seguir acercando — que es
    // lo que se espera al mirar de cerca dónde está una piedra.
    // Son los límites REALES de cada proveedor, no números redondos.
    const val TOPO_MAX_ZOOM = 17        // OpenTopoMap corta en 17
    const val OSM_MAX_ZOOM = 19
    const val SATELLITE_MAX_ZOOM = 19   // Esri World Imagery, cobertura global
    const val DARK_MAX_ZOOM = 20        // CARTO basemaps

    /**
     * Construye un style JSON raster de una sola capa. Si [bg] != null añade una
     * capa de fondo (evita el flash azul por defecto de MapLibre mientras cargan
     * los tiles). Si [attribution] != null la incluye en la fuente.
     *
     * @param maxZoom último nivel con teselas del proveedor. Al declararlo,
     *   MapLibre estira la última en vez de dejar el hueco con
     *   "map data not yet available". null = no declararlo (compatibilidad).
     */
    fun raster(
        id: String,
        tiles: List<String>,
        attribution: String? = null,
        bg: String? = null,
        maxZoom: Int? = null
    ): String {
        val tilesJson = tiles.joinToString(",") { "\"$it\"" }
        val attr = attribution?.let { ",\"attribution\":\"$it\"" } ?: ""
        val max = maxZoom?.let { ",\"maxzoom\":$it" } ?: ""
        val bgLayer = bg?.let {
            "{\"id\":\"bg\",\"type\":\"background\",\"paint\":{\"background-color\":\"$it\"}},"
        } ?: ""
        return "{\"version\":8,\"sources\":{\"$id\":{\"type\":\"raster\"," +
            "\"tiles\":[$tilesJson],\"tileSize\":256$max$attr}}," +
            "\"layers\":[$bgLayer{\"id\":\"$id\",\"type\":\"raster\",\"source\":\"$id\"}]}"
    }

    // ── Presets sobre fondo PAPEL (mapas a pantalla: escuelas, quedadas) ──────
    val topoPaper get() = raster("topo", TOPO, "© OpenTopoMap (CC-BY-SA)", PAPER_BG, TOPO_MAX_ZOOM)
    val satellitePaper get() = raster("sat", SATELLITE, "Tiles © Esri", PAPER_BG, SATELLITE_MAX_ZOOM)
    val osmPaper get() = raster("osm", OSM, "© OpenStreetMap", PAPER_BG, OSM_MAX_ZOOM)
    val darkPaper get() = raster("carto", DARK, "© OpenStreetMap © CARTO", DARK_BG, DARK_MAX_ZOOM)

    // ── Presets SIN fondo (mini-mapas admin, cachés) ─────────────────────────
    val topo get() = raster("topo", TOPO, "© OpenTopoMap (CC-BY-SA)", maxZoom = TOPO_MAX_ZOOM)
    val satellite get() = raster("sat", SATELLITE, "Tiles © Esri", maxZoom = SATELLITE_MAX_ZOOM)
    val osm get() = raster("osm", listOf(OSM.first()), null, maxZoom = OSM_MAX_ZOOM)
}
