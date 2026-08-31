package com.meteomontana.android.data.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La espera ACOTADA del token de Firebase.
 *
 * Contexto del bug (2026-08-13/16): al arrancar en frío, el SDK de Firebase aún
 * se está inicializando; como se le pedía el token en cada petición SIN límite,
 * las llamadas se quedaban paradas antes de salir — incluso las de rutas
 * públicas, que no necesitan token. Síntoma: entras en una escuela y no carga
 * ni un bloque, y al salir y volver a entrar va bien.
 *
 * Lo que se fija aquí: la espera termina, y quedarse sin token **degrada** la
 * petición (sale sin identificar) en vez de tumbarla.
 */
class TokenWaitTest {

    @Test
    fun `devuelve el token cuando llega a tiempo`() = runTest {
        val token = tokenOrNull { "abc123" }

        assertEquals("abc123", token)
    }

    @Test
    fun `no espera indefinidamente si Firebase tarda`() = runTest {
        // Firebase arrancando: tarda MUCHO más que la paciencia configurada.
        val token = tokenOrNull {
            delay(TOKEN_WAIT_MS * 10)
            "llega-tarde"
        }

        // Sale sin token: las rutas públicas se sirven igual y la pantalla pinta.
        assertNull(token)
    }

    @Test
    fun `un provider que falla no tumba la peticion`() = runTest {
        val token = tokenOrNull { error("Firebase no inicializado") }

        assertNull(token)
    }

    @Test
    fun `un usuario sin sesion simplemente no tiene token`() = runTest {
        val token = tokenOrNull { null }

        assertNull(token)
    }
}
