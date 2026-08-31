package com.meteomontana.android.domain.repository

/**
 * Puerto de dominio para las acciones de chat que pasan por el BACKEND (no por
 * Firestore directo): crear la conversación (puerta de autorización) y disparar
 * la push al receptor. El chat 1-a-1 en sí va por [com.meteomontana.android.domain.port.ChatService].
 */
interface ChatPushRepository {
    /** El backend crea/autoriza la conversación (los clientes no pueden por reglas Firestore). */
    suspend fun startConversation(toUid: String)

    /** Pide al backend que dispare la notificación push del mensaje al receptor. */
    suspend fun notifyMessage(toUid: String, preview: String)
}
