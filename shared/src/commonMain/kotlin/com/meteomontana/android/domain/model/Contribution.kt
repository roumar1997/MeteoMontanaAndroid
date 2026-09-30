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
    val targetBlockId: String?,
    val targetLineId: String? = null,
    val sectorBlockId: String? = null,
    val proposedLat: Double? = null,
    val proposedLon: Double? = null,
    val correctionReason: String? = null,
    // Muro: geometría POINT/LINE, polilínea JSON, sentido de numeración LTR/RTL.
    val geometry: String? = null,
    val path: String? = null,
    val direction: String? = null,
    // Álvaro, 2026-09-30: "saber quién la hizo, quién la aprobó" — historial.
    val submittedByUid: String? = null,
    val submittedByPhotoPath: String? = null,
    val reviewedByUid: String? = null,
    val reviewedByName: String? = null,
    /** Bloque resultante si esta mejora CREÓ una piedra/sector/parking nuevo
     *  (Álvaro, 2026-09-30: historial → "MODIFICAR"). */
    val createdBlockId: String? = null
)
