package com.meteomontana.android.topo

import com.meteomontana.android.domain.model.DrawOp
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.renderTopo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Modo FOCO: al elegir una vía, las demás se apagan. Bajan de opacidad y
 * pierden su número, pero NO desaparecen — hace falta verlas para saber dónde
 * está la elegida respecto a sus vecinas.
 */
class MutedLinesTest {

    private fun via(nombre: String, grado: String, x: Float, muted: Boolean = false) =
        TopoLineData(nombre, grado, null, listOf(x to 0.9f, x to 0.2f), muted = muted)

    private fun alfa(argb: Long) = ((argb shr 24) and 0xFF).toInt()

    @Test
    fun laViaApagadaSePintaTranslucida() {
        val ops = renderTopo(listOf(via("La ola", "7a", 0.3f, muted = true)), 1000f, 800f)
        val trazos = ops.filterIsInstance<DrawOp.LinePath>()
        assertTrue(trazos.isNotEmpty(), "debe seguir pintándose")
        assertTrue(trazos.all { alfa(it.argb) < 0x80 },
            "una vía apagada no puede ir a opacidad plena")
    }

    @Test
    fun laViaEncendidaSigueOpaca() {
        val ops = renderTopo(listOf(via("La ola", "7a", 0.3f)), 1000f, 800f)
        val trazos = ops.filterIsInstance<DrawOp.LinePath>()
        assertTrue(trazos.any { alfa(it.argb) == 0xFF }, "la elegida va a tope")
    }

    @Test
    fun laViaApagadaConservaSuColorDeGrado() {
        // Gris plano haría perder la referencia de dificultad de un vistazo.
        val encendida = renderTopo(listOf(via("A", "7a", 0.3f)), 1000f, 800f)
            .filterIsInstance<DrawOp.LinePath>().first().argb and 0x00FFFFFF
        val apagada = renderTopo(listOf(via("A", "7a", 0.3f, muted = true)), 1000f, 800f)
            .filterIsInstance<DrawOp.LinePath>().first().argb and 0x00FFFFFF
        assertEquals(encendida, apagada)
    }

    @Test
    fun laViaApagadaPierdeSuNumero() {
        // En un muro de 13 vías, dejar los 13 números encima anularía el foco.
        val ops = renderTopo(
            listOf(via("A", "7a", 0.3f, muted = true), via("B", "6b", 0.6f)),
            1000f, 800f
        )
        val numeros = ops.filterIsInstance<DrawOp.TextLabel>().map { it.text }
        assertTrue("1" !in numeros, "la apagada no debe llevar número")
        assertTrue("2" in numeros, "la encendida sí")
    }
}
