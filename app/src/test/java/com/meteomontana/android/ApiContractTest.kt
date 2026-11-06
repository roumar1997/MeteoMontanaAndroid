package com.meteomontana.android

import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.FeatureFlagsDto
import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.JournalSessionDto
import com.meteomontana.android.data.api.dto.MeetupDto
import com.meteomontana.android.data.api.dto.PrivateProfileDto
import com.meteomontana.android.data.api.dto.SchoolDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CONTRATO de los endpoints principales con el backend — la mitad "app".
 *
 * La otra mitad: api/src/test/.../contract/ApiContractTest.java en el repo del
 * backend. Los dos leen el MISMO fichero contract/<nombre>.json (idéntico byte
 * a byte; está en app/src/test/resources/contract/ aquí y en
 * api/src/test/resources/contract/ allí). El backend serializa sus vistas
 * reales y exige igualdad con la muestra; esta clase parsea la muestra con los
 * DTOs REALES de shared y la MISMA config de Json que ApiHttpClient.
 *
 * Cada fichero lleva "full" (todo con valor) y "nulls" (todo lo nullable a
 * null). Además, [assertDtoKeysInGolden] re-serializa el DTO con TODOS sus
 * campos y exige que cada uno exista en la muestra "full": si la app lee un
 * campo que el backend no manda, falla aquí.
 *
 * Endpoints: GET schools, schools/{id}/blocks, schools/{id}/forecast,
 * journal/me, me, meetups, feature-flags.
 *
 * Si cambias un DTO o el backend: actualiza el MISMO json en los dos repos.
 * Campo nuevo nullable = aditivo; renombrar/borrar/cambiar tipo rompe las apps
 * instaladas → expand-contract, no esto.
 */
class ApiContractTest {

    /** Misma config que ApiHttpClient.kt. */
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** Para re-serializar el DTO con todos sus campos (también los de por defecto). */
    private val fullJson = Json { encodeDefaults = true; explicitNulls = true }

    private fun golden(file: String, sample: String): String {
        val text = javaClass.getResourceAsStream("/contract/$file")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        assertNotNull("falta la muestra contract/$file", text)
        return json.parseToJsonElement(text!!).let { (it as JsonObject)[sample]!!.toString() }
    }

    private fun <T> parse(file: String, sample: String, serializer: KSerializer<T>): T =
        json.decodeFromString(serializer, golden(file, sample))

    /** Cada clave que el DTO conoce debe venir en la muestra del backend. */
    private fun <T> assertDtoKeysInGolden(file: String, serializer: KSerializer<T>) {
        val dto = fullJson.encodeToJsonElement(serializer, parse(file, "full", serializer))
        val sample = json.parseToJsonElement(golden(file, "full"))
        assertKeysCovered(file, "$", dto, sample)
    }

    private fun assertKeysCovered(file: String, path: String, dto: JsonElement, sample: JsonElement) {
        when {
            dto is JsonObject && sample is JsonObject -> dto.forEach { (key, value) ->
                assertTrue("$file: la app lee '$path.$key' pero el backend no lo manda", key in sample)
                assertKeysCovered(file, "$path.$key", value, sample.getValue(key))
            }
            dto is JsonArray && sample is JsonArray ->
                dto.zip(sample).forEach { (d, s) -> assertKeysCovered(file, "$path[]", d, s) }
            else -> Unit
        }
    }

    // ── GET schools ─────────────────────────────────────────────────────────

    @Test fun catalogoDeEscuelas() {
        val ser = ListSerializer(SchoolDto.serializer())
        val s = parse("schools.json", "full", ser).single()
        assertEquals("zarzalejo", s.id)
        assertEquals("Madrid", s.location)
        assertEquals("Granito", s.rockType)
        assertEquals(40.5667, s.lat, 0.0)
        assertEquals("ES", s.country)
        assertTrue(s.hasKnownProcessionary)
        assertTrue(s.processionaryAlertActive)
        assertTrue(s.processionaryActiveNowSet)

        val n = parse("schools.json", "nulls", ser).single()
        assertNull(n.location); assertNull(n.region); assertNull(n.style)
        assertNull(n.rockType); assertNull(n.source); assertNull(n.country)
        assertFalse(n.hasKnownProcessionary)
        assertFalse(n.processionaryAlertActive)
        assertDtoKeysInGolden("schools.json", ser)
    }

