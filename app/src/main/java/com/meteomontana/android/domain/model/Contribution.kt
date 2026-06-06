package com.meteomontana.android.domain.model

data class Contribution(
    val id: String,
    val type: String,
    val status: String,
    val schoolId: String,
    val schoolName: String,
    val name: String?,
    val lat: Double,
    val lon: Double,
    val notes: String?,
    val description: String?,
    val submittedByName: String?,
    val reviewReason: String?,
    val createdAt: String?,
    val reviewedAt: String?,
    val photoUrl: String?,
    val bloquesJson: String?,
    val topoLinesJson: String?,
    val targetBlockId: String?
)
