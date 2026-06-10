package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.data.api.dto.JournalSessionDto
import com.meteomontana.android.data.api.dto.JournalStatsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class KtorJournalApi(private val client: HttpClient) {

    suspend fun createJournalSession(req: CreateJournalRequest): JournalSessionDto =
        client.post("journal") { setBody(req) }.body()

    suspend fun getMyJournal(): List<JournalSessionDto> = client.get("journal/me").body()

    suspend fun getMyJournalStats(): JournalStatsDto = client.get("journal/me/stats").body()

    suspend fun getUserStats(uid: String): JournalStatsDto = client.get("users/$uid/stats").body()

    suspend fun getUserJournal(uid: String): List<JournalSessionDto> =
        client.get("users/$uid/journal").body()

    suspend fun deleteJournalSession(id: String) { client.delete("journal/$id") }
}
