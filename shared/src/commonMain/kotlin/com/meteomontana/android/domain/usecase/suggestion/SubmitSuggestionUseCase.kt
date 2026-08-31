package com.meteomontana.android.domain.usecase.suggestion

import com.meteomontana.android.domain.repository.SuggestionRepository

class SubmitSuggestionUseCase(private val repository: SuggestionRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(message: String, platform: String, appVersion: String? = null) =
        repository.submit(message, platform, appVersion)
}
