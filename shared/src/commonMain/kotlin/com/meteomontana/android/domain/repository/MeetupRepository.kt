package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.CreateMeetupRequest
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.model.MeetupAlertState

/**
 * Puerto de quedadas (hexagonal). Los use cases dependen de esta abstracción,
 * NO del `KtorMeetupApi` concreto. El adaptador `KtorMeetupRepository` implementa
 * la orquestación red + caché offline (stale-while-revalidate), igual que
 * BlockRepository/ForecastRepository. Devuelve modelos de dominio, nunca DTOs.
 */
interface MeetupRepository {
    /** Lista de quedadas. Offline: devuelve la caché local filtrada. */
    suspend fun getMeetups(schoolId: String?, date: String?, relation: String?): List<Meetup>

    /** Detalle de una quedada. Offline: la de la caché, o null. */
    suspend fun getMeetup(id: String): Meetup?

    /** Resuelve la quedada por su conversación de chat (abrir detalle desde el chat). */
    suspend fun getMeetupByConversation(conversationId: String): Meetup?

    suspend fun createMeetup(req: CreateMeetupRequest): Meetup
    suspend fun updateMeetup(meetupId: String, description: String?): Meetup

    /** Unirse. [invite] = token del enlace de invitación (o null). */
    suspend fun joinMeetup(id: String, invite: String?): Meetup
    suspend fun leaveMeetup(id: String)

    suspend fun kickMember(meetupId: String, targetUid: String)
    suspend fun updateMyGear(meetupId: String, gearJson: String): Meetup
    suspend fun deleteMeetup(meetupId: String)

    suspend fun reportMeetup(meetupId: String, reportedUid: String?, reason: String, context: String?)

    suspend fun getMeetupAlert(): MeetupAlertState
    suspend fun setMeetupAlert(state: MeetupAlertState): MeetupAlertState

    /** Enlace de invitación al grupo (solo miembros): permite unirse sin
     *  relación de follows. Para el botón de compartir. */
    suspend fun getInviteLink(id: String): String
}
