package com.meteomontana.android.ui.assistant.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.meteomontana.android.data.vision.StoneImageEmbedder
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolsUseCase
import com.meteomontana.android.domain.util.Geo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Una piedra candidata a ser la de la foto. */
data class StoneCandidate(
    val blockId: String,
    val blockName: String,
    val schoolId: String,
    val schoolName: String,
    val photoUrl: String?,
    /** Primera vía de la piedra: abrir la escuela con ella abre la piedra. */
    val lineId: String?,
    /** Metros desde el punto de la foto. Negativo = no se sabe dónde se hizo (se comparó solo por cómo se ve). */
    val meters: Double,
    /** 0 (nada que ver) a 1 (idéntica). null si la piedra no tiene foto con que comparar. */
    val similarity: Double?
)

sealed interface StoneOutcome {
    /** Las más probables, de más a menos. `sure` = la primera destaca claramente. */
    data class Found(val top: List<StoneCandidate>, val sure: Boolean, val approximate: Boolean) : StoneOutcome
    /** Ni la foto ni el móvil dicen dónde: hay que preguntar la escuela. */
    data object NoLocation : StoneOutcome
    /** No hay ninguna escuela de Cumbre cerca de ese punto (se dice cuál es la más cercana, para ver si la ubicación se leyó bien). */
    data class NoSchoolNear(val closestName: String?, val closestKm: Double?) : StoneOutcome
    /** No se pudo cargar la lista de escuelas (sin red). */
    data object NoData : StoneOutcome
    /** La escuela no tiene piedras con posición cerca de ese punto. */
    data class NoStones(val schoolName: String) : StoneOutcome
}

/** Por qué paso va la búsqueda (se enseña bajo los puntos de «escribiendo»). */
sealed interface StoneStage {
    data object Schools : StoneStage
    data object Stones : StoneStage
    data class Comparing(val count: Int) : StoneStage
}

/**
 * «¿Qué piedra es esta?» a partir de una foto. Espejo de `AssistantPhotoMatcher.swift`. Dos pasos, y ninguno sale del móvil:
 *  1. DÓNDE: la posición que guarda la foto (o, si no la trae, donde está el usuario) da la escuela y las piedras de
 *     alrededor, normalmente pocas.
 *  2. CUÁL: entre esas pocas se compara la foto con la de cada piedra ([StoneImageEmbedder]). No es infalible: por
 *     eso se enseñan las 3 más probables con su foto y elige el usuario.
 */
