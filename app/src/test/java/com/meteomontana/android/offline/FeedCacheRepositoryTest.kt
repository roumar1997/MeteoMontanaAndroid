package com.meteomontana.android.offline

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meteomontana.android.data.api.KtorFeedApi
import com.meteomontana.android.data.api.dto.FeedAuthorDto
import com.meteomontana.android.data.api.dto.FeedPostDto
import com.meteomontana.android.data.repository.KtorFeedRepository
import com.meteomontana.db.MeteoMontanaDb
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Caché de disco de la PRIMERA página del feed (KtorFeedRepository + SQLDelight
 * en memoria): con red se guarda; sin red se devuelve la última buena; la
 * paginación (before != null) nunca toca la caché.
 */
class FeedCacheRepositoryTest {

    private lateinit var db: MeteoMontanaDb
    private lateinit var api: KtorFeedApi
    private lateinit var repo: KtorFeedRepository

    private fun post(id: Long) = FeedPostDto(
        id = id, kind = "TICK", createdAt = "2026-07-19T10:00:00",
        author = FeedAuthorDto(uid = "u1", username = "ana", displayName = "Ana", photoUrl = null)
    )

    @Before fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeteoMontanaDb.Schema.create(driver)
        db = MeteoMontanaDb(driver)
        api = mockk()
        repo = KtorFeedRepository(api, db)
    }

    @Test fun `primera pagina OK se sirve y queda cacheada`() = runTest {
        coEvery { api.getFeed("explore", null, 20, null) } returns listOf(post(1), post(2))
        val out = repo.getFeed("explore", null, 20, null)
        assertEquals(listOf(1L, 2L), out.map { it.id })
        val row = db.schemaQueries.selectFeedPage("explore|").executeAsOneOrNull()
        assertTrue("la página debe quedar en la caché", row != null && row.json.contains("\"id\":1"))
    }

    @Test fun `sin red devuelve la ultima primera pagina buena`() = runTest {
        coEvery { api.getFeed("explore", null, 20, null) } returns listOf(post(7))
        repo.getFeed("explore", null, 20, null)          // llena la caché
        coEvery { api.getFeed("explore", null, 20, null) } throws RuntimeException("sin red")
        val out = repo.getFeed("explore", null, 20, null)
        assertEquals(listOf(7L), out.map { it.id })
    }

    @Test(expected = RuntimeException::class)
    fun `sin red y sin cache relanza el error`() = runTest {
        coEvery { api.getFeed("following", null, 20, null) } throws RuntimeException("sin red")
        repo.getFeed("following", null, 20, null)
    }

    @Test(expected = RuntimeException::class)
    fun `la paginacion nunca usa la cache`() = runTest {
        coEvery { api.getFeed("explore", null, 20, null) } returns listOf(post(1))
        repo.getFeed("explore", null, 20, null)
        coEvery { api.getFeed("explore", 1L, 20, null) } throws RuntimeException("sin red")
        repo.getFeed("explore", 1L, 20, null)   // before != null → error directo
    }
}
