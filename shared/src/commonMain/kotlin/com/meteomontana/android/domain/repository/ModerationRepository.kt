package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.AdminNoteRow
import com.meteomontana.android.domain.model.AdminUserRow
import com.meteomontana.android.domain.model.ContentReport
import com.meteomontana.android.domain.model.UserModeration

/** Consola de moderación del admin (cola de denuncias + acciones sobre usuarios). */
interface ModerationRepository {
    suspend fun getContentReports(): List<ContentReport>
    suspend fun resolveContentReport(id: String, action: String): ContentReport?
    suspend fun getAdminUsers(): List<AdminUserRow>
    suspend fun getAdminNotes(): List<AdminNoteRow>
    suspend fun getUserModeration(uid: String): UserModeration?
    suspend fun warnUser(uid: String, reason: String?): UserModeration?
    suspend fun suspendUser(uid: String, days: Int, reason: String?): UserModeration?
    suspend fun banUser(uid: String, reason: String?): UserModeration?
    suspend fun unbanUser(uid: String, reason: String?): UserModeration?
}
