package com.meteomontana.android.domain.usecase.admin

import com.meteomontana.android.domain.model.AdminNoteRow
import com.meteomontana.android.domain.model.AdminSuggestionRow
import com.meteomontana.android.domain.model.AdminUserRow
import com.meteomontana.android.domain.model.ContentReport
import com.meteomontana.android.domain.model.UserModeration
import com.meteomontana.android.domain.repository.ModerationRepository

class GetContentReportsUseCase(private val repo: ModerationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<ContentReport> = repo.getContentReports()
}

class ResolveContentReportUseCase(private val repo: ModerationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String, action: String): ContentReport? =
        repo.resolveContentReport(id, action)
}

class GetAdminUsersUseCase(private val repo: ModerationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<AdminUserRow> = repo.getAdminUsers()
}

class GetAdminNotesUseCase(private val repo: ModerationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<AdminNoteRow> = repo.getAdminNotes()
}

class GetAdminSuggestionsUseCase(private val repo: ModerationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<AdminSuggestionRow> = repo.getAdminSuggestions()
}

class RespondToSuggestionUseCase(private val repo: ModerationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String, resolved: Boolean?, reply: String?): AdminSuggestionRow? =
        repo.respondToSuggestion(id, resolved, reply)
}

class GetUserModerationUseCase(private val repo: ModerationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String): UserModeration? = repo.getUserModeration(uid)
}

class WarnUserUseCase(private val repo: ModerationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String, reason: String?): UserModeration? = repo.warnUser(uid, reason)
}

class SuspendUserUseCase(private val repo: ModerationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String, days: Int, reason: String?): UserModeration? =
        repo.suspendUser(uid, days, reason)
}

class BanUserUseCase(private val repo: ModerationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String, reason: String?): UserModeration? = repo.banUser(uid, reason)
}

class UnbanUserUseCase(private val repo: ModerationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String, reason: String?): UserModeration? = repo.unbanUser(uid, reason)
}
