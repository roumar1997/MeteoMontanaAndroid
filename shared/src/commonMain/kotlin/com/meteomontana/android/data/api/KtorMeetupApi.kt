package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.CreateMeetupRequestDto
import com.meteomontana.android.data.api.dto.MeetupDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorMeetupApi(private val client: HttpClient) {

    suspend fun getMeetups(schoolId: String? = null, date: String? = null,
                           relation: String? = null): List<MeetupDto> =
        client.get("meetups") {
            schoolId?.let { parameter("schoolId", it) }
            date?.let { parameter("date", it) }
            relation?.let { parameter("relation", it) }
        }.body()

    suspend fun getMeetup(id: String): MeetupDto = client.get("meetups/$id").body()

    suspend fun createMeetup(req: CreateMeetupRequestDto): MeetupDto =
        client.post("meetups") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    suspend fun joinMeetup(id: String): MeetupDto =
        client.post("meetups/$id/join").body()

    suspend fun leaveMeetup(id: String) { client.post("meetups/$id/leave") }

    suspend fun kickMember(meetupId: String, targetUid: String) {
        client.post("meetups/$meetupId/kick") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("uid" to targetUid))
        }
    }
}
