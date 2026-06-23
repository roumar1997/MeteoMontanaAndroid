package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

class KtorChatPushApi(private val client: HttpClient) {

    @Serializable
    data class NotifyRequest(val toUid: String, val preview: String)

    @Serializable
    data class StartRequest(val toUid: String)

    @Serializable
    data class CreateGroupRequest(val name: String, val memberUids: List<String>)

    @Serializable
    data class CreateGroupResponse(val convId: String)

    @Serializable
    data class NotifyGroupRequest(val convId: String, val preview: String)

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

    /**
     * Crea un GRUPO de chat en el backend (las reglas Firestore prohíben crear
     * conversaciones desde el cliente). El backend crea el documento con
     * participants = [yo + memberUids], isGroup=true y name. Devuelve su convId.
     */
    suspend fun createGroup(name: String, memberUids: List<String>): String {
        return client.post("chat/group") {
            setBody(CreateGroupRequest(name, memberUids))
        }.body<CreateGroupResponse>().convId
    }

    /** Dispara push a TODOS los miembros del grupo (menos al emisor). */
    suspend fun notifyGroup(convId: String, preview: String) {
        client.post("chat/notify-group") {
            setBody(NotifyGroupRequest(convId, preview))
        }
    }
}
