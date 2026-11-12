package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorTitleEffectApi
import com.meteomontana.android.data.api.dto.TitleEffectDto
import com.meteomontana.android.data.api.dto.TitleEffectsDto
import com.meteomontana.android.domain.model.TitleEffectCatalog
import com.meteomontana.android.domain.model.TitleEffectMotion
import com.meteomontana.android.domain.model.TitleEffectSpec
import com.meteomontana.android.domain.model.TitleEffectSprite
import com.meteomontana.android.domain.port.KeyValueStore
import com.meteomontana.android.domain.repository.TitleEffectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stale-while-revalidate: al arrancar se enseña lo guardado en disco (o los integrados si no hay nada) SIN esperar a la
 * red, y [refresh] lo renueva detrás. A diferencia de los interruptores, lo guardado no caduca: un catálogo viejo
 * sigue siendo válido para pintar. Los integrados que el servidor no mande se añaden igual (respaldo).
 */
class TitleEffectRepositoryImpl(
    private val api: KtorTitleEffectApi,
    private val store: KeyValueStore,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : TitleEffectRepository {

    private val stored: Stored? = loadStored()
    private val _catalog = MutableStateFlow(stored?.dto?.toDomain() ?: TitleEffectCatalog.BUILT_IN)
    override val catalog: StateFlow<TitleEffectCatalog> = _catalog.asStateFlow()

    private var lastFetchAt: Long? = stored?.savedAt
    private var refreshAfterMs: Long = (stored?.dto?.refreshAfterSeconds ?: 0) * 1000L

    /** Muchas fotos piden refrescar a la vez al abrir una lista: solo va una petición. */
    private val inFlight = Mutex()

    override suspend fun refresh() {
        if (!inFlight.tryLock()) return
        try {
            fetchIfStale()
        } finally {
            inFlight.unlock()
        }
    }

    private suspend fun fetchIfStale() {
        val now = nowMillis()
        val last = lastFetchAt
        if (last != null && now - last in 0 until refreshAfterMs) return
        val dto = runCatching { api.get() }.getOrNull() ?: return
        lastFetchAt = now
        refreshAfterMs = dto.refreshAfterSeconds.coerceAtLeast(0) * 1000L
        _catalog.value = dto.toDomain()
        runCatching { store.putString(STORE_KEY, JSON.encodeToString(Stored(now, dto))) }
    }

    private fun loadStored(): Stored? {
        val raw = runCatching { store.getString(STORE_KEY) }.getOrNull() ?: return null
        return runCatching { JSON.decodeFromString<Stored>(raw) }.getOrNull()
    }

    @Serializable
    private data class Stored(val savedAt: Long, val dto: TitleEffectsDto)

    internal companion object {
        const val STORE_KEY = "title_effects_v1"
        val JSON = Json { ignoreUnknownKeys = true }

        fun TitleEffectsDto.toDomain(): TitleEffectCatalog {
            val remote = effects.filter { it.sprites.isNotEmpty() }.map { it.toDomain() }
            val keys = remote.map { it.key }.toSet()
            return TitleEffectCatalog(remote + TitleEffectCatalog.BUILT_IN.effects.filter { it.key !in keys })
        }

        private fun TitleEffectDto.toDomain() = TitleEffectSpec(
            key = key.uppercase(), name = name, orientation = orientation, figureCount = figureCount.coerceIn(1, 8),
            sprites = sprites.map { TitleEffectSprite(it.url, it.asset, it.facesRight) },
            ringColors = ringColors.ifEmpty { listOf(glowColor) }, glowColor = glowColor,
            highlightColor = highlightColor, particleColor = particleColor, particleStyle = particleStyle,
            motion = TitleEffectMotion(motion.orbit, motion.size, motion.speed, motion.staticAngles, motion.staticSize),
        )
    }
}
