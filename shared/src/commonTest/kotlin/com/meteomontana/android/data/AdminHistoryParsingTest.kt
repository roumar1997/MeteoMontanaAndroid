package com.meteomontana.android.data

import com.meteomontana.android.data.repository.KtorAdminHistoryRepository
import com.meteomontana.android.domain.model.ContentChangeItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminHistoryParsingTest {

    @Test
    fun elEstadoSeLeeCampoACampoYSeVeLoQueCambio() {
        val before = KtorAdminHistoryRepository.fields("""{"name":"Piedra","lat":40.1,"photoPath":null,"sortOrder":3}""")
        val after = KtorAdminHistoryRepository.fields("""{"name":"La ola","lat":40.1,"photoPath":"a.jpg","sortOrder":3}""")
        assertEquals("40.1", before["lat"])
        assertTrue("photoPath" !in before)
        val c = ContentChangeItem("1", "BLOCK", "b1", "b1", "s1", null, null, "ADMIN", null, before, after, "2026-10-18T10:00")
        assertEquals(listOf("name", "photoPath"), c.changedFields)
        assertEquals("La ola", c.title)
    }

    @Test
    fun jsonVacioONuloEsAltaOBorrado() {
        assertTrue(KtorAdminHistoryRepository.fields(null).isEmpty())
        assertTrue(KtorAdminHistoryRepository.fields("no es json").isEmpty())
    }
}
