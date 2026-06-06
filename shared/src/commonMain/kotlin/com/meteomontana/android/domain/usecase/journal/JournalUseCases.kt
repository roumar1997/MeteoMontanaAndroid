package com.meteomontana.android.domain.usecase.journal

import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.repository.JournalRepository

class GetMyJournalUseCase(private val repo: JournalRepository) {
    suspend operator fun invoke(): List<JournalSession> = repo.getMyJournal()
}

class GetMyJournalStatsUseCase(private val repo: JournalRepository) {
    suspend operator fun invoke(): JournalStats = repo.getMyJournalStats()
}

class CreateJournalEntryUseCase(private val repo: JournalRepository) {
    suspend operator fun invoke(req: CreateJournalRequest): JournalSession =
        repo.createJournalSession(req)
}

class DeleteJournalEntryUseCase(private val repo: JournalRepository) {
    suspend operator fun invoke(id: String) = repo.deleteJournalSession(id)
}
