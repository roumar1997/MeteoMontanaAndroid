package com.meteomontana.android.ui.screens.detail.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.meteomontana.android.R
import com.meteomontana.android.data.map.MapStyles
import com.meteomontana.android.domain.model.Block
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng

/**
 * Mapa general de la escuela como IMAGEN (satélite + piedras, parkings y sectores) para la GUÍA y su PDF.
 * Los nombres de los sectores NO van pintados: van encima como botones, en coordenadas 0–1 de la imagen.
 */
data class GuideOverview(val image: Bitmap, val sectors: List<Place>) {
    data class Place(val name: String, val x: Double, val y: Double)
}

/**
 * Hace las fotos aéreas de la guía con MapLibre (satélite de Esri) y las guarda en disco: sin conexión se ve la
 * última copia. Espejo de `GuideOverviewStore` (iOS, MapKit), con las mismas medidas y el mismo dibujo.
 */
@Singleton
class GuideMapSnapshots @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir: File get() = File(context.cacheDir, "guide-overview").apply { mkdirs() }
    private fun imageFile(schoolId: String) = File(dir, "$schoolId.png")
    private fun placesFile(schoolId: String) = File(dir, "$schoolId.json")
    private fun sectorFile(schoolId: String, key: String) =
        File(dir, "$schoolId-sector-${key.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")}.png")

    /** La última copia guardada del mapa general, o null si nunca se generó. */
    suspend fun cached(schoolId: String): GuideOverview? = withContext(Dispatchers.IO) {
        val img = BitmapFactory.decodeFile(imageFile(schoolId).path) ?: return@withContext null
        val places = runCatching {
            val arr = JSONArray(placesFile(schoolId).readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GuideOverview.Place(o.getString("name"), o.getDouble("x"), o.getDouble("y"))
            }
        }.getOrDefault(emptyList())
        GuideOverview(img, places)
    }

    /** Genera el mapa general (hace falta red para el fondo) y lo guarda. null si no hay nada o no hay red. */
    suspend fun generate(schoolId: String, blocks: List<Block>): GuideOverview? {
        val stones = blocks.filter { it.type.uppercase() !in setOf("ZONE", "PARKING") }
        val zones = blocks.filter { it.type.uppercase() == "ZONE" }
        val parkings = blocks.filter { it.type.uppercase() == "PARKING" }
        val all = (stones + zones + parkings).filter { it.lat != 0.0 || it.lon != 0.0 }
        if (all.isEmpty()) return null
        val snap = snapshot(all.map { it.lat }, all.map { it.lon }, 1.35, 0.0025, 0.0025, 1100, 800) ?: return null

        val bmp = snap.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(bmp)
        val names = outlinedText(15f)
        // Piedras muy juntas (se pisarían) van en UN icono con cuántas son.
        val groups = mutableListOf<Pair<android.graphics.PointF, MutableList<Block>>>()
        for (b in stones) {
            val p = snap.pixelForLatLng(LatLng(b.lat, b.lon))
            val g = groups.firstOrNull { hypot(it.first.x - p.x, it.first.y - p.y) < 34 }
            if (g != null) g.second.add(b) else groups.add(p to mutableListOf(b))
        }
        for ((p, list) in groups) {
            stoneIcon(c, p.x, p.y, 17f, list.first())
            if (list.size > 1) {
                val bx = p.x + 17 - 12 + 10; val by = p.y - 17 - 6 + 10
                c.drawCircle(bx, by, 10f, fill(TERRA))
                c.drawCircle(bx, by, 10f, stroke(WHITE, 1.5f))
                val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = WHITE; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
                }
                c.drawText("${list.size}", bx, by - (t.ascent() + t.descent()) / 2, t)
            } else if (list.first().name.isNotEmpty()) {
                drawOutlined(c, list.first().name, p.x + 20, p.y, names)
            }
        }
        // Parkings: cuadrado azul con «P».
        for (b in parkings) {
            val p = snap.pixelForLatLng(LatLng(b.lat, b.lon))
            val r = RectF(p.x - 16, p.y - 16, p.x + 16, p.y + 16)
            c.drawRoundRect(r, 7f, 7f, fill(PARKING_BLUE))
            c.drawRoundRect(r, 7f, 7f, stroke(WHITE, 2.5f))
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = WHITE; textSize = 19f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            c.drawText("P", p.x, p.y - (t.ascent() + t.descent()) / 2, t)
        }
        // Cada sector, ENCIMA del centro de sus piedras (o en su marca si no tiene).
        var places = zones.filter { it.name.isNotEmpty() }.map { z ->
            val inside = stones.filter { it.sectorBlockId == z.id }
            val ll = if (inside.isEmpty()) LatLng(z.lat, z.lon)
            else LatLng(inside.map { it.lat }.average(), inside.map { it.lon }.average())
            val p = snap.pixelForLatLng(ll)
            GuideOverview.Place(z.name, (p.x / bmp.width).toDouble(), ((p.y - 34) / bmp.height).toDouble())
        }
        places = separated(places, bmp.width, bmp.height)

        withContext(Dispatchers.IO) {
            runCatching {
                imageFile(schoolId).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val arr = JSONArray()
                places.forEach { arr.put(JSONObject().put("name", it.name).put("x", it.x).put("y", it.y)) }
                placesFile(schoolId).writeText(arr.toString())
            }
        }
        return GuideOverview(bmp, places)
    }

    /**
     * La foto aérea de UN sector: satélite acercado a SUS piedras, cada una con su icono y su nombre. Con red se
     * genera y se guarda; sin red, la última copia. null si no hay nada.
     */
    suspend fun sectorMap(schoolId: String, key: String, stones: List<Block>): Bitmap? {
        val file = sectorFile(schoolId, key)
        val spots = stones.filter { it.lat != 0.0 || it.lon != 0.0 }
        val cached = suspend { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) } }
        if (spots.isEmpty()) return cached()
        val snap = snapshot(spots.map { it.lat }, spots.map { it.lon }, 1.6, 0.0009, 0.0013, 1100, 520)
            ?: return cached()
        val bmp = snap.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(bmp)
        val names = outlinedText(17f)
        for (b in spots) {
            val p = snap.pixelForLatLng(LatLng(b.lat, b.lon))
            stoneIcon(c, p.x, p.y, 19f, b)
            if (b.name.isNotEmpty()) drawOutlined(c, b.name, p.x + 22, p.y, names)
        }
        withContext(Dispatchers.IO) {
            runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        return bmp
    }

    // ── Piezas ──────────────────────────────────────────────────────────────────────────────────────────────────

    /** Foto de satélite ya montada y su encuadre (para saber dónde cae cada coordenada). */
    private class AerialShot(val bitmap: Bitmap, val frame: AerialFrame) {
        fun pixelForLatLng(ll: LatLng): android.graphics.PointF =
            frame.pixel(ll.latitude, ll.longitude).let { (x, y) -> android.graphics.PointF(x, y) }
    }

    /**
     * Encuadre alrededor de los puntos (margen [pad], mínimos de alto/ancho) y foto de satélite de [w]×[h], pegando las
     * teselas de Esri una a una (ver [AerialFrame]: el `MapSnapshotter` de MapLibre cerraba la app). null sin red.
     */
    private suspend fun snapshot(
        lats: List<Double>, lons: List<Double>, pad: Double, minLat: Double, minLon: Double, w: Int, h: Int
    ): AerialShot? {
        val cLat = (lats.min() + lats.max()) / 2
        val cLon = (lons.min() + lons.max()) / 2
        val dLat = maxOf((lats.max() - lats.min()) * pad, minLat) / 2
        val dLon = maxOf((lons.max() - lons.min()) * pad, minLon) / 2
        val frame = AerialFrame(cLat + dLat, cLat - dLat, cLon + dLon, cLon - dLon, w, h, MapStyles.SATELLITE_MAX_ZOOM)
        val template = MapStyles.SATELLITE.first()
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(20_000) {
                val tiles = coroutineScope {
                    frame.tiles().map { (tx, ty) ->
                        async {
                            val n = 1 shl frame.zoom
                            val url = template.replace("{z}", "${frame.zoom}")
                                .replace("{x}", "${((tx % n) + n) % n}").replace("{y}", "$ty")
                            Triple(tx, ty, fetchTile(url))
                        }
                    }.awaitAll()
                }
                if (tiles.none { it.third != null }) return@withTimeoutOrNull null
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawColor(android.graphics.Color.rgb(40, 44, 38))
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                for ((tx, ty, tile) in tiles) {
                    tile ?: continue
                    val r = frame.tileRect(tx, ty)
                    c.drawBitmap(tile, null, RectF(r[0], r[1], r[0] + r[2], r[1] + r[3]), paint)
                    tile.recycle()
                }
                AerialShot(bmp, frame)
            }
        }
    }

    private fun fetchTile(url: String): Bitmap? = runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8_000; conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "Cumbre-Android")
        try { conn.inputStream.use { BitmapFactory.decodeStream(it) } } finally { conn.disconnect() }
    }.getOrNull()


    /** Crashpad (bloque) o mosquetón (vía) en un círculo blanco con borde terracota. */
    private fun stoneIcon(c: Canvas, x: Float, y: Float, radius: Float, b: Block) {
        c.drawCircle(x, y, radius, fill(WHITE))
        c.drawCircle(x, y, radius, stroke(TERRA, 2.5f))
        val res = if (b.discipline.uppercase() == "ROUTE") R.drawable.kind_route else R.drawable.kind_boulder
        val icon = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        icon.setTint(TERRA)
        val inset = radius - radius * 0.62f
        icon.setBounds((x - radius + inset).toInt(), (y - radius + inset).toInt(),
            (x + radius - inset).toInt(), (y + radius - inset).toInt())
        icon.draw(c)
    }

    private fun outlinedText(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; typeface = Typeface.DEFAULT_BOLD
    }

    /** Texto blanco con contorno negro, centrado en vertical en [y] (se lee sobre el satélite). */
    private fun drawOutlined(c: Canvas, text: String, x: Float, y: Float, p: Paint) {
        val base = y - (p.ascent() + p.descent()) / 2
        p.style = Paint.Style.STROKE; p.strokeWidth = p.textSize * 0.21f; p.color = BLACK
        p.strokeJoin = Paint.Join.ROUND
        c.drawText(text, x, base, p)
        p.style = Paint.Style.FILL; p.color = WHITE
        c.drawText(text, x, base, p)
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, w: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = w }

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val BLACK = 0xFF000000.toInt()
        private const val TERRA = 0xFFC2410C.toInt()
        private const val PARKING_BLUE = 0xFF1C66D6.toInt()

        /** Separa los nombres de sector que se pisarían: el que choca baja lo justo para quedar debajo. */
        internal fun separated(places: List<GuideOverview.Place>, w: Int, h: Int): List<GuideOverview.Place> {
            fun rect(p: GuideOverview.Place): RectF {
                val rw = p.name.length * 10f + 22; val rh = 30f
                val cx = (p.x * w).toFloat(); val cy = (p.y * h).toFloat()
                return RectF(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2)
            }
            val out = mutableListOf<GuideOverview.Place>()
            for (p in places.sortedBy { it.y }) {
                var q = p
                while (true) {
                    val hit = out.firstOrNull { RectF.intersects(rect(it), rect(q)) } ?: break
                    q = q.copy(y = ((rect(hit).bottom + 16) / h).toDouble())
                }
                out.add(q)
            }
            return out
        }
    }
}
