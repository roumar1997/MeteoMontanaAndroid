package com.meteomontana.android.gym

import com.meteomontana.android.data.api.dto.GymReportDto
import com.meteomontana.android.data.api.dto.GymProposalDto
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.api.dto.toDto
import com.meteomontana.android.domain.model.Current
import com.meteomontana.android.domain.model.gym.GymCorrection
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymReportIssue
import com.meteomontana.android.domain.model.gym.GymReportKind
import com.meteomontana.android.domain.model.gym.GymReportStatus
import com.meteomontana.android.domain.usecase.gym.ShouldSuggestGymsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GymPlanBAndModelsTest {

    private fun current(dryRock: Boolean) = Current(
        time = "2026-11-19T10:00", temperature = 10.0, humidity = 80.0, windSpeed = 5.0,
        precipitation = 0.0, precipitationProbability = 0, cloudCover = 0, dewPoint = null,
        precip24h = 0.0, precip72h = 0.0, dryRock = dryRock, score = 50, scoreLabel = "ok", factors = emptyList()
    )

    @Test
    fun `plan B solo con roca mojada y el interruptor encendido`() {
        val uc = ShouldSuggestGymsUseCase()
        assertTrue(uc(current(dryRock = false), gymsEnabled = true))
        assertFalse(uc(current(dryRock = true), gymsEnabled = true))
        assertFalse(uc(current(dryRock = false), gymsEnabled = false))
        assertEquals(30.0, ShouldSuggestGymsUseCase.RADIUS_KM)
        assertEquals(5, ShouldSuggestGymsUseCase.LIMIT)
    }

    @Test
    fun `la correccion viaja con los codigos de los chips y el comentario recortado`() {
        val dto = GymCorrection(5, listOf(GymReportIssue.IS_FREE, GymReportIssue.CLOSED), "  hola  ", null).toDto()
        assertEquals(listOf("IS_FREE", "CLOSED"), dto.issues)
        assertEquals("hola", dto.comment)
        assertNull(GymCorrection(5, emptyList(), "   ", null).toDto().comment)
    }

    @Test
    fun `el reporte del servidor se lee aunque traiga valores nuevos`() {
        val r = GymReportDto(
            id = 9, kind = "NEW_GYM", issues = listOf("IS_FREE", "CHIP_NUEVO"), status = "OTRA_COSA",
            proposed = GymProposalDto("Muro", 40.1, -3.2, "FREE", "BOULDER", true)
        ).toDomain()
        assertEquals(GymReportKind.NEW_GYM, r.kind)
        assertEquals(GymReportStatus.PENDING, r.status)
        assertEquals(listOf("IS_FREE", "CHIP_NUEVO"), r.issues)
        assertEquals(GymKind.FREE, r.proposed?.kind)
        assertEquals(GymReportIssue.IS_FREE, GymReportIssue.fromCode("IS_FREE"))
        assertNull(GymReportIssue.fromCode("CHIP_NUEVO"))
    }

    @Test
    fun `atribucion de OpenStreetMap solo cuando la fuente lo es`() {
        assertTrue(gym(1, source = "OpenStreetMap node1").needsOsmAttribution)
        assertFalse(gym(2, source = "Web oficial").needsOsmAttribution)
        assertFalse(gym(3).needsOsmAttribution)
    }
}
