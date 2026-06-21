package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ContributionRequest(
    val type: String,
    val name: String? = null,
    val lat: Double,
    val lon: Double,
    val notes: String? = null,
    val description: String? = null,
    val proposedLat: Double? = null,
    val proposedLon: Double? = null,
    val correctionReason: String? = null,
    val targetBlockId: String? = null,
    val targetLineId: String? = null,
    val sectorBlockId: String? = null,
    val photoUrl: String? = null,
    val bloquesJson: String? = null,
    val topoLinesJson: String? = null,
    val discipline: String? = null,  // BOULDER (bloque) / ROUTE (vía) al proponer piedra
    val geometry: String? = null,    // POINT / LINE (muro)
    val path: String? = null,        // polilínea JSON si LINE
    val direction: String? = null    // "LTR"/"RTL"
)

@Serializable
data class ContributionDto(
    val id: String,
    val type: String,
    val status: String,
    val schoolId: String,
    val schoolName: String,
    val name: String? = null,
    val lat: Double,
    val lon: Double,
    val notes: String? = null,
    val description: String? = null,
    val submittedByName: String? = null,
    val reviewReason: String? = null,
    val createdAt: String? = null,
    val reviewedAt: String? = null,
    val photoUrl: String? = null,
    val bloquesJson: String? = null,
    val topoLinesJson: String? = null,
    val targetBlockId: String? = null,
    val targetLineId: String? = null,
    val sectorBlockId: String? = null,
    val proposedLat: Double? = null,
    val proposedLon: Double? = null,
    val correctionReason: String? = null,
    val discipline: String? = null   // BOULDER (bloque) / ROUTE (vía) si la propuesta es de piedra
)
