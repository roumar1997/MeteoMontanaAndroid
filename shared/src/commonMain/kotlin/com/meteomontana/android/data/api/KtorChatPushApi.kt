package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

class KtorChatPushApi(private val client: HttpClient) {

    @Serializable
    data class NotifyRequest(val toUid: String, val preview: String)

    @Serializable
    data class StartRequest(val toUid: String)

    /** Dispara la push notification del receptor tras escribir el mensaje en Firestore. */
    suspend fun notifyMessage(toUid: String, preview: String) {
        client.post("chat/notify") {
            setBody(NotifyRequest(toUid, preview))
        }
    }

    /**
     * Inicia (o asegura) la conversación en el backend antes del primer mensaje.
     * El backend autoriza según el modelo de privacidad y crea el documento de
     * conversación en Firestore (los clientes no pueden crearlo). Lanza si el
     * backend responde error (p.ej. 403 si no está permitido escribir).
     */
    suspend fun startConversation(toUid: String) {
        client.post("chat/start") {
            setBody(StartRequest(toUid))
        }
    }
}
