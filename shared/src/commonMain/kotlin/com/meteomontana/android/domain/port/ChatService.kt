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
        val unreadCount: Long,
        /** millis en que YO "borré para mí" esta conversación (null = no borrada).
         *  Si hay un mensaje posterior a esto, vuelve a aparecer. */
        val clearedAtMillis: Long? = null,
        /** Grupos: true y `name` no nulo para conversaciones de grupo (varias
         *  personas). En 1-a-1 isGroup=false y name=null. Aditivo: docs viejos
         *  sin estos campos se leen como 1-a-1. */
        val isGroup: Boolean = false,
        val name: String? = null
    )

    data class ChatMessage(
        val id: String,
        val fromUid: String,
        val text: String,
        val createdAtMillis: Long?,  // millis desde epoch
        /** Responder citando otro mensaje (estilo WhatsApp). null si no es respuesta. */
        val replyToId: String? = null,
        val replyText: String? = null,
        val replyFromUid: String? = null
    )

    fun convIdFor(uidA: String, uidB: String): String

    fun observeMyConversations(): Flow<List<Conversation>>

    /**
     * Observa los últimos [limit] mensajes de la conversación (ventana en vivo).
     * Para "cargar mensajes anteriores" se vuelve a llamar con un [limit] mayor:
     * el listener re-suscribe con una ventana más grande (patrón de ventana
     * creciente). Empezar en [MESSAGE_PAGE] recorta las lecturas de Firestore al
     * abrir un chat (antes leía siempre 200 de golpe).
     */
    fun observeMessages(convId: String, limit: Int): Flow<List<ChatMessage>>

    companion object {
        /** Tamaño de la ventana inicial y del incremento al cargar antiguos. */
        const val MESSAGE_PAGE: Int = 50
    }

    /** Envía a un chat 1-a-1. Los params reply* van null si no es una respuesta. */
    @Throws(Exception::class)
    suspend fun sendMessage(
        otherUid: String,
        text: String,
        replyToId: String?,
        replyText: String?,
        replyFromUid: String?
    )

    /** Envía a un GRUPO (por convId; el doc del grupo lo crea el backend). Los
     *  params reply* van null si no es una respuesta. */
    @Throws(Exception::class)
    suspend fun sendGroupMessage(
        convId: String,
        text: String,
        replyToId: String?,
        replyText: String?,
        replyFromUid: String?
    )

    @Throws(Exception::class)
    suspend fun markRead(convId: String)

    /** Marca la conversación como NO leída para mí (badge vuelve a aparecer). */
    @Throws(Exception::class)
    suspend fun markUnread(convId: String)

    /** "Borrar para mí": oculta la conversación de MI lista y mi historial
     *  (marca cleared_<miUid> = ahora). No afecta a la otra persona; si me
     *  vuelve a escribir, reaparece con los mensajes nuevos. */
    @Throws(Exception::class)
    suspend fun deleteConversation(convId: String)
}
