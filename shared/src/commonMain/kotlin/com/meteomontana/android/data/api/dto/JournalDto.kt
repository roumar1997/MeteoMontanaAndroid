package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class JournalSessionDto(
    val id: String,
    val schoolId: String? = null,
    val schoolName: String? = null,
    val sector: String? = null,
    val blockName: String,
    val grade: String? = null,
    val notes: String? = null,
    val date: String,
    val createdAt: String
)

@Serializable
data class CreateJournalRequest(
    val schoolId: String? = null,
    val schoolName: String? = null,
    val sector: String? = null,
    val blockName: String,
    val grade: String? = null,
    val notes: String? = null,
    val date: String
)

@Serializable
data class JournalStatsDto(
    val blockCount: Int,
    val schoolCount: Int,
    val maxGrade: String? = null,
    val bySchool: List<SchoolStatsDto>
)

@Serializable
data class SchoolStatsDto(
    val schoolName: String,
    val blockCount: Int,
    val maxGrade: String? = null
)

@Serializable
data class NoteDto(
    val id: String,
    val schoolId: String,
    val text: String,
    val author: String? = null,
    val uid: String,
    val createdAt: String,
    val upvotesCount: Int,
    val downvotesCount: Int,
    val photoUrl: String? = null
)

@Serializable
data class CreateNoteRequest(val text: String, val photoUrl: String? = null)
