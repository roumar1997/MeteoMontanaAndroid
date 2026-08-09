package com.meteomontana.android.topo

import com.meteomontana.android.domain.model.DrawOp
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.renderTopo
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Lo que el usuario ve MIENTRAS arrastra el dedo.
 *
 * Reportado: "en el Xiaomi la línea son todo puntos chiquititos, no el color
 * del grado" y "en iOS aparece toda de golpe al soltar". Sospecha: el trazo en
 * curso llega con muchos puntos casi pegados y el limpiador de pasadas
 * superpuestas (dropRetrace, pensado para trazos VIEJOS guardados con varias
 * pasadas encima) se lo carga, así que no se pinta la línea — solo quedan los
 * puntos que dibuja el editor aparte.
 */
class LiveStrokeTest {

    /** Un arrastre real: muchos puntos seguidos, con el temblor del pulso. */
    private fun trazoEnCurso(): List<Pair<Float, Float>> {
        val pts = mutableListOf<Pair<Float, Float>>()
        var y = 0.90f
        var i = 0
        while (y > 0.30f) {
            val ruido = if (i % 3 == 0) 0.0008f else -0.0006f
            pts.add((0.40f + ruido) to y)
            y -= 0.004f
            i++
        }
        return pts
    }

    @Test
    fun elTrazoEnCursoSePinta() {
        val ops = renderTopo(
            listOf(TopoLineData("nueva", "7a", null, trazoEnCurso())),
            1000f, 800f
        )
        val trazos = ops.filterIsInstance<DrawOp.LinePath>()
        assertTrue(trazos.isNotEmpty(),
            "mientras arrastras no se pinta ninguna línea: el trazo llega con " +
            "${trazoEnCurso().size} puntos y se queda en nada")
    }
}
