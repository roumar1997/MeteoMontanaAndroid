package com.meteomontana.android.data.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class JournalSessionDto(
    val id: String,
    val schoolId: String?,
    val schoolName: String?,
    val sector: String?,
    val blockName: String,
    val grade: String?,
    val notes: String?,
    val date: String,           // ISO yyyy-MM-dd
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class CreateJournalRequest(
    val schoolId: String?,
    val schoolName: String?,
    val sector: String?,
    val blockName: String,
    val grade: String?,
    val notes: String?,
    val date: String            // ISO yyyy-MM-dd
)

@JsonClass(generateAdapter = true)
data class JournalStatsDto(
    val blockCount: Int,
    val schoolCount: Int,
    val maxGrade: String?,
    val bySchool: List<SchoolStatsDto>
)

@JsonClass(generateAdapter = true)
data class SchoolStatsDto(
    val schoolName: String,
    val blockCount: Int,
    val maxGrade: String?
)

@JsonClass(generateAdapter = true)
data class NoteDto(
    val id: String,
    val schoolId: String,
    val text: String,
    val author: String?,
    val uid: String,
    val createdAt: String,
    val upvotesCount: Int,
    val downvotesCount: Int
)

@JsonClass(generateAdapter = true)
data class CreateNoteRequest(val text: String)
