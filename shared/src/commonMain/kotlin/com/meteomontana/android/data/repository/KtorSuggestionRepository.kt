package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorSuggestionApi
import com.meteomontana.android.data.api.dto.SuggestionRequest
import com.meteomontana.android.domain.repository.SuggestionRepository

class KtorSuggestionRepository(private val api: KtorSuggestionApi) : SuggestionRepository {
    override suspend fun submit(message: String, platform: String, appVersion: String?) {
        api.submit(SuggestionRequest(message, platform, appVersion))
    }
}
