package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.Contribution

fun ContributionDto.toDomain() = Contribution(
    id = id, type = type, status = status, schoolId = schoolId,
    schoolName = schoolName, name = name, lat = lat, lon = lon,
    notes = notes, description = description,
    submittedByName = submittedByName, reviewReason = reviewReason,
    createdAt = createdAt, reviewedAt = reviewedAt,
    photoUrl = photoUrl, bloquesJson = bloquesJson,
    topoLinesJson = topoLinesJson, targetBlockId = targetBlockId
)
