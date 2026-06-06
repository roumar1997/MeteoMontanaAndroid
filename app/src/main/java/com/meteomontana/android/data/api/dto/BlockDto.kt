package com.meteomontana.android.data.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BlockDto(
    val id: String,
    val schoolId: String,
    val type: String,                // BLOCK / PARKING / ZONE
    val name: String,
    val lat: Double,
    val lon: Double,
    val photoPath: String?,
    val description: String?,
    val createdByUid: String,
    val createdAt: String,
    val lines: List<BlockLineDto>
)

@JsonClass(generateAdapter = true)
data class BlockLineDto(
    val id: String,
    val name: String,
    val grade: String?,
    val startType: String?,          // SIT / STAND / JUMP / TRAV
    val linePath: String?,            // JSON con puntos
    val sortOrder: Int
)

@JsonClass(generateAdapter = true)
data class CreateBlockLineRequest(
    val name: String,
    val grade: String?,
    val startType: String?,
    val linePath: String?
)

@JsonClass(generateAdapter = true)
data class CreateBlockRequest(
    val type: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val photoPath: String?,
    val description: String?,
    val lines: List<CreateBlockLineRequest>
)

@JsonClass(generateAdapter = true)
data class SchoolScoreDto(
    val id: String,
    val todayScore: Int,
    val hourlyScores: List<Int>,
    val dryRock: Boolean,
    val rainMm: Double = 0.0,
    val rainProb: Int = 0
)
