package com.meteomontana.android.ui.guide

/**
 * Puntos de la guía y de la ayuda (compartidas con iOS) de funciones que Android todavía NO tiene, o que ya no tiene
 * en esa pantalla. Se quitan al enseñarlas para no señalar un botón que no existe. Al portar la función, se borra de aquí.
 */
val NOT_YET_ON_ANDROID: Set<String> = setOf(
    "weather.compare", // la comparación de favoritas pasó del Tiempo a Escuelas (estrella FAVS), 2026-10-28
)
