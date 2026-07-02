package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class GripTypeDto(
    val id: Int,
    val fingerGroup: String,
    val style: String
)

@Serializable
data class GripMaxRecordDto(
    val id: String,
    val gripTypeId: Int,
    val hand: String,
    val maxKg: Double,
    val edgeMm: String? = null,
    val measuredAt: String
)

@Serializable
data class SaveGripMaxRequest(
    val gripTypeId: Int,
    val hand: String,
    val kg: Double,
    val edgeMm: String? = null
)

@Serializable
data class GripMeasureSessionDto(
    val id: String,
    val gripTypeId: Int,
    val hand: String,
    val peakKg: Double,
    val avgKg: Double,
    val durationS: Int,
    val edgeMm: String? = null,
    val createdAt: String
)

@Serializable
data class CreateGripMeasureSessionRequest(
    val gripTypeId: Int,
    val hand: String,
    val peakKg: Double,
    val avgKg: Double,
    val durationS: Int,
    val edgeMm: String? = null
)

@Serializable
data class GripWorkoutSetDto(
    val id: String,
    val sortOrder: Int,
    val reps: Int,
    val workS: Int,
    val restS: Int,
    val gripTypeId: Int,
    val targetMinPct: Double,
    val targetMaxPct: Double
)

@Serializable
data class GripWorkoutSetRequest(
    val sortOrder: Int,
    val reps: Int,
    val workS: Int,
    val restS: Int,
    val gripTypeId: Int,
    val targetMinPct: Double,
    val targetMaxPct: Double
)

@Serializable
data class GripWorkoutDto(
    val id: String,
    val name: String,
    val handMode: String,
    val countMode: String,
    val restBetweenSetsS: Int,
    val createdAt: String,
    val updatedAt: String,
    val sets: List<GripWorkoutSetDto>
)

@Serializable
data class CreateGripWorkoutRequest(
    val name: String,
    val handMode: String,
    val countMode: String,
    val restBetweenSetsS: Int,
    val sets: List<GripWorkoutSetRequest>
)
