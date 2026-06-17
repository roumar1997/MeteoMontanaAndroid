package com.meteomontana.android.data.outbox

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.meteomontana.db.MeteoMontanaDb
import com.meteomontana.db.Outbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

/** Tipos de petición que pueden encolarse en el outbox. */
object OutboxType {
    const val CONTRIBUTION = "CONTRIBUTION"
    const val NOTE         = "NOTE"
    const val SUBMISSION   = "SUBMISSION"
    const val JOURNAL        = "JOURNAL"        // vía marcada como hecha (POST /api/journal)
    const val JOURNAL_DELETE = "JOURNAL_DELETE" // vía DESMARCADA sin red (payload = clave "escuelaId|vía")
}

class OutboxRepository(private val db: MeteoMontanaDb) {

    private val q get() = db.schemaQueries

    fun observePending(): Flow<List<Outbox>> =
        q.outboxAllFlow().asFlow().mapToList(Dispatchers.Default)

    suspend fun all(): List<Outbox> = q.outboxAll().executeAsList()

    suspend fun enqueue(type: String, schoolId: String, payloadJson: String) {
        q.outboxInsert(
            type = type,
            schoolId = schoolId,
            payloadJson = payloadJson,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
    }

    suspend fun delete(id: Long) { q.outboxDelete(id) }

    suspend fun markRetry(id: Long, error: String?) {
        q.outboxIncrementRetry(error, id)
    }
}
