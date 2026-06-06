package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.ContributionDto
import com.meteomontana.android.data.api.dto.ContributionRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class KtorContributionApi(private val client: HttpClient) {

    suspend fun submitContribution(schoolId: String, req: ContributionRequest): ContributionDto =
        client.post("schools/$schoolId/contributions") { setBody(req) }.body()

    suspend fun getMyContributions(): List<ContributionDto> =
        client.get("contributions/me").body()
}
