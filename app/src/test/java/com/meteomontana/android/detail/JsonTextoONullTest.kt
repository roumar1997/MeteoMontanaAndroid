package com.meteomontana.android.detail

import com.meteomontana.android.ui.screens.detail.textoONull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El bug que costó tres intentos arreglar (2026-08-25): en Android,
 * `optString` devuelve la CADENA "null" cuando el valor guardado es
 * `JSONObject.NULL`. En los borradores eso convertía una foto ausente en la
 * ruta ".../borradores/null" — un Uri no nulo que apunta a nada, así que la
 * pantalla nunca caía al respaldo y la piedra se abría sin foto.
 *
 * OJO, LECCIÓN APARTE: estos tests NO pueden reproducir el bug original. El
 * `org.json:json` de Maven que usan los tests JVM devuelve "" en ese caso,
 * mientras que la implementación que Android trae de serie devuelve "null".
 * Dos comportamientos distintos con la misma API — por eso el fallo solo se
 * veía en el móvil y ningún test lo habría cazado. Lo que sí fijan estos
 * tests es que el helper devuelve null en TODAS las formas posibles del
 * caso, sea cual sea la implementación por debajo.
 */
class JsonTextoONullTest {

    @Test fun `JSON null da null`() {
        val o = JSONObject().apply { put("foto", JSONObject.NULL) }
        assertNull(o.textoONull("foto"))
    }

    @Test fun `clave ausente da null`() {
        assertNull(JSONObject().textoONull("noExiste"))
    }

    @Test fun `cadena vacia da null`() {
        val o = JSONObject().apply { put("foto", "") }
        assertNull(o.textoONull("foto"))
    }

    @Test fun `solo espacios da null`() {
        val o = JSONObject().apply { put("foto", "   ") }
        assertNull(o.textoONull("foto"))
    }

    @Test fun `un valor real se devuelve tal cual`() {
        val o = JSONObject().apply { put("foto", "piedra-cara0.jpg") }
        assertEquals("piedra-cara0.jpg", o.textoONull("foto"))
    }

    @Test fun `el texto literal null tambien se descarta`() {
        // Borradores viejos ya guardados en móviles reales pueden tener la
        // cadena "null" escrita de antes: no debe resucitar el bug.
        val o = JSONObject().apply { put("foto", "null") }
        assertNull(o.textoONull("foto"))
    }
}
