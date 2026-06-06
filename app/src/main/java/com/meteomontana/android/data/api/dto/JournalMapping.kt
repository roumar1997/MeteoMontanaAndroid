package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.model.SchoolStats

fun JournalSessionDto.toDomain() = JournalSession(
    id, schoolId, schoolName, sector, blockName, grade, notes, date, createdAt
)

fun JournalStatsDto.toDomain() = JournalStats(
    blockCount, schoolCount, maxGrade, bySchool.map { it.toDomain() }
)

fun SchoolStatsDto.toDomain() = SchoolStats(schoolName, blockCount, maxGrade)
