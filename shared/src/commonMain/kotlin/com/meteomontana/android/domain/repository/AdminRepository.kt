package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminPushResult
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.MeetupReport
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.model.Submission

interface AdminRepository {
    suspend fun getStats(): AdminStats
    /** status null = PENDING; APPROVED/REJECTED = historial (Álvaro, 2026-09-30). */
    suspend fun getPendingSubmissions(status: String? = null): List<Submission>
    suspend fun getPendingContributions(status: String? = null): List<Contribution>
    suspend fun getLogs(limit: Int = 100): List<AdminLog>
    suspend fun approveSubmission(id: String): Submission
    suspend fun rejectSubmission(id: String, reason: String?): Submission
    suspend fun approveContribution(id: String, editedBloquesJson: String? = null): Contribution
    suspend fun rejectContribution(id: String, reason: String?): Contribution
    suspend fun sendPush(targetUid: String?, title: String, body: String): AdminPushResult
    suspend fun getPendingReports(): List<MeetupReport>
    suspend fun resolveReport(id: String, action: String): MeetupReport
    /** Admin: mueve la escuela a una posición nueva. */
    suspend fun moveSchool(schoolId: String, lat: Double, lon: Double)
    /** Admin: edita nombre/ubicación/región/estilo/roca. Null = no tocar ese campo. */
    suspend fun editSchool(
        schoolId: String, name: String? = null, location: String? = null,
        region: String? = null, style: String? = null, rockType: String? = null
    ): School
}
