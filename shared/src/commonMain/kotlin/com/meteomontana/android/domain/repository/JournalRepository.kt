package com.meteomontana.android.domain.repository

import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.JournalStats

interface JournalRepository {
    suspend fun createJournalSession(req: CreateJournalRequest): JournalSession
    suspend fun getMyJournal(): List<JournalSession>
    suspend fun getMyJournalStats(): JournalStats
    suspend fun getUserStats(uid: String): JournalStats
    suspend fun getUserJournal(uid: String): List<JournalSession>
    suspend fun deleteJournalSession(id: String)
    suspend fun updateJournalDate(id: String, date: String): JournalSession
    /** [attempts]: null = no tocar los intentos, 0 = quitarlos, 1 a 99 = ponerlos. */
    suspend fun updateJournalStyle(id: String, aVista: Boolean, alFlash: Boolean, attempts: Int? = null): JournalSession
}
