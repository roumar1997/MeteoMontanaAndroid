package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymCorrection
import com.meteomontana.android.domain.model.gym.GymNear
import com.meteomontana.android.domain.model.gym.GymReport
import com.meteomontana.android.domain.model.gym.NewGymDraft

/** Copia local del catalogo de rocodromos (con el ETag con el que se descargo). Funciona sin conexion. */
data class GymCatalogSnapshot(val gyms: List<Gym>, val etag: String?)

interface GymCatalogCache {
    /** null si no hay nada guardado (o esta ilegible). */
    fun load(): GymCatalogSnapshot?
    fun save(snapshot: GymCatalogSnapshot)
}

/** Puerto del catalogo. Los use cases dependen de esto, nunca del `KtorGymApi`. */
interface GymRepository {
    /** Lo que haya en la copia local, al instante (vacio si nunca se ha descargado). */
    fun cachedGyms(): List<Gym>

    /**
     * Catalogo entero. Pregunta al servidor con el ETag; en 304 o sin red usa la copia local.
     * Lanza solo si no hay red Y no hay copia.
     */
    suspend fun getGyms(): List<Gym>

    suspend fun getGym(id: Long): Gym

    suspend fun findNear(lat: Double, lon: Double, radiusKm: Double, limit: Int): List<GymNear>
}

interface GymFavoriteRepository {
    suspend fun getFavoriteIds(): Set<Long>
    suspend fun setFavorite(gymId: Long, favorite: Boolean)
}

interface GymReportRepository {
    suspend fun submitCorrection(correction: GymCorrection): GymReport
    suspend fun submitNewGym(draft: NewGymDraft): GymReport
    suspend fun uploadPhoto(bytes: ByteArray): String
}

/** "Quien va hoy" en un rocodromo; devuelve el mismo modelo que la presencia de escuelas. */
interface GymPresenceRepository {
    suspend fun getActivePresence(gymId: Long): List<SchoolPresence>
    suspend fun markPresence(gymId: Long): SchoolPresence
    suspend fun clearPresence(gymId: Long)
}
