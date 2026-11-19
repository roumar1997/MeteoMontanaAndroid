package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorGymAdminApi
import com.meteomontana.android.data.api.KtorGymApi
import com.meteomontana.android.data.api.KtorGymPresenceApi
import com.meteomontana.android.data.api.KtorGymReportApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.api.dto.toDto
import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymAdminEdit
import com.meteomontana.android.domain.model.gym.GymCorrection
import com.meteomontana.android.domain.model.gym.GymReport
import com.meteomontana.android.domain.model.gym.NewGymDraft
import com.meteomontana.android.domain.repository.GymAdminRepository
import com.meteomontana.android.domain.repository.GymFavoriteRepository
import com.meteomontana.android.domain.repository.GymPresenceRepository
import com.meteomontana.android.domain.repository.GymReportRepository

class KtorGymFavoriteRepository(private val api: KtorGymApi) : GymFavoriteRepository {
    override suspend fun getFavoriteIds(): Set<Long> = api.getFavoriteIds().toSet()

    override suspend fun setFavorite(gymId: Long, favorite: Boolean) {
        if (favorite) api.addFavorite(gymId) else api.removeFavorite(gymId)
    }
}

class KtorGymReportRepository(private val api: KtorGymReportApi) : GymReportRepository {
    override suspend fun submitCorrection(correction: GymCorrection): GymReport =
        api.submitCorrection(correction.gymId, correction.toDto()).toDomain()

    override suspend fun submitNewGym(draft: NewGymDraft): GymReport =
        api.submitNewGym(draft.toDto()).toDomain()

    override suspend fun uploadPhoto(bytes: ByteArray): String = api.uploadPhoto(bytes)
}

/** Sin cache: es informacion en vivo, igual que la presencia de escuelas. */
class KtorGymPresenceRepository(private val api: KtorGymPresenceApi) : GymPresenceRepository {
    override suspend fun getActivePresence(gymId: Long): List<SchoolPresence> =
        api.getActivePresence(gymId).map { it.toDomain() }

    override suspend fun markPresence(gymId: Long): SchoolPresence = api.markPresence(gymId).toDomain()

    override suspend fun clearPresence(gymId: Long) = api.clearPresence(gymId)
}

/** Revision de reportes y edicion de fichas (admin, solo iOS). Sin cache. */
class KtorGymAdminRepository(private val api: KtorGymAdminApi) : GymAdminRepository {
    override suspend fun pendingReports(): List<GymReport> = api.pendingReports().map { it.toDomain() }
    override suspend fun applyReport(id: Long): GymReport = api.applyReport(id).toDomain()
    override suspend fun rejectReport(id: Long): GymReport = api.rejectReport(id).toDomain()
    override suspend fun editGym(id: Long, edit: GymAdminEdit): Gym = api.editGym(id, edit.toDto()).toDomain()
}
