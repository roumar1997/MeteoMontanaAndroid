package com.meteomontana.android.ui.guide

/**
 * Puntos de la guía y de la ayuda (compartidas con iOS) de funciones que Android todavía NO tiene. Se quitan al
 * enseñarlas para no señalar un botón que no existe. Al portar la función, se borra de aquí.
 */
val NOT_YET_ON_ANDROID: Set<String> = setOf(
    "detail.photo", // «¿Qué piedra es esta?» desde la ficha de escuela (iOS, build 312)
    "schools.favs", // comparar favoritas con la estrella FAVS de Escuelas (iOS, build 315)
)