    // ── GET schools/{id}/blocks ─────────────────────────────────────────────

    @Test fun bloquesDeUnaEscuela() {
        val ser = ListSerializer(BlockDto.serializer())
        val b = parse("blocks.json", "full", ser).single()
        assertEquals("block-9", b.id)
        assertEquals("BLOCK", b.type)
        assertEquals("2026-07-19T10:30:15", b.createdAt)
        assertEquals("zone-1", b.sectorBlockId)
        assertEquals("LINE", b.geometry)
        assertEquals("RTL", b.direction)
        assertEquals(listOf("BOULDER", "ROUTE"), b.sectorDisciplines)
        val l = b.lines.single()
        assertEquals("line-7", l.id)
        assertEquals("7a", l.grade)
        assertEquals("SIT", l.startType)
        assertEquals(4.5f, l.avgStars!!, 0f)
        assertEquals(3, l.myStars)
        assertEquals("directa", l.variant)
        assertEquals("topos/p15.jpg", b.faces.single().photoPath)
        assertEquals("line-7", b.faces.single().lines.single().id)

        val z = parse("blocks.json", "nulls", ser).single()
        assertEquals("ZONE", z.type)
        assertNull(z.photoPath); assertNull(z.description); assertNull(z.sectorBlockId)
        assertNull(z.path); assertNull(z.sectorDisciplines)
        val zl = z.lines.single()
        assertNull(zl.grade); assertNull(zl.startType); assertNull(zl.linePath)
        assertNull(zl.avgStars); assertNull(zl.myStars); assertNull(zl.variant)
        assertTrue(z.faces.single().lines.isEmpty())
        assertDtoKeysInGolden("blocks.json", ser)
    }

    // ── GET schools/{id}/forecast ───────────────────────────────────────────

    @Test fun previsionDeUnaEscuela() {
        val ser = ForecastDto.serializer()
        val f = parse("forecast.json", "full", ser)
        assertEquals("zarzalejo", f.schoolId)
        assertEquals("2026-11-06T10:00", f.current.time)
        assertEquals(12.5, f.current.temperature, 0.0)
        assertEquals(3.5, f.current.dewPoint!!, 0.0)
        assertEquals(72, f.current.score)
        assertFalse(f.current.dryRock)
        assertEquals("TEMPERATURA", f.current.factors.single().name)
        assertEquals(12, f.current.drying!!.dryingHours)
        assertEquals("Seca en ~12 h", f.current.drying!!.message)
        assertEquals(3, f.hours.single().weatherCode)
        assertEquals(16.5, f.days.single().tempMax, 0.0)
        assertEquals(2, f.bestDay!!.daysFromToday)
        assertEquals(85, f.bestWindow!!.avgScore)

        val n = parse("forecast.json", "nulls", ser)
        assertNull(n.current.dewPoint)
        assertNull(n.current.drying!!.dryingHours)
        assertNull(n.current.drying!!.message)
        assertNull(n.hours.single().dewPoint)
        assertTrue(n.days.isEmpty())
        assertNull(n.bestDay); assertNull(n.bestWindow)
        assertDtoKeysInGolden("forecast.json", ser)
    }

    // ── GET journal/me ──────────────────────────────────────────────────────

