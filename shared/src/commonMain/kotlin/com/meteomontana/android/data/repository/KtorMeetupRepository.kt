package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorMeetupApi
import com.meteomontana.android.data.api.dto.CreateMeetupRequestDto
import com.meteomontana.android.data.api.dto.MeetupAlertDto
import com.meteomontana.android.data.api.dto.ReportRequestDto
import com.meteomontana.android.data.api.dto.SetAlertRequestDto
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.saved.MeetupCacheRepository
import com.meteomontana.android.domain.model.CreateMeetupRequest
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.model.MeetupAlertState
import com.meteomontana.android.domain.repository.MeetupRepository
import kotlinx.datetime.Clock

/**
 * Adaptador Ktor del puerto [MeetupRepository]. Orquesta la API remota y la caché
 * local (SQLDelight) — stale-while-revalidate: primero red, si falla devuelve la
 * caché filtrada (ver la lista de quedadas offline). La lógica vivía antes
 * repartida en los MeetupUseCases; centralizada aquí (cierra la violación
 * hexagonal: los use cases ya no tocan `KtorMeetupApi` concreto).
 *
 * [cache] es nullable porque en iOS la BD puede no existir; los use cases que la
 * necesitan solo se construyen cuando existe (gate en IosDependencyContainer),
 * pero el adaptador degrada con gracia igualmente.
 */
class KtorMeetupRepository(
    private val api: KtorMeetupApi,
    private val cache: MeetupCacheRepository?,
) : MeetupRepository {

    override suspend fun getMeetups(schoolId: String?, date: String?, relation: String?): List<Meetup> {
        // Caché local, filtrando caducadas CON gracia (expiresAt es medianoche →
        // una quedada de hoy sigue viva; 2 días cubren el día + zonas horarias).
        suspend fun fromCache(): List<Meetup> {
            val cutoff = Clock.System.now().toEpochMilliseconds() -
                com.meteomontana.android.data.saved.MeetupCacheRepository.EXPIRY_GRACE_MS
            var result = (cache?.getAll() ?: emptyList()).filter { it.expiresAt > cutoff }
            if (schoolId != null) result = result.filter { it.schoolId == schoolId }
            if (date != null) result = result.filter { it.days.contains(date) }
            return result
        }
        return try {
            val dtos = api.getMeetups(schoolId, date, relation)
            if (dtos.isNotEmpty()) cache?.saveAll(dtos)   // no vaciar la caché con un vacío
            val fresh = dtos.map { it.toDomain() }
            // RED-PRIMERO, CACHÉ-DE-RESPALDO: si la red viene VACÍA (sin conexión que
            // no lanza, respuesta transitoria vacía…) NO borramos lo que se veía;
            // caemos a la caché. Solo mostramos vacío si la caché también lo está
            // (bug RC2: al recargar sin red desaparecían las quedadas).
            if (fresh.isEmpty()) fromCache().ifEmpty { fresh } else fresh
        } catch (e: Exception) {
            // Sin red: caché si la hay. Si la caché TAMBIÉN está vacía, RELANZAMOS
            // en vez de devolver vacío → el VM (su catch conserva la lista actual)
            // no borra las quedadas que ya se veían (bug RC2, definitivo).
            fromCache().ifEmpty { throw e }
        }
    }

    override suspend fun getMeetup(id: String): Meetup? {
        return try {
            val dto = api.getMeetup(id)
            cache?.saveAll(listOf(dto))
            dto.toDomain()
        } catch (e: Exception) {
            cache?.getById(id)
        }
    }

    override suspend fun getMeetupByConversation(conversationId: String): Meetup? {
        return try {
            api.getMeetupByConversation(conversationId)?.toDomain()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun createMeetup(req: CreateMeetupRequest): Meetup {
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
        cache?.saveAll(listOf(dto))
        return dto.toDomain()
    }

    override suspend fun updateMeetup(meetupId: String, description: String?): Meetup {
        val dto = api.updateMeetup(meetupId, description)
        cache?.saveAll(listOf(dto))
        return dto.toDomain()
    }

    override suspend fun joinMeetup(id: String, invite: String?): Meetup {
        val dto = api.joinMeetup(id, invite = invite)
        cache?.saveAll(listOf(dto))
        return dto.toDomain()
    }

    override suspend fun leaveMeetup(id: String) {
        api.leaveMeetup(id)
        // Actualizar caché: not joined.
        val cached = cache?.getById(id)
        if (cached != null) {
            cache?.updateJoined(id, false, maxOf(0, cached.memberCount - 1))
        }
    }

    override suspend fun kickMember(meetupId: String, targetUid: String) {
        api.kickMember(meetupId, targetUid)
    }

    override suspend fun updateMyGear(meetupId: String, gearJson: String): Meetup =
        api.updateMyGear(meetupId, gearJson).toDomain()

    override suspend fun deleteMeetup(meetupId: String) {
        api.deleteMeetup(meetupId)
    }

    override suspend fun reportMeetup(meetupId: String, reportedUid: String?, reason: String, context: String?) {
        api.reportMeetup(meetupId, ReportRequestDto(reportedUid = reportedUid, reason = reason, context = context))
    }

    override suspend fun getMeetupAlert(): MeetupAlertState =
        api.getMeetupAlert()?.toState() ?: MeetupAlertState(enabled = false)

    override suspend fun setMeetupAlert(state: MeetupAlertState): MeetupAlertState {
        val dto = api.setMeetupAlert(
            SetAlertRequestDto(
                enabled = state.enabled, daysCsv = state.daysCsv, schoolId = state.schoolId,
                discipline = state.discipline, privacy = state.privacy,
                maxDistanceKm = state.maxDistanceKm, userLat = state.userLat, userLon = state.userLon
            )
        )
        return dto.toState()
    }

    override suspend fun getInviteLink(id: String): String = api.getInviteLink(id)

    private fun MeetupAlertDto.toState() = MeetupAlertState(
        enabled = enabled, daysCsv = daysCsv, schoolId = schoolId, schoolName = schoolName,
        discipline = discipline, privacy = privacy, maxDistanceKm = maxDistanceKm,
        userLat = userLat, userLon = userLon
    )
}
