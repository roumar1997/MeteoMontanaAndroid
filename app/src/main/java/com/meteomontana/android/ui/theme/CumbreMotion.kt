package com.meteomontana.android.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Cómo se mueven las cosas en Cumbre.
 *
 * Antes todo iba con `tween(280)`: duración fija y velocidad casi constante.
 * Ese es el motivo técnico de que la app "se sienta antigua" aunque cada
 * pantalla esté bien dibujada — el movimiento uniforme no existe en el mundo
 * físico, y el ojo lo lee como mecánico. Un muelle arranca rápido y frena
 * suave, que es lo que hace iOS y por lo que allí todo parece vivo.
 *
 * El ajuste está calibrado para Cumbre, que es una app seria: **firme, sin
 * rebote**. Un muelle blandito con el pantallazo balanceándose quedaría de
 * juguete, y esto es una guía de escalada.
 */
object CumbreMotion {

    /**
     * DURACION FIJA, no muelles. Y esto no se cambia sin leer lo de abajo.
     *
     * La ronda de rediseño (vc93) cambio estas transiciones a `spring`. Un
     * muelle se acerca al destino de forma asintotica: sin un umbral que diga
     * "esto ya esta", la animacion NO TERMINA NUNCA y el contenido se queda a
     * la deriva moviendose pixel a pixel.
     *
     * El sintoma tardo horas en cazarse: dentro de una escuela, pulsar
     * "OCULTAR MAPA" no hacia nada. Medido con registros, la barra estaba en
     * y=684 y bajando (704, 699, 695, 692, 690, 689, 688, 687, 684...) mientras
     * los dedos de Rodrigo caian en y=650-678 — apuntaba a lo que veia y el
     * objetivo real ya se habia movido. Como la deriva crece hacia abajo, los
     * botones de arriba respondian y los de abajo no: por eso parecia que
     * fallaba solo ese boton.
     *
     * 280 ms es lo que habia en la vc92, donde todo funcionaba. Si algun dia se
     * quieren muelles, tienen que llevar `visibilityThreshold` SIEMPRE.
     */
    private const val DURACION_MS = 280

    /** Para lo que se desplaza: pantallas que entran y salen. */
    val desplazamiento: FiniteAnimationSpec<IntOffset> = tween(DURACION_MS)

    /** Para lo que aparece y desaparece acompañando al desplazamiento. */
    val opacidad: FiniteAnimationSpec<Float> = tween(DURACION_MS)
}
