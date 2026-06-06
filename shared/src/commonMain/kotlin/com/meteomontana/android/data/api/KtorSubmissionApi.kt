package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.SubmissionDto
import com.meteomontana.android.data.api.dto.SubmitSchoolRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class KtorSubmissionApi(private val client: HttpClient) {

    suspend fun submitSchool(req: SubmitSchoolRequest): SubmissionDto =
        client.post("submissions") { setBody(req) }.body()

    suspend fun getMySubmissions(): List<SubmissionDto> = client.get("submissions/me").body()
}
