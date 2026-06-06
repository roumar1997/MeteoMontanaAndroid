package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.FcmTokenRequest
import com.meteomontana.android.data.api.dto.PrivateProfileDto
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class KtorProfileApi(private val client: HttpClient) {

    suspend fun getMyProfile(): PrivateProfileDto = client.get("me").body()

    suspend fun updateMyProfile(req: UpdateProfileRequest): PrivateProfileDto =
        client.put("me") { setBody(req) }.body()

    suspend fun updateFcmToken(req: FcmTokenRequest) {
        client.put("me/fcm-token") { setBody(req) }
    }
}
