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

    /** Voto de utilidad (1 o -1; repetir lo retira). Devuelve el voto vigente. */
    suspend fun voteNote(noteId: String, value: Int): Int {
        val resp: Map<String, Int> = client.post("notes/$noteId/vote") {
            setBody(mapOf("value" to value))
        }.body()
        return resp["myVote"] ?: 0
    }
}
