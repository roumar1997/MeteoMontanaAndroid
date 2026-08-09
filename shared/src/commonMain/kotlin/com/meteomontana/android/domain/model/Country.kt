package com.meteomontana.android.domain.model

/**
 * Un país en el que hay (o puede haber) escuelas, con sus regiones.
 *
 * Las regiones vienen del servidor y no de las escuelas que ya existen: si se
 * dedujeran de ellas, el primer país que se abre tendría el desplegable vacío y
 * nadie podría proponer su primera escuela.
 *
 * [code] es ISO 3166-1 alfa-2 en mayúsculas (ES, FR, PT).
 */
data class Country(
    val code: String,
    val name: String,
    val regions: List<String>
)
