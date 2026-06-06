package com.meteomontana.android.domain.model

/**
 * Modelo de dominio: lo que la app usa internamente.
 * No tiene anotaciones de Moshi/Retrofit. Si cambia la API,
 * traducimos en el repositorio y aquí no toca nada.
 */
data class School(
    val id: String,
    val name: String,
    val location: String?,
    val region: String?,
    val style: String?,
    val rockType: String?,
    val lat: Double,
    val lon: Double,
    val source: String?
)
