package com.meteomontana.android.domain.model.gym

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Estado de apertura en un instante. [Unknown] = el rocodromo no tiene horario cargado. */
sealed class GymOpenStatus {
    object Unknown : GymOpenStatus()

    /** Abierto; [closesAt] es "HH:mm" del cierre del tramo en curso ("24:00" se enseña como "00:00"). */
    data class Open(val closesAt: String) : GymOpenStatus()

    /** Cerrado; [opensAt] es "HH:mm" de la proxima apertura de HOY, o null si ya no abre hoy. */
    data class Closed(val opensAt: String?) : GymOpenStatus()
}

/**
 * Horarios en hora de Madrid (los rocodromos de Espana, Canarias incluida, se leen aqui con una unica zona:
 * la app no sabe en que zona esta el rocodromo y casi todos estan en la peninsula).
 */
object GymOpeningHours {

    val MADRID: TimeZone = TimeZone.of("Europe/Madrid")

    private val DAYS = mapOf(
        "MON" to DayOfWeek.MONDAY, "TUE" to DayOfWeek.TUESDAY, "WED" to DayOfWeek.WEDNESDAY,
        "THU" to DayOfWeek.THURSDAY, "FRI" to DayOfWeek.FRIDAY, "SAT" to DayOfWeek.SATURDAY,
        "SUN" to DayOfWeek.SUNDAY
    )

    /** true/false, o null si no hay horario (no se sabe: la UI no debe decir "cerrado"). */
    fun isOpenAt(gym: Gym, instant: Instant): Boolean? = when (statusAt(gym, instant)) {
        GymOpenStatus.Unknown -> null
        is GymOpenStatus.Open -> true
        is GymOpenStatus.Closed -> false
    }

    fun statusAt(gym: Gym, instant: Instant): GymOpenStatus {
        val slots = gym.openingHours?.takeIf { it.isNotEmpty() } ?: return GymOpenStatus.Unknown
        val local = instant.toLocalDateTime(MADRID)
        val today = local.dayOfWeek
        val yesterday = DayOfWeek.entries[(today.ordinal + 6) % 7]
        val now = local.hour * 60 + local.minute

        var closesAt: Int? = null
        var nextOpen: Int? = null
        for (slot in slots) {
            val open = parseMinutes(slot.open) ?: continue
            val close = parseMinutes(slot.close) ?: continue
            if (open == close) continue
            val crossesMidnight = close < open
            val daySet = expandDays(slot.days)
            if (today in daySet) {
                val end = if (crossesMidnight) 24 * 60 else close
                if (now in open until end) closesAt = maxOf(closesAt ?: 0, if (crossesMidnight) close else end)
                else if (open > now) nextOpen = minOf(nextOpen ?: Int.MAX_VALUE, open)
            }
            // Un tramo que empezo ayer y acaba hoy de madrugada.
            if (crossesMidnight && yesterday in daySet && now < close) {
                closesAt = maxOf(closesAt ?: 0, close)
            }
        }
        return closesAt?.let { GymOpenStatus.Open(format(it)) }
            ?: GymOpenStatus.Closed(nextOpen?.let { format(it) })
    }

    /** "MON-FRI", "SAT,SUN", "FRI-MON" (da la vuelta a la semana), "MON-FRI,SUN". Lo ilegible se ignora. */
    internal fun expandDays(raw: String): Set<DayOfWeek> {
        val out = mutableSetOf<DayOfWeek>()
        for (token in raw.uppercase().split(',')) {
            val t = token.trim()
            if (t.isEmpty()) continue
            val parts = t.split('-').map { it.trim() }
            val from = DAYS[parts[0]] ?: continue
            if (parts.size == 1) { out += from; continue }
            val to = DAYS[parts[1]] ?: continue
            var i = from.ordinal
            while (true) {
                out += DayOfWeek.entries[i]
                if (i == to.ordinal) break
                i = (i + 1) % 7
            }
        }
        return out
    }

    private fun parseMinutes(hhmm: String): Int? {
        val p = hhmm.trim().split(':')
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        if (h !in 0..24 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun format(minutes: Int): String {
        val m = minutes % (24 * 60)
        return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
    }
}
