package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.LineExploreCriteria
import com.meteomontana.android.domain.model.LineSearchHit
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchoolLinesExplorerTest {

    private fun hit(
        id: String?, name: String, grade: String? = "6A", sector: String? = null,
        reps: Int? = null, rating: Double? = null, votes: Int? = null, block: String = "Piedra"
    ) = LineSearchHit(
        schoolId = "s1", schoolName = "Escuela", blockId = "b-$block", blockName = block,
        lineId = id, lineName = name, grade = grade, sectorName = sector,
        photoPath = null, linePath = null, startType = null,
        rating = rating, ratingCount = votes, repetitions = reps
    )

    private fun filter(
        query: String = "", grades: Set<String> = emptySet(), betas: Set<String> = emptySet(),
        order: LinesOrder = LinesOrder.MOST_REPEATED
    ) = LinesFilter(query, grades, betas, order)

    private val lines = listOf(
        hit("l1", "Sisha nulera", "7A", "Techos", reps = 23),
        hit("l2", "Viejos ciegos", "7A", "Techos", reps = 18, rating = 4.2, votes = 5),
        hit("l3", "Plan b", "7A", "La Fuente", reps = 9),
        hit("l4", "Arrebato", "7B", "La Fuente", reps = 7),
        hit("l5", "Sin hacer", "6A", null, reps = 0),
        hit("l6", "Bonita", "6A+", "Techos", reps = 0, rating = 4.9, votes = 8),
        hit(null, "Piedra suelta", null, "Techos") // piedra sin vía: nunca entra
    )

    @Test fun `solo entran las vias y nunca las piedras sin via`() {
        val v = SchoolLinesExplorer.view(lines, filter(), emptyMap())!!
        assertEquals(6, v.total)
        assertTrue((v.podium + v.groups.flatMap { it.hits }).none { it.lineId == null })
    }

    @Test fun `mas repetidos - podio con las 3 mas repetidas y el resto por sectores`() {
        val v = SchoolLinesExplorer.view(lines, filter(), emptyMap())!!
        assertEquals(listOf("l1", "l2", "l3"), v.podium.map { it.lineId })
        // Resto: Arrebato (La Fuente, 7 reps) abre el grupo de La Fuente; "Bonita" abre Techos; "Sin hacer", sin sector.
        assertEquals(listOf("La Fuente", "Techos", null), v.groups.map { it.sector })
        assertEquals(listOf("l4"), v.groups[0].hits.map { it.lineId })
    }

    @Test fun `el podio solo lleva vias con alguna repeticion`() {
        val sinReps = listOf(hit("a", "A", reps = 0), hit("b", "B", reps = 0))
        assertTrue(SchoolLinesExplorer.view(sinReps, filter(), emptyMap())!!.podium.isEmpty())
    }

    @Test fun `mejor valoradas - sin podio y por estrellas`() {
        val v = SchoolLinesExplorer.view(lines, filter(order = LinesOrder.BEST_RATED), emptyMap())!!
        assertTrue(v.podium.isEmpty())
        val order = v.groups.flatMap { it.hits }.map { it.lineId }
        assertEquals("l6", order.first())   // 4,9
        assertEquals("l2", order[1])        // 4,2
    }

    @Test fun `una valoracion sin votos no cuenta`() {
        val l = listOf(hit("a", "A", rating = 5.0, votes = 0), hit("b", "B", rating = 3.0, votes = 2))
        val v = SchoolLinesExplorer.view(l, filter(order = LinesOrder.BEST_RATED), emptyMap())!!
        assertEquals(listOf("b", "a"), v.groups.flatMap { it.hits }.map { it.lineId })
    }

    @Test fun `varios grados a la vez`() {
        val v = SchoolLinesExplorer.view(lines, filter(grades = setOf("7B", "6A+")), emptyMap())!!
        assertEquals(setOf("l4", "l6"), (v.podium + v.groups.flatMap { it.hits }).map { it.lineId }.toSet())
    }

    @Test fun `la busqueda ignora mayusculas y tildes y mira vias bloques y sectores`() {
        assertEquals(1, SchoolLinesExplorer.view(lines, filter(query = "  ARREBATO "), emptyMap())!!.total)
        assertEquals(2, SchoolLinesExplorer.view(lines, filter(query = "la fuente"), emptyMap())!!.total)
        val tildes = listOf(hit("a", "Qué día", sector = "Peña"))
        assertEquals(1, SchoolLinesExplorer.view(tildes, filter(query = "que dia"), emptyMap())!!.total)
        assertEquals(1, SchoolLinesExplorer.view(tildes, filter(query = "pena"), emptyMap())!!.total)
    }

    @Test fun `beta - con las dos alturas se ve la union`() {
        val ids = mapOf(BetaHeights.TALL to setOf("l1"), BetaHeights.SHORT to setOf("l3", "l1"))
        val one = SchoolLinesExplorer.view(lines, filter(betas = setOf(BetaHeights.TALL)), ids)!!
        assertEquals(1, one.total)
        val both = SchoolLinesExplorer.view(lines, filter(betas = setOf(BetaHeights.TALL, BetaHeights.SHORT)), ids)!!
        assertEquals(setOf("l1", "l3"), (both.podium + both.groups.flatMap { it.hits }).map { it.lineId }.toSet())
    }

    @Test fun `beta - si falta el conjunto de ids de una altura marcada todavia esta cargando`() {
        assertNull(SchoolLinesExplorer.view(lines, filter(betas = setOf(BetaHeights.TALL)), emptyMap()))
    }

    @Test fun `opciones de grado - solo los que hay, de facil a dificil, sin duplicados`() {
        assertEquals(listOf("6A", "6A+", "7A", "7B"), SchoolLinesExplorer.gradeOptions(lines))
    }

    @Test fun `opciones de grado - los no puntuables van al final`() {
        val l = listOf(hit("a", "A", "PROY"), hit("b", "B", "5C"), hit("c", "C", "7a/7a+"))
        assertEquals(listOf("5C", "7A/7A+", "PROY"), SchoolLinesExplorer.gradeOptions(l))
    }

    @Test fun `el orden es estable - mismo resultado con la lista en otro orden`() {
        val a = SchoolLinesExplorer.view(lines, filter(), emptyMap())!!
        val b = SchoolLinesExplorer.view(lines.reversed(), filter(), emptyMap())!!
        assertEquals(a, b)
    }

    // ── descarga por páginas ──

    private fun page(from: Int, n: Int) = (from until from + n).map { hit("l$it", "V$it") }

    @Test fun `pide paginas hasta que llega una vacia usando lo recibido como desplazamiento`() = runTest {
        val offsets = mutableListOf<Int>()
        val all = fetchAllLinePages(LineExploreCriteria(schoolIds = listOf("s1")), fetch = { c ->
            offsets += c.offset
            when (c.offset) { 0 -> page(0, 300); 300 -> page(300, 120); else -> emptyList() }
        })
        assertEquals(420, all.size)
        assertEquals(listOf(0, 300, 420), offsets)
    }

    @Test fun `una escuela pequena hace dos peticiones - la segunda confirma que no hay mas`() = runTest {
        var calls = 0
        val all = fetchAllLinePages(LineExploreCriteria(), fetch = { c ->
            calls++; if (c.offset == 0) page(0, 5) else emptyList()
        })
        assertEquals(5, all.size)
        assertEquals(2, calls)
    }

    @Test fun `tope de paginas - un servidor que nunca acaba no cuelga la app`() = runTest {
        var calls = 0
        fetchAllLinePages(LineExploreCriteria(), fetch = { c -> calls++; page(c.offset, 10) }, maxPages = 3)
        assertEquals(3, calls)
    }
}
