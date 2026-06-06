package com.meteomontana.android.theme

import androidx.compose.ui.graphics.Color
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests para el mapeo de grado climbing → color/estilo.
 *
 * La paleta debe coincidir EXACTAMENTE con la PWA (`js/utils/topo-draw.js`).
 * Cualquier cambio aquí significa que el admin y los usuarios verán colores
 * distintos en Android vs PWA — divergencia que debemos evitar.
 *
 * Paleta:
 *   ≤5c+   → blanco (texto interior oscuro)
 *   6a-6b+ → verde
 *   6c-6c+ → azul
 *   7a-7a+ → morado
 *   7b-7c+ → rojo
 *   ≥8a    → negro
 *   proyecto/sin grado → rosa punteado
 */
class GradeColorTest {

    @Test fun `grados blancos hasta 5c+`() {
        listOf("3", "4", "5", "5+", "5a", "5b", "5c", "5c+").forEach { g ->
            val style = gradeStyle(g)
            assertEquals("$g debería ser blanco", Color(0xFFFFFFFF), style.stroke)
            assertTrue("$g debería marcarse como dark (texto negro encima)", style.dark)
            assertFalse("$g no debe ser dashed", style.dashed)
        }
    }

    @Test fun `grados verdes de 6a a 6b+`() {
        val esperado = Color(0xFF1FA84E)
        listOf("6a", "6a+", "6b", "6b+").forEach { g ->
            assertEquals("$g debería ser verde", esperado, gradeStyle(g).stroke)
            assertFalse(gradeStyle(g).dark)
        }
    }

    @Test fun `grados azules de 6c a 6c+`() {
        val esperado = Color(0xFF1D6DD6)
        listOf("6c", "6c+").forEach { g ->
            assertEquals("$g debería ser azul", esperado, gradeStyle(g).stroke)
        }
    }

    @Test fun `grados morados de 7a a 7a+`() {
        val esperado = Color(0xFF8E3FBF)
        listOf("7a", "7a+").forEach { g ->
            assertEquals("$g debería ser morado", esperado, gradeStyle(g).stroke)
        }
    }

    @Test fun `grados rojos de 7b a 7c+`() {
        val esperado = Color(0xFFD62828)
        listOf("7b", "7b+", "7c", "7c+").forEach { g ->
            assertEquals("$g debería ser rojo", esperado, gradeStyle(g).stroke)
        }
    }

    @Test fun `grados negros de 8a en adelante`() {
        val esperado = Color(0xFF111111)
        listOf("8a", "8a+", "8b", "8b+", "8c", "8c+", "9a").forEach { g ->
            assertEquals("$g debería ser negro", esperado, gradeStyle(g).stroke)
        }
    }

    @Test fun `proyecto y null y vacio son rosa dashed`() {
        val rosa = Color(0xFFFF4FA3)
        listOf(null, "", "PROY", "proyecto", "?").forEach { g ->
            val style = gradeStyle(g)
            assertEquals("$g debería ser rosa", rosa, style.stroke)
            assertTrue("$g debería ser dashed", style.dashed)
        }
    }

    @Test fun `grados invalidos caen a rosa dashed`() {
        // Cualquier string que no matchee el regex cae a "proyecto".
        listOf("foo", "10a", "2a", "ABC").forEach { g ->
            val style = gradeStyle(g)
            assertEquals(Color(0xFFFF4FA3), style.stroke)
            assertTrue(style.dashed)
        }
    }

    @Test fun `colorForGrade es alias de gradeStyle stroke`() {
        listOf("6a", "7c", "PROY", null, "8a").forEach { g ->
            assertEquals(gradeStyle(g).stroke, colorForGrade(g))
        }
    }

    @Test fun `grados en mayusculas y minusculas dan mismo color`() {
        assertEquals(gradeStyle("6A").stroke, gradeStyle("6a").stroke)
        assertEquals(gradeStyle("7C+").stroke, gradeStyle("7c+").stroke)
        assertEquals(gradeStyle("proy").stroke, gradeStyle("PROY").stroke)
    }
}
