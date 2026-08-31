package com.meteomontana.android.domain.repository

interface SuggestionRepository {
    @Throws(Exception::class)
    suspend fun submit(message: String, platform: String, appVersion: String?)
}
