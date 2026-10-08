package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorAssistantApi
import com.meteomontana.android.data.api.dto.AssistantAskRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.api.dto.toDto
import com.meteomontana.android.domain.model.AssistantAnswer
import com.meteomontana.android.domain.model.AssistantUnderstood
import com.meteomontana.android.domain.repository.AssistantRepository

class KtorAssistantRepository(private val api: KtorAssistantApi) : AssistantRepository {

    override suspend fun ask(
        text: String,
        previous: AssistantUnderstood?,
        lat: Double?,
        lon: Double?,
        schoolId: String?
    ): AssistantAnswer =
        api.ask(AssistantAskRequest(text, previous?.toDto(), lat, lon, schoolId)).toDomain()
}
