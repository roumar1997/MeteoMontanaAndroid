package com.meteomontana.android.domain.port

import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Puerto de mensajería 1-a-1. Abstrae Firestore del resto de la app.
 * Los modelos de datos son Kotlin puro (sin Firebase).
 * Listo para commonMain en Fase 2 cuando Firebase KMP sea estable.
 */
interface ChatService {
    data class Conversation(
        val id: String,
        val participants: List<String>,
        val lastMessage: String?,
        val lastFromUid: String?,
        val lastAt: Date?,
        val unreadCount: Long
    )

    data class ChatMessage(
        val id: String,
        val fromUid: String,
        val text: String,
        val createdAt: Date?
    )

    fun convIdFor(uidA: String, uidB: String): String

    fun observeMyConversations(): Flow<List<Conversation>>

    fun observeMessages(convId: String): Flow<List<ChatMessage>>

    suspend fun sendMessage(otherUid: String, text: String)

    suspend fun markRead(convId: String)
}
