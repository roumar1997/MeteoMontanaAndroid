package com.meteomontana.android.map

import com.meteomontana.android.data.map.MapStyles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda P1.5 (MapStyles único). El JSON de estilo raster de MapLibre estaba
 * copiado en 6 ficheros; ahora lo genera `MapStyles.raster`. Estos tests fijan
 * el CONTRATO del generador y de los presets para que un cambio no rompa los
 * mapas en silencio (MapLibre falla al parsear un JSON mal formado, sin excepción
 * visible en compilación).
 */
class MapStylesTest {

    @Test
    fun `raster con fondo incluye la capa background con su color`() {
        val json = MapStyles.raster("topo", listOf("https://x/{z}/{x}/{y}.png"),
            attribution = "attr", bg = "#F4F1E9")
        assertTrue("versión 8", json.contains("\"version\":8"))
        assertTrue("capa de fondo", json.contains("\"type\":\"background\""))
        assertTrue("color de fondo", json.contains("\"background-color\":\"#F4F1E9\""))
        assertTrue("atribución", json.contains("\"attribution\":\"attr\""))
        assertTrue("tile", json.contains("https://x/{z}/{x}/{y}.png"))
    }

    @Test
    fun `raster sin fondo ni atribucion los omite`() {
        val json = MapStyles.raster("osm", listOf("https://y/{z}/{x}/{y}.png"))
        assertFalse("sin capa de fondo", json.contains("\"type\":\"background\""))
        assertFalse("sin atribución", json.contains("\"attribution\""))
        assertTrue("capa raster", json.contains("\"type\":\"raster\""))
        assertTrue("fuente por id", json.contains("\"source\":\"osm\""))
    }

    @Test
    fun `raster incluye TODAS las tiles multi-host`() {
        val tiles = listOf("https://a/x.png", "https://b/x.png", "https://c/x.png")
        val json = MapStyles.raster("topo", tiles)
        tiles.forEach { assertTrue("contiene $it", json.contains(it)) }
    }

    @Test
    fun `preset topoPaper usa tiles de OpenTopoMap y fondo papel`() {
        val json = MapStyles.topoPaper
        assertTrue(json.contains("tile.opentopomap.org"))
        assertTrue(json.contains("\"background-color\":\"${MapStyles.PAPER_BG}\""))
        assertTrue(json.contains("\"source\":\"topo\""))
    }

    @Test
    fun `preset satellite usa Esri con orden z-y-x`() {
        // El satélite de Esri invierte y/x respecto a OSM/Topo — regresión sutil.
        assertTrue(MapStyles.satellite.contains("World_Imagery/MapServer/tile/{z}/{y}/{x}"))
    }

    @Test
    fun `preset darkPaper usa carto dark y fondo oscuro`() {
        val json = MapStyles.darkPaper
        assertTrue(json.contains("basemaps.cartocdn.com/dark_all"))
        assertTrue(json.contains("\"background-color\":\"${MapStyles.DARK_BG}\""))
    }

    @Test
    fun `preset osm sin fondo usa un solo host`() {
        val json = MapStyles.osm
        assertTrue(json.contains("tile.openstreetmap.org"))
        assertFalse(json.contains("\"type\":\"background\""))
    }

    // ── maxzoom: sin él salía "map data not yet available" al acercarse ──────

    @Test
    fun `raster declara el maxzoom cuando se le pasa`() {
        val json = MapStyles.raster("topo", listOf("https://x/{z}/{x}/{y}.png"), maxZoom = 17)
        assertTrue("maxzoom en la fuente", json.contains("\"maxzoom\":17"))
    }

    @Test
    fun `raster omite el maxzoom si no se indica`() {
        val json = MapStyles.raster("topo", listOf("https://x/{z}/{x}/{y}.png"))
        assertFalse(json.contains("maxzoom"))
    }

    @Test
    fun `TODOS los presets declaran su maxzoom real`() {
        // Si alguien añade un preset sin maxzoom, vuelve el hueco con el mensaje
        // de error al pasarse de zoom. Los valores son los límites REALES de
        // cada proveedor, no números redondos.
        assertTrue("topo corta en 17", MapStyles.topoPaper.contains("\"maxzoom\":17"))
        assertTrue("Esri llega a 19", MapStyles.satellitePaper.contains("\"maxzoom\":19"))
        assertTrue("OSM llega a 19", MapStyles.osmPaper.contains("\"maxzoom\":19"))
        assertTrue("CARTO llega a 20", MapStyles.darkPaper.contains("\"maxzoom\":20"))
        assertTrue(MapStyles.topo.contains("\"maxzoom\":17"))
        assertTrue(MapStyles.satellite.contains("\"maxzoom\":19"))
        assertTrue(MapStyles.osm.contains("\"maxzoom\":19"))
    }
}
