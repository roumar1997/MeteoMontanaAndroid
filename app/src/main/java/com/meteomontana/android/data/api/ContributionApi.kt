package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.ContributionDto
import com.meteomontana.android.data.api.dto.ContributionRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ContributionApi {

    @POST("schools/{schoolId}/contributions")
    suspend fun submitContribution(
        @Path("schoolId") schoolId: String,
        @Body req: ContributionRequest
    ): ContributionDto

    @GET("contributions/me")
    suspend fun getMyContributions(): List<ContributionDto>
}
