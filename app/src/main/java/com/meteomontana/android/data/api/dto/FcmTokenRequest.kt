package com.meteomontana.android.data.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FcmTokenRequest(val token: String)
