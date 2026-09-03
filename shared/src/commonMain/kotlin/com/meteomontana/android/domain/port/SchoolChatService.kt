package com.meteomontana.android.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * Chat ABIERTO de una escuela: sin lista de participantes, cualquiera puede
 * leer y escribir — la contrapartida de mensajería de "Estoy aquí"
 * ([com.meteomontana.android.domain.model.SchoolPresence]). Distinto del
 * [ChatService] (1-a-1 / grupos cerrados de Quedadas): ese exige ser
 * miembro, este no.
 */
interface SchoolChatService {
    // Solo fromUid: el nombre/foto de quien escribe se resuelve por uid con
    // GetPublicProfileUseCase (mismo patrón que GroupChatView.resolveNames),
    // no se denormaliza en el mensaje.
    data class Message(
        val id: String,
        val fromUid: String,
        val text: String,
        val createdAtMillis: Long?,
    )

    /** En vivo, últimos [limit] mensajes de la escuela (más recientes primero, invertidos al pintar). */
    fun observeMessages(schoolId: String, limit: Int): Flow<List<Message>>

    @Throws(Exception::class)
    suspend fun sendMessage(schoolId: String, text: String)
}
