package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminPushResult
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.MeetupReport
import com.meteomontana.android.domain.model.Submission

interface AdminRepository {
    suspend fun getStats(): AdminStats
    suspend fun getPendingSubmissions(): List<Submission>
    suspend fun getPendingContributions(): List<Contribution>
    suspend fun getLogs(limit: Int = 100): List<AdminLog>
    suspend fun approveSubmission(id: String): Submission
    suspend fun rejectSubmission(id: String, reason: String?): Submission
    suspend fun approveContribution(id: String, editedBloquesJson: String? = null): Contribution
    suspend fun rejectContribution(id: String, reason: String?): Contribution
    suspend fun sendPush(targetUid: String?, title: String, body: String): AdminPushResult
    suspend fun getPendingReports(): List<MeetupReport>
    suspend fun resolveReport(id: String, action: String): MeetupReport
}
