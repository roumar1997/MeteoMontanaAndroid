package com.meteomontana.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voto doble y filtros de grado sueltos (espejo de `nextVote`, `gradeParts` y `gradeMatches` de iOS). */
class GradeVoteTest {

    @Test fun losGradosSueltosDeUnGradoDoble() {
        assertEquals(listOf("6b+", "6c"), gradeParts("6B+/6C"))
        assertEquals(listOf("6b"), gradeParts(" 6b "))
        assertEquals(emptyList<String>(), gradeParts(null))
        assertEquals(emptyList<String>(), gradeParts(""))
    }

    @Test fun unGradoDobleCuadraConCualquieraDeSusDosGrados() {
        assertTrue(gradeMatches("6b+/6c", setOf("6b+")))
        assertTrue(gradeMatches("6b+/6c", setOf("6c")))
        assertFalse(gradeMatches("6b+/6c", setOf("6b")))
        assertTrue(gradeMatches("6b", emptySet()))
        assertTrue(gradeMatches(null, emptySet()))
        assertFalse(gradeMatches(null, setOf("6b")))
    }

    @Test fun pulsarUnGradoSinVotoLoElige() {
        assertEquals("6b", nextGradeVote("6b", null))
    }

    @Test fun pulsarElContiguoHaceElPar_deMenorAMayor() {
        assertEquals("6b/6b+", nextGradeVote("6b+", "6b"))
        assertEquals("6b/6b+", nextGradeVote("6b", "6b+"))
        assertEquals("6c+/7a", nextGradeVote("7a", "6c+"))   // el salto de número también es contiguo
    }

    @Test fun pulsarUnoDeLosDosQuitaEseYDejaElOtro() {
        assertEquals("6b+", nextGradeVote("6b", "6b/6b+"))
        assertEquals("6b", nextGradeVote("6b+", "6b/6b+"))
    }

    @Test fun pulsarUnGradoNoContiguoSustituyeAlElegido() {
        assertEquals("6c", nextGradeVote("6c", "6b"))
        assertEquals("7a", nextGradeVote("7a", "6b/6b+"))
    }

    @Test fun pulsarElMismoGradoSueltoLoDejaIgual() {
        assertEquals("6b", nextGradeVote("6b", "6b"))
    }
}
