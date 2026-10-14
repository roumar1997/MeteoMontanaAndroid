package com.meteomontana.android.ui.assistant

import com.meteomontana.android.domain.model.AssistantClimbing
import com.meteomontana.android.domain.model.AssistantHourPoint
import com.meteomontana.android.domain.model.AssistantNow
import com.meteomontana.android.domain.model.AssistantRain
import com.meteomontana.android.domain.model.AssistantStatus
import com.meteomontana.android.domain.model.AssistantUnderstood
import com.meteomontana.android.domain.model.AssistantWeather
import com.meteomontana.android.domain.model.LineSearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPresenterTest {

    private val labels = object : AssistantLabels {
        override fun rock(raw: String) = raw
        override fun region(raw: String) = raw
        override fun aspect(code: String) = code
    }
    private val es = AssistantPresenter("es", labels)
    private val en = AssistantPresenter("en", labels)

    private fun hit(school: String, sector: String?, line: String, rating: Double? = null, votes: Int? = null) = LineSearchHit(
        schoolId = school, schoolName = "Escuela $school", blockId = "b-$line", blockName = "Piedra $line", lineId = "l-$line",
        lineName = line, grade = "7a", sectorName = sector, photoPath = null, linePath = null, startType = null,
        rating = rating, ratingCount = votes
    )

    // ── Estados e idioma ──────────────────────────────────────────────────────────────────────────────────────────

    @Test fun `los estados con datos no llevan texto`() {
        assertNull(es.message(AssistantStatus.OK))
        assertNull(es.message(AssistantStatus.NEEDS_INPUT))
    }

    @Test fun `un estado sin datos se dice en el idioma de la app`() {
        assertTrue(es.message(AssistantStatus.BUSY)!!.startsWith("Hay muchas preguntas"))
        assertTrue(en.message(AssistantStatus.BUSY)!!.startsWith("Lots of questions"))
    }

    // ── Fechas ────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun `si el asistente no esta activado para la cuenta se dice en el idioma de la app`() {
        assertEquals("El asistente no está activado para tu cuenta.", es.message(AssistantStatus.NOT_ENABLED))
        assertEquals("The assistant isn't turned on for your account.", en.message(AssistantStatus.NOT_ENABLED))
    }

    @Test fun `un dia y un tramo`() {
        assertEquals("6 dic", es.dateRange("2026-12-06", null))
        assertEquals("6 dic", es.dateRange("2026-12-06", "2026-12-06"))
        assertEquals("6 – 8 dic", es.dateRange("2026-12-06", "2026-12-08"))
        assertEquals("30 nov – 2 dic", es.dateRange("2026-11-30", "2026-12-02"))
        assertEquals("6 – 8 Dec", en.dateRange("2026-12-06", "2026-12-08"))
    }

    @Test fun `el dia de la semana abreviado`() {
        assertEquals("DOM 6", es.dayLabel("2026-12-06"))   // 6 dic 2026 es domingo
        assertEquals("MIÉ 9", es.dayLabel("2026-12-09"))
        assertEquals("SUN 6", en.dayLabel("2026-12-06"))
    }

    // ── Chips ─────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun `los chips resumen lo entendido`() {
        val u = AssistantUnderstood(intent = "SEARCH", discipline = "BOULDER", gradeMin = "6A", gradeMax = "7A",
            maxDistanceKm = 50.0, useMyLocation = true, notDone = true)
        assertEquals(listOf("Albarracín", "Bloque", "6A – 7A", "< 50 km", "Mi ubicación", "Sin hacer"), es.chips(u, "Albarracín", null))
    }

    @Test fun `un grado solo es un limite`() {
        assertEquals(listOf("≥ 7A"), es.chips(AssistantUnderstood(intent = "S", gradeMin = "7A"), null, null))
        assertEquals(listOf("≤ 6C"), es.chips(AssistantUnderstood(intent = "S", gradeMax = "6C"), null, null))
        assertEquals(listOf("7A"), es.chips(AssistantUnderstood(intent = "S", gradeMin = "7A", gradeMax = "7A"), null, null))
    }

    // ── Resultados agrupados ──────────────────────────────────────────────────────────────────────────────────────

    @Test fun `agrupa por escuela conservando el orden y por sector`() {
        val hits = listOf(hit("A", "Techos", "1"), hit("B", null, "2"), hit("A", "Techos", "3"), hit("A", "Fuente", "4"))
        val groups = es.groupAllHits(hits)
        assertEquals(listOf("A", "B"), groups.map { it.id })
        assertEquals(3, groups[0].total)
        assertEquals(listOf("Techos", "Fuente"), groups[0].sectors.map { it.name })   // el que más tiene, primero
    }

    @Test fun `sin sector va siempre al final`() {
        val sectors = es.bySector(listOf(hit("A", null, "1"), hit("A", null, "2"), hit("A", null, "3"), hit("A", "Z", "4")))
        assertEquals(listOf<String?>("Z", null), sectors.map { it.name })
    }

    @Test fun `con pocos resultados empiezan todas desplegadas y con muchos solo la primera`() {
        val few = es.groupAllHits(listOf(hit("A", null, "1"), hit("B", null, "2")))
        assertEquals(setOf("A", "B"), es.initiallyExpanded(few))
        val many = es.groupAllHits((1..30).map { hit(if (it % 2 == 0) "A" else "B", null, "$it") })
        assertEquals(setOf("B"), es.initiallyExpanded(many))
    }

    @Test fun `con varias escuelas se recorta cada una`() {
        val hits = (1..10).map { hit("A", null, "a$it") } + (1..10).map { hit("B", null, "b$it") }
        val groups = es.groupHits(hits)
        assertEquals(listOf(4, 4), groups.map { g -> g.sectors.sumOf { it.hits.size } })
        assertEquals(listOf(10, 10), groups.map { it.total })
    }

    // ── Estrellas ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun `ordena por estrellas, luego votos y luego llegada, y descarta las sin votos`() {
        val a = hit("A", null, "a", 4.5, 2)
        val b = hit("A", null, "b", 4.5, 8)
        val c = hit("A", null, "c", 5.0, 1)
        val d = hit("A", null, "d")
        assertEquals(listOf("c", "b", "a"), es.rankByRating(listOf(a, b, c, d), null).map { it.lineName })
    }

    @Test fun `el minimo de estrellas filtra`() {
        val ranked = es.rankByRating(listOf(hit("A", null, "a", 3.9, 5), hit("A", null, "b", 4.0, 5)), minStars = 4)
        assertEquals(listOf("b"), ranked.map { it.lineName })
    }

    @Test fun `una decima con coma en espanol y punto en ingles`() {
        assertEquals("4,5", es.oneDecimal(4.5))
        assertEquals("4.5", en.oneDecimal(4.5))
        assertEquals("★ 4,5 (8)", es.starsLabel(4.5, 8))
    }

    // ── El tiempo ─────────────────────────────────────────────────────────────────────────────────────────────────

    private fun weather(topic: String, rain: AssistantRain) = AssistantWeather(
        placeName = "Zarzalejo", myLocation = false, topic = topic,
        now = AssistantNow(18.0, 55.0, 10.0, 0.0, 0, 30, 9.0),
        hours = listOf(AssistantHourPoint("2026-10-08T15:00", 18.0, 0.0, 0, 10.0), AssistantHourPoint("2026-10-08T16:00", 20.0, 0.0, 0, 30.0)),
        rain = rain,
        climbing = AssistantClimbing(62, "Bueno", false, null, "2026-10-08T18:00", "2026-10-08T21:00")
    )

    @Test fun `sin lluvia hasta una hora`() {
        val r = AssistantRain(expected = false, startsInHours = null, totalMm = 0.0, maxProbability = 0, hoursChecked = 8,
            until = "2026-10-08T23:00")
        assertEquals("No, no se espera lluvia hasta las 23h. La probabilidad máxima es del 0 %.", es.rainHeadline(weather("RAIN", r)))
    }

    @Test fun `va a llover a una hora`() {
        val r = AssistantRain(expected = true, startsInHours = 2, totalMm = 1.5, maxProbability = 80, hoursChecked = 8,
            startsAt = "2026-10-08T17:00", until = "2026-10-08T23:00")
        val text = es.rainHeadline(weather("RAIN", r))
        assertTrue(text.startsWith("Sí, va a llover: empezará sobre las 17h"))
        assertTrue(text.contains("1,5 mm"))
    }

    @Test fun `lloviendo ahora`() {
        val r = AssistantRain(expected = true, startsInHours = 0, totalMm = 2.0, maxProbability = 90, hoursChecked = 6)
        assertTrue(es.rainHeadline(weather("RAIN", r)).startsWith("Sí, está lloviendo ahora"))
    }

    @Test fun `el viento se califica`() {
        assertEquals("flojo", es.windLabel(5.0))
        assertEquals("moderado", es.windLabel(15.0))
        assertEquals("fuerte", es.windLabel(30.0))
        assertEquals("very strong", en.windLabel(60.0))
    }

    @Test fun `la frase del viento usa el maximo de las horas`() {
        val r = AssistantRain(false, null, 0.0, 0, 8)
        val text = es.weatherHeadline(weather("WIND", r))
        assertTrue(text.contains("viento ahora 10 km/h (flojo)"))
        assertTrue(text.contains("30 km/h"))
    }

    @Test fun `la hora se queda con dos cifras`() {
        assertEquals("15h", es.hourLabel("2026-10-08T15:00"))
    }

    @Test fun `la barra de lluvia llena con 3 mm y siempre se ve un poco`() {
        assertEquals(0f, es.rainBarHeight(0.0, 34f), 0.001f)
        assertEquals(34f, es.rainBarHeight(3.0, 34f), 0.001f)
        assertEquals(34f, es.rainBarHeight(9.0, 34f), 0.001f)
        assertEquals(3f, es.rainBarHeight(0.1, 34f), 0.001f)
    }

    // ── Guardado ──────────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun `no avisa si hace menos de media hora y avisa en horas despues`() {
        val now = 10_000_000_000L
        assertNull(es.savedNote(now - 10 * 60_000, now))
        assertNotNull(es.savedNote(now - 3 * 3_600_000, now))
        assertEquals("Guardado hace 3 h. El tiempo puede haber cambiado.", es.savedNote(now - 3 * 3_600_000, now))
    }
}
