package com.meteomontana.android.domain.util

/**
 * El imán del editor de topos, como REGLA: cuándo se aplica y qué puntos han
 * quedado realmente unidos a otra vía.
 *
 * Vive aquí, y no en cada app, porque es una decisión de producto: si Android
 * la escribiera por su cuenta y iOS por la suya, acabarían pegándose a cosas
 * distintas y una misma piedra se guardaría diferente según el móvil.
 *
 * El interruptor existe porque el imán acertado no siempre es el imán deseado
 * (Rodrigo, con una guía real delante): dos vías pueden pasar muy cerca al
 * arrancar sin compartir nada, y juntarse solo a media pared. Quién decide eso
 * es quien está mirando la roca, no una distancia en el código.
 */
object TopoMagnet {

    /**
     * Aplica el imán a [stroke] **solo si está activado**.
     *
     * Con [enabled] a false el trazo se respeta tal cual, aunque pase pegado a
     * otra vía. Es el único sitio donde se toma esa decisión.
     */
    fun apply(
        stroke: List<Pair<Float, Float>>,
        others: List<List<Pair<Float, Float>>>,
        threshold: Float = 0.04f,
        enabled: Boolean = true
    ): List<Pair<Float, Float>> =
        if (!enabled) stroke else magnetizeStroke(stroke, others, threshold)

    /**
     * Índices de [line] que han quedado UNIDOS: los que caen exactamente sobre
     * un vértice de otra vía.
     *
     * Sirve para marcarlos en el editor. Hasta ahora, saber si el imán había
     * enganchado de verdad obligaba a fijarse en si aparecían las franjas del
     * tramo compartido; con la marca se ve al colocar el punto.
     *
     * "Exactamente" es literal: el imán copia el vértice de la otra vía, así
     * que la igualdad es la misma que usa la detección de tramos compartidos.
     * Un punto que solo pasa cerca NO cuenta — precisamente lo que distingue
     * "unido" de "al lado".
     */
    fun joinedIndices(
        line: List<Pair<Float, Float>>,
        others: List<List<Pair<Float, Float>>>
    ): Set<Int> {
        if (line.isEmpty() || others.isEmpty()) return emptySet()
        val vertices = HashSet<Pair<Float, Float>>()
        others.forEach { vertices.addAll(it) }
        return line.indices.filter { line[it] in vertices }.toSet()
    }
}
