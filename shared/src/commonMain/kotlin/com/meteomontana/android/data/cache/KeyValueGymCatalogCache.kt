package com.meteomontana.android.data.cache

import com.meteomontana.android.data.api.dto.GymDto
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.api.dto.toDto
import com.meteomontana.android.domain.port.KeyValueStore
import com.meteomontana.android.domain.repository.GymCatalogCache
import com.meteomontana.android.domain.repository.GymCatalogSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Copia del catalogo en el [KeyValueStore] (JSON). 349 fichas son unos 150 KB: caben de sobra y no tocan la
 * base de datos local (una tabla nueva alli borraria los datos sin conexion de los usuarios).
 * Cualquier fallo de lectura/escritura se traga: la cache solo acelera, nunca debe tumbar la pantalla.
 */
class KeyValueGymCatalogCache(private val store: KeyValueStore) : GymCatalogCache {

    override fun load(): GymCatalogSnapshot? {
        val raw = runCatching { store.getString(KEY) }.getOrNull() ?: return null
        val stored = runCatching { JSON.decodeFromString<Stored>(raw) }.getOrNull() ?: return null
        return GymCatalogSnapshot(stored.gyms.map { it.toDomain() }, stored.etag)
    }

    override fun save(snapshot: GymCatalogSnapshot) {
        runCatching { store.putString(KEY, JSON.encodeToString(Stored(snapshot.etag, snapshot.gyms.map { it.toDto() }))) }
    }

    @Serializable
    private data class Stored(val etag: String? = null, val gyms: List<GymDto> = emptyList())

    private companion object {
        const val KEY = "gym_catalog_v1"
        val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}
