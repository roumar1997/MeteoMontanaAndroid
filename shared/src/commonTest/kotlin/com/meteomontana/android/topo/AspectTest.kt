package com.meteomontana.android.topo

import com.meteomontana.android.domain.util.Aspect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Grados de la brújula → punto cardinal.
 *
 * El caso que justifica el test: 350° es NORTE. Si los sectores se partieran en
 * trozos de 45 empezando en cero, saldría NO, y la orientación de la pared se
 * guardaría mal justo cuando el usuario apunta al norte.
 */
class AspectTest {

    @Test
    fun losOchoPuntosEnSuDireccionExacta() {
        assertEquals("N", Aspect.fromDegrees(0f))
        assertEquals("NE", Aspect.fromDegrees(45f))
        assertEquals("E", Aspect.fromDegrees(90f))
        assertEquals("SE", Aspect.fromDegrees(135f))
        assertEquals("S", Aspect.fromDegrees(180f))
        assertEquals("SO", Aspect.fromDegrees(225f))
        assertEquals("O", Aspect.fromDegrees(270f))
        assertEquals("NO", Aspect.fromDegrees(315f))
    }

    @Test
    fun elNorteAbarcaLosDosLadosDelCero() {
        assertEquals("N", Aspect.fromDegrees(350f))
        assertEquals("N", Aspect.fromDegrees(10f))
        assertEquals("N", Aspect.fromDegrees(338f))
        assertEquals("N", Aspect.fromDegrees(22f))
    }

    @Test
    fun justoEnLaFronteraCambia() {
        // 22,5 es el límite entre N y NE. Da igual a cuál caiga, pero no puede
        // caer fuera de esos dos.
        assertEquals("NE", Aspect.fromDegrees(23f))
        assertEquals("N", Aspect.fromDegrees(22f))
    }

    @Test
    fun losSensoresDevuelvenDeTodo() {
        // Rumbos negativos y por encima de 360: no pueden reventar ni salir del
        // rango de los ocho.
        assertEquals("N", Aspect.fromDegrees(-5f))
        assertEquals("E", Aspect.fromDegrees(450f))
        assertEquals("O", Aspect.fromDegrees(-90f))
    }

    @Test
    fun idaYVuelta() {
        Aspect.ALL.forEach { punto ->
            val grados = Aspect.degreesOf(punto)!!
            assertEquals(punto, Aspect.fromDegrees(grados),
                "$punto → $grados → debería volver a $punto")
        }
    }

    @Test
    fun loQueNoEsUnPuntoCardinalNoTieneGrados() {
        assertNull(Aspect.degreesOf("ESTE"))
        assertNull(Aspect.degreesOf(""))
        assertEquals(90f, Aspect.degreesOf(" e "), "se acepta con espacios y en minúscula")
    }

    @Test
    fun elGradoQueSeEnsenaVaRedondeadoYDentroDeVuelta() {
        assertEquals("52°", Aspect.degreesLabel(51.7f))
        assertEquals("0°", Aspect.degreesLabel(359.8f))
        assertEquals("355°", Aspect.degreesLabel(-5f))
    }
}
