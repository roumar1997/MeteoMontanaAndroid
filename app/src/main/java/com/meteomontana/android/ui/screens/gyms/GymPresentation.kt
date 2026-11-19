package com.meteomontana.android.ui.screens.gyms

import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymDiscipline
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymOpenStatus
import com.meteomontana.android.domain.model.gym.GymVerification
import com.meteomontana.android.domain.model.gym.OpeningSlot
import com.meteomontana.android.util.AppText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Textos derivados de una ficha de rocódromo (etiquetas, horario, precio con su fecha). Funciones puras sobre
 * [AppText]: se prueban en JVM y las claves son las de docs/rocodromos/TEXTOS.md, que iOS usa idénticas.
 */
object GymPresentation {

    fun kindLabel(kind: GymKind): String = AppText.get(
        when (kind) {
            GymKind.COMMERCIAL -> R.string.gym_kind_commercial
            GymKind.MUNICIPAL -> R.string.gym_kind_municipal
            GymKind.FREE -> R.string.gym_kind_free
        }
    )

    /** Modalidad como etiquetas sueltas (Bloque, Cuerda…); un muro al aire libre suma la suya. */
    fun disciplineChips(gym: Gym): List<String> = buildList {
        when (gym.discipline) {
            GymDiscipline.BOULDER -> add(AppText.get(R.string.gym_disc_boulder))
            GymDiscipline.ROPE -> add(AppText.get(R.string.gym_disc_rope))
            GymDiscipline.BOTH -> {
                add(AppText.get(R.string.gym_disc_boulder))
                add(AppText.get(R.string.gym_disc_rope))
            }
            GymDiscipline.UNKNOWN -> Unit
        }
        if (gym.outdoor) add(AppText.get(R.string.gym_disc_outdoor))
    }

    /** "Gratis" / "De pago" / nada (un municipal sin precio no se etiqueta: no se sabe). */
    fun priceTag(gym: Gym): String? = when {
        gym.isFree -> AppText.get(R.string.gym_free_tag)
        gym.kind == GymKind.COMMERCIAL || (gym.priceSingleEur ?: 0.0) > 0.0 -> AppText.get(R.string.gym_pay)
        else -> null
    }

    /** "850 m" por debajo del kilómetro, "18 km" por encima; null sin ubicación. */
    fun distanceText(km: Double?): String? = when {
        km == null -> null
        km < 1.0 -> AppText.get(R.string.gym_distance_m, (km * 1000).roundToInt().coerceAtLeast(1))
        else -> AppText.get(R.string.gym_distance_km, km.roundToInt())
    }

    /** "Abierto · hasta 23:00", "Cerrado · abre a las 10:00", "Cerrado"; null si no hay horario (no se afirma nada). */
    fun openStatusText(status: GymOpenStatus): String? = when (status) {
        GymOpenStatus.Unknown -> null
        is GymOpenStatus.Open -> AppText.get(R.string.gym_open_until, status.closesAt)
        is GymOpenStatus.Closed ->
            if (status.opensAt != null) AppText.get(R.string.gym_closed_opens, status.opensAt)
            else AppText.get(R.string.gym_closed)
    }

    fun verificationText(v: GymVerification): String = AppText.get(
        if (v == GymVerification.UNCONFIRMED) R.string.gym_unknown else R.string.gym_verified
    )

    /** Servicio sí/no/no se sabe: nunca "no" por un NULL. */
    fun serviceValue(value: Boolean?): String = AppText.get(
        when (value) {
            true -> R.string.gym_yes
            false -> R.string.gym_no
            null -> R.string.gym_unknown
        }
    )

    // ── Horario ──

    /** Una línea por tramo ("L–V 10:00–23:00"); vacío si la ficha no trae horario. */
    fun hoursLines(slots: List<OpeningSlot>?): List<String> =
        slots.orEmpty().map { AppText.get(R.string.gym_hours_line, daysLabel(it.days), it.open, displayTime(it.close)) }

    private fun displayTime(t: String) = if (t == "24:00") "00:00" else t

    private val FULL = mapOf(
        "MON" to DayOfWeek.MONDAY, "TUE" to DayOfWeek.TUESDAY, "WED" to DayOfWeek.WEDNESDAY,
        "THU" to DayOfWeek.THURSDAY, "FRI" to DayOfWeek.FRIDAY, "SAT" to DayOfWeek.SATURDAY, "SUN" to DayOfWeek.SUNDAY
    )

    /** "MON-FRI" → "L–V", "SAT,SUN" → "S–D", "MON-SUN" → "Todos los días"; el resto, nombres cortos del idioma. */
    internal fun daysLabel(raw: String): String {
        val compact = raw.uppercase().replace(" ", "")
        return when (compact) {
            "MON-FRI" -> AppText.get(R.string.gym_hours_mon_fri)
            "SAT-SUN", "SAT,SUN" -> AppText.get(R.string.gym_hours_sat_sun)
            "MON-SUN", "SAT-FRI" -> AppText.get(R.string.gym_hours_daily)
            "SAT" -> AppText.get(R.string.gym_hours_sat)
            "SUN" -> AppText.get(R.string.gym_hours_sun)
            else -> compact.split(',').joinToString(", ") { token ->
                token.split('-').joinToString("–") { shortDay(it) }
            }
        }
    }

    private fun shortDay(token: String): String =
        FULL[token]?.getDisplayName(TextStyle.SHORT, Locale.getDefault()) ?: token

    // ── Precio ──

    /** Líneas de precio y nota de fecha/fiabilidad. [lines] vacío = "Sin dato de precio todavía". */
    data class PriceInfo(val lines: List<String>, val note: String?)

    /**
     * Sin fecha de revisión no se enseña el precio (puede estar caducado): `lines` queda vacío. Un muro gratis
     * dice "Entrada gratuita" sin fecha.
     */
    fun priceInfo(gym: Gym): PriceInfo {
        if (gym.isFree) return PriceInfo(listOf(AppText.get(R.string.gym_price_free)), null)
        val date = gym.priceUpdatedAt?.let(::formatDate)
        val single = gym.priceSingleEur
        val pass = gym.pricePassEur
        if (date == null || (single == null && pass == null)) return PriceInfo(emptyList(), null)
        val lines = buildList {
            single?.let { add(AppText.get(R.string.gym_price_single, formatAmount(it))) }
            pass?.let {
                val label = gym.pricePassLabel?.takeIf(String::isNotBlank) ?: AppText.get(R.string.gym_price_pass_default)
                add(AppText.get(R.string.gym_price_pass, label, formatAmount(it)))
            }
        }
        val note = buildString {
            append(AppText.get(R.string.gym_price_asof, date))
            if (gym.verification == GymVerification.UNCONFIRMED) append(' ').append(AppText.get(R.string.gym_price_unverified))
        }
        return PriceInfo(lines, note)
    }

    /** 12.0 → "12"; 12.5 → "12,50" (coma en español, punto en inglés). */
    internal fun formatAmount(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(Locale.getDefault(), "%.2f", v)

    private fun formatDate(iso: String): String? = runCatching {
        LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrNull()
}