@Singleton
class StonePhotoMatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getSchools: GetSchoolsUseCase,
    private val getBlocks: GetBlocksUseCase,
    private val embedder: StoneImageEmbedder
) {
    /** Huella de cada foto de piedra ya calculada: repetir la búsqueda en la misma escuela es instantáneo. */
    private val printCache = object : LinkedHashMap<String, FloatArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > PRINT_CACHE_SIZE
    }

    /** Buscar entre todas las escuelas de alrededor del punto (desde el chat). */
    suspend fun match(
        photo: Uri, lat: Double?, lon: Double?, approximate: Boolean, onStage: (StoneStage) -> Unit = {}
    ): StoneOutcome {
        if (lat == null || lon == null) return StoneOutcome.NoLocation
        onStage(StoneStage.Schools)
        val schools = withTimeoutOrNull(LOAD_TIMEOUT_MS) { runCatching { getSchools() }.getOrNull() }
        if (schools.isNullOrEmpty()) return StoneOutcome.NoData
        val byDistance = schools.map { it to Geo.haversineKm(lat, lon, it.lat, it.lon) }.sortedBy { it.second }
        val around = byDistance.filter { it.second <= SEARCH_RADIUS_KM }.take(MAX_SCHOOLS_CHECKED)

        // La escuela es la que tiene la PIEDRA más cercana a la foto, no la de punto central más cercano.
        onStage(StoneStage.Stones)
        val loaded = coroutineScope {
            around.map { (school, _) ->
                async {
                    val blocks = withTimeoutOrNull(LOAD_TIMEOUT_MS) { runCatching { getBlocks(school.id) }.getOrNull() }.orEmpty()
                    school to nearStones(blocks, lat, lon)
                }
            }.awaitAll()
        }
        val best = loaded
            .filter { (it.second.firstOrNull()?.second ?: Double.MAX_VALUE) <= NEAREST_STONE_KM * 1000 }
            .minByOrNull { it.second.first().second }
        if (best == null) {
            val point = byDistance.firstOrNull()
            if (point != null && point.second <= SCHOOL_POINT_KM) return StoneOutcome.NoStones(point.first.name)
            return StoneOutcome.NoSchoolNear(point?.first?.name, point?.second)
        }
        val (school, near) = best
        var pool = near.filter { it.second <= STONE_RADIUS_M }
        if (pool.isEmpty()) pool = near.take(5)             // GPS muy desviado: las más cercanas igualmente
        pool = pool.take(MAX_COMPARED)
        if (pool.isEmpty()) return StoneOutcome.NoStones(school.name)
        return rank(photo, pool, school, approximate, onStage)
    }

    /**
     * Ya se sabe la escuela (se pide desde su pantalla): solo se comparan SUS piedras. Con ubicación útil se mira lo
     * que hay cerca del punto; sin ella (foto reenviada, capturas, estar en casa) se comparan las de la escuela por
     * cómo se ven, hasta [MAX_COMPARED_WITHOUT_LOCATION].
     */
    suspend fun matchInSchool(
        photo: Uri, lat: Double?, lon: Double?, approximate: Boolean, school: School, onStage: (StoneStage) -> Unit = {}
    ): StoneOutcome {
        onStage(StoneStage.Stones)
        val blocks = withTimeoutOrNull(LOAD_TIMEOUT_MS) { runCatching { getBlocks(school.id) }.getOrNull() }
            ?: return StoneOutcome.NoData
        val stones = blocks.filter { it.type == "BLOCK" }
        if (stones.isEmpty()) return StoneOutcome.NoStones(school.name)

        if (lat != null && lon != null) {
            val near = nearStones(stones, lat, lon)
            if (near.isNotEmpty() && near.first().second <= NEAREST_STONE_KM * 1000) {
                var pool = near.filter { it.second <= STONE_RADIUS_M }
                if (pool.isEmpty()) pool = near.take(5)
                return rank(photo, pool.take(MAX_COMPARED), school, approximate, onStage)
            }
        }
        // Sin ubicación útil: las piedras de la escuela que tienen foto, las más cercanas a su centro.
        val withPhoto = stones.filter { photoOf(it) != null }
        if (withPhoto.isEmpty()) return StoneOutcome.NoStones(school.name)
        val pool = withPhoto
            .sortedBy { Geo.haversineKm(school.lat, school.lon, it.lat, it.lon) }
            .take(MAX_COMPARED_WITHOUT_LOCATION)
            .map { it to -1.0 }
        return rank(photo, pool, school, approximate = false, onStage)
    }

    /** Las piedras (BLOCK) con su distancia en metros al punto, de más cerca a más lejos. */
    private fun nearStones(blocks: List<Block>, lat: Double, lon: Double): List<Pair<Block, Double>> =
        blocks.filter { it.type == "BLOCK" }
            .map { it to Geo.haversineKm(lat, lon, it.lat, it.lon) * 1000 }
            .sortedBy { it.second }

    private fun photoOf(b: Block): String? = (b.photoPath ?: b.faces.firstOrNull()?.photoPath)?.takeIf { it.isNotBlank() }

    private fun firstLineId(b: Block): String? =
        b.lines.firstOrNull()?.id ?: b.faces.firstNotNullOfOrNull { face -> face.lines.firstOrNull()?.id }

    /** Compara la foto con la de cada piedra del grupo y devuelve las 3 más probables. */
    private suspend fun rank(
        photo: Uri, pool: List<Pair<Block, Double>>, school: School, approximate: Boolean, onStage: (StoneStage) -> Unit
    ): StoneOutcome {
        onStage(StoneStage.Comparing(pool.size))
        val mine = bitmap(photo)?.let { embedder.embed(it) }
        val gate = Semaphore(DOWNLOADS_AT_ONCE)
        val candidates = coroutineScope {
            pool.map { (block, meters) ->
                async {
                    val url = photoOf(block)
                    val theirs = if (mine != null && url != null) gate.withPermit { printOf(url) } else null
                    StoneCandidate(
                        blockId = block.id, blockName = block.name, schoolId = school.id, schoolName = school.name,
                        photoUrl = url, lineId = firstLineId(block), meters = meters,
                        similarity = if (mine != null && theirs != null) StoneImageEmbedder.similarity(mine, theirs) else null
                    )
                }
            }.awaitAll()
        }
        val top = candidates.sortedByDescending { score(it) }.take(3)
        return StoneOutcome.Found(top, sure = isSure(top), approximate = approximate)
    }

    private suspend fun printOf(url: String): FloatArray? {
        synchronized(printCache) { printCache[url] }?.let { return it }
        val print = withTimeoutOrNull(PHOTO_TIMEOUT_MS) { bitmap(url) }?.let { embedder.embed(it) } ?: return null
        synchronized(printCache) { printCache[url] = print }
        return print
    }

    /** La foto en pequeño (Coil la reduce al leerla y respeta la rotación del EXIF), sin hardware para poder leer sus píxeles. */
    private suspend fun bitmap(data: Any): Bitmap? {
        val request = ImageRequest.Builder(context).data(data).size(Size(IMAGE_SIDE, IMAGE_SIDE)).allowHardware(false).build()
        return (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
    }

    companion object {
        /** Escuelas que se miran alrededor de la foto (por su punto central). Las zonas grandes tienen piedras a varios km de él. */
        const val SEARCH_RADIUS_KM = 25.0
        /** Cuántas de esas escuelas (las más cercanas) se cargan para buscar su piedra más próxima. */
        const val MAX_SCHOOLS_CHECKED = 6
        /** La foto es «de esa escuela» si alguna de sus piedras está a menos de esto. */
        const val NEAREST_STONE_KM = 2.0
        /** Sin piedras cerca, una escuela cuyo punto esté a menos de esto sigue contando (para decir «no hay piedras»). */
        const val SCHOOL_POINT_KM = 3.0
        /** Radio de piedras candidatas alrededor del punto (el GPS falla 10-30 m entre piedras). */
        const val STONE_RADIUS_M = 120.0
        /** Máximo de piedras a comparar (cada una es una descarga de su foto). */
        const val MAX_COMPARED = 15
        /** Máximo de piedras a comparar cuando no se sabe dónde se hizo la foto. */
        const val MAX_COMPARED_WITHOUT_LOCATION = 30
        /** Por debajo de este parecido, ni la mejor se parece de verdad: puede que la piedra aún no esté en Cumbre. */
        const val WEAK_BELOW = 0.35

        const val LOAD_TIMEOUT_MS = 15_000L
        const val PHOTO_TIMEOUT_MS = 8_000L
        const val TOTAL_TIMEOUT_MS = 45_000L
        private const val IMAGE_SIDE = 640
        private const val DOWNLOADS_AT_ONCE = 4
        private const val PRINT_CACHE_SIZE = 200

        /** Mezcla del parecido de la foto (manda) y la cercanía (desempata). Sin foto, solo cuenta la cercanía. */
        fun score(c: StoneCandidate): Double {
            if (c.meters < 0) return c.similarity ?: 0.0                 // sin ubicación solo cuenta el parecido
            val closeness = maxOf(0.0, 1 - c.meters / (STONE_RADIUS_M * 2))
            val s = c.similarity ?: return 0.35 * closeness
            return 0.75 * s + 0.25 * closeness
        }

        /** La primera destaca: es la única, o saca al menos 0,15 a la segunda. */
        fun isSure(top: List<StoneCandidate>): Boolean =
            top.size == 1 || (top.size >= 2 && score(top[0]) - score(top[1]) >= 0.15)

        /** Ni la más parecida se parece de verdad. */
        fun isWeak(top: List<StoneCandidate>): Boolean = (top.firstOrNull()?.similarity ?: 0.0) < WEAK_BELOW
    }
}
