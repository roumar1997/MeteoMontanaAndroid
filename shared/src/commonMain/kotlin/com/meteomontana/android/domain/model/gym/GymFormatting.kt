package com.meteomontana.android.domain.model.gym

import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

// Logica de presentacion de una ficha de rocodromo que NO depende del idioma ni de la plataforma. Vive aqui para
// que iOS y Android decidan exactamente igual (que dias se agrupan, cuando se enseña un precio, que URL se abre);
// cada plataforma solo pone las palabras (strings del idioma) y el formato local de fechas.

/** Nombres que pone la plataforma a las etiquetas de dias; [shortNames] son los 7 dias, de lunes a domingo. */
data class GymDayLabels(
    val monFri: String,
    val satSun: String,
    val sat: String,
    val sun: String,
    val daily: String,
    val shortNames: List<String>
)

/** Una linea de horario ya normalizada: "L–V", "10:00", "23:00" ("24:00" se enseña como "00:00"). */
data class GymHoursEntry(val daysLabel: String, val open: String, val close: String)

object GymHoursFormatter {

    private val CODES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    /** Un [GymHoursEntry] por tramo; vacio si la ficha no trae horario. */
    fun entries(slots: List<OpeningSlot>?, labels: GymDayLabels): List<GymHoursEntry> =
        slots.orEmpty().map { GymHoursEntry(daysLabel(it.days, labels), it.open, displayTime(it.close)) }

    fun displayTime(t: String): String = if (t == "24:00") "00:00" else t

    /** "MON-FRI" -> "L-V", "SAT,SUN" -> "S-D", "MON-SUN" -> "Todos los dias"; el resto, nombres cortos del idioma. */
    fun daysLabel(raw: String, labels: GymDayLabels): String {
        val compact = raw.uppercase().replace(" ", "")
        return when (compact) {
            "MON-FRI" -> labels.monFri
            "SAT-SUN", "SAT,SUN" -> labels.satSun
            "MON-SUN", "SAT-FRI" -> labels.daily
            "SAT" -> labels.sat
            "SUN" -> labels.sun
            else -> compact.split(',').joinToString(", ") { token ->
                token.split('-').joinToString("–") { shortDay(it, labels) }
            }
        }
    }

    private fun shortDay(token: String, labels: GymDayLabels): String {
        val i = CODES.indexOf(token)
        return if (i >= 0 && i < labels.shortNames.size) labels.shortNames[i] else token
    }
}

/** Distancia ya redondeada: metros por debajo del kilometro, kilometros por encima. */
data class GymDistance(val value: Int, val inMeters: Boolean) {
    companion object {
        fun of(km: Double?): GymDistance? = when {
            km == null -> null
            km < 1.0 -> GymDistance((km * 1000).roundToInt().coerceAtLeast(1), true)
            else -> GymDistance(km.roundToInt(), false)
        }
    }
}

/**
 * Lo que se sabe del precio de una ficha. [lines] vacio = "Sin dato de precio todavia".
 * Sin fecha de revision no se enseña el precio (puede estar caducado); un muro gratis dice "Entrada gratuita".
 */
data class GymPrice(
    val isFree: Boolean,
    val singleEur: Double?,
    val passEur: Double?,
    /** null = la ficha no pone nombre al bono (la plataforma usa "Bono"). */
    val passLabel: String?,
    /** Fecha ISO yyyy-MM-dd de la revision; null solo en un muro gratis. */
    val reviewedAtIso: String?,
    /** Ficha sin confirmar: la nota lleva "(sin verificar)". */
    val unverified: Boolean
) {
    companion object {
        /** null = no hay precio que enseñar. */
        fun of(gym: Gym): GymPrice? {
            if (gym.isFree) return GymPrice(true, null, null, null, null, false)
            val date = gym.priceUpdatedAt?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
            if (date == null || (gym.priceSingleEur == null && gym.pricePassEur == null)) return null
            return GymPrice(
                isFree = false, singleEur = gym.priceSingleEur, passEur = gym.pricePassEur,
                passLabel = gym.pricePassLabel?.takeIf { it.isNotBlank() }, reviewedAtIso = date,
                unverified = gym.verification == GymVerification.UNCONFIRMED
            )
        }

        /** 12.0 -> "12"; 12.5 -> "12,50" con el separador decimal que pide la plataforma (coma en ES, punto en EN). */
        fun amountText(v: Double, decimalSeparator: String): String {
            if (v == v.toLong().toDouble()) return v.toLong().toString()
            val cents = (v * 100).roundToInt()
            val whole = cents / 100
            val frac = (cents % 100).toString().padStart(2, '0')
            return "$whole$decimalSeparator$frac"
        }
    }
}

/** Enlaces de la ficha: la web y el Instagram vienen en cualquier forma. */
object GymLinks {
    fun webUrl(raw: String): String = raw.trim().let { if (it.startsWith("http", ignoreCase = true)) it else "https://$it" }

    /** Acepta "@nombre", "nombre" o la URL entera. */
    fun instagramUrl(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("http", ignoreCase = true)) t else "https://instagram.com/" + t.removePrefix("@").trim('/')
    }
}
