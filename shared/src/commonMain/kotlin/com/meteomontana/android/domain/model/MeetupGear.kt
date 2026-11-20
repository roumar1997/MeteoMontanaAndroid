package com.meteomontana.android.domain.model

/** Material minimo que pide una quedada (contrato final del servidor: `requiredGear`). */
data class MeetupGear(val item: String, val qty: Int)

/**
 * Cuanto de ese material cubren los apuntados: [provided] de [required]. [ok] lo calcula el servidor (cuerda y
 * gri-gri cuentan 1 por persona que los lleva).
 */
data class MeetupGearCoverage(val item: String, val required: Int, val provided: Int, val ok: Boolean)

/** Claves de material que acepta el servidor y su cantidad por defecto al pedirlas. */
object MeetupGearItem {
    const val ROPE = "ROPE"
    const val GRIGRI = "GRIGRI"
    const val QUICKDRAWS = "QUICKDRAWS"
    const val CRASHPAD = "CRASHPAD"

    const val MAX_QTY = 50

    /** El material se pide con cantidad (cintas, crashpads) o solo se pide o no (cuerda, gri-gri: 1). */
    fun hasQuantity(item: String): Boolean = item == QUICKDRAWS || item == CRASHPAD

    fun defaultQty(item: String): Int = when (item) {
        QUICKDRAWS -> 12
        CRASHPAD -> 2
        else -> 1
    }

    /**
     * Material que tiene sentido pedir segun el lugar: en un rocodromo solo cuerda y gri-gri si hay via (el rocodromo
     * pone presas, cintas y colchonetas); en roca, segun la modalidad ([forDiscipline]).
     */
    fun forPlace(discipline: String?, gym: Boolean): List<String> =
        if (!gym) forDiscipline(discipline)
        else when (discipline) {
            "ROUTE", "BOTH" -> listOf(ROPE, GRIGRI)
            else -> emptyList()
        }

    /** Material que tiene sentido pedir segun la modalidad: vias: cuerda, gri-gri y cintas; bloque: crashpads. */
    fun forDiscipline(discipline: String?): List<String> = when (discipline) {
        "ROUTE" -> listOf(ROPE, GRIGRI, QUICKDRAWS)
        "BOULDER" -> listOf(CRASHPAD)
        "BOTH" -> listOf(ROPE, GRIGRI, QUICKDRAWS, CRASHPAD)
        else -> emptyList()
    }
}

/** Una linea del material de una quedada: lo que se pide, lo que ya hay y si esta cubierto. */
data class MeetupGearLine(val item: String, val required: Int, val provided: Int, val ok: Boolean) {
    /** Cuanto falta (0 si esta cubierto). */
    val missing: Int get() = if (ok) 0 else (required - provided).coerceAtLeast(1)
}

/**
 * Que material TIENE ya el grupo y cual FALTA, para mostrarlo en la tarjeta de la lista antes de apuntarse. Se parte del
 * material pedido; si es una cordada en via y no se pidio nada, se da por pedido cuerda y gri-gri. Lo que cubre cada cosa
 * lo dice el servidor (`gearCoverage`, que cuenta al organizador y a los apuntados); sin dato, cuenta como no cubierto.
 */
object MeetupGearStatus {
    fun lines(meetup: Meetup): List<MeetupGearLine> {
        val needed = when {
            meetup.requiredGear.isNotEmpty() -> meetup.requiredGear
            meetup.isRopeTeam -> listOf(MeetupGear(MeetupGearItem.ROPE, 1), MeetupGear(MeetupGearItem.GRIGRI, 1))
            else -> emptyList()
        }
        val coverage = meetup.gearCoverage.associateBy { it.item }
        return needed.map { g ->
            val c = coverage[g.item]
            MeetupGearLine(g.item, g.qty, c?.provided ?: 0, c?.ok ?: false)
        }
    }
}
