package com.meteomontana.android.topo

import com.meteomontana.android.domain.util.TopoCamera
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La cámara es la que decide dónde cae cada punto del trazo, así que un error
 * aquí guarda las vías torcidas sin que nada falle a la vista. Por eso se
 * prueba entera y sin pintar nada.
 */
class TopoCameraTest {

    private val W = 1000f
    private val H = 800f

    private fun casi(a: Float, b: Float, tol: Float = 0.001f) =
        assertTrue(abs(a - b) < tol, "esperaba $b y llegó $a")

    @Test
    fun sinZoomLaConversionEsDirecta() {
        val c = TopoCamera.NONE
        val (px, py) = c.toPhoto(500f, 400f, W, H)
        casi(px, 0.5f); casi(py, 0.5f)
    }

    @Test
    fun idaYVueltaDejaElPuntoDondeEstaba() {
        // Es LA propiedad importante: si convertir a pantalla y volver moviera
        // el punto, cada guardado desplazaría un poco el trazo.
        val c = TopoCamera(scale = 3.2f, offsetX = -420f, offsetY = -310f)
        listOf(0f to 0f, 0.25f to 0.9f, 0.5f to 0.5f, 1f to 1f).forEach { (px, py) ->
            val (sx, sy) = c.toScreen(px, py, W, H)
            val (bx, by) = c.toPhoto(sx, sy, W, H)
            casi(bx, px); casi(by, py)
        }
    }

    @Test
    fun ampliarMantieneFijoElPuntoBajoLosDedos() {
        val c = TopoCamera.NONE
        val focoX = 300f; val focoY = 200f
        val antes = c.toPhoto(focoX, focoY, W, H)
        val z = c.zoomBy(2f, focoX, focoY, W, H)
        val despues = z.toPhoto(focoX, focoY, W, H)
        casi(despues.first, antes.first)
        casi(despues.second, antes.second)
    }

    @Test
    fun ampliarYReducirVuelveAlPuntoDePartida() {
        val c = TopoCamera.NONE.zoomBy(2.5f, 400f, 300f, W, H)
        val vuelta = c.zoomBy(1f / 2.5f, 400f, 300f, W, H)
        casi(vuelta.scale, 1f)
        casi(vuelta.offsetX, 0f)
        casi(vuelta.offsetY, 0f)
    }

    @Test
    fun noSePuedeReducirPorDebajoDeLaFotoEntera() {
        val c = TopoCamera.NONE.zoomBy(0.2f, 500f, 400f, W, H)
        assertEquals(TopoCamera.MIN_SCALE, c.scale)
        assertTrue(c.isIdentity)
    }

    @Test
    fun hayUnTopeDeAmpliacion() {
        var c = TopoCamera.NONE
        repeat(20) { c = c.zoomBy(2f, 500f, 400f, W, H) }
        assertEquals(TopoCamera.MAX_SCALE, c.scale)
    }

    @Test
    fun laFotoNuncaSeSaleDelLienzo() {
        // Arrastrando a lo bestia en las cuatro direcciones, los bordes aguantan.
        var c = TopoCamera.NONE.zoomBy(3f, 500f, 400f, W, H)
        c = c.panBy(9999f, 9999f, W, H)
        assertTrue(c.offsetX <= 0f && c.offsetY <= 0f, "no debe asomar por arriba/izquierda")
        c = c.panBy(-99999f, -99999f, W, H)
        assertTrue(c.offsetX >= W - W * c.scale - 0.01f, "no debe asomar por la derecha")
        assertTrue(c.offsetY >= H - H * c.scale - 0.01f, "no debe asomar por abajo")
    }

    @Test
    fun sinZoomNoHayDesplazamientoPosible() {
        val c = TopoCamera.NONE.panBy(300f, 300f, W, H)
        casi(c.offsetX, 0f); casi(c.offsetY, 0f)
    }

    @Test
    fun elDobleToqueAmpliaYVuelve() {
        val ampliada = TopoCamera.NONE.toggleZoomAt(250f, 250f, W, H)
        assertTrue(ampliada.scale > 1f)
        val vuelta = ampliada.toggleZoomAt(250f, 250f, W, H)
        assertTrue(vuelta.isIdentity)
    }

    @Test
    fun elTrazoAdelgazaAlAmpliar() {
        // Si el grosor no se dividiera, al 400% la línea taparía la roca que
        // justamente querías mirar de cerca.
        casi(TopoCamera.NONE.strokeFactor(), 1f)
        casi(TopoCamera(scale = 4f).strokeFactor(), 0.25f)
    }

    @Test
    fun unLienzoSinTamanoNoRevienta() {
        // Pasa de verdad: el primer fotograma, antes de medir.
        val c = TopoCamera.NONE
        val (px, py) = c.toPhoto(10f, 10f, 0f, 0f)
        casi(px, 0f); casi(py, 0f)
        assertEquals(c, c.clamped(0f, 0f))
    }

    @Test
    fun unPuntoFueraDelLienzoSeQuedaDentroDeLaFoto() {
        // Al arrastrar el dedo fuera del lienzo, el punto se pega al borde en
        // vez de guardarse en -0,3 y desaparecer del dibujo.
        val c = TopoCamera.NONE
        val (px, py) = c.toPhoto(-200f, 5000f, W, H)
        casi(px, 0f); casi(py, 1f)
    }
}
