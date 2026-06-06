package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.SubmissionDto
import com.meteomontana.android.data.api.dto.SubmitSchoolRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface SubmissionApi {

    @POST("submissions")
    suspend fun submitSchool(@Body req: SubmitSchoolRequest): SubmissionDto

    @GET("submissions/me")
    suspend fun getMySubmissions(): List<SubmissionDto>
}
