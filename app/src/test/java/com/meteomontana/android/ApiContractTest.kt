package com.meteomontana.android

import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.FeatureFlagsDto
import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.GymAscentStatsDto
import com.meteomontana.android.data.api.dto.GymDto
import com.meteomontana.android.data.api.dto.GymNearDto
import com.meteomontana.android.data.api.dto.GymReportDto
import com.meteomontana.android.data.api.dto.GymSessionDto
import com.meteomontana.android.data.api.dto.GymStatsDto
import com.meteomontana.android.data.api.dto.toDomain
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
 * journal/me, me, meetups, feature-flags, gyms, gyms/near, gyms/{id}/reports.
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
    private fun <T> assertDtoKeysInGolden(file: String, serializer: KSerializer<T>, sampleName: String = "full") {
        val dto = fullJson.encodeToJsonElement(serializer, parse(file, sampleName, serializer))
        val sample = json.parseToJsonElement(golden(file, sampleName))
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
        assertEquals(12L, m.gymId)
        assertEquals("Zarzalejo", m.placeName)
        assertEquals("6a", m.gradeMin); assertEquals("6c", m.gradeMax); assertEquals(2, m.partnersWanted)
        assertEquals(listOf("ROPE" to 1, "QUICKDRAWS" to 12), m.requiredGear.map { it.item to it.qty })
        assertEquals(listOf(true, false), m.gearCoverage.map { it.ok })
        assertEquals(12, m.gearCoverage[1].required); assertEquals(6, m.gearCoverage[1].provided)
        val mem = m.members.single()
        assertEquals("uid123", mem.uid)
        assertEquals("{\"crashpads\":2}", mem.gearJson)

        val n = parse("meetups.json", "nulls", ser).single()
        assertNull(n.schoolName); assertNull(n.schoolLat); assertNull(n.schoolLon)
        assertNull(n.description); assertNull(n.discipline); assertNull(n.memberLimit)
        assertNull(n.photoUrl); assertNull(n.creatorUsername); assertNull(n.creatorPhotoUrl)
        assertFalse(n.joined)
        assertNull(n.gymId); assertNull(n.placeName)
        assertNull(n.gradeMin); assertNull(n.gradeMax); assertNull(n.partnersWanted)
        assertTrue(n.requiredGear.isEmpty()); assertTrue(n.gearCoverage.isEmpty())
        assertNull(n.members.single().username)
        assertNull(n.members.single().gearJson)
        assertDtoKeysInGolden("meetups.json", ser)
    }

    // ── GET gyms, gyms/near, POST gyms/{id}/reports ─────────────────────────

    @Test fun catalogoDeRocodromos() {
        val ser = ListSerializer(GymDto.serializer())
        val g = parse("gyms.json", "full", ser).single()
        assertEquals(5L, g.id)
        assertEquals("COMMERCIAL", g.kind)
        assertEquals("BOTH", g.discipline)
        assertEquals(12.5, g.priceSingleEur!!, 0.0)
        assertEquals("2026-11-01", g.priceUpdatedAt)
        assertEquals(2, g.openingHours!!.size)
        assertEquals("MON-FRI", g.openingHours!![0].days)
        assertEquals(true, g.hasAutobelay)
        assertEquals("VERIFIED", g.verification)
        assertEquals("+34 645 511 702", g.phone)
        assertEquals("COLORS", g.scale!!.type)
        assertEquals(listOf("blanco", "verde"), g.scale!!.levels.map { it.name })
        assertEquals("#2E9E4F", g.scale!!.levels[1].hex)
        assertNull(g.scale!!.levels[0].gradeFrom); assertEquals("5c", g.scale!!.levels[1].gradeTo)

        val n = parse("gyms.json", "nulls", ser).single()
        assertNull(n.phone); assertNull(n.scale)
        assertNull(n.chain); assertNull(n.address); assertNull(n.website); assertNull(n.instagram)
        assertNull(n.openingHours); assertNull(n.priceSingleEur); assertNull(n.pricePassEur)
        assertNull(n.priceUpdatedAt); assertNull(n.hasAutobelay); assertNull(n.hasGearRental)
        assertNull(n.kidsFriendly); assertNull(n.beginnerFriendly); assertNull(n.source)
        assertTrue(n.outdoor)
        assertDtoKeysInGolden("gyms.json", ser)
    }

    @Test fun rocodromosCerca() {
        val ser = ListSerializer(GymNearDto.serializer())
        val g = parse("gyms-near.json", "full", ser).single()
        assertEquals(1.2, g.distanceKm, 0.0)
        assertEquals("Sputnik", g.chain)
        assertNull(parse("gyms-near.json", "nulls", ser).single().chain)
        assertDtoKeysInGolden("gyms-near.json", ser)
    }

    @Test fun reporteDeRocodromo() {
        val ser = GymReportDto.serializer()
        val r = parse("gym-report.json", "full", ser)
        assertEquals(listOf("IS_FREE", "CLOSED"), r.issues)
        assertEquals("FREE", r.proposed!!.kind)
        assertEquals("PENDING", r.status)
        val n = parse("gym-report.json", "nulls", ser)
        assertNull(n.gymId); assertNull(n.gymName); assertNull(n.comment); assertNull(n.photoUrl); assertNull(n.proposed)
        assertNull(n.scale); assertNull(r.scale)
        val sc = parse("gym-report.json", "scale", ser)
        assertEquals(listOf("SCALE"), sc.issues)
        assertEquals("COLORS", sc.scale!!.type)
        assertEquals(listOf("verde", "azul"), sc.scale!!.levels.map { it.name })
        assertNull(sc.scale!!.levels[1].gradeTo)
        assertDtoKeysInGolden("gym-report.json", ser)
    }

    // ── POST gyms/{id}/sessions/today, GET users/{u}/gym-stats ─────────────

    @Test fun entrenosDeRocodromo() {
        val s = parse("gym-sessions.json", "session", GymSessionDto.serializer())
        assertEquals(5L, s.gymId); assertEquals("2026-11-19", s.date); assertEquals("MANUAL", s.source)
        assertEquals(11L, s.id)
        assertEquals(listOf("verde" to 5, "azul" to 2), s.ascents.map { it.key to it.count })
        assertTrue(parse("gym-sessions.json", "sessionEmpty", GymSessionDto.serializer()).ascents.isEmpty())
        assertDtoKeysInGolden("gym-sessions.json", GymSessionDto.serializer(), "session")
        val a = parse("gym-sessions.json", "ascentStats", GymAscentStatsDto.serializer())
        assertEquals("WEEK", a.period); assertEquals("2026-11-16", a.from); assertEquals("2026-11-22", a.to)
        assertEquals(2, a.sessions); assertEquals(7, a.total)
        assertEquals(listOf("verde" to 5, "azul" to 2), a.items.map { it.key to it.count })
        assertDtoKeysInGolden("gym-sessions.json", GymAscentStatsDto.serializer(), "ascentStats")
        val st = parse("gym-sessions.json", "stats", GymStatsDto.serializer())
        assertEquals(31, st.sessionsThisYear); assertEquals("Sputnik Madrid", st.topGym!!.name); assertEquals(3, st.streakWeeks)
        assertNull(parse("gym-sessions.json", "statsEmpty", GymStatsDto.serializer()).topGym)
        assertDtoKeysInGolden("gym-sessions.json", GymStatsDto.serializer(), "stats")
        val sum = parse("gym-sessions.json", "summary", com.meteomontana.android.data.api.dto.GymSummaryDto.serializer())
        assertEquals(6, sum.ascents); assertEquals(5L, sum.homeGym!!.id); assertEquals("Chango", sum.homeGym!!.name); assertEquals("Madrid", sum.homeGym!!.city)
        assertEquals("Chango", sum.toDomain().homeGym!!.name)
        assertNull(parse("gym-sessions.json", "summaryEmpty", com.meteomontana.android.data.api.dto.GymSummaryDto.serializer()).homeGym)
        assertEquals("BOARDS,BLOCKS,ROUTES", sum.ascentOrder); assertEquals("BOARDS,BLOCKS,ROUTES", sum.toDomain().ascentOrder)
        assertNull(parse("gym-sessions.json", "summaryEmpty", com.meteomontana.android.data.api.dto.GymSummaryDto.serializer()).ascentOrder)
        assertEquals("5,3,9", sum.gymOrder); assertEquals("5,3,9", sum.toDomain().gymOrder)
        assertNull(parse("gym-sessions.json", "summaryEmpty", com.meteomontana.android.data.api.dto.GymSummaryDto.serializer()).gymOrder)
        assertEquals(2, sum.toDomain().byGym.first().days.first().items.size)
        val feedCount = parse("feed-count.json", "count", com.meteomontana.android.data.api.dto.FeedCountDto.serializer())
        assertEquals(7L, feedCount.count)
        assertDtoKeysInGolden("feed-count.json", com.meteomontana.android.data.api.dto.FeedCountDto.serializer(), "count")
        assertDtoKeysInGolden("gym-sessions.json", com.meteomontana.android.data.api.dto.GymSummaryDto.serializer(), "summary")
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

    // ── GET title-effects (público) ─────────────────────────────────────────

    @Test fun catalogoDeEfectos() {
        val ser = com.meteomontana.android.data.api.dto.TitleEffectsDto.serializer()
        val f = parse("title-effects.json", "full", ser)
        assertEquals(3600, f.refreshAfterSeconds)
        val lola = f.effects.single()
        assertEquals("ORBIT", lola.orientation)
        assertEquals("effect_van1", lola.sprites.single().asset)
        assertEquals(1.5f, lola.motion.orbit)

        val n = parse("title-effects.json", "nulls", ser)
        assertNull(n.effects.single().sprites.single().url)
        assertTrue(n.effects.single().motion.staticAngles.isEmpty())
        assertDtoKeysInGolden("title-effects.json", ser)
    }
}
