package com.meteomontana.android.domain.model.gym

/**
 * Edicion parcial de una ficha por el admin (solo iOS). Lo que va a null NO se toca en el servidor: desde aqui
 * no se puede borrar un dato (para eso, ocultar la ficha).
 */
data class GymAdminEdit(
    val kind: GymKind? = null,
    val discipline: GymDiscipline? = null,
    val verification: GymVerification? = null,
    val openingHours: List<OpeningSlot>? = null,
    val priceSingleEur: Double? = null,
    val pricePassEur: Double? = null,
    val pricePassLabel: String? = null,
    /** Fecha ISO (yyyy-MM-dd) en que se reviso el precio; sin ella la app no enseña el precio. */
    val priceUpdatedAt: String? = null,
    val website: String? = null,
    val instagram: String? = null,
    val hidden: Boolean? = null
)
