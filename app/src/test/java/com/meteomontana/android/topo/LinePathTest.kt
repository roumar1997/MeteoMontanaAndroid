package com.meteomontana.android.topo

import androidx.compose.ui.geometry.Offset
import com.meteomontana.android.ui.screens.topo.LineStroke
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.ui.screens.topo.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests para la serialización de líneas de topo (puntos normalizados 0..1).
 *
 * El formato JSON es el contrato entre la app y el backend
 * (`block_lines.line_path`). Si cambia, las líneas dibujadas se pierden o
 * se ven mal posicionadas.
 */
class LinePathTest {

    @Test fun `linea vacia serializa a array vacio`() {
        val stroke = LineStroke(emptyList())
        assertEquals("[]", stroke.toJson())
    }

    @Test fun `un punto serializa correctamente`() {
        val stroke = LineStroke(listOf(Offset(0.5f, 0.5f)))
        val json = stroke.toJson()
        // No asumimos formato exacto (depende del orden de llaves en JSON);
        // verificamos contenido haciendo round-trip.
        val back = parseLineStroke(json)
        assertEquals(1, back.points.size)
        assertEquals(0.5f, back.points[0].x, 0.0001f)
        assertEquals(0.5f, back.points[0].y, 0.0001f)
    }

    @Test fun `round trip preserva muchos puntos`() {
        val original = listOf(
            Offset(0.1f, 0.2f),
            Offset(0.3f, 0.4f),
            Offset(0.5f, 0.6f),
            Offset(0.7f, 0.8f),
            Offset(0.9f, 1.0f)
        )
        val json = LineStroke(original).toJson()
        val back = parseLineStroke(json)
        assertEquals(original.size, back.points.size)
        original.forEachIndexed { i, p ->
            assertEquals("x[$i]", p.x, back.points[i].x, 0.0001f)
            assertEquals("y[$i]", p.y, back.points[i].y, 0.0001f)
        }
    }

    @Test fun `parseLineStroke con null devuelve vacio`() {
        assertEquals(0, parseLineStroke(null).points.size)
    }

    @Test fun `parseLineStroke con string vacio devuelve vacio`() {
        assertEquals(0, parseLineStroke("").points.size)
        assertEquals(0, parseLineStroke("   ").points.size)
    }

    @Test fun `parseLineStroke con JSON invalido devuelve vacio sin throw`() {
        assertEquals(0, parseLineStroke("esto no es JSON").points.size)
        assertEquals(0, parseLineStroke("{\"x\": 0.1}").points.size) // no es array
        assertEquals(0, parseLineStroke("[{\"foo\": \"bar\"}]").points.size) // sin x/y
    }

    @Test fun `puntos en los bordes 0 y 1 se preservan`() {
        val original = listOf(Offset(0f, 0f), Offset(1f, 1f), Offset(0f, 1f), Offset(1f, 0f))
        val back = parseLineStroke(LineStroke(original).toJson())
        assertEquals(4, back.points.size)
        assertEquals(0f, back.points[0].x, 0.0001f)
        assertEquals(1f, back.points[1].x, 0.0001f)
    }

    @Test fun `puntos fuera del rango 0_1 se preservan literales (no se clampean)`() {
        // El parseo es transparente. Si en algún caso por error se guardan
        // coordenadas fuera de rango, no se silencian.
        val original = listOf(Offset(-0.1f, 1.5f), Offset(2f, -0.5f))
        val back = parseLineStroke(LineStroke(original).toJson())
        assertEquals(-0.1f, back.points[0].x, 0.0001f)
        assertEquals(1.5f, back.points[0].y, 0.0001f)
        assertEquals(2f, back.points[1].x, 0.0001f)
        assertEquals(-0.5f, back.points[1].y, 0.0001f)
    }

    @Test fun `el JSON contiene x e y como claves`() {
        val stroke = LineStroke(listOf(Offset(0.42f, 0.84f)))
        val json = stroke.toJson()
        assertTrue("debe contener clave x", json.contains("\"x\""))
        assertTrue("debe contener clave y", json.contains("\"y\""))
    }
}
