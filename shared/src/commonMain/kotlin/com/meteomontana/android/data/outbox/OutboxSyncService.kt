package com.meteomontana.android.data.outbox

import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.domain.journal.journalViaKey
import com.meteomontana.android.domain.model.JournalSession
import kotlinx.serialization.json.Json

/**
 * Cola offline (outbox) — responsabilidad ÚNICA: encolar, consultar y drenar los
 * cambios hechos sin red (diario, contribuciones, favoritas). Antes vivía dentro
 * de `IosDependencyContainer` (god-object); extraído aquí para cumplir SRP y para
 * que la lógica sea reutilizable y testeable sin arrastrar toda la DI.
 *
 * Comparte la tabla `Outbox` de SQLDelight. Los colaboradores (use cases) se
 * inyectan como lambdas → el servicio depende de operaciones, no del contenedor.
 * Es el espejo conceptual del `OutboxFlusher` de Android.
 *
 * El contenedor iOS mantiene métodos delegadores con la MISMA firma (Swift los
 * llama con `try?`), así que esta extracción NO cambia el lado Swift.
 */
class OutboxSyncService(
    private val outbox: OutboxRepository?,
    private val submitContribution: suspend (String, ContributionRequest) -> Unit,
    private val getMyJournal: suspend () -> List<JournalSession>,
    private val createJournalEntry: suspend (CreateJournalRequest) -> Unit,
    private val deleteJournalEntry: suspend (String) -> Unit,
    private val addFavorite: suspend (String) -> Unit,
    private val removeFavorite: suspend (String) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ─── Diario: encolar / desencolar ───────────────────────────────────────

    /** Encola una vía marcada como hecha (sin red) para subirla más tarde. */
    suspend fun enqueueJournal(req: CreateJournalRequest) {
        outbox?.enqueue(
            OutboxType.JOURNAL,
            req.schoolId ?: "",
            json.encodeToString(CreateJournalRequest.serializer(), req)
        )
    }

    /** Quita de la cola la CREACIÓN pendiente con esa clave "escuela|vía"
     *  (al desmarcar). Devuelve true si había una (marcada offline, sin subir). */
    suspend fun dequeueJournal(key: String): Boolean {
        val repo = outbox ?: return false
        var removed = false
        repo.all().filter { it.type == OutboxType.JOURNAL }
            .forEach { row ->
                val match = runCatching {
                    json.decodeFromString(CreateJournalRequest.serializer(), row.payloadJson)
                }.getOrNull()?.let { req ->
                    // Mismo formato id-aware que pendingJournalKeysByStatus.
                    val lid = req.lineId
                    val rowKey = if (!lid.isNullOrBlank()) "${req.schoolId ?: ""}|#$lid"
                    else "${req.schoolId ?: ""}|${req.blockName.trim().lowercase()}"
                    rowKey == key
                } ?: false
                if (match) { repo.delete(row.id); removed = true }
            }
        return removed
    }

    /** Encola un BORRADO de vía (desmarcada sin red); se aplica al volver online. */
    suspend fun enqueueJournalDelete(key: String) {
        outbox?.enqueue(OutboxType.JOURNAL_DELETE, "", key)
    }

    /** Cancela un borrado pendiente de esa vía (al re-marcarla). */
    suspend fun dequeueJournalDelete(key: String) {
        val repo = outbox ?: return
        repo.all().filter { it.type == OutboxType.JOURNAL_DELETE }
            .forEach { row -> if (row.payloadJson == key) repo.delete(row.id) }
    }

    /** Encola un BORRADO de entrada de diario por su uid (borrada sin red desde el
     *  perfil). Se aplica al volver la conexión. Borra por id → no puede tocar otra. */
    suspend fun enqueueJournalDeleteById(id: String) {
        outbox?.enqueue(OutboxType.JOURNAL_DELETE_ID, "", id)
    }

    // ─── Diario: claves pendientes ──────────────────────────────────────────

    /** Claves "escuela|vía" de las vías encoladas para CREAR (✓ sin red aún). */
    suspend fun pendingJournalKeys(): Set<String> {
        val pend = outbox?.all() ?: return emptySet()
        return pend.filter { it.type == OutboxType.JOURNAL }
            .mapNotNull { row ->
                runCatching {
                    json.decodeFromString(CreateJournalRequest.serializer(), row.payloadJson)
                }.getOrNull()
            }
            .map { "${it.schoolId ?: ""}|${it.blockName.trim().lowercase()}" }
            .toSet()
    }

    /** Como [pendingJournalKeys], pero solo las de estado [status] (DONE|PROJECT).
     *  Necesario porque la cola JOURNAL guarda tanto "hechas" como "proyecto"
     *  bajo el mismo tipo — sin filtrar por status no se pueden distinguir. */
    suspend fun pendingJournalKeysByStatus(status: String): Set<String> {
        val pend = outbox?.all() ?: return emptySet()
        return pend.filter { it.type == OutboxType.JOURNAL }
            .mapNotNull { row ->
                runCatching {
                    json.decodeFromString(CreateJournalRequest.serializer(), row.payloadJson)
                }.getOrNull()
            }
            .filter { (it.status ?: "DONE") == status }
            // Clave por lineId si lo tiene (aguanta vías homónimas — fix "La
            // ola"); por nombre solo como legado. Mismo formato que Android
            // (journalViaKey) y que las claves que computa SchoolDetailView.
            .map { req ->
                val lid = req.lineId
                if (!lid.isNullOrBlank()) "${req.schoolId ?: ""}|#$lid"
                else "${req.schoolId ?: ""}|${req.blockName.trim().lowercase()}"
            }
            .toSet()
    }

    /** Claves "escuela|vía" con BORRADO pendiente (desmarcadas sin red). */
    suspend fun pendingJournalDeleteKeys(): Set<String> {
        val pend = outbox?.all() ?: return emptySet()
        return pend.filter { it.type == OutboxType.JOURNAL_DELETE }
            .map { it.payloadJson }.toSet()
    }

    /** uids de entradas de diario con BORRADO pendiente (borradas sin red). */
    suspend fun pendingJournalDeleteIds(): Set<String> {
        val pend = outbox?.all() ?: return emptySet()
        return pend.filter { it.type == OutboxType.JOURNAL_DELETE_ID }
            .map { it.payloadJson }.toSet()
    }

    // ─── Contribuciones (parking/sector/piedra) ─────────────────────────────
    // Espejo del OutboxFlusher de Android: las simples (sin fotos) se envían
    // desde aquí (Kotlin); las de PIEDRA llevan fotos locales y las drena un
    // flusher Swift (necesita StorageUploader nativo).

    /** Encola una contribución simple (parking/sector). [requestJson] = el
     *  ContributionRequest serializado (mismas claves que el DTO). */
    suspend fun enqueueContribution(schoolId: String, requestJson: String) {
        outbox?.enqueue(OutboxType.CONTRIBUTION, schoolId, requestJson)
    }

    /** Encola una propuesta de PIEDRA guardada sin red (payload con rutas
     *  locales de fotos; lo drena ContributionOutboxFlusher.swift). */
    suspend fun enqueueBoulderContribution(schoolId: String, payloadJson: String) {
        outbox?.enqueue(OutboxType.CONTRIBUTION_BOULDER, schoolId, payloadJson)
    }

    /** Envía las contribuciones SIMPLES pendientes. Devuelve cuántas subió. */
    suspend fun flushSimpleContributions(): Int {
        val repo = outbox ?: return 0
        var sent = 0
        repo.all().filter { it.type == OutboxType.CONTRIBUTION }
            .forEach { row ->
                val ok = runCatching {
                    val req = json.decodeFromString(ContributionRequest.serializer(), row.payloadJson)
                    submitContribution(row.schoolId, req)
                }.isSuccess
                if (ok) { repo.delete(row.id); sent++ }
                else repo.markRetry(row.id, null)
            }
        return sent
    }

    /** Filas de PIEDRA pendientes, para el flusher Swift. */
    suspend fun pendingBoulderContributions(): List<PendingContributionRow> {
        val repo = outbox ?: return emptyList()
        return repo.all()
            .filter { it.type == OutboxType.CONTRIBUTION_BOULDER }
            .map { PendingContributionRow(it.id, it.schoolId, it.payloadJson) }
    }

    /** Borra una fila del outbox (el flusher Swift la llama tras subir). */
    suspend fun deleteOutboxRow(id: Long) { outbox?.delete(id) }

    // ─── Favoritas ──────────────────────────────────────────────────────────

    /** Encola marcar/desmarcar una favorita sin red (anula la opuesta pendiente). */
    suspend fun enqueueFavorite(schoolId: String, favorite: Boolean) {
        outbox?.enqueueFavorite(schoolId, favorite)
    }

    /** ids con FAVORITE pendiente (reflejar la estrella offline). */
    suspend fun pendingFavoriteIds(): Set<String> =
        outbox?.pendingFavoriteIds() ?: emptySet()

    /** ids con FAVORITE_DELETE pendiente (quitar la estrella offline). */
    suspend fun pendingFavoriteDeleteIds(): Set<String> =
        outbox?.pendingFavoriteDeleteIds() ?: emptySet()

    // ─── Drenado del diario/favoritas ───────────────────────────────────────

    /**
     * Sube/aplica todo lo encolado del diario y favoritas (marcado sin red).
     * Llamar al abrir o activar la app. Borra cada entrada al aplicarla con
     * éxito; las que fallen se quedan en la cola para el próximo intento.
     */
    suspend fun flushJournalOutbox() {
        val repo = outbox ?: return
        repo.all().forEach { row ->
            when (row.type) {
                OutboxType.JOURNAL -> {
                    val ok = runCatching {
                        val req = json.decodeFromString(CreateJournalRequest.serializer(), row.payloadJson)
                        // Idempotente: no crear si esa vía ya está en el diario.
                        // Clave POR lineId (journalViaKey) para no confundir homónimas.
                        val key = journalViaKey(req.schoolId, req.lineId, req.blockName)
                        val exists = getMyJournal().any { e ->
                            journalViaKey(e.schoolId, e.lineId, e.blockName) == key
                        }
                        if (!exists) createJournalEntry(req)
                        true
                    }.isSuccess
                    if (ok) repo.delete(row.id)
                }
                OutboxType.JOURNAL_DELETE -> {
                    // payload = clave journalViaKey ("escuela|#lineId" o por nombre
                    // legado). Casa por la MISMA clave id-aware que usa el encolado
                    // (antes se comparaba por nombre → el borrado por id nunca casaba
                    // → se perdía al reconectar). Resolvemos el id real y borramos.
                    val ok = runCatching {
                        val entry = getMyJournal().firstOrNull { e ->
                            journalViaKey(e.schoolId, e.lineId, e.blockName) == row.payloadJson
                        }
                        if (entry != null) deleteJournalEntry(entry.id)
                        true   // si no existe ya, también se considera hecho
                    }.isSuccess
                    if (ok) repo.delete(row.id)
                }
                OutboxType.JOURNAL_DELETE_ID -> {
                    // payload = uid exacto de la entrada; solo borra esa.
                    val ok = runCatching {
                        val exists = getMyJournal().any { it.id == row.payloadJson }
                        if (exists) deleteJournalEntry(row.payloadJson)
                        true   // si ya no está, también hecho
                    }.isSuccess
                    if (ok) repo.delete(row.id)
                }
                OutboxType.FAVORITE -> {
                    val ok = runCatching { addFavorite(row.schoolId); true }.isSuccess
                    if (ok) repo.delete(row.id)
                }
                OutboxType.FAVORITE_DELETE -> {
                    val ok = runCatching { removeFavorite(row.schoolId); true }.isSuccess
                    if (ok) repo.delete(row.id)
                }
                else -> {}
            }
        }
    }
}

/** Fila del outbox pendiente de subir — expuesta a Swift para el flusher de
 *  piedras (SKIE no expone bien el tipo generado por SQLDelight). */
class PendingContributionRow(
    val id: Long,
    val schoolId: String,
    val payloadJson: String
)
