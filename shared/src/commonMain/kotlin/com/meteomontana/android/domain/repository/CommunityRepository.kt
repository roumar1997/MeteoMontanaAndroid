package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.model.OrientationSummary
import com.meteomontana.android.domain.model.SunHours

/**
 * Votación comunitaria (puerto de dominio). @Throws en cada método:
 * Kotlin/Native exige el MISMO filtro en interfaz e implementación
 * (ARCHITECTURE.md §3.b) o el build de iOS no compila.
 */
interface CommunityRepository {
    @Throws(Exception::class)
    suspend fun getOrientation(blockId: String): List<OrientationSummary>

    @Throws(Exception::class)
    suspend fun voteOrientation(blockId: String, photoIndex: Int?, aspect: String): List<OrientationSummary>

    @Throws(Exception::class)
    suspend fun getSunHours(blockId: String, photoIndex: Int?): SunHours

    @Throws(Exception::class)
    suspend fun getGradeVotes(lineId: String): GradeSummary

    @Throws(Exception::class)
    suspend fun voteGrade(lineId: String, grade: String): GradeSummary
}
