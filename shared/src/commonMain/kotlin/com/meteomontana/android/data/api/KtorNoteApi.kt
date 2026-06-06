package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.CreateNoteRequest
import com.meteomontana.android.data.api.dto.NoteDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class KtorNoteApi(private val client: HttpClient) {

    suspend fun getNotesBySchool(schoolId: String): List<NoteDto> =
        client.get("schools/$schoolId/notes").body()

    suspend fun createNote(schoolId: String, req: CreateNoteRequest): NoteDto =
        client.post("schools/$schoolId/notes") { setBody(req) }.body()
}
