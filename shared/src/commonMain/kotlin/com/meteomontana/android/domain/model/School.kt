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
    val source: String?,
    /**
     * Pais ISO 3166-1 alfa-2. Por defecto "ES": las escuelas anteriores al
     * catalogo son espanolas, y una respuesta vieja del servidor tampoco lo
     * trae. Decide si aplican radar y boletin de montana, que son de AEMET.
     */
    val country: String = "ES"
)
