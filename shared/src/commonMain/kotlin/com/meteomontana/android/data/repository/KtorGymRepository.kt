package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorGymApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymNear
import com.meteomontana.android.domain.repository.GymCatalogCache
import com.meteomontana.android.domain.repository.GymCatalogSnapshot
import com.meteomontana.android.domain.repository.GymRepository
import kotlinx.coroutines.CancellationException

/**
 * Adaptador Ktor del catalogo. Descarga la lista entera con ETag y la guarda; sin red usa la copia.
 * Las fichas ocultas (baja logica) nunca se enseñan.
 */
class KtorGymRepository(
    private val api: KtorGymApi,
    private val cache: GymCatalogCache
) : GymRepository {

    override fun cachedGyms(): List<Gym> = cache.load()?.gyms.orEmpty().filter { !it.hidden }

    override suspend fun getGyms(): List<Gym> {
        val local = cache.load()
        try {
            val resp = api.getCatalog(local?.etag)
            val fresh = resp.gyms?.map { it.toDomain() }
            if (fresh == null) {
                // 304: la lista no ha cambiado. Si por lo que sea no hay copia, se pide sin ETag.
                if (local != null) return local.gyms.filter { !it.hidden }
                return getGymsWithoutEtag()
            }
            cache.save(GymCatalogSnapshot(fresh, resp.etag))
            return fresh.filter { !it.hidden }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (local != null) return local.gyms.filter { !it.hidden }
            throw e
        }
    }

    private suspend fun getGymsWithoutEtag(): List<Gym> {
        val resp = api.getCatalog(null)
        val fresh = resp.gyms?.map { it.toDomain() }.orEmpty()
        cache.save(GymCatalogSnapshot(fresh, resp.etag))
        return fresh.filter { !it.hidden }
    }

    override suspend fun getGym(id: Long): Gym =
        try {
            api.getGym(id).toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cachedGyms().firstOrNull { it.id == id } ?: throw e
        }

    override suspend fun findNear(lat: Double, lon: Double, radiusKm: Double, limit: Int): List<GymNear> =
        api.near(lat, lon, radiusKm, limit).map { it.toDomain() }
}
