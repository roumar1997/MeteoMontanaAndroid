package com.meteomontana.android.util

import com.meteomontana.android.R

/**
 * Nombres de meses y días en el idioma de la app, desde recursos (no desde
 * `java.time`, que da abreviaturas distintas según la versión de Java: "sept."
 * frente a "sep"). Todos los arreglos empiezan en lunes / enero. Quien necesite
 * mayúsculas o minúsculas las aplica al usarlos.
 */
object CalendarLabels {
    fun monthsShort(): List<String> = AppText.array(R.array.cal_months_short)
    fun monthsLong(): List<String> = AppText.array(R.array.cal_months_long)
    fun daysShortMonFirst(): List<String> = AppText.array(R.array.cal_days_short_mon)
    fun daysLongMonFirst(): List<String> = AppText.array(R.array.cal_days_long_mon)
    fun dayLettersMonFirst(): List<String> = AppText.array(R.array.cal_days_letter_mon)

    /** Domingo primero (índice 0 = domingo), como `Calendar.DAY_OF_WEEK - 1`. */
    fun daysShortSunFirst(): List<String> = daysShortMonFirst().let { listOf(it.last()) + it.dropLast(1) }
}
