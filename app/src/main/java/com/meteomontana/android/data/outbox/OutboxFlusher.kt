package com.meteomontana.android.data.outbox

import co.touchlab.kermit.Logger
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.data.api.dto.CreateNoteRequest
import com.meteomontana.android.domain.port.NetworkMonitor
import com.meteomontana.android.domain.usecase.contributions.SubmitContributionUseCase
import com.meteomontana.android.domain.usecase.journal.CreateJournalEntryUseCase
import com.meteomontana.android.domain.usecase.journal.DeleteJournalEntryUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import com.meteomontana.android.domain.usecase.notes.CreateNoteUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drena el outbox cuando el dispositivo recupera la conexión.
 * Se inicia desde MeteoMontanaApp.onCreate().
 */
@Singleton
class OutboxFlusher @Inject constructor(
    private val outbox: OutboxRepository,
    private val networkMonitor: NetworkMonitor,
    private val submitContribution: SubmitContributionUseCase,
    private val createNote: CreateNoteUseCase,
    private val createJournalEntry: CreateJournalEntryUseCase,
    private val getMyJournal: GetMyJournalUseCase,
    private val deleteJournalEntry: DeleteJournalEntryUseCase,
    private val addFavorite: com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase,
    private val removeFavorite: com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase,
    private val photoUploader: com.meteomontana.android.domain.port.PhotoUploader
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val log = Logger.withTag("Outbox")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun start() {
        // 1) Cuando el monitor cambie a online → drenar.
        scope.launch {
            networkMonitor.isOnline
                .filter { it }
                .collect { flush() }
        }
        // 2) Al arrancar la app, si ya hay conexión, intenta drenar también.
        scope.launch {
            if (networkMonitor.isOnline.value) flush()
        }
    }

    private suspend fun flush() {
        val pending = outbox.all()
        if (pending.isEmpty()) return
        log.i("Drenando outbox: ${pending.size} pendientes")
        pending.forEach { row ->
            val result = runCatching {
                when (row.type) {
                    OutboxType.CONTRIBUTION -> {
                        val req = json.decodeFromString<ContributionRequest>(row.payloadJson)
                        submitContribution(row.schoolId, req)
                    }
                    OutboxType.CONTRIBUTION_BOULDER -> {
                        // Piedra guardada sin red: sube las fotos locales y monta
                        // el request igual que el envío online.
                        val q = json.decodeFromString<QueuedBoulder>(row.payloadJson)
                        val (faces, localByFace) = q.toFaces()
                        val urlByFace = HashMap<String, String?>()
                        faces.forEach { f ->
                            val path = localByFace[f.id]
                            urlByFace[f.id] = path?.let {
                                val bytes = java.io.File(it).readBytes()
                                photoUploader.uploadBoulderPhoto(bytes, "image/jpeg", q.schoolId)
                            }
                        }
                        val req = ContributionRequest(
                            type = "BOULDER",
                            name = q.name?.takeIf { it.isNotBlank() },
                            lat = q.lat, lon = q.lon,
                            notes = null, description = null,
                            proposedLat = null, proposedLon = null,
                            correctionReason = null, targetBlockId = null, targetLineId = null,
                            sectorBlockId = q.sectorBlockId,
                            photoUrl = faces.firstNotNullOfOrNull { urlByFace[it.id] },
                            bloquesJson = com.meteomontana.android.ui.screens.detail
                                .facesToBloquesJson(faces, urlByFace),
                            topoLinesJson = null,
                            discipline = q.discipline,
                            geometry = q.geometry,
                            path = q.pathJson,
                            direction = q.direction
                        )
                        submitContribution(q.schoolId, req)
                        // Limpia las fotos copiadas — ya viven en Storage.
                        localByFace.values.filterNotNull()
                            .forEach { runCatching { java.io.File(it).delete() } }
                    }
                    OutboxType.NOTE -> {
                        val req = json.decodeFromString<CreateNoteRequest>(row.payloadJson)
                        createNote(row.schoolId, req.text, req.photoUrl)
                    }
                    OutboxType.JOURNAL -> {
                        val req = json.decodeFromString<CreateJournalRequest>(row.payloadJson)
                        // Idempotente: si esa vía ya está en el diario (p.ej. se
                        // marcó también en otro sitio), NO la creamos otra vez.
                        val key = "${req.schoolId ?: ""}|${req.blockName.trim().lowercase()}"
                        val exists = getMyJournal().any { e ->
                            "${e.schoolId ?: ""}|${e.blockName.trim().lowercase()}" == key
                        }
                        if (!exists) createJournalEntry(req)
                    }
                    OutboxType.JOURNAL_DELETE -> {
                        // payload = clave "escuelaId|nombreVía". Resolvemos el id real
                        // contra el diario actual y borramos esa entrada.
                        val key = row.payloadJson
                        val entry = getMyJournal().firstOrNull { e ->
                            "${e.schoolId ?: ""}|${e.blockName.trim().lowercase()}" == key
                        }
                        if (entry != null) deleteJournalEntry(entry.id)
                        // Si no existe, ya estaba borrada → se considera hecho.
                    }
                    OutboxType.FAVORITE -> addFavorite(row.schoolId)
                    OutboxType.FAVORITE_DELETE -> removeFavorite(row.schoolId)
                    else -> log.w("Tipo desconocido en outbox: ${row.type}")
                }
            }
            if (result.isSuccess) {
                outbox.delete(row.id)
                log.i("Outbox #${row.id} enviada y borrada")
            } else {
                val err = result.exceptionOrNull()?.message ?: "unknown"
                outbox.markRetry(row.id, err)
                log.w("Outbox #${row.id} falló: $err (reintentos=${row.retries + 1})")
            }
        }
    }
}
