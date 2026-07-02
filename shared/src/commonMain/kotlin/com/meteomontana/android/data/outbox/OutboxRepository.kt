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
    // Propuesta de PIEDRA guardada sin red: el payload lleva las vías por cara
    // y rutas LOCALES de las fotos (copiadas al almacenamiento de la app); el
    // flusher de cada plataforma sube las fotos y monta el request al reconectar.
    const val CONTRIBUTION_BOULDER = "CONTRIBUTION_BOULDER"
    const val NOTE         = "NOTE"
    const val SUBMISSION   = "SUBMISSION"
    const val JOURNAL        = "JOURNAL"        // vía marcada como hecha (POST /api/journal)
    const val JOURNAL_DELETE = "JOURNAL_DELETE" // vía DESMARCADA sin red (payload = clave "escuelaId|vía")
    const val JOURNAL_DELETE_ID = "JOURNAL_DELETE_ID" // entrada de diario borrada sin red (payload = uid de la entrada)
    const val FAVORITE        = "FAVORITE"        // escuela marcada favorita sin red (schoolId = id)
    const val FAVORITE_DELETE = "FAVORITE_DELETE" // escuela desmarcada favorita sin red (schoolId = id)
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

    /**
     * Encola marcar/desmarcar una favorita sin conexión, anulando la opuesta
     * pendiente. [favorite] = true → FAVORITE; false → FAVORITE_DELETE. Si para
     * esa escuela ya había encolada la acción contraria (marcaste y desmarcaste
     * offline), se cancelan y no queda nada por sincronizar. Idempotente: no
     * duplica la misma acción. (schoolId va tanto en la columna como en payload.)
     */
    suspend fun enqueueFavorite(schoolId: String, favorite: Boolean) {
        val want = if (favorite) OutboxType.FAVORITE else OutboxType.FAVORITE_DELETE
        val opposite = if (favorite) OutboxType.FAVORITE_DELETE else OutboxType.FAVORITE
        val pending = all().filter {
            it.schoolId == schoolId &&
                (it.type == OutboxType.FAVORITE || it.type == OutboxType.FAVORITE_DELETE)
        }
        val cancels = pending.filter { it.type == opposite }
        if (cancels.isNotEmpty()) { cancels.forEach { delete(it.id) }; return }
        if (pending.any { it.type == want }) return
        enqueue(want, schoolId, schoolId)
    }

    /** ids de escuelas con un FAVORITE pendiente (para reflejar el ✓ offline). */
    suspend fun pendingFavoriteIds(): Set<String> =
        all().filter { it.type == OutboxType.FAVORITE }.map { it.schoolId }.toSet()

    /** ids de escuelas con un FAVORITE_DELETE pendiente (para quitar el ✓ offline). */
    suspend fun pendingFavoriteDeleteIds(): Set<String> =
        all().filter { it.type == OutboxType.FAVORITE_DELETE }.map { it.schoolId }.toSet()

    suspend fun markRetry(id: Long, error: String?) {
        q.outboxIncrementRetry(error, id)
    }
}
