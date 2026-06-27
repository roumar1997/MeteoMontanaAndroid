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
            // Offline: devolver caché local
            cache.getAll().let { cached ->
                var result = cached
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

class ReportMeetupUseCase(private val api: KtorMeetupApi) {
    /** reason: SPAM | INAPPROPRIATE | HARASSMENT | OTHER */
    suspend fun execute(meetupId: String, reportedUid: String?,
                        reason: String, context: String?) {
        api.reportMeetup(meetupId,
            com.meteomontana.android.data.api.dto.ReportRequestDto(
                reportedUid = reportedUid, reason = reason, context = context))
    }
}

/** Devuelve si la alerta está activa (enabled=true) y los días configurados. */
data class MeetupAlertState(val enabled: Boolean, val daysCsv: String?)

class GetMeetupAlertUseCase(private val api: KtorMeetupApi) {
    suspend fun execute(): MeetupAlertState {
        val dto = api.getMeetupAlert()
        return MeetupAlertState(enabled = dto != null, daysCsv = dto?.daysCsv)
    }
}

class SetMeetupAlertUseCase(private val api: KtorMeetupApi) {
    suspend fun execute(enabled: Boolean, daysCsv: String?): MeetupAlertState {
        val dto = api.setMeetupAlert(enabled, daysCsv)
        return MeetupAlertState(enabled = enabled, daysCsv = dto.daysCsv)
    }
}
