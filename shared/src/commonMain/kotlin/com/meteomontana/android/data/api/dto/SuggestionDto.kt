package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SuggestionRequest(
    val message: String,
    val platform: String,
    val appVersion: String? = null
)

@Serializable
data class SuggestionResponse(
    val id: String,
    val createdAt: String
)
