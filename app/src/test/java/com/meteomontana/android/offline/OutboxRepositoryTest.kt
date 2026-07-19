package com.meteomontana.android.offline

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meteomontana.android.data.outbox.OutboxRepository
import com.meteomontana.android.data.outbox.OutboxType
import com.meteomontana.db.MeteoMontanaDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * OutboxRepository (cola de sincronización offline) sobre una BD SQLDelight EN
 * MEMORIA — la lógica más frágil según el estudio de arquitectura, hasta ahora
 * sin ningún test. Protege sobre todo la CANCELACIÓN de acciones opuestas
 * (marcar y desmarcar favorita sin red = nada que sincronizar) y la
 * idempotencia (no duplicar la misma acción).
 */
class OutboxRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repo: OutboxRepository

    @Before fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeteoMontanaDb.Schema.create(driver)
        repo = OutboxRepository(MeteoMontanaDb(driver))
    }

    @After fun tearDown() { driver.close() }

    @Test fun `encolar y borrar una peticion`() = runTest {
        repo.enqueue(OutboxType.NOTE, "esc", "{\"text\":\"hola\"}")
        assertEquals(1, repo.all().size)
        repo.delete(repo.all().first().id)
        assertTrue(repo.all().isEmpty())
    }

    @Test fun `marcar y desmarcar favorita sin red se cancelan y no queda nada`() = runTest {
        // El caso clave: sin cancelación, se sincronizarían dos acciones opuestas
        // (marca + desmarca) contra el servidor. Deben anularse.
        repo.enqueueFavorite("esc-1", favorite = true)
        assertEquals(setOf("esc-1"), repo.pendingFavoriteIds())
        repo.enqueueFavorite("esc-1", favorite = false)
        assertTrue("marcar+desmarcar debe dejar la cola vacía", repo.all().isEmpty())
    }

    @Test fun `marcar favorita dos veces es idempotente`() = runTest {
        repo.enqueueFavorite("esc-1", favorite = true)
        repo.enqueueFavorite("esc-1", favorite = true)
        assertEquals(1, repo.all().size)  // no duplica
    }

    @Test fun `desmarcar sobre una marca pendiente la cancela, no crea DELETE`() = runTest {
        repo.enqueueFavorite("esc-1", favorite = true)
        repo.enqueueFavorite("esc-1", favorite = false)
        assertTrue(repo.pendingFavoriteIds().isEmpty())
        assertTrue(repo.pendingFavoriteDeleteIds().isEmpty())
    }

    @Test fun `favoritas de escuelas distintas no se interfieren`() = runTest {
        repo.enqueueFavorite("esc-1", favorite = true)
        repo.enqueueFavorite("esc-2", favorite = false)
        assertEquals(setOf("esc-1"), repo.pendingFavoriteIds())
        assertEquals(setOf("esc-2"), repo.pendingFavoriteDeleteIds())
    }

    @Test fun `markRetry incrementa sin borrar la fila`() = runTest {
        repo.enqueue(OutboxType.JOURNAL, "esc", "{}")
        val id = repo.all().first().id
        repo.markRetry(id, "timeout")
        assertEquals("la fila sigue en la cola para reintentar", 1, repo.all().size)
    }
}
