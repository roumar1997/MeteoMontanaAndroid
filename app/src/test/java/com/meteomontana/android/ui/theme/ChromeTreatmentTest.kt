package com.meteomontana.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Qué armazón puede pintar cada móvil.
 *
 * El caso que justifica estos tests es el Redmi Note 8 de Rodrigo: Android 11,
 * API 30. El desenfoque real de Android llegó en la 12 (API 31), así que ahí
 * **no existe** — y si se le pidiera igualmente, la barra se quedaría sin
 * fondo o la app petaría. Tiene que degradar a sólido sola.
 *
 * Es también el único trozo de este trabajo con tests: que la barra quede
 * bonita no lo dice ningún assert, lo dice el ojo de Rodrigo.
 */
class ChromeTreatmentTest {

    @Test
    fun elRedmiDeRodrigoSeQuedaEnSolido() {
        // Android 11 = API 30. Es el móvil con el que se prueba que nada se
        // rompe en la gama antigua.
        assertEquals(ChromeTreatment.SOLIDO,
            ChromeTreatment.paraApi(30, ChromeTreatment.ESMERILADO))
        assertEquals(ChromeTreatment.SOLIDO,
            ChromeTreatment.paraApi(30, ChromeTreatment.CRISTAL))
    }

    @Test
    fun elMinimoQueSoportaLaApp() {
        // minSdk 26: el más viejo que puede instalarla. Tampoco puede reventar.
        assertEquals(ChromeTreatment.SOLIDO,
            ChromeTreatment.paraApi(26, ChromeTreatment.CRISTAL))
    }

    @Test
    fun desdeAndroid12HayDesenfoqueDeVerdad() {
        assertEquals(ChromeTreatment.ESMERILADO,
            ChromeTreatment.paraApi(31, ChromeTreatment.ESMERILADO))
        assertEquals(ChromeTreatment.CRISTAL,
            ChromeTreatment.paraApi(31, ChromeTreatment.CRISTAL))
    }

    @Test
    fun dondeEstanLosUsuariosDeVerdad() {
        // Play Console (ago-2026): TODOS los usuarios de Cumbre van en Android
        // 13 o superior — API 33+. Las versiones viejas del desglose eran los
        // propios móviles de prueba de Rodrigo. O sea: el cristal es el caso
        // normal, no el privilegiado.
        listOf(33, 34, 35, 36).forEach { api ->
            assertEquals("API $api debería poder con el cristal",
                ChromeTreatment.CRISTAL,
                ChromeTreatment.paraApi(api, ChromeTreatment.CRISTAL))
        }
    }

    @Test
    fun pedirSolidoDaSolidoEnCualquierMovil() {
        // Si el usuario elige la barra sólida, se respeta aunque el móvil
        // pudiera con el cristal: es una preferencia, no una limitación.
        assertEquals(ChromeTreatment.SOLIDO,
            ChromeTreatment.paraApi(36, ChromeTreatment.SOLIDO))
        assertEquals(ChromeTreatment.SOLIDO,
            ChromeTreatment.paraApi(26, ChromeTreatment.SOLIDO))
    }
}
