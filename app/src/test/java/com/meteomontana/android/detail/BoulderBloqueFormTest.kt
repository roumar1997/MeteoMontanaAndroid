package com.meteomontana.android.detail

import androidx.compose.ui.geometry.Offset
import com.meteomontana.android.ui.screens.detail.BOULDER_GRADES
import com.meteomontana.android.ui.screens.detail.BoulderBloqueForm
import com.meteomontana.android.ui.screens.detail.toBloquesJson
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de serialización de bloques de una propuesta BOULDER.
 *
 * Asegura que `toBloquesJson()` produce el formato JSON que el backend
 * espera, y que el round-trip (serializar → deserializar) preserva los
 * datos.
 */
class BoulderBloqueFormTest {

    @Test fun `lista vacia produce array JSON vacio`() {
        val json = emptyList<BoulderBloqueForm>().toBloquesJson()
        val arr = JSONArray(json)
        assertEquals(0, arr.length())
    }

    @Test fun `un bloque con todos los campos serializa correctamente`() {
        val bloque = BoulderBloqueForm(
            name = "Directa",
            grade = "6c+",
            startType = "SIT",
            linePath = listOf(Offset(0.1f, 0.2f), Offset(0.5f, 0.5f), Offset(0.9f, 0.8f))
        )
        val json = listOf(bloque).toBloquesJson()
        val arr = JSONArray(json)

        assertEquals(1, arr.length())
        val obj = arr.getJSONObject(0)
        assertEquals(0, obj.getInt("idx"))
        assertEquals("Directa", obj.getString("name"))
        assertEquals("6c+", obj.getString("grade"))
        assertEquals("SIT", obj.getString("startType"))

        // linePath es un string JSON anidado: [{"x":0.1,"y":0.2}, ...]
        val linePath = JSONArray(obj.getString("linePath"))
        assertEquals(3, linePath.length())
        assertEquals(0.1, linePath.getJSONObject(0).getDouble("x"), 0.0001)
        assertEquals(0.2, linePath.getJSONObject(0).getDouble("y"), 0.0001)
    }

    @Test fun `bloque sin grade ni startType serializa con null JSON`() {
        val bloque = BoulderBloqueForm(name = "Sin grado")
        val json = listOf(bloque).toBloquesJson()
        val arr = JSONArray(json)
        val obj = arr.getJSONObject(0)
        assertTrue("grade debe ser null JSON", obj.isNull("grade"))
        assertTrue("startType debe ser null JSON", obj.isNull("startType"))
    }

    @Test fun `varios bloques mantienen el indice idx`() {
        val bloques = listOf(
            BoulderBloqueForm(name = "A"),
            BoulderBloqueForm(name = "B"),
            BoulderBloqueForm(name = "C")
        )
        val arr = JSONArray(bloques.toBloquesJson())
        assertEquals(3, arr.length())
        assertEquals(0, arr.getJSONObject(0).getInt("idx"))
        assertEquals(1, arr.getJSONObject(1).getInt("idx"))
        assertEquals(2, arr.getJSONObject(2).getInt("idx"))
    }

    @Test fun `bloque con linePath vacio produce array vacio en JSON`() {
        val bloque = BoulderBloqueForm(name = "Sin dibujar")
        val arr = JSONArray(listOf(bloque).toBloquesJson())
        val linePath = JSONArray(arr.getJSONObject(0).getString("linePath"))
        assertEquals(0, linePath.length())
    }

    @Test fun `BOULDER_GRADES contiene los grados esperados de la PWA`() {
        // Si esta lista cambia, los chips de grado en la UI cambian y se
        // rompe la paridad con la PWA.
        assertEquals(24, BOULDER_GRADES.size)
        assertTrue("debe contener 3 (mínimo)", BOULDER_GRADES.contains("3"))
        assertTrue("debe contener 9a (máximo realista)", BOULDER_GRADES.contains("9a"))
        assertTrue("debe contener PROY", BOULDER_GRADES.contains("PROY"))
        assertTrue("debe contener 6a, 6b, 6c con sus +", BOULDER_GRADES.containsAll(
            listOf("6a", "6a+", "6b", "6b+", "6c", "6c+")
        ))
        assertTrue("debe contener 7a-7c con sus +", BOULDER_GRADES.containsAll(
            listOf("7a", "7a+", "7b", "7b+", "7c", "7c+")
        ))
        assertTrue("debe contener 8a-8c con sus +", BOULDER_GRADES.containsAll(
            listOf("8a", "8a+", "8b", "8b+", "8c", "8c+")
        ))
    }
}
