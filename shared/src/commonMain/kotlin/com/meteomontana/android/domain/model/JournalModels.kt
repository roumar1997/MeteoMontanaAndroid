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
    val createdAt: String,
    val discipline: String = "BOULDER"   // BOULDER (bloque) / ROUTE (vía)
)

data class JournalStats(
    val blockCount: Int,                 // total (bloques + vías), por compat
    val boulderCount: Int = 0,           // nº de bloques
    val routeCount: Int = 0,             // nº de vías
    val schoolCount: Int,
    val maxGrade: String?,               // grado máx global
    val maxBoulderGrade: String? = null, // grado máx de bloque
    val maxRouteGrade: String? = null,   // grado máx de vía
    val bySchool: List<SchoolStats>
)

data class SchoolStats(val schoolName: String, val blockCount: Int, val maxGrade: String?)
