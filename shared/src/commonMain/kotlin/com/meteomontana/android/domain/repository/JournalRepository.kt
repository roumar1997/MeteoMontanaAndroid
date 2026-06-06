package com.meteomontana.android.domain.repository

import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.JournalStats

interface JournalRepository {
    suspend fun createJournalSession(req: CreateJournalRequest): JournalSession
    suspend fun getMyJournal(): List<JournalSession>
    suspend fun getMyJournalStats(): JournalStats
    suspend fun deleteJournalSession(id: String)
}
