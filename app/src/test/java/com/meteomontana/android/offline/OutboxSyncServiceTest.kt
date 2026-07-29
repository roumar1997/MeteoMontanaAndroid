package com.meteomontana.android.offline

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.data.outbox.OutboxRepository
import com.meteomontana.android.data.outbox.OutboxSyncService
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.db.MeteoMontanaDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Blinda P1.1: OutboxSyncService (extraído del god-object IosDependencyContainer).
 * BD SQLDelight EN MEMORIA + colaboradores falsos (lambdas que registran) para
 * comprobar el drenado del diario/favoritas y las claves pendientes sin arrastrar
 * la DI real. Protege la lógica más frágil: idempotencia y clave id-aware.
 */
class OutboxSyncServiceTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var outbox: OutboxRepository
    private lateinit var service: OutboxSyncService

    // Colaboradores falsos: registran lo que el drenado les pide.
    private val createdReqs = mutableListOf<CreateJournalRequest>()
    private val deletedIds = mutableListOf<String>()
    private val addedFavs = mutableListOf<String>()
    private val removedFavs = mutableListOf<String>()
    private var journal: List<JournalSession> = emptyList()

    @Before fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeteoMontanaDb.Schema.create(driver)
        outbox = OutboxRepository(MeteoMontanaDb(driver))
        service = OutboxSyncService(
            outbox = outbox,
            submitContribution = { _, _ -> },
            getMyJournal = { journal },
            createJournalEntry = { createdReqs += it },
            deleteJournalEntry = { deletedIds += it },
            addFavorite = { addedFavs += it },
            removeFavorite = { removedFavs += it },
        )
    }

    @After fun tearDown() { driver.close() }

    @Test fun `pendingJournalKeysByStatus separa DONE de PROJECT`() = runTest {
        service.enqueueJournal(CreateJournalRequest(schoolId = "s1", blockName = "Uno",
            date = "2026-01-01", lineId = "L1", status = "DONE"))
        service.enqueueJournal(CreateJournalRequest(schoolId = "s1", blockName = "Dos",
            date = "2026-01-01", lineId = "L2", status = "PROJECT"))
        assertEquals(setOf("s1|#L1"), service.pendingJournalKeysByStatus("DONE"))
        assertEquals(setOf("s1|#L2"), service.pendingJournalKeysByStatus("PROJECT"))
    }

    @Test fun `dequeueJournal quita la creacion pendiente por su clave`() = runTest {
        service.enqueueJournal(CreateJournalRequest(schoolId = "s1", blockName = "Uno",
            date = "2026-01-01", lineId = "L1"))
        assertTrue(service.dequeueJournal("s1|#L1"))
        assertTrue(service.pendingJournalKeys().isEmpty())
        // Una clave que no existe → false, no borra nada.
        assertFalse(service.dequeueJournal("s1|#NOPE"))
    }

    @Test fun `flushJournalOutbox crea la via encolada y limpia la fila`() = runTest {
        val req = CreateJournalRequest(schoolId = "s1", blockName = "Uno",
            date = "2026-01-01", lineId = "L1")
        service.enqueueJournal(req)
        journal = emptyList()              // no existe aún → debe crearse
        service.flushJournalOutbox()
        assertEquals(1, createdReqs.size)
        assertEquals("Uno", createdReqs.first().blockName)
        assertTrue("fila drenada", service.pendingJournalKeys().isEmpty())
    }

    @Test fun `flushJournalOutbox aplica una favorita encolada sin red`() = runTest {
        outbox.enqueueFavorite("esc-9", favorite = true)
        service.flushJournalOutbox()
        assertEquals(listOf("esc-9"), addedFavs)
        assertTrue(service.pendingFavoriteIds().isEmpty())
    }
}
