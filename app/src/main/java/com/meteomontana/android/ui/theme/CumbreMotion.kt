package com.meteomontana.android.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
     * Casi crítico (0.9): la pantalla se asienta sin oscilar. Por debajo de 1
     * hay un punto mínimo de vida al frenar; en 1 exacto queda algo muerto.
     */
    private const val AMORTIGUACION = 0.9f

    /**
     * Rigidez media-baja. Más alto se vuelve brusco; más bajo, perezoso — y en
     * una app que se abre para consultar el tiempo antes de salir a escalar,
     * la lentitud se paga cara.
     */
    private const val RIGIDEZ = Spring.StiffnessMediumLow

    /** Para lo que se desplaza: pantallas que entran y salen. */
    val desplazamiento: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = AMORTIGUACION, stiffness = RIGIDEZ)

    /** Para lo que aparece y desaparece acompañando al desplazamiento. */
    val opacidad: FiniteAnimationSpec<Float> =
        spring(dampingRatio = AMORTIGUACION, stiffness = RIGIDEZ)
}
