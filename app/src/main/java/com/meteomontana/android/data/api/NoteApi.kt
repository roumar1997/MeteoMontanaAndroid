package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.CreateNoteRequest
import com.meteomontana.android.data.api.dto.NoteDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface NoteApi {

    @GET("schools/{id}/notes")
    suspend fun getNotesBySchool(@Path("id") id: String): List<NoteDto>

    @POST("schools/{id}/notes")
    suspend fun createNote(@Path("id") id: String, @Body req: CreateNoteRequest): NoteDto
}
