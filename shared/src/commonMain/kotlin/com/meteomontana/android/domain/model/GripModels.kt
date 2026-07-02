package com.meteomontana.android.domain.model

/** Combinación agarre = dedos/posición × estilo. Catálogo fijo del backend. */
data class GripType(
    val id: Int,
    val fingerGroup: String,   // FIVE | FOUR | THREE | FRONT_TWO | MID_TWO
    val style: String          // CRIMP | HALF_CRIMP | DRAG
)

/** Tu máximo vigente para un agarre + mano. */
data class GripMaxRecord(
    val id: String,
    val gripTypeId: Int,
    val hand: String,          // LEFT | RIGHT
    val maxKg: Double,
    val edgeMm: String? = null,
    val measuredAt: String
)

/** Un test de "Medir" guardado en el historial (gráfica de progreso). */
data class GripMeasureSession(
    val id: String,
    val gripTypeId: Int,
    val hand: String,
    val peakKg: Double,
    val avgKg: Double,
    val durationS: Int,
    val edgeMm: String? = null,
    val createdAt: String
)

/** Un set dentro de una plantilla de entreno de agarres. */
data class GripWorkoutSet(
    val id: String,
    val sortOrder: Int,
    val reps: Int,
    val workS: Int,
    val restS: Int,
    val gripTypeId: Int,
    val targetMinPct: Double,
    val targetMaxPct: Double
)

/** Plantilla de entreno de agarres, guardada para reutilizar. */
data class GripWorkout(
    val id: String,
    val name: String,
    val handMode: String,      // UNA | POR_SERIE | POR_REP
    val countMode: String,     // TIEMPO | PESO
    val restBetweenSetsS: Int,
    val createdAt: String,
    val updatedAt: String,
    val sets: List<GripWorkoutSet>
)
