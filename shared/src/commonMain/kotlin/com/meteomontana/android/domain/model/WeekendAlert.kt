package com.meteomontana.android.domain.model

/**
 * Configuración de la "Alerta de tiempo" del usuario (aviso de fin de semana
 * y ventana óptima). Modelo de dominio — la pantalla y los use cases trabajan
 * con esto, no con el DTO del backend.
 */
data class WeekendAlert(
    val enabled: Boolean,
    val notifyDay: Int,
    val notifyHour: Int,
    val schoolIds: List<String>,
    /** SCHOOLS = escuelas elegidas a mano; NEARBY = las mejores en un radio. */
    val mode: String = "SCHOOLS",
    val radiusKm: Int? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Días ISO-8601 a comparar en el aviso (1=lunes .. 7=domingo). */
    val alertDays: List<Int> = listOf(5, 6, 7),
    /** Alerta "ventana óptima hoy" sobre las favoritas (null en apps antiguas). */
    val optimalEnabled: Boolean? = null,
    /** Umbral 0-100 que debe superar la ventana óptima para avisar. */
    val optimalThreshold: Int? = null
)
