package com.meteomontana.android.gym

import com.meteomontana.android.domain.model.MeetupDraft
import com.meteomontana.android.domain.model.MeetupGear
import com.meteomontana.android.domain.model.MeetupGearItem
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymTraining
import com.meteomontana.android.domain.model.gym.GymTrainingPeriod
import com.meteomontana.android.domain.usecase.gym.WetRockAdvisor
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GymTrainingAndDraftTest {

    // 2026-11-19 es jueves: la semana va del 16 al 22.
    private val today = LocalDate(2026, 11, 19)

    private fun s(gym: Long, date: String, vararg a: Pair<String, Int>) =
        GymSession(gym, LocalDate.parse(date), ascents = a.map { GymAscent(it.first, it.second) })

    @Test
    fun `los periodos van de lunes a domingo, mes y anio enteros`() {
        assertEquals(LocalDate(2026, 11, 16) to LocalDate(2026, 11, 22), GymTrainingPeriod.WEEK.range(today))
        assertEquals(LocalDate(2026, 11, 1) to LocalDate(2026, 11, 30), GymTrainingPeriod.MONTH.range(today))
        assertEquals(LocalDate(2026, 1, 1) to LocalDate(2026, 12, 31), GymTrainingPeriod.YEAR.range(today))
        assertEquals(LocalDate(2026, 11, 16) to LocalDate(2026, 11, 22), GymTrainingPeriod.WEEK.range(LocalDate(2026, 11, 22)))
    }

    @Test
    fun `el resumen cuenta sesiones, rocodromos y ascensiones y ordena de mas reciente a mas antigua`() {
        val all = listOf(
            s(5, "2026-11-19", "verde" to 5, "azul" to 2), s(6, "2026-11-17", "6a" to 3),
            s(5, "2026-11-10", "verde" to 9)
        )
        val week = GymTraining.summary(all, GymTrainingPeriod.WEEK, today)
        assertEquals(2, week.sessions.size)
        assertEquals(2, week.gyms)
        assertEquals(10, week.ascents)
        assertEquals(listOf("2026-11-19", "2026-11-17"), week.sessions.map { it.date.toString() })
        val month = GymTraining.summary(all, GymTrainingPeriod.MONTH, today)
        assertEquals(3, month.sessions.size)
        assertEquals(2, month.gyms)
        assertEquals(19, month.ascents)
    }

    @Test
    fun `las estadisticas suman por clave, con filtro de rocodromo y mas hechas primero`() {
        val all = listOf(s(5, "2026-11-19", "verde" to 5, "azul" to 2), s(5, "2026-11-12", "verde" to 1), s(6, "2026-11-17", "6a" to 3))
        val st = GymTraining.stats(all, GymTrainingPeriod.MONTH, today, gymId = 5)
        assertEquals(2, st.sessions)
        assertEquals(8, st.total)
        assertEquals(listOf(GymAscent("verde", 6), GymAscent("azul", 2)), st.items)
        assertEquals(11, GymTraining.stats(all, GymTrainingPeriod.MONTH, today).total)
    }

    @Test
    fun `apuntar suma y resta sin bajar de cero ni dejar claves a cero`() {
        var list = emptyList<GymAscent>()
        list = GymTraining.adjust(list, "verde", 1)
        list = GymTraining.adjust(list, "verde", 1)
        list = GymTraining.adjust(list, "azul", 1)
        assertEquals(listOf(GymAscent("verde", 2), GymAscent("azul", 1)), list)
        list = GymTraining.adjust(list, "azul", -1)
        assertEquals(listOf(GymAscent("verde", 2)), list)
        assertEquals(listOf(GymAscent("verde", 2)), GymTraining.adjust(list, "rojo", -1))
        assertEquals(0, GymTraining.countOf(list, "azul"))
    }

    @Test
    fun `roca mojada solo avisa con muestra suficiente y mas de la mitad mojada`() {
        assertFalse(WetRockAdvisor.isMostlyWet(emptyList()))
        assertFalse(WetRockAdvisor.isMostlyWet(listOf(null, null, null)))
        assertFalse(WetRockAdvisor.isMostlyWet(listOf(false, false, null, null, null)), "2 de 5 conocidas no basta")
        assertTrue(WetRockAdvisor.isMostlyWet(listOf(false, false, true)))
        assertFalse(WetRockAdvisor.isMostlyWet(listOf(false, true)), "empate no es mayoria")
        assertTrue(WetRockAdvisor.isMostlyWet(listOf(false)), "con una sola escuela cercana vale ese dato")
        assertFalse(WetRockAdvisor.isMostlyWet(listOf(false, true, true, null)))
    }

    // ── Quedada en 3 pasos ────────────────────────────────────────────────

    @Test
    fun `el paso 1 pide lugar y dia, y cambiar de lugar suelta lo elegido`() {
        var d = MeetupDraft()
        assertFalse(d.canAdvance(1))
        d = d.withGym(5, "Sputnik").toggleDay("2026-11-28")
        assertTrue(d.canAdvance(1))
        d = d.withPlaceKind(false)
        assertFalse(d.canAdvance(1))
        assertEquals("", d.placeName)
        assertEquals(setOf("2026-11-28"), d.days, "los dias se conservan")
        assertTrue(d.withSchool("zarzalejo", "Zarzalejo").canAdvance(1))
    }

    @Test
    fun `cordada pone una persona y modalidad via, y quitarla vuelve a dos`() {
        var d = MeetupDraft().toggleRopeTeam()
        assertTrue(d.isRopeTeam)
        assertEquals("ROUTE", d.discipline)
        assertEquals(1, d.partners)
        d = d.toggleRopeTeam()
        assertFalse(d.isRopeTeam)
        assertEquals(2, d.partners)
        assertEquals(1, d.withPartners(0).partners)
        assertEquals(20, d.withPartners(99).partners)
    }

    @Test
    fun `el grado se toca como en Escuelas y siempre queda ordenado`() {
        var d = MeetupDraft().toggleGrade("6c")
        assertEquals("6c", d.gradeMin); assertEquals("6c", d.gradeMax)
        d = d.toggleGrade("6a")
        assertEquals("6a", d.gradeMin); assertEquals("6c", d.gradeMax)
        assertTrue(d.isGradeSelected("6b")); assertFalse(d.isGradeSelected("7a"))
        d = d.toggleGrade("7b")
        assertEquals("7b", d.gradeMin); assertEquals("7b", d.gradeMax)
        d = d.toggleGrade("7b")
        assertNull(d.gradeMin); assertNull(d.gradeMax)
    }

    @Test
    fun `el material depende de la modalidad y cambiarla suelta el que ya no vale`() {
        var d = MeetupDraft().withDiscipline("ROUTE").toggleGear(MeetupGearItem.ROPE).toggleGear(MeetupGearItem.QUICKDRAWS)
        assertEquals(mapOf("ROPE" to 1, "QUICKDRAWS" to 12), d.gear)
        d = d.withGearQty(MeetupGearItem.QUICKDRAWS, 99)
        assertEquals(50, d.gear.getValue("QUICKDRAWS"))
        d = d.withDiscipline("BOULDER")
        assertTrue(d.gear.isEmpty())
        assertEquals(mapOf("CRASHPAD" to 2), d.toggleGear(MeetupGearItem.CRASHPAD).gear)
    }

    @Test
    fun `el limite sin tope es null, sube desde 2 y baja hasta quitarse`() {
        var d = MeetupDraft()
        assertNull(d.stepLimit(-1).memberLimit)
        d = d.stepLimit(1)
        assertEquals(2, d.memberLimit)
        assertEquals(4, d.stepLimit(1).stepLimit(1).memberLimit)
        assertNull(d.stepLimit(-1).memberLimit)
    }

    @Test
    fun `la peticion lleva gymId sin escuela, grado minusculas, cordada y material de la modalidad`() {
        val d = MeetupDraft().withGym(5, "Sputnik Chamberi").toggleDay("2026-11-29").toggleDay("2026-11-28")
            .withDiscipline("ROUTE").toggleRopeTeam().toggleGrade("6A").toggleGrade("6c+")
            .toggleGear(MeetupGearItem.ROPE).toggleGear(MeetupGearItem.QUICKDRAWS).withGearQty(MeetupGearItem.QUICKDRAWS, 9)
        val r = d.toRequest("Quedada en Sputnik Chamberi")
        assertEquals("", r.schoolId)
        assertEquals(5L, r.gymId)
        assertEquals("Quedada en Sputnik Chamberi", r.name)
        assertEquals(listOf("2026-11-28", "2026-11-29"), r.days)
        assertEquals("ROUTE", r.discipline)
        assertEquals("6a", r.gradeMin); assertEquals("6c+", r.gradeMax)
        assertEquals(1, r.partnersWanted)
        assertEquals(listOf(MeetupGear("ROPE", 1), MeetupGear("QUICKDRAWS", 9)), r.requiredGear)
    }

    @Test
    fun `una escuela envia schoolId sin gymId y un nombre propio manda sobre el automatico`() {
        val r = MeetupDraft(name = "  Sabado de cuerda ").withSchool("zarzalejo", "Zarzalejo").toggleDay("2026-11-28").toRequest("x")
        assertEquals("zarzalejo", r.schoolId)
        assertNull(r.gymId)
        assertEquals("Sabado de cuerda", r.name)
        assertTrue(r.requiredGear.isEmpty())
    }
}
