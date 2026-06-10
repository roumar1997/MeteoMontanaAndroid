package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

class KtorChatPushApi(private val client: HttpClient) {

    @Serializable
    data class NotifyRequest(val toUid: String, val preview: String)

    /** Dispara la push notification del receptor tras escribir el mensaje en Firestore. */
    suspend fun notifyMessage(toUid: String, preview: String) {
        client.post("chat/notify") {
            setBody(NotifyRequest(toUid, preview))
        }
    }
}
