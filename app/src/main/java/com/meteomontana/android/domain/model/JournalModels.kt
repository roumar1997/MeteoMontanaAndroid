package com.meteomontana.android.domain.model

data class JournalSession(
    val id: String,
    val schoolId: String?,
    val schoolName: String?,
    val sector: String?,
    val blockName: String,
    val grade: String?,
    val notes: String?,
    val date: String,
    val createdAt: String
)

data class JournalStats(
    val blockCount: Int,
    val schoolCount: Int,
    val maxGrade: String?,
    val bySchool: List<SchoolStats>
)

data class SchoolStats(val schoolName: String, val blockCount: Int, val maxGrade: String?)
