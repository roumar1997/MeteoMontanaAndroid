package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

/**
 * NOTA KMP↔Swift: toda `suspend` pública de esta clase va con
 * `@Throws(Exception::class)`. Se llaman desde Swift (`container.chatPushApi...`)
 * con `try`/`try?`; sin la anotación SKIE genera una firma NO-throwing y una
 * excepción de Ktor (p.ej. sin red o 403) escapa a Kotlin/Native → ABORT.
 */
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
    @Throws(Exception::class)
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
    @Throws(Exception::class)
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
    @Throws(Exception::class)
    suspend fun createGroup(name: String, memberUids: List<String>): String {
        return client.post("chat/group") {
            setBody(CreateGroupRequest(name, memberUids))
        }.body<CreateGroupResponse>().convId
    }

    /** Dispara push a TODOS los miembros del grupo (menos al emisor). */
    @Throws(Exception::class)
    suspend fun notifyGroup(convId: String, preview: String) {
        client.post("chat/notify-group") {
            setBody(NotifyGroupRequest(convId, preview))
        }
    }
}
