package com.meteomontana.android.domain.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Lo que ACABAS de cambiar en la ficha de una escuela (confirmar/quitar/
 * avisar antes de tiempo), para que la pestaña Escuelas se entere al momento
 * sin recargar todo el catálogo (Álvaro, 2026-09-05: "que no haga falta
 * estar recargando"). Vive mientras la app esté abierta; la siguiente
 * recarga completa del catálogo sigue mandando el servidor como verdad.
 */
object ProcessionaryOverrideStore {
    private val state = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    fun set(schoolId: String, alertActive: Boolean) {
        state.value = state.value + (schoolId to alertActive)
    }

    /** Emite el mapa completo cada vez que algo cambia. */
    fun observe(): Flow<Map<String, Boolean>> = state

    fun currentValue(): Map<String, Boolean> = state.value
}
