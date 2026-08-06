package com.meteomontana.android.domain.util

import kotlin.math.max
import kotlin.math.min

/**
 * La cámara con la que se mira una foto de topo: cuánto está ampliada y por
 * dónde. Es aritmética pura, sin nada de UI, y vive en el módulo compartido a
 * propósito.
 *
 * El motivo es el de siempre en este proyecto: si la conversión pantalla↔foto
 * se escribe una vez en Compose y otra en SwiftUI, las dos versiones divergen
 * y el trazo acaba guardándose en un sitio distinto en cada app. Aquí se
 * escribe una vez, se prueba una vez, y las dos apps la usan.
 *
 * **Regla que sostiene todo**: los trazos se guardan SIEMPRE en coordenadas de
 * la foto (0..1), nunca en píxeles de pantalla. Así puedes dibujar medio trazo
 * ampliado al 400% y el resto alejado: al guardar es la misma línea, y se ve
 * igual en un móvil, en un iPad y en la imagen que se comparte.
 *
 * @param scale cuánto está ampliada (1 = la foto entera cabe en el lienzo).
 * @param offsetX desplazamiento horizontal en píxeles del lienzo, ≤ 0.
 * @param offsetY desplazamiento vertical en píxeles del lienzo, ≤ 0.
 */
data class TopoCamera(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {

    companion object {
        /** Sin ampliar: la foto entera a la vista. */
        val NONE = TopoCamera()

        /** Tope de ampliación. Más allá, la foto es puro píxel gordo. */
        const val MAX_SCALE = 6f

        /** Escala mínima: la foto nunca se queda más pequeña que su lienzo. */
        const val MIN_SCALE = 1f
    }

    /** true si no hay ampliación (para decidir si mostrar el botón de ajustar). */
    val isIdentity: Boolean get() = scale <= MIN_SCALE + 0.001f

    /**
     * Amplía o reduce MANTENIENDO FIJO el punto [focusX], [focusY] del lienzo.
     * Es lo que hace que el pellizco "agarre" la roca que tienes bajo los dedos
     * en vez de tirar de la imagen desde el centro.
     */
    fun zoomBy(factor: Float, focusX: Float, focusY: Float,
               viewW: Float, viewH: Float): TopoCamera {
        val target = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (target == scale) return this
        val k = target / scale
        return copy(
            scale = target,
            offsetX = focusX - (focusX - offsetX) * k,
            offsetY = focusY - (focusY - offsetY) * k
        ).clamped(viewW, viewH)
    }

    /** Desplaza la foto. Los bordes se respetan: nunca se ve fuera de la foto. */
    fun panBy(dx: Float, dy: Float, viewW: Float, viewH: Float): TopoCamera =
        copy(offsetX = offsetX + dx, offsetY = offsetY + dy).clamped(viewW, viewH)

    /**
     * Recorta el desplazamiento para que no asome nada fuera de la foto.
     * Sin esto se puede arrastrar la imagen fuera de la pantalla y aparece un
     * hueco negro que desorienta.
     */
    fun clamped(viewW: Float, viewH: Float): TopoCamera {
        if (viewW <= 0f || viewH <= 0f) return this
        val minX = viewW - viewW * scale     // ≤ 0
        val minY = viewH - viewH * scale
        return copy(
            offsetX = offsetX.coerceIn(min(minX, 0f), 0f),
            offsetY = offsetY.coerceIn(min(minY, 0f), 0f)
        )
    }

    /**
     * Punto del lienzo (píxeles) → punto de la FOTO (0..1). Es la conversión
     * que decide dónde queda un trazo, así que es la que más falla si se
     * duplica: aquí solo existe una vez.
     */
    fun toPhoto(x: Float, y: Float, viewW: Float, viewH: Float): Pair<Float, Float> {
        if (viewW <= 0f || viewH <= 0f) return 0f to 0f
        val px = (x - offsetX) / (viewW * scale)
        val py = (y - offsetY) / (viewH * scale)
        return px.coerceIn(0f, 1f) to py.coerceIn(0f, 1f)
    }

    /** Punto de la FOTO (0..1) → punto del lienzo (píxeles). El camino inverso. */
    fun toScreen(px: Float, py: Float, viewW: Float, viewH: Float): Pair<Float, Float> =
        (px * viewW * scale + offsetX) to (py * viewH * scale + offsetY)

    /**
     * Amplía centrado en un punto de la foto, o vuelve a la vista completa si
     * ya estaba ampliada. Es el doble toque.
     */
    fun toggleZoomAt(x: Float, y: Float, viewW: Float, viewH: Float,
                     zoomedScale: Float = 2.5f): TopoCamera =
        if (isIdentity) zoomBy(zoomedScale, x, y, viewW, viewH)
        else NONE

    /**
     * Cuánto hay que dividir los grosores al pintar. Sin esto, al ampliar el
     * trazo engorda con la imagen y acaba tapando la roca justo cuando querías
     * ver el detalle.
     */
    fun strokeFactor(): Float = 1f / max(1f, scale)
}
