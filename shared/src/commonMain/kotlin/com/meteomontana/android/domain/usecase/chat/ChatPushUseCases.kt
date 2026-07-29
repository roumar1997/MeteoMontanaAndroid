package com.meteomontana.android.domain.usecase.chat

import com.meteomontana.android.domain.repository.ChatPushRepository

/** El backend crea/autoriza la conversación antes del primer mensaje. */
class StartConversationUseCase(private val repo: ChatPushRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(toUid: String) = repo.startConversation(toUid)
}

/** Dispara la push del mensaje al receptor (tras escribirlo en Firestore). */
class NotifyChatMessageUseCase(private val repo: ChatPushRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(toUid: String, preview: String) = repo.notifyMessage(toUid, preview)
}
