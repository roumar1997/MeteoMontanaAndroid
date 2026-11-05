package com.meteomontana.android.ui.assistant

import com.meteomontana.android.data.vision.StoneImageEmbedder
import com.meteomontana.android.ui.assistant.photo.StoneCandidate
import com.meteomontana.android.ui.assistant.photo.StonePhotoMatcher
import com.meteomontana.android.ui.assistant.photo.StonePhotoWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La parte de «¿Qué piedra es esta?» que no necesita móvil: cómo se puntúa, cuándo se está seguro y cómo se dice. */
class StonePhotoMatchingTest {

    private fun candidate(name: String, meters: Double, similarity: Double?) = StoneCandidate(
        blockId = name, blockName = name, schoolId = "s", schoolName = "La Pedriza",
        photoUrl = null, lineId = "l-$name", meters = meters, similarity = similarity
    )

    @Test fun `sin ubicacion solo cuenta el parecido`() {
        assertEquals(0.8, StonePhotoMatcher.score(candidate("a", -1.0, 0.8)), 1e-9)
        assertEquals(0.0, StonePhotoMatcher.score(candidate("a", -1.0, null)), 1e-9)
    }

    @Test fun `con ubicacion manda el parecido y la cercania desempata`() {
        val near = StonePhotoMatcher.score(candidate("near", 10.0, 0.6))
        val far = StonePhotoMatcher.score(candidate("far", 110.0, 0.6))
        assertTrue(near > far)
        // Mucho más parecida gana a otra que está más cerca.
        assertTrue(StonePhotoMatcher.score(candidate("sim", 100.0, 0.9)) > StonePhotoMatcher.score(candidate("close", 5.0, 0.4)))
    }

    @Test fun `una piedra sin foto puntua solo por cercania y por debajo de una parecida`() {
        val noPhoto = StonePhotoMatcher.score(candidate("a", 0.0, null))
        assertEquals(0.35, noPhoto, 1e-9)
        assertTrue(StonePhotoMatcher.score(candidate("b", 60.0, 0.5)) > noPhoto)
    }

    @Test fun `esta seguro si es la unica o saca 015 a la segunda`() {
        assertTrue(StonePhotoMatcher.isSure(listOf(candidate("a", 20.0, 0.5))))
        val clear = listOf(candidate("a", 20.0, 0.9), candidate("b", 20.0, 0.5))
        assertTrue(StonePhotoMatcher.isSure(clear))
        val close = listOf(candidate("a", 20.0, 0.6), candidate("b", 20.0, 0.55))
        assertFalse(StonePhotoMatcher.isSure(close))
        assertFalse(StonePhotoMatcher.isSure(emptyList()))
    }

    @Test fun `debil si ni la mejor se parece`() {
        assertTrue(StonePhotoMatcher.isWeak(listOf(candidate("a", 20.0, 0.2))))
        assertTrue(StonePhotoMatcher.isWeak(listOf(candidate("a", 20.0, null))))
        assertFalse(StonePhotoMatcher.isWeak(listOf(candidate("a", 20.0, 0.7))))
    }

    @Test fun `el parecido se reescala del suelo del coseno a 0-1`() {
        val v = floatArrayOf(1f, 0f, 0f)
        assertEquals(1.0, StoneImageEmbedder.similarity(v, v)!!, 1e-6)
        // Perpendiculares (coseno 0) y por debajo del suelo: nada que ver.
        assertEquals(0.0, StoneImageEmbedder.similarity(v, floatArrayOf(0f, 1f, 0f))!!, 1e-9)
        // Tamaños distintos o vacíos no se comparan.
        assertNull(StoneImageEmbedder.similarity(v, floatArrayOf(1f, 0f)))
        assertNull(StoneImageEmbedder.similarity(floatArrayOf(), floatArrayOf()))
    }

    @Test fun `el detalle dice metros y parecido, y sin ubicacion solo el parecido`() {
        val es = StonePhotoWords("es")
        assertEquals("a 12 m · se parece un 74 %", es.detail(candidate("a", 12.4, 0.74)))
        assertEquals("se parece un 74 %", es.detail(candidate("a", -1.0, 0.74)))
        assertEquals("a 5 m · sin foto para comparar", es.detail(candidate("a", 5.0, null)))
        assertEquals("12 m away · 74 % similar", StonePhotoWords("en").detail(candidate("a", 12.0, 0.74)))
    }

    @Test fun `la presentacion cambia segun este seguro o sea debil`() {
        val es = StonePhotoWords("es")
        assertEquals("Creo que es esta, en La Pedriza:", es.intro(sure = true, approximate = false, school = "La Pedriza", weak = false))
        assertEquals("Puede ser una de estas de La Pedriza:", es.intro(false, false, "La Pedriza", false))
        assertTrue(es.intro(true, false, "La Pedriza", weak = true).startsWith("No se parece mucho"))
        assertTrue(es.intro(true, true, "La Pedriza", false).endsWith("uso dónde estás ahora)"))
    }

    @Test fun `modo de voz decide cuando se lee`() {
        assertTrue(AssistantSpeechMode.WHEN_SPOKEN.shouldSpeak(spokenQuestion = true))
        assertFalse(AssistantSpeechMode.WHEN_SPOKEN.shouldSpeak(spokenQuestion = false))
        assertTrue(AssistantSpeechMode.ALWAYS.shouldSpeak(false))
        assertFalse(AssistantSpeechMode.NEVER.shouldSpeak(true))
    }

    @Test fun `lo que se lee incluye el nombre de la primera piedra de la foto`() {
        val m = AssistantMessage(1, false, "Creo que es esta, en La Pedriza:", photoMatches = listOf(candidate("El Hongo", 10.0, 0.9)))
        assertEquals("Creo que es esta, en La Pedriza: El Hongo.", spokenText(m, "es"))
    }
}
