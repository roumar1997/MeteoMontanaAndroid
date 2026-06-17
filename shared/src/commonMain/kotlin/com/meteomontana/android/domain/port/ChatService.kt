package com.meteomontana.android.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * Puerto de mensajería 1-a-1. Abstrae Firestore del resto de la app.
 * Los modelos de datos son Kotlin puro (sin Firebase, sin java.util.Date).
 * Listo para commonMain en Fase 2.
 */
interface ChatService {
    data class Conversation(
        val id: String,
        val participants: List<String>,
        val lastMessage: String?,
        val lastFromUid: String?,
        val lastAtMillis: Long?,     // millis desde epoch — portable entre plataformas
        val unreadCount: Long
    )

    data class ChatMessage(
        val id: String,
        val fromUid: String,
        val text: String,
        val createdAtMillis: Long?   // millis desde epoch
    )

    fun convIdFor(uidA: String, uidB: String): String

    fun observeMyConversations(): Flow<List<Conversation>>

    fun observeMessages(convId: String): Flow<List<ChatMessage>>

    @Throws(Exception::class)
    suspend fun sendMessage(otherUid: String, text: String)

    @Throws(Exception::class)
    suspend fun markRead(convId: String)

    /** Marca la conversación como NO leída para mí (badge vuelve a aparecer). */
    @Throws(Exception::class)
    suspend fun markUnread(convId: String)

    /** Borra la conversación (mensajes + documento) para ambos. */
    @Throws(Exception::class)
    suspend fun deleteConversation(convId: String)
}
