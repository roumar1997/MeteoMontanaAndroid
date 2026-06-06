package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.FcmTokenRequest
import com.meteomontana.android.data.api.dto.PrivateProfileDto
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

interface ProfileApi {

    @GET("me")
    suspend fun getMyProfile(): PrivateProfileDto

    @PUT("me")
    suspend fun updateMyProfile(@Body req: UpdateProfileRequest): PrivateProfileDto

    @Multipart
    @POST("me/photo")
    suspend fun uploadMyPhoto(@Part file: MultipartBody.Part): PrivateProfileDto

    @PUT("me/fcm-token")
    suspend fun updateFcmToken(@Body req: FcmTokenRequest)
}
