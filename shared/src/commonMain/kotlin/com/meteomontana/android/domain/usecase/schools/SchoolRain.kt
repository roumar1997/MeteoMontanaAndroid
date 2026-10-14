package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.RangeScore
import com.meteomontana.android.domain.model.SchoolScore

/**
 * ¿Llueve en esta escuela? Regla única para el chip "SIN LLUVIA" y el aviso de la lista de Escuelas
 * (iOS y Android). Pura: recibe lo que ya calcula el backend.
 */
object SchoolRain {

    /**
     * - Con tramo de días elegido: llueve si LLUEVE ALGÚN día del tramo.
     * - Sin tramo (hoy): llueve si la previsión de hoy trae lluvia (mm > 0).
     * Sin datos todavía (score nulo) NO se considera lluvia: la escuela no desaparece mientras carga.
     */
    fun isRainy(today: SchoolScore?, range: RangeScore?): Boolean =
        if (range != null) range.rainDays > 0 else (today?.rainMm ?: 0.0) > 0.0
}
