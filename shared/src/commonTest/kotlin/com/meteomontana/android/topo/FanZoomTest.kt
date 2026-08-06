package com.meteomontana.android.topo

import com.meteomontana.android.domain.model.DrawOp
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.renderTopo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Al ampliar la foto, los tamaños del dibujo se dividen por la escala para que
 * el trazo siga midiendo lo mismo en pantalla. Pero el ABANICO que separa los
 * badges que coinciden en el mismo punto NO debe encoger con ellos: si encoge,
 * los números se van juntando y parece que la línea se desliza sobre la roca
 * (reportado por Rodrigo probando el muro de Teverga).
 */
class FanZoomTest {

    private fun dosViasQueEmpiezanJuntas() = listOf(
        TopoLineData("A", "6a", null, listOf(0.5f to 0.9f, 0.4f to 0.2f)),
        TopoLineData("B", "7a", null, listOf(0.5f to 0.9f, 0.6f to 0.2f))
    )

    /** X de los badges numéricos, que son los círculos rellenos del inicio. */
    private fun xBadges(ops: List<DrawOp>): List<Float> =
        ops.filterIsInstance<DrawOp.TextLabel>()
            .filter { it.text == "1" || it.text == "2" }
            .map { it.cx }
            .sorted()

    @Test
    fun conElAbanicoFijoLosBadgesNoSeMuevenAlAmpliar() {
        val sinZoom = renderTopo(dosViasQueEmpiezanJuntas(), 1000f, 800f,
            fanSpacingPx = 40f to 48f)
        // Mismo dibujo "ampliado x4": los tamaños encogen, el abanico no.
        val ampliado = renderTopo(dosViasQueEmpiezanJuntas(), 1000f, 800f,
            badgeR = 14f * 0.25f to 11f * 0.25f,
            startR = 22f * 0.25f to 18f * 0.25f,
            fanSpacingPx = 40f to 48f)
        val a = xBadges(sinZoom)
        val b = xBadges(ampliado)
        assertTrue(a.size == 2 && b.size == 2, "deben salir los dos badges")
        a.zip(b).forEach { (x1, x2) ->
            assertTrue(abs(x1 - x2) < 0.01f, "el badge se movió de $x1 a $x2 al ampliar")
        }
    }

    @Test
    fun sinPasarAbanicoSeSigueDeduciendoDelBadge() {
        // Retrocompatible: quien no pase fanSpacingPx (feed, share, miniaturas)
        // se comporta exactamente igual que antes.
        val ops = renderTopo(dosViasQueEmpiezanJuntas(), 1000f, 800f)
        assertTrue(xBadges(ops).size == 2)
    }

    @Test
    fun elAbanicoSepara() {
        // Dos vías que arrancan en el MISMO punto no pueden acabar con sus dos
        // badges encima: para eso existe el abanico.
        val ops = renderTopo(dosViasQueEmpiezanJuntas(), 1000f, 800f,
            fanSpacingPx = 40f to 48f)
        val x = xBadges(ops)
        assertTrue(abs(x[0] - x[1]) > 20f, "los badges deberían separarse: $x")
    }
}
