package com.meteomontana.android.domain.usecase.meetups

import com.meteomontana.android.data.api.KtorMeetupApi
import com.meteomontana.android.data.api.dto.CreateMeetupRequestDto
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.saved.MeetupCacheRepository
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.model.CreateMeetupRequest

class GetMeetupsUseCase(
    private val api: KtorMeetupApi,
    private val cache: MeetupCacheRepository
) {
    /** Devuelve lista de quedadas. Stale-while-revalidate: primero caché, luego refresca. */
    suspend fun execute(schoolId: String? = null, date: String? = null,
                        relation: String? = null): List<Meetup> {
        return try {
            val dtos = api.getMeetups(schoolId, date, relation)
            cache.saveAll(dtos)
            dtos.map { it.toDomain() }
        } catch (e: Exception) {
            // Offline: devolver caché local, filtrando caducadas
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            cache.getAll().let { cached ->
                var result = cached.filter { it.expiresAt > now }
                if (schoolId != null) result = result.filter { it.schoolId == schoolId }
                if (date != null) result = result.filter { it.days.contains(date) }
                result
            }
        }
    }
}

class GetMeetupUseCase(
    private val api: KtorMeetupApi,
    private val cache: MeetupCacheRepository
) {
    suspend fun execute(id: String): Meetup? {
        return try {
            val dto = api.getMeetup(id)
            cache.saveAll(listOf(dto))
            dto.toDomain()
        } catch (e: Exception) {
            cache.getById(id)
        }
    }
}

class CreateMeetupUseCase(
    private val api: KtorMeetupApi,
    private val cache: MeetupCacheRepository
) {
    suspend fun execute(req: CreateMeetupRequest): Meetup {
        val dto = api.createMeetup(
            CreateMeetupRequestDto(
                schoolId = req.schoolId,
                name = req.name,
                description = req.description,
                discipline = req.discipline,
                privacy = req.privacy,
                memberLimit = req.memberLimit,
                photoUrl = req.photoUrl,
                days = req.days
            )
        )
        cache.saveAll(listOf(dto))
        return dto.toDomain()
    }
}

/** Editar la descripción de una quedada (solo el organizador). */
class UpdateMeetupUseCase(
    private val api: KtorMeetupApi,
    private val cache: MeetupCacheRepository
) {
    suspend fun execute(meetupId: String, description: String?): Meetup {
        val dto = api.updateMeetup(meetupId, description)
        cache.saveAll(listOf(dto))
        return dto.toDomain()
    }
}

/** Resolver la quedada por su conversación de chat (para abrir el detalle desde el chat). */
class GetMeetupByConversationUseCase(
    private val api: KtorMeetupApi
) {
    suspend fun execute(conversationId: String): Meetup? {
        return try {
            api.getMeetupByConversation(conversationId)?.toDomain()
        } catch (e: Exception) {
            null
        }
    }
}

class JoinMeetupUseCase(
    private val api: KtorMeetupApi,
    private val cache: MeetupCacheRepository
) {
    suspend fun execute(id: String): Meetup {
        val dto = api.joinMeetup(id)
        cache.saveAll(listOf(dto))
        return dto.toDomain()
    }
}

class LeaveMeetupUseCase(
    private val api: KtorMeetupApi,
    private val cache: MeetupCacheRepository
) {
    suspend fun execute(id: String) {
        api.leaveMeetup(id)
        // Actualizar caché: not joined
        val cached = cache.getById(id)
        if (cached != null) {
            cache.updateJoined(id, false, maxOf(0, cached.memberCount - 1))
        }
    }
}

class KickMeetupMemberUseCase(
    private val api: KtorMeetupApi
) {
    suspend fun execute(meetupId: String, targetUid: String) {
        api.kickMember(meetupId, targetUid)
    }
}

class UpdateMyGearUseCase(private val api: KtorMeetupApi) {
    suspend fun execute(meetupId: String, gearJson: String): Meetup {
        return api.updateMyGear(meetupId, gearJson).toDomain()
    }
}

class DeleteMeetupUseCase(private val api: KtorMeetupApi) {
    suspend fun execute(meetupId: String) { api.deleteMeetup(meetupId) }
}

class ReportMeetupUseCase(private val api: KtorMeetupApi) {
    /** reason: SPAM | INAPPROPRIATE | HARASSMENT | OTHER */
    suspend fun execute(meetupId: String, reportedUid: String?,
                        reason: String, context: String?) {
        api.reportMeetup(meetupId,
            com.meteomontana.android.data.api.dto.ReportRequestDto(
                reportedUid = reportedUid, reason = reason, context = context))
    }
}

/** Estado completo de la alerta de quedadas, con todos los filtros configurables. */
data class MeetupAlertState(
    val enabled: Boolean,
    val daysCsv: String? = null,
    val schoolId: String? = null,
    val schoolName: String? = null,
    val discipline: String? = null,    // BOULDER | ROUTE | BOTH | null = cualquiera
    val privacy: String? = null,       // OPEN | FOLLOWERS | WOMEN | null = cualquiera
    val maxDistanceKm: Int? = null,
    val userLat: Double? = null,
    val userLon: Double? = null
)

private fun com.meteomontana.android.data.api.dto.MeetupAlertDto.toState() = MeetupAlertState(
    enabled = enabled, daysCsv = daysCsv, schoolId = schoolId, schoolName = schoolName,
    discipline = discipline, privacy = privacy, maxDistanceKm = maxDistanceKm,
    userLat = userLat, userLon = userLon
)

class GetMeetupAlertUseCase(private val api: KtorMeetupApi) {
    suspend fun execute(): MeetupAlertState {
        val dto = api.getMeetupAlert()
        return dto?.toState() ?: MeetupAlertState(enabled = false)
    }
}

class SetMeetupAlertUseCase(private val api: KtorMeetupApi) {
    suspend fun execute(
        enabled: Boolean,
        daysCsv: String? = null,
        schoolId: String? = null,
        discipline: String? = null,
        privacy: String? = null,
        maxDistanceKm: Int? = null,
        userLat: Double? = null,
        userLon: Double? = null
    ): MeetupAlertState {
        val dto = api.setMeetupAlert(
            com.meteomontana.android.data.api.dto.SetAlertRequestDto(
                enabled = enabled, daysCsv = daysCsv, schoolId = schoolId,
                discipline = discipline, privacy = privacy, maxDistanceKm = maxDistanceKm,
                userLat = userLat, userLon = userLon
            )
        )
        return dto.toState()
    }
}
