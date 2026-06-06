package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine

fun BlockDto.toDomain() = Block(
    id = id, schoolId = schoolId, type = type, name = name,
    lat = lat, lon = lon, photoPath = photoPath, description = description,
    createdByUid = createdByUid, createdAt = createdAt,
    lines = lines.map { it.toDomain() }
)

fun BlockLineDto.toDomain() = BlockLine(
    id = id, name = name, grade = grade, startType = startType,
    linePath = linePath, sortOrder = sortOrder
)
