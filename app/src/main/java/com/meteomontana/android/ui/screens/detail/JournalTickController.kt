package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.data.local.JournalDoneStore
import com.meteomontana.android.data.local.JournalProjectStore
import com.meteomontana.android.data.outbox.OutboxRepository
import com.meteomontana.android.data.outbox.OutboxType
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.port.NetworkMonitor
import com.meteomontana.android.domain.usecase.journal.CreateJournalEntryUseCase
import com.meteomontana.android.domain.usecase.journal.DeleteJournalEntryUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Diario de vías HECHAS/PROYECTO de la ficha de piedra: los ✓, sus claves, el
 * toggle y toda la coreografía offline (registro local + cola outbox + verdad
 * del servidor). Extraído de SchoolDetailViewModel — es la lógica más delicada
 * de la app (carreras del ✓, homónimas "La ola", borrados encolados) y ahora
 * vive sola y es testeable con fakes, sin arrastrar las otras 20 dependencias
 * del ViewModel.
 *
 * Los flows expuestos son FRÍOS: el ViewModel les hace stateIn con su scope.
 * Los toggles reciben los sets ACTUALES como parámetro (el VM les pasa el
 * .value de sus StateFlow) — así esta clase no necesita scope propio.
 */
class JournalTickController @Inject constructor(
    private val getMyJournal: GetMyJournalUseCase,
    private val createJournalEntry: CreateJournalEntryUseCase,
    private val deleteJournalEntry: DeleteJournalEntryUseCase,
    private val journalDoneStore: JournalDoneStore,
    private val journalProjectStore: JournalProjectStore,
    private val outboxRepo: OutboxRepository,
    private val networkMonitor: NetworkMonitor
) {
    private val journalJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Diario del usuario (para marcar las vías ya hechas con ✓ persistente). */
    private val _myJournal = MutableStateFlow<List<JournalSession>>(emptyList())
    val myJournal: StateFlow<List<JournalSession>> = _myJournal.asStateFlow()

    /**
     * Claves "escuela|vía" de las vías HECHAS, combinando el diario (ya subidas)
     * y la cola offline pendiente (marcadas sin red, aún sin subir). Así el ✓
     * queda persistente incluso sin conexión.
     */
    fun doneViaKeys(): Flow<Set<String>> =
        combine(_myJournal, outboxRepo.observePending(), journalDoneStore.keys) { journal, pending, local ->
            val keys = mutableSetOf<String>()
            keys.addAll(local)   // registro local: funciona también SIN conexión
            // Solo las entradas DONE cuentan como "hecha" — un PROYECTO no lo es.
            journal.filter { it.status != "PROJECT" }.forEach { keys.add(entryKey(it.schoolId, it.lineId, it.blockName)) }
            pending.filter { it.type == OutboxType.JOURNAL }.forEach { row ->
                runCatching {
                    journalJson.decodeFromString(CreateJournalRequest.serializer(), row.payloadJson)
                }.getOrNull()?.takeIf { it.status != "PROJECT" }?.let { keys.add(entryKey(it.schoolId, it.lineId, it.blockName)) }
            }
            // Vías con borrado pendiente (desmarcadas sin red) → fuera, aunque el
            // diario del servidor todavía las tenga.
            pending.filter { it.type == OutboxType.JOURNAL_DELETE }
                .forEach { keys.remove(it.payloadJson) }
            keys
        }

    /**
     * Claves "escuela|vía" de las vías marcadas como PROYECTO (las estás
     * probando, aún no te han salido). Espejo exacto de [doneViaKeys].
     */
    fun projectViaKeys(): Flow<Set<String>> =
        combine(_myJournal, outboxRepo.observePending(), journalProjectStore.keys) { journal, pending, local ->
            val keys = mutableSetOf<String>()
            keys.addAll(local)
            journal.filter { it.status == "PROJECT" }.forEach { keys.add(entryKey(it.schoolId, it.lineId, it.blockName)) }
            pending.filter { it.type == OutboxType.JOURNAL }.forEach { row ->
                runCatching {
                    journalJson.decodeFromString(CreateJournalRequest.serializer(), row.payloadJson)
                }.getOrNull()?.takeIf { it.status == "PROJECT" }?.let { keys.add(entryKey(it.schoolId, it.lineId, it.blockName)) }
            }
            pending.filter { it.type == OutboxType.JOURNAL_DELETE }
                .forEach { keys.remove(it.payloadJson) }
            keys
        }

    private fun viaKey(schoolId: String?, name: String) =
        "${schoolId ?: ""}|${name.trim().lowercase()}"

    /**
     * Clave de una entrada del diario, POR lineId cuando lo tiene
     * ("escuela|#lineId") y por nombre solo como LEGADO (entradas antiguas sin
     * lineId). Fix de raíz de "La ola": dos vías homónimas compartían la clave
     * por nombre → marcar una encendía/borraba la otra. Con el id son claves
     * distintas. Debe casar con [journalViaKey] (la versión de la UI).
     */
    private fun entryKey(schoolId: String?, lineId: String?, blockName: String) =
        journalViaKey(schoolId, lineId, blockName)

    /** Recarga el diario. Con red, sincroniza el registro local de "hechas" con
     *  la verdad del servidor (+ las pendientes en cola). Sin red lo deja como
     *  está (el registro local mantiene el ✓). */
    suspend fun refresh() {
        val net = runCatching { getMyJournal() }.getOrNull() ?: return
        _myJournal.value = net
        val all = outboxRepo.all()
        val pendingRequests = all
            .filter { it.type == OutboxType.JOURNAL }
            .mapNotNull { row ->
                runCatching {
                    journalJson.decodeFromString(CreateJournalRequest.serializer(), row.payloadJson)
                }.getOrNull()
            }
        val pendingCreateDone = pendingRequests.filter { it.status != "PROJECT" }
            .map { entryKey(it.schoolId, it.lineId, it.blockName) }.toSet()
        val pendingCreateProject = pendingRequests.filter { it.status == "PROJECT" }
            .map { entryKey(it.schoolId, it.lineId, it.blockName) }.toSet()
        // Vías con borrado pendiente: aún no se deben mostrar como hechas/proyecto.
        val pendingDelete = all
            .filter { it.type == OutboxType.JOURNAL_DELETE }
            .map { it.payloadJson }.toSet()
        val serverDoneKeys = net.filter { it.status != "PROJECT" }
            .map { entryKey(it.schoolId, it.lineId, it.blockName) }.toSet() - pendingDelete
        val serverProjectKeys = net.filter { it.status == "PROJECT" }
            .map { entryKey(it.schoolId, it.lineId, it.blockName) }.toSet() - pendingDelete
        journalDoneStore.sync(serverDoneKeys, pendingCreateDone)
        journalProjectStore.sync(serverProjectKeys, pendingCreateProject)
    }

    /**
     * Marca/DESMARCA una vía como hecha (toggle). Si no estaba hecha la añade al
     * diario (POST, o cola si no hay red); si ya estaba, la quita (borra la
     * entrada subida y/o la pendiente en la cola). Evita duplicados: si ya está
     * hecha, desmarca en vez de volver a añadir. Devuelve el nuevo estado
     * (true = ahora hecha). Espejo del tic de iOS.
     *
     * [doneKeys]/[projectKeys] son los sets ACTUALES (el VM pasa el .value de
     * sus StateFlow). [markDone] = estado DESEADO explícito (lo que el usuario
     * ve en la ficha); null = decidir por los sets (comportamiento antiguo).
     * Sin esto había una CARRERA: la ficha abre antes de que cargue el diario →
     * el ✓ visual y doneKeys divergen → "marcar" borraba la entrada vieja.
     */
    suspend fun toggleLine(
        block: Block,
        line: BlockLine,
        index: Int,
        schoolName: String,
        sectorName: String?,
        markDone: Boolean?,
        doneKeys: Set<String>,
        projectKeys: Set<String>
    ): Result<Boolean> = runCatching {
        val viaName = line.name.ifBlank { "Vía ${index + 1}" }
        // Clave por lineId (aguanta homónimas). La clave por NOMBRE se sigue
        // mirando como legado: entradas antiguas sin lineId la usan.
        val key = entryKey(block.schoolId, line.id.takeIf { it.isNotBlank() }, viaName)
        val legacyKey = viaKey(block.schoolId, viaName)
        val alreadyDone = doneKeys.contains(key) || doneKeys.contains(legacyKey)
        if (markDone == true && alreadyDone) {
            // Idempotente: ya estaba hecha (el diario llegó tarde a la ficha).
            journalDoneStore.add(key)
            return@runCatching true
        }
        val unmark = markDone?.let { !it } ?: alreadyDone
        if (unmark) {
            // DESMARCAR (quita también la clave legado por nombre, por si el ✓
            // venía de una entrada antigua sin lineId).
            journalDoneStore.remove(key)
            journalDoneStore.remove(legacyKey)
            // 1) Si solo estaba ENCOLADA (marcada offline, sin subir) → cancela la
            //    creación y listo (no hay nada en el servidor que borrar).
            val hadPendingCreate = removeOutboxByKey(OutboxType.JOURNAL, key)
            if (!hadPendingCreate) {
                // 2) Está (o estará) en el servidor: borra ya si hay red; si no,
                //    ENCOLA el borrado para que se aplique al volver la conexión.
                //    La entrada se localiza POR lineId; solo si no hay ninguna con
                //    ese id, por nombre entre las SIN lineId (entradas antiguas) —
                //    nunca borra la entrada de una homónima con id distinto.
                val online = networkMonitor.isOnline.value
                val entry = if (online) _myJournal.value.firstOrNull {
                    (line.id.isNotBlank() && it.lineId == line.id) ||
                        (it.lineId == null && viaKey(it.schoolId, it.blockName) == legacyKey)
                } else null
                val deleted = entry != null && runCatching { deleteJournalEntry(entry.id) }.isSuccess
                if (!deleted) {
                    // El payload del borrado pendiente usa la clave de LA ENTRADA
                    // encontrada (id o legado) para que el filtrado offline case.
                    val delKey = entry?.let { entryKey(it.schoolId, it.lineId, it.blockName) } ?: key
                    removeOutboxByKey(OutboxType.JOURNAL_DELETE, delKey)
                    outboxRepo.enqueue(OutboxType.JOURNAL_DELETE, block.schoolId, delKey)
                }
            }
            refresh()
            false
        } else {
            // Si era un PROYECTO, primero lo quitamos (local + servidor/cola): al
            // conseguirla, desaparece de Proyectos y pasa a Vías/Bloques, sin
            // quedar duplicada.
            if (projectKeys.contains(key) || projectKeys.contains(legacyKey)) {
                removeProjectEntry(block.schoolId, key, legacyKey)
            }
            // MARCAR HECHA: registro local primero (dedup offline), luego crear/encolar.
            journalDoneStore.add(key)
            // Cancela cualquier BORRADO pendiente de esta vía (la re-marcamos).
            removeOutboxByKey(OutboxType.JOURNAL_DELETE, key)
            val req = CreateJournalRequest(
                schoolId = block.schoolId,
                schoolName = schoolName.ifBlank { null },
                sector = sectorName,
                blockName = viaName,
                grade = line.grade,
                // No guardamos "Piedra: N": el número se recicla/borra y quedaría
                // obsoleto. La vía se localiza por nombre al abrir la escuela.
                notes = null,
                date = java.time.LocalDate.now().toString(),
                // La modalidad la hereda la vía de su piedra: el contador del
                // perfil sabrá si es BLOQUE o VÍA.
                discipline = block.discipline,
                // Enganche estable por id (Fase 8): el perfil resuelve grado/foto/
                // posición EN VIVO por lineId (aguanta renombres, reordenes y muros).
                // Va también offline (la cola serializa la request entera).
                lineId = line.id.takeIf { it.isNotBlank() },
                status = "DONE"
            )
            val sent = networkMonitor.isOnline.value && runCatching { createJournalEntry(req) }.isSuccess
            if (sent) {
                refresh()
            } else {
                outboxRepo.enqueue(
                    OutboxType.JOURNAL, block.schoolId,
                    journalJson.encodeToString(CreateJournalRequest.serializer(), req)
                )
            }
            true
        }
    }

    /**
     * Marca/DESMARCA una vía como PROYECTO (la estás probando, aún no te ha
     * salido). Espejo exacto de [toggleLine], pero con status="PROJECT". Si la
     * vía ya está marcada como HECHA, no hace nada (no tiene sentido marcarla
     * como proyecto): la UI debe ocultar/deshabilitar este botón en ese caso.
     */
    suspend fun toggleProject(
        block: Block,
        line: BlockLine,
        index: Int,
        schoolName: String,
        sectorName: String?,
        markProject: Boolean?,
        doneKeys: Set<String>,
        projectKeys: Set<String>
    ): Result<Boolean> = runCatching {
        val viaName = line.name.ifBlank { "Vía ${index + 1}" }
        // Clave por lineId + legado por nombre (ver toggleLine).
        val key = entryKey(block.schoolId, line.id.takeIf { it.isNotBlank() }, viaName)
        val legacyKey = viaKey(block.schoolId, viaName)
        if (doneKeys.contains(key) || doneKeys.contains(legacyKey)) return@runCatching false
        val alreadyProject = projectKeys.contains(key) || projectKeys.contains(legacyKey)
        if (markProject == true && alreadyProject) {
            journalProjectStore.add(key)
            return@runCatching true
        }
        val unmarkProject = markProject?.let { !it } ?: alreadyProject
        if (unmarkProject) {
            // DESMARCAR proyecto
            removeProjectEntry(block.schoolId, key, legacyKey)
            refresh()
            false
        } else {
            // MARCAR proyecto
            journalProjectStore.add(key)
            removeOutboxByKey(OutboxType.JOURNAL_DELETE, key)
            val req = CreateJournalRequest(
                schoolId = block.schoolId,
                schoolName = schoolName.ifBlank { null },
                sector = sectorName,
                blockName = viaName,
                grade = line.grade,
                notes = null,
                date = java.time.LocalDate.now().toString(),
                discipline = block.discipline,
                lineId = line.id.takeIf { it.isNotBlank() },
                status = "PROJECT"
            )
            val sent = networkMonitor.isOnline.value && runCatching { createJournalEntry(req) }.isSuccess
            if (sent) {
                refresh()
            } else {
                outboxRepo.enqueue(
                    OutboxType.JOURNAL, block.schoolId,
                    journalJson.encodeToString(CreateJournalRequest.serializer(), req)
                )
            }
            true
        }
    }

    /** Cancela/borra la entrada PROYECTO de [key] (cola pendiente o ya subida
     *  al servidor). Compartido por toggleProject (desmarcar) y toggleLine
     *  (promoción proyecto→hecha). */
    private suspend fun removeProjectEntry(schoolId: String, key: String, legacyKey: String = key) {
        journalProjectStore.remove(key)
        journalProjectStore.remove(legacyKey)
        val hadPendingCreate = removeOutboxByKey(OutboxType.JOURNAL, key)
        if (!hadPendingCreate) {
            val online = networkMonitor.isOnline.value
            // Por clave nueva (id) o, para entradas antiguas SIN lineId, por nombre.
            val entry = if (online) {
                _myJournal.value.firstOrNull {
                    it.status == "PROJECT" && (
                        entryKey(it.schoolId, it.lineId, it.blockName) == key ||
                            (it.lineId == null && viaKey(it.schoolId, it.blockName) == legacyKey))
                }
            } else null
            val deleted = entry != null && runCatching { deleteJournalEntry(entry.id) }.isSuccess
            if (!deleted) {
                val delKey = entry?.let { entryKey(it.schoolId, it.lineId, it.blockName) } ?: key
                removeOutboxByKey(OutboxType.JOURNAL_DELETE, delKey)
                outboxRepo.enqueue(OutboxType.JOURNAL_DELETE, schoolId, delKey)
            }
        }
    }

    /** Borra de la cola las filas de [type] cuya clave "escuela|vía" coincide.
     *  Para JOURNAL el payload es JSON (CreateJournalRequest); para JOURNAL_DELETE
     *  el payload ES la clave. Devuelve true si borró alguna. */
    private suspend fun removeOutboxByKey(type: String, key: String): Boolean {
        var removed = false
        outboxRepo.all().filter { it.type == type }.forEach { row ->
            val rowKey = if (type == OutboxType.JOURNAL_DELETE) {
                row.payloadJson
            } else {
                runCatching {
                    journalJson.decodeFromString(CreateJournalRequest.serializer(), row.payloadJson)
                }.getOrNull()?.let { entryKey(it.schoolId, it.lineId, it.blockName) }
            }
            if (rowKey == key) { outboxRepo.delete(row.id); removed = true }
        }
        return removed
    }
}
