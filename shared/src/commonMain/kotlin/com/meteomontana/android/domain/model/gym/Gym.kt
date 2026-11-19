package com.meteomontana.android.domain.model.gym

/** Tipo de rocodromo. Un valor que el servidor invente mas adelante cae en [COMMERCIAL] sin romper nada. */
enum class GymKind {
    COMMERCIAL, MUNICIPAL, FREE;

    companion object {
        fun parse(raw: String?): GymKind = entries.firstOrNull { it.name == raw } ?: COMMERCIAL
    }
}

/** Modalidad. Un valor desconocido se trata como [UNKNOWN] (no se cae, solo no entra en los filtros). */
enum class GymDiscipline {
    BOULDER, ROPE, BOTH, UNKNOWN;

    val hasBoulder: Boolean get() = this == BOULDER || this == BOTH
    val hasRope: Boolean get() = this == ROPE || this == BOTH

    companion object {
        fun parse(raw: String?): GymDiscipline = entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}

/** Fiabilidad de la ficha. Lo desconocido cuenta como [UNCONFIRMED]. */
enum class GymVerification {
    UNCONFIRMED, COMMUNITY, VERIFIED;

    companion object {
        fun parse(raw: String?): GymVerification = entries.firstOrNull { it.name == raw } ?: UNCONFIRMED
    }
}

/** Un tramo de horario: [days] ("MON-FRI", "SAT,SUN", "FRI-MON"), apertura y cierre en "HH:mm". */
data class OpeningSlot(val days: String, val open: String, val close: String)

/**
 * Ficha de un rocodromo. Todo es opcional salvo la identificacion, el tipo y las coordenadas; los
 * booleanos nulos significan "no se sabe" (la UI enseña "sin confirmar", nunca "no").
 */
data class Gym(
    val id: Long,
    val slug: String,
    val name: String,
    val chain: String?,
    val kind: GymKind,
    val discipline: GymDiscipline,
    val region: String,
    val province: String,
    val city: String,
    val address: String?,
    val lat: Double,
    val lon: Double,
    val website: String?,
    val instagram: String?,
    val openingHours: List<OpeningSlot>?,
    val priceSingleEur: Double?,
    val pricePassEur: Double?,
    val pricePassLabel: String?,
    /** Fecha ISO (yyyy-MM-dd) en que se miro el precio. Sin fecha, la UI no enseña el precio. */
    val priceUpdatedAt: String?,
    val hasAutobelay: Boolean?,
    val hasGearRental: Boolean?,
    val kidsFriendly: Boolean?,
    val beginnerFriendly: Boolean?,
    val outdoor: Boolean,
    val verification: GymVerification,
    val source: String?,
    /** Solo llega a true en respuestas incrementales; el catalogo ya la excluye. */
    val hidden: Boolean,
    val updatedAt: String?
) {
    val isFree: Boolean get() = kind == GymKind.FREE || priceSingleEur == 0.0

    /** Los datos de OpenStreetMap exigen mostrar la atribucion (ODbL). */
    val needsOsmAttribution: Boolean get() = source?.contains("OpenStreetMap", ignoreCase = true) == true

    /** true/false, o null si no hay horario. Hora de Madrid. */
    fun isOpenAt(instant: kotlinx.datetime.Instant): Boolean? = GymOpeningHours.isOpenAt(this, instant)

    fun openStatusAt(instant: kotlinx.datetime.Instant): GymOpenStatus = GymOpeningHours.statusAt(this, instant)
}

/** Rocodromo cercano a un punto (Plan B), con la distancia en km ya calculada por el servidor. */
data class GymNear(
    val id: Long,
    val slug: String,
    val name: String,
    val chain: String?,
    val kind: GymKind,
    val discipline: GymDiscipline,
    val city: String,
    val province: String,
    val lat: Double,
    val lon: Double,
    val outdoor: Boolean,
    val verification: GymVerification,
    val distanceKm: Double
)
