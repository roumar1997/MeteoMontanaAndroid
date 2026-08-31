package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorChatPushApi
import com.meteomontana.android.domain.repository.ChatPushRepository

class KtorChatPushRepository(private val api: KtorChatPushApi) : ChatPushRepository {
    override suspend fun startConversation(toUid: String) = api.startConversation(toUid)
    override suspend fun notifyMessage(toUid: String, preview: String) = api.notifyMessage(toUid, preview)
}
