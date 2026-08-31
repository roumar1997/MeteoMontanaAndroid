package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.AddApproachPinRequest
import com.meteomontana.android.data.api.dto.ApproachDto
import com.meteomontana.android.data.api.dto.ApproachPinDto
import com.meteomontana.android.data.api.dto.CreateApproachRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class KtorApproachApi(private val client: HttpClient) {

    @Throws(Exception::class)
    suspend fun getApproaches(schoolId: String): List<ApproachDto> =
        client.get("schools/$schoolId/approaches").body()

    /** SOLO ADMIN — el backend responde 403 a cualquier otro uid. */
    @Throws(Exception::class)
    suspend fun createApproach(schoolId: String, req: CreateApproachRequest): ApproachDto =
        client.post("schools/$schoolId/approaches") { setBody(req) }.body()

    @Throws(Exception::class)
    suspend fun addPin(approachId: String, req: AddApproachPinRequest): ApproachPinDto =
        client.post("approaches/$approachId/pins") { setBody(req) }.body()

    @Throws(Exception::class)
    suspend fun deleteApproach(approachId: String) {
        client.delete("approaches/$approachId")
    }

    @Throws(Exception::class)
    suspend fun deletePin(pinId: String) {
        client.delete("approaches/pins/$pinId")
    }
}
