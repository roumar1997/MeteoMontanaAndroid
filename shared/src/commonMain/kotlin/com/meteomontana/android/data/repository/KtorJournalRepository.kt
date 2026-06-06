package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorJournalApi
import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.repository.JournalRepository

class KtorJournalRepository(private val api: KtorJournalApi) : JournalRepository {
    override suspend fun createJournalSession(req: CreateJournalRequest): JournalSession =
        api.createJournalSession(req).toDomain()
    override suspend fun getMyJournal(): List<JournalSession> =
        api.getMyJournal().map { it.toDomain() }
    override suspend fun getMyJournalStats(): JournalStats =
        api.getMyJournalStats().toDomain()
    override suspend fun deleteJournalSession(id: String) =
        api.deleteJournalSession(id)
}
