package com.meteomontana.android.domain.usecase.meetups

import com.meteomontana.android.domain.model.CreateMeetupRequest
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.model.MeetupAlertState
import com.meteomontana.android.domain.repository.MeetupRepository

// Los use cases dependen del PUERTO MeetupRepository (no del KtorMeetupApi
// concreto). La orquestación red+caché (stale-while-revalidate) vive en
// KtorMeetupRepository. TODOS llevan @Throws porque cruzan a Swift: aunque el
// repo trague la excepción en las lecturas y caiga a caché, declarar @Throws
// mantiene la firma Swift `async throws` uniforme (Swift ya las llama con try)
// y blinda contra un futuro cambio del repo — ver SwiftBoundaryThrowsTest.

class GetMeetupsUseCase(private val repo: MeetupRepository) {
    /** Devuelve lista de quedadas. Stale-while-revalidate en el repositorio. */
    @Throws(Exception::class)
    suspend fun execute(schoolId: String? = null, date: String? = null,
                        relation: String? = null): List<Meetup> =
        repo.getMeetups(schoolId, date, relation)
}

class GetMeetupUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(id: String): Meetup? = repo.getMeetup(id)
}

class CreateMeetupUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(req: CreateMeetupRequest): Meetup = repo.createMeetup(req)
}

/** Editar la descripción de una quedada (solo el organizador). */
class UpdateMeetupUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(meetupId: String, description: String?): Meetup =
        repo.updateMeetup(meetupId, description)
}

/** Resolver la quedada por su conversación de chat (para abrir el detalle desde el chat). */
class GetMeetupByConversationUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(conversationId: String): Meetup? =
        repo.getMeetupByConversation(conversationId)
}

class JoinMeetupUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(id: String): Meetup =
        // Si llegamos por un enlace de invitación, el token permite unirse
        // aunque no haya relación de follows (los "no mixto" siguen exigiendo género).
        repo.joinMeetup(id, invite = PendingMeetupInvite.tokenFor(id))
}

class LeaveMeetupUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(id: String) = repo.leaveMeetup(id)
}

class KickMeetupMemberUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(meetupId: String, targetUid: String) = repo.kickMember(meetupId, targetUid)
}

class UpdateMyGearUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(meetupId: String, gearJson: String): Meetup =
        repo.updateMyGear(meetupId, gearJson)
}

class DeleteMeetupUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(meetupId: String) = repo.deleteMeetup(meetupId)
}

class ReportMeetupUseCase(private val repo: MeetupRepository) {
    /** reason: SPAM | INAPPROPRIATE | HARASSMENT | OTHER */
    @Throws(Exception::class)
    suspend fun execute(meetupId: String, reportedUid: String?,
                        reason: String, context: String?) =
        repo.reportMeetup(meetupId, reportedUid, reason, context)
}

/** Enlace de invitación al grupo (solo miembros): para el botón de compartir.
 *  Espejo Android/iOS — meetupApi.getInviteLink en iOS pasa por el mismo
 *  puerto hexagonal aquí en vez de inyectar KtorMeetupApi directo en el VM. */
class GetMeetupInviteLinkUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(id: String): String = repo.getInviteLink(id)
}

class GetMeetupAlertUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(): MeetupAlertState = repo.getMeetupAlert()
}

class SetMeetupAlertUseCase(private val repo: MeetupRepository) {
    @Throws(Exception::class)
    suspend fun execute(
        enabled: Boolean,
        daysCsv: String? = null,
        schoolId: String? = null,
        discipline: String? = null,
        privacy: String? = null,
        maxDistanceKm: Int? = null,
        userLat: Double? = null,
        userLon: Double? = null
    ): MeetupAlertState = repo.setMeetupAlert(
        MeetupAlertState(
            enabled = enabled, daysCsv = daysCsv, schoolId = schoolId,
            discipline = discipline, privacy = privacy, maxDistanceKm = maxDistanceKm,
            userLat = userLat, userLon = userLon
        )
    )
}

/**
 * Invitación pendiente de consumir (viene del enlace /s/q/{id}?i={token}).
 * La guarda quien parsea el enlace (MainActivity / ShareLinkRouter) y la lee
 * el join. Un solo hueco: la última invitación abierta.
 */
object PendingMeetupInvite {
    private var meetupId: String? = null
    private var token: String? = null

    fun set(meetupId: String, token: String?) {
        this.meetupId = meetupId
        this.token = token
    }

    fun tokenFor(id: String): String? = if (id == meetupId) token else null
}
