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

    /**
     * Construye un style JSON raster de una sola capa. Si [bg] != null añade una
     * capa de fondo (evita el flash azul por defecto de MapLibre mientras cargan
     * los tiles). Si [attribution] != null la incluye en la fuente.
     */
    fun raster(id: String, tiles: List<String>, attribution: String? = null, bg: String? = null): String {
        val tilesJson = tiles.joinToString(",") { "\"$it\"" }
        val attr = attribution?.let { ",\"attribution\":\"$it\"" } ?: ""
        val bgLayer = bg?.let {
            "{\"id\":\"bg\",\"type\":\"background\",\"paint\":{\"background-color\":\"$it\"}},"
        } ?: ""
        return "{\"version\":8,\"sources\":{\"$id\":{\"type\":\"raster\"," +
            "\"tiles\":[$tilesJson],\"tileSize\":256$attr}}," +
            "\"layers\":[$bgLayer{\"id\":\"$id\",\"type\":\"raster\",\"source\":\"$id\"}]}"
    }

    // ── Presets sobre fondo PAPEL (mapas a pantalla: escuelas, quedadas) ──────
    val topoPaper get() = raster("topo", TOPO, "© OpenTopoMap (CC-BY-SA)", PAPER_BG)
    val satellitePaper get() = raster("sat", SATELLITE, "Tiles © Esri", PAPER_BG)
    val osmPaper get() = raster("osm", OSM, "© OpenStreetMap", PAPER_BG)
    val darkPaper get() = raster("carto", DARK, "© OpenStreetMap © CARTO", DARK_BG)

    // ── Presets SIN fondo (mini-mapas admin, cachés) ─────────────────────────
    val topo get() = raster("topo", TOPO, "© OpenTopoMap (CC-BY-SA)")
    val satellite get() = raster("sat", SATELLITE, "Tiles © Esri")
    val osm get() = raster("osm", listOf(OSM.first()), null)
}