    @Test fun miDiario() {
        val ser = ListSerializer(JournalSessionDto.serializer())
        val j = parse("journal.json", "full", ser).single()
        assertEquals("j-1", j.id)
        assertEquals("Norte", j.sector)
        assertEquals("La ola", j.blockName)
        assertEquals("2026-07-19", j.date)
        assertEquals("2026-07-19T10:30:15", j.createdAt)
        assertEquals("line-7", j.lineId)
        assertEquals("DONE", j.status)
        assertTrue(j.aVista)
        assertFalse(j.alFlash)

        val n = parse("journal.json", "nulls", ser).single()
        assertNull(n.schoolId); assertNull(n.schoolName); assertNull(n.sector)
        assertNull(n.grade); assertNull(n.notes); assertNull(n.lineId)
        assertEquals("ROUTE", n.discipline)
        assertEquals("PROJECT", n.status)
        assertDtoKeysInGolden("journal.json", ser)
    }

    // ── GET me ──────────────────────────────────────────────────────────────

    @Test fun miPerfil() {
        val ser = PrivateProfileDto.serializer()
        val p = parse("me.json", "full", ser)
        assertEquals("uid123", p.uid)
        assertEquals("ana@example.com", p.email)
        assertEquals("ana_escaladora", p.username)
        assertEquals("7a", p.topGrade)
        assertTrue(p.isPublic)
        assertFalse(p.isAdmin)
        assertTrue(p.isPremium)
        assertEquals("WOMAN", p.gender)
        assertEquals("{\"cuerda\":true,\"cintas\":12}", p.gearJson)

        val n = parse("me.json", "nulls", ser)
        assertNull(n.email); assertNull(n.username); assertNull(n.displayName)
        assertNull(n.photoUrl); assertNull(n.bio); assertNull(n.topGrade)
        assertNull(n.gender); assertNull(n.gearJson)
        assertFalse(n.isPublic)
        assertTrue(n.isAdmin)
        assertDtoKeysInGolden("me.json", ser)
    }

    // ── GET meetups ─────────────────────────────────────────────────────────

    @Test fun listaDeQuedadas() {
        val ser = ListSerializer(MeetupDto.serializer())
        val m = parse("meetups.json", "full", ser).single()
        assertEquals("m-1", m.id)
        assertEquals("Zarzalejo", m.schoolName)
        assertEquals(40.5667, m.schoolLat!!, 0.0)
        assertEquals("OPEN", m.privacy)
        assertEquals(8, m.memberLimit)
        assertEquals(2, m.memberCount)
        assertEquals("conv-1", m.conversationId)
        assertEquals(listOf("2026-11-07", "2026-11-08"), m.days)
        assertEquals("2026-11-08", m.lastDay)
        assertEquals("2026-11-09T06:00:00", m.expiresAt)
        assertEquals("2026-11-01T12:30:45", m.createdAt)
        assertTrue(m.joined)
        val mem = m.members.single()
        assertEquals("uid123", mem.uid)
        assertEquals("{\"crashpads\":2}", mem.gearJson)

        val n = parse("meetups.json", "nulls", ser).single()
        assertNull(n.schoolName); assertNull(n.schoolLat); assertNull(n.schoolLon)
        assertNull(n.description); assertNull(n.discipline); assertNull(n.memberLimit)
        assertNull(n.photoUrl); assertNull(n.creatorUsername); assertNull(n.creatorPhotoUrl)
        assertFalse(n.joined)
        assertNull(n.members.single().username)
        assertNull(n.members.single().gearJson)
        assertDtoKeysInGolden("meetups.json", ser)
    }

    // ── GET feature-flags (público) ─────────────────────────────────────────

    @Test fun interruptoresDeFunciones() {
        val ser = FeatureFlagsDto.serializer()
        val f = parse("feature-flags.json", "full", ser)
        assertEquals(300, f.refreshAfterSeconds)
        assertFalse(f.flags.getValue("assistant").enabled)
        assertEquals("Asistente en mantenimiento", f.flags.getValue("assistant").message)
        assertTrue(f.flags.getValue("feed_publish").enabled)

        val n = parse("feature-flags.json", "nulls", ser)
        assertTrue(n.flags.getValue("guide_pdf").enabled)
        assertNull(n.flags.getValue("guide_pdf").message)
        assertDtoKeysInGolden("feature-flags.json", ser)
    }
}
