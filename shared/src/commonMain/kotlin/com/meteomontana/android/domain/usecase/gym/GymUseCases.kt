package com.meteomontana.android.domain.usecase.gym

import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymAdminEdit
import com.meteomontana.android.domain.model.gym.GymClusterer
import com.meteomontana.android.domain.model.gym.GymCorrection
import com.meteomontana.android.domain.model.gym.GymFilterEngine
import com.meteomontana.android.domain.model.gym.GymFilters
import com.meteomontana.android.domain.model.gym.GymListItem
import com.meteomontana.android.domain.model.gym.GymMapItem
import com.meteomontana.android.domain.model.gym.GymMode
import com.meteomontana.android.domain.model.gym.GymNear
import com.meteomontana.android.domain.model.gym.GymOrigin
import com.meteomontana.android.domain.model.gym.GymReport
import com.meteomontana.android.domain.model.gym.NewGymDraft
import com.meteomontana.android.domain.repository.GymAdminRepository
import com.meteomontana.android.domain.repository.GymFavoriteRepository
import com.meteomontana.android.domain.repository.GymPresenceRepository
import com.meteomontana.android.domain.repository.GymReportRepository
import com.meteomontana.android.domain.repository.GymRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// Los use cases dependen de los PUERTOS. @Throws en cada suspend publica porque cruzan a Swift
// (ver SwiftBoundaryThrowsTest). Los que son logica pura no son suspend.

/** Lo que haya en la copia local, al instante (para pintar antes de que llegue la red). */
class GetCachedGymsUseCase(private val repo: GymRepository) {
    operator fun invoke(): List<Gym> = repo.cachedGyms()
}

class GetGymsUseCase(private val repo: GymRepository) {
    @Throws(Exception::class)
    suspend fun execute(): List<Gym> = repo.getGyms()
}

class GetGymUseCase(private val repo: GymRepository) {
    @Throws(Exception::class)
    suspend fun execute(id: Long): Gym = repo.getGym(id)
}

/** Rocodromos cerca de un punto (Plan B). Por defecto 30 km y 5 resultados. */
class FindGymsNearUseCase(private val repo: GymRepository) {
    @Throws(Exception::class)
    suspend fun execute(
        lat: Double,
        lon: Double,
        radiusKm: Double = ShouldSuggestGymsUseCase.RADIUS_KM,
        limit: Int = ShouldSuggestGymsUseCase.LIMIT
    ): List<GymNear> = repo.findNear(lat, lon, radiusKm, limit)
}

/** Filtra y ordena el catalogo (logica pura, igual en iOS y Android). */
class FilterGymsUseCase {
    operator fun invoke(
        gyms: List<Gym>,
        filters: GymFilters,
        origin: GymOrigin? = null,
        favoriteIds: Set<Long> = emptySet(),
        now: Instant = Clock.System.now()
    ): List<GymListItem> = GymFilterEngine.apply(gyms, filters, origin, favoriteIds, now)

    fun chains(gyms: List<Gym>, mode: GymMode): List<String> = GymFilterEngine.chains(gyms, mode)
}

/** Agrupa para el mapa segun el zoom (rejilla comun a las dos plataformas). */
class ClusterGymsUseCase {
    operator fun invoke(gyms: List<Gym>, zoom: Double): List<GymMapItem> = GymClusterer.cluster(gyms, zoom)
}

class GetGymFavoritesUseCase(private val repo: GymFavoriteRepository) {
    @Throws(Exception::class)
    suspend fun execute(): Set<Long> = repo.getFavoriteIds()
}

/** Pone o quita el favorito y devuelve el estado final. */
class SetGymFavoriteUseCase(private val repo: GymFavoriteRepository) {
    @Throws(Exception::class)
    suspend fun execute(gymId: Long, favorite: Boolean): Boolean {
        repo.setFavorite(gymId, favorite)
        return favorite
    }
}

class SubmitGymCorrectionUseCase(private val repo: GymReportRepository) {
    @Throws(Exception::class)
    suspend fun execute(correction: GymCorrection): GymReport = repo.submitCorrection(correction)
}

class SubmitNewGymUseCase(private val repo: GymReportRepository) {
    @Throws(Exception::class)
    suspend fun execute(draft: NewGymDraft): GymReport = repo.submitNewGym(draft)
}

class UploadGymReportPhotoUseCase(private val repo: GymReportRepository) {
    @Throws(Exception::class)
    suspend fun execute(bytes: ByteArray): String = repo.uploadPhoto(bytes)
}

class GetGymPresenceUseCase(private val repo: GymPresenceRepository) {
    @Throws(Exception::class)
    suspend fun execute(gymId: Long): List<SchoolPresence> = repo.getActivePresence(gymId)
}

class MarkGymPresenceUseCase(private val repo: GymPresenceRepository) {
    @Throws(Exception::class)
    suspend fun execute(gymId: Long): SchoolPresence = repo.markPresence(gymId)
}

class ClearGymPresenceUseCase(private val repo: GymPresenceRepository) {
    @Throws(Exception::class)
    suspend fun execute(gymId: Long) = repo.clearPresence(gymId)
}

// Admin (solo iOS): cola de reportes pendientes y edicion de fichas.

class ListPendingGymReportsUseCase(private val repo: GymAdminRepository) {
    @Throws(Exception::class)
    suspend fun execute(): List<GymReport> = repo.pendingReports()
}

class ApplyGymReportUseCase(private val repo: GymAdminRepository) {
    @Throws(Exception::class)
    suspend fun execute(id: Long): GymReport = repo.applyReport(id)
}

class RejectGymReportUseCase(private val repo: GymAdminRepository) {
    @Throws(Exception::class)
    suspend fun execute(id: Long): GymReport = repo.rejectReport(id)
}

class AdminEditGymUseCase(private val repo: GymAdminRepository) {
    @Throws(Exception::class)
    suspend fun execute(id: Long, edit: GymAdminEdit): Gym = repo.editGym(id, edit)
}
