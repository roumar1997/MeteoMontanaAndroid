package com.meteomontana.android.domain.usecase.assistant

import com.meteomontana.android.domain.model.FavoriteSchool
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.model.Meetup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MineAnswererTest {

    private fun entry(
        name: String, grade: String?, date: String, school: String = "Zarzalejo", schoolId: String = "z",
        status: String = "DONE", discipline: String = "BOULDER", lineId: String? = null
    ) = JournalSession(
        id = name + date, schoolId = schoolId, schoolName = school, sector = null, blockName = name,
        grade = grade, notes = null, date = date, createdAt = date, discipline = discipline,
        lineId = lineId, status = status
    )

    private val journal = listOf(
        entry("A", "6C", "2026-03-01"),
        entry("B", "7A", "2026-05-10", school = "Albarracín", schoolId = "a"),
        entry("C", "6C+", "2025-11-02"),
        entry("D", "7B", "2026-06-01", status = "PROJECT"),
        entry("E", "6C", "2026-06-20", discipline = "ROUTE")
    )

    private fun stats(
        discipline: String? = null, min: String? = null, max: String? = null, year: Int? = null,
        schoolId: String? = null, schoolName: String? = null
    ) = MineAnswerer.stats(journal, discipline, min, max, year, schoolId, schoolName)

    @Test fun losProyectosNoCuentanComoHechos() {
        val r = stats()
        assertEquals(4, r.count)
        assertEquals("7A", r.maxGrade)               // el 7B es un proyecto: no se ha hecho
    }

    @Test fun elAnoYLaEscuelaFiltran() {
        assertEquals(3, stats(year = 2026).count)
        assertEquals(1, stats(schoolName = "albarracín").count)
        assertEquals(1, stats(schoolId = "a").count)
    }

    @Test fun elRangoDeGradoYLaModalidadFiltran() {
        assertEquals(3, stats(min = "6C", max = "6C+").count)           // A, C y E
        assertEquals(1, stats(discipline = "ROUTE", min = "6C", max = "6C").count)
    }

    @Test fun laUltimaVisitaEsLaFechaMasReciente() {
        assertEquals("2026-06-20", MineAnswerer.lastVisit(journal, null, null).lastDate)
        assertEquals("2026-05-10", MineAnswerer.lastVisit(journal, "a", null).lastDate)
        assertNull(MineAnswerer.lastVisit(journal, "nadie", "Nada").lastDate)
    }

    @Test fun lasFavoritasSeListanConSuNombre() {
        val favs = listOf(
            FavoriteSchool("1", "Albarracín", null, null, true), FavoriteSchool("2", "La Pedriza", null, null, true)
        )
        val r = MineAnswerer.favorites(favs)
        assertEquals(2, r.count)
        assertEquals(listOf("Albarracín", "La Pedriza"), r.items)
    }

    @Test fun excluirLoHechoQuitaPorIdYPorNombreDeLasAntiguas() {
        val diario = listOf(
            entry("x", "6A", "2026-01-01", lineId = "l1"),
            entry("Vieja", "6A", "2026-01-02"),                          // sin lineId: se reconoce por nombre
            entry("Proyecto", "6B", "2026-01-03", status = "PROJECT", lineId = "l3")
        )
        fun hit(id: String?, name: String?) = LineSearchHit(
            schoolId = "s", schoolName = "S", blockId = "b", blockName = "P", lineId = id, lineName = name,
            grade = "6a", sectorName = null, photoPath = null, linePath = null, startType = null
        )
        val hits = listOf(hit("l1", "Hecha"), hit("l2", "vieja"), hit("l3", "Proyecto"), hit("l4", "Nueva"), hit(null, null))

        val out = MineAnswerer.excludeDone(hits, diario)

        assertEquals(listOf("l3", "l4", null), out.map { it.lineId })    // el proyecto SÍ se muestra; la piedra también
    }

    private fun meetup(id: String, name: String, first: String, last: String, school: String = "s", joined: Boolean = false) =
        Meetup(
            id = id, schoolId = school, schoolName = "Escuela", schoolLat = null, schoolLon = null, name = name,
            description = null, discipline = null, privacy = "OPEN", memberLimit = null, memberCount = 1,
            photoUrl = null, creatorUid = "u", creatorUsername = null, creatorPhotoUrl = null,
            conversationId = "c", days = listOf(first), lastDay = last, expiresAt = 0, createdAt = 0,
            members = emptyList(), joined = joined
        )

    @Test fun lasQuedadasPasadasNoSalenYLasProximasVanPrimero() {
        val all = listOf(
            meetup("1", "Vieja", "2026-10-01", "2026-10-02"),
            meetup("2", "Sábado", "2026-10-17", "2026-10-17"),
            meetup("3", "Mañana", "2026-10-09", "2026-10-09", joined = true)
        )
        val r = MineAnswerer.meetups(all, "2026-10-08", null)
        assertEquals(2, r.count)
        assertEquals("Mañana", r.items.first().substringBefore(" ·"))
        assertEquals(1, MineAnswerer.meetups(all, "2026-10-08", null, joinedOnly = true).count)
    }
}
