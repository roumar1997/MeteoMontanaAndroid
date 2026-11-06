package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorFeatureFlagApi
import com.meteomontana.android.data.api.dto.FeatureFlagsDto
import com.meteomontana.android.domain.model.FeatureFlagState
import com.meteomontana.android.domain.model.FeatureFlags
import com.meteomontana.android.domain.port.KeyValueStore
import com.meteomontana.android.domain.repository.FeatureFlagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Guarda la última respuesta del servidor (con la hora) para que, sin red, la app siga
 * comportándose como la última vez. Reglas:
 * - nada guardado -> todo encendido;
 * - guardado hace más de 72 h -> se ignora (todo encendido): un aviso viejo no debe tapar una función para siempre;
 * - si la red falla, se conserva lo último;
 * - se pregunta al servidor como mucho cada `refreshAfterSeconds` que él mismo indicó.
 */
class FeatureFlagRepositoryImpl(
    private val api: KtorFeatureFlagApi,
    private val store: KeyValueStore,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : FeatureFlagRepository {

    private val _flags = MutableStateFlow(loadStored())
    override val flags: StateFlow<FeatureFlags> = _flags.asStateFlow()

    private var lastFetchAt: Long? = null
    private var refreshAfterMs: Long = DEFAULT_REFRESH_MS

    override suspend fun refresh() {
        val now = nowMillis()
        val last = lastFetchAt
        if (last != null && now - last < refreshAfterMs) return
        val dto = runCatching { api.get() }.getOrNull() ?: return
        lastFetchAt = now
        refreshAfterMs = dto.refreshAfterSeconds.coerceAtLeast(0) * 1000L
        _flags.value = dto.toDomain()
        runCatching { store.putString(STORE_KEY, JSON.encodeToString(Stored(now, dto))) }
    }

    private fun loadStored(): FeatureFlags {
        val raw = runCatching { store.getString(STORE_KEY) }.getOrNull() ?: return FeatureFlags.ALL_ON
        val stored = runCatching { JSON.decodeFromString<Stored>(raw) }.getOrNull() ?: return FeatureFlags.ALL_ON
        if (nowMillis() - stored.savedAt > MAX_AGE_MS) return FeatureFlags.ALL_ON
        return stored.dto.toDomain()
    }

    private fun FeatureFlagsDto.toDomain() =
        FeatureFlags(flags.mapValues { (_, f) -> FeatureFlagState(f.enabled, f.message) })

    @Serializable
    private data class Stored(val savedAt: Long, val dto: FeatureFlagsDto)

    private companion object {
        const val STORE_KEY = "feature_flags_v1"
        const val MAX_AGE_MS = 72L * 60 * 60 * 1000
        const val DEFAULT_REFRESH_MS = 300_000L
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
