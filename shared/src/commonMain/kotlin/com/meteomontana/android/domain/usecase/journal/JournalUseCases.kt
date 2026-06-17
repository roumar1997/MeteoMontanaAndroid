package com.meteomontana.android.domain.usecase.journal

import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.repository.JournalRepository

class GetMyJournalUseCase(private val repo: JournalRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<JournalSession> = repo.getMyJournal()
}

class GetMyJournalStatsUseCase(private val repo: JournalRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): JournalStats = repo.getMyJournalStats()
}

class GetUserStatsUseCase(private val repo: JournalRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String): JournalStats = repo.getUserStats(uid)
}

class GetUserJournalUseCase(private val repo: JournalRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String): List<JournalSession> = repo.getUserJournal(uid)
}

class CreateJournalEntryUseCase(private val repo: JournalRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(req: CreateJournalRequest): JournalSession =
        repo.createJournalSession(req)
}

class DeleteJournalEntryUseCase(private val repo: JournalRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String) = repo.deleteJournalSession(id)
}
