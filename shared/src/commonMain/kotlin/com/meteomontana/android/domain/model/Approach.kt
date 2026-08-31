package com.meteomontana.android.domain.model

/**
 * Camino grabado (o dibujado) del parking a un sector/piedra, con sus
 * chinchetas. Fase 1 de APPROACH_DESIGN.md — solo lectura.
 */
data class Approach(
    val id: String,
    val schoolId: String,
    val fromBlockId: String?,
    val toBlockId: String?,
    val name: String?,
    /** Polilínea "[[lat,lon],...]" — mismo formato que Block.path. */
    val pathJson: String,
    val distanceM: Int?,
    val ascentM: Int?,
    val durationMin: Int?,
    val source: String,   // RECORDED / DRAWN / GPX
    val status: String,   // UNVERIFIED / VERIFIED
    val authorUid: String,
    val pins: List<ApproachPin> = emptyList()
) {
    val isVerified: Boolean get() = status == "VERIFIED"
}

/** Chincheta (foto y/o texto) sobre una aproximación. */
data class ApproachPin(
    val id: String,
    val lat: Double,
    val lon: Double,
    val positionIdx: Int,
    val kind: String,      // FORK / LANDMARK / HAZARD / KEY
    val message: String?,
    val photoPath: String?,
    val authorUid: String,
    val status: String     // UNVERIFIED / VERIFIED
)
