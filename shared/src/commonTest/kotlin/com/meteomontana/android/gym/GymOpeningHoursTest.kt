package com.meteomontana.android.gym

import com.meteomontana.android.domain.model.gym.GymOpenStatus
import com.meteomontana.android.domain.model.gym.OpeningSlot
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Noviembre de 2026 en Madrid es CET (UTC+1). El lunes 16 a las 09:00Z son las 10:00 en Madrid.
 * (Lunes 16, martes 17, ... sabado 21, domingo 22.)
 */
class GymOpeningHoursTest {

    private val standard = listOf(
        OpeningSlot("MON-FRI", "10:00", "23:00"),
        OpeningSlot("SAT,SUN", "10:00", "21:00")
    )

    private fun at(iso: String) = Instant.parse(iso)

    @Test
    fun `sin horario no se sabe, no dice cerrado`() {
        assertNull(gym(hours = null).isOpenAt(at("2026-11-16T09:00:00Z")))
        assertNull(gym(hours = emptyList()).isOpenAt(at("2026-11-16T09:00:00Z")))
        assertEquals(GymOpenStatus.Unknown, gym(hours = null).openStatusAt(at("2026-11-16T09:00:00Z")))
    }

    @Test
    fun `abierto entre semana y hasta cuando`() {
        val g = gym(hours = standard)
        assertEquals(true, g.isOpenAt(at("2026-11-16T09:00:00Z")))               // lunes 10:00
        assertEquals(GymOpenStatus.Open("23:00"), g.openStatusAt(at("2026-11-16T15:00:00Z")))
    }

    @Test
    fun `cerrado antes de abrir dice a que hora abre`() {
        val g = gym(hours = standard)
        assertEquals(GymOpenStatus.Closed("10:00"), g.openStatusAt(at("2026-11-16T07:00:00Z"))) // 08:00
    }

    @Test
    fun `cerrado despues de cerrar ya no abre hoy`() {
        val g = gym(hours = standard)
        assertEquals(GymOpenStatus.Closed(null), g.openStatusAt(at("2026-11-16T22:30:00Z"))) // 23:30
    }

    @Test
    fun `el cierre es exclusivo y la apertura inclusiva`() {
        val g = gym(hours = standard)
        assertEquals(true, g.isOpenAt(at("2026-11-16T09:00:00Z")))   // 10:00
        assertEquals(false, g.isOpenAt(at("2026-11-16T22:00:00Z")))  // 23:00
        assertEquals(true, g.isOpenAt(at("2026-11-16T21:59:00Z")))   // 22:59
    }

    @Test
    fun `domingo usa el tramo de fin de semana`() {
        val g = gym(hours = standard)
        assertEquals(true, g.isOpenAt(at("2026-11-22T19:30:00Z")))   // domingo 20:30
        assertEquals(false, g.isOpenAt(at("2026-11-22T20:30:00Z")))  // domingo 21:30
    }

    @Test
    fun `dia sin tramo esta cerrado`() {
        val g = gym(hours = listOf(OpeningSlot("MON-FRI", "10:00", "23:00")))
        assertEquals(GymOpenStatus.Closed(null), g.openStatusAt(at("2026-11-21T10:00:00Z"))) // sabado
    }

    @Test
    fun `horario partido de mediodia`() {
        val g = gym(hours = listOf(OpeningSlot("MON-FRI", "09:00", "14:00"), OpeningSlot("MON-FRI", "17:00", "22:00")))
        assertEquals(GymOpenStatus.Open("14:00"), g.openStatusAt(at("2026-11-16T12:00:00Z")))   // 13:00
        assertEquals(GymOpenStatus.Closed("17:00"), g.openStatusAt(at("2026-11-16T14:00:00Z"))) // 15:00
    }

    @Test
    fun `cierre pasada la medianoche cuenta en el dia siguiente`() {
        val g = gym(hours = listOf(OpeningSlot("FRI", "18:00", "02:00")))
        assertEquals(GymOpenStatus.Open("02:00"), g.openStatusAt(at("2026-11-20T22:00:00Z"))) // viernes 23:00
        assertEquals(GymOpenStatus.Open("02:00"), g.openStatusAt(at("2026-11-21T00:00:00Z"))) // sabado 01:00
        assertEquals(false, g.isOpenAt(at("2026-11-21T01:30:00Z")))                           // sabado 02:30
    }

    @Test
    fun `cierre a las 24 horas se enseña como 00 00`() {
        val g = gym(hours = listOf(OpeningSlot("MON", "10:00", "24:00")))
        assertEquals(GymOpenStatus.Open("00:00"), g.openStatusAt(at("2026-11-16T20:00:00Z")))
    }

    @Test
    fun `rangos que dan la vuelta a la semana y listas mezcladas`() {
        val g = gym(hours = listOf(OpeningSlot("FRI-MON", "10:00", "20:00"), OpeningSlot("WED,THU", "16:00", "22:00")))
        assertEquals(true, g.isOpenAt(at("2026-11-22T10:00:00Z")))   // domingo
        assertEquals(false, g.isOpenAt(at("2026-11-17T10:00:00Z")))  // martes
        assertEquals(true, g.isOpenAt(at("2026-11-18T17:00:00Z")))   // miercoles 18:00
    }

    @Test
    fun `tramos ilegibles se ignoran sin romper`() {
        val g = gym(hours = listOf(OpeningSlot("XXX", "foo", "bar"), OpeningSlot("MON", "10:00", "10:00")))
        assertEquals(false, g.isOpenAt(at("2026-11-16T10:00:00Z")))
    }

    @Test
    fun `el cambio de hora de verano se resuelve en Madrid`() {
        // Julio es CEST (UTC+2): 08:00Z = 10:00 en Madrid, lunes 20 de julio de 2026.
        val g = gym(hours = standard)
        assertEquals(true, g.isOpenAt(at("2026-07-20T08:00:00Z")))
        assertEquals(false, g.isOpenAt(at("2026-07-20T07:30:00Z")))
    }
}
