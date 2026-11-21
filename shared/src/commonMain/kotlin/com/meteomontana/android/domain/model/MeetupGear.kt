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
 * Mochila de una persona ("Mi material" del perfil o material que lleva a una quedada): JSON
 * `{"cuerda":true,"grigri":false,"cintas":12,"crashpads":2}`. Los si/no cuentan 1; lo que no se entiende, 0.
 */
object GearJson {
    fun parse(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        val out = HashMap<String, Int>()
        json.trim().trim('{', '}').split(",").forEach { pair ->
            val parts = pair.split(":")
            if (parts.size != 2) return@forEach
            val key = parts[0].trim().trim('"')
            val value = when (val raw = parts[1].trim().trim('"')) {
                "true" -> 1
                "false" -> 0
                else -> raw.toIntOrNull() ?: 0
            }
            if (key.isNotEmpty() && value > 0) out[key] = value
        }
        return out
    }

    /** Clave de la mochila que cubre cada material pedido. */
    fun keyFor(item: String): String? = when (item) {
        MeetupGearItem.ROPE -> "cuerda"
        MeetupGearItem.GRIGRI -> "grigri"
        MeetupGearItem.QUICKDRAWS -> "cintas"
        MeetupGearItem.CRASHPAD -> "crashpads"
        else -> null
    }
}

/**
 * Que material TIENE ya el grupo y cual FALTA, para mostrarlo en la tarjeta de la lista antes de apuntarse. Se parte del
 * material pedido; si es una cordada en via y no se pidio nada, se da por pedido cuerda y gri-gri. Lo que cubre cada cosa
 * lo dice el servidor (`gearCoverage`, que cuenta al organizador y a los apuntados); sin dato, cuenta como no cubierto.
 * [withMochilas] suma ademas la mochila de las personas del grupo cuando el servidor aun no la tiene.
 */
object MeetupGearStatus {
    private fun needed(meetup: Meetup): List<MeetupGear> = when {
        meetup.requiredGear.isNotEmpty() -> meetup.requiredGear
        meetup.isRopeTeam -> listOf(MeetupGear(MeetupGearItem.ROPE, 1), MeetupGear(MeetupGearItem.GRIGRI, 1))
        else -> emptyList()
    }

    fun lines(meetup: Meetup): List<MeetupGearLine> {
        val coverage = meetup.gearCoverage.associateBy { it.item }
        return needed(meetup).map { g ->
            val c = coverage[g.item]
            MeetupGearLine(g.item, g.qty, c?.provided ?: 0, c?.ok ?: false)
        }
    }

    /**
     * Quedada con el material de las mochilas ya contado: el organizador y cada apuntado aportan lo que llevan a la
     * quedada y, si no han indicado nada, su mochila del perfil ([mochilas]: uid -> JSON; solo se conoce la de quien
     * usa la app). Nunca baja lo que ya cuenta el servidor.
     */
    fun withMochilas(meetup: Meetup, mochilas: Map<String, String?>): Meetup {
        val items = needed(meetup)
        if (items.isEmpty()) return meetup
        val people = (listOf(meetup.creatorUid) + meetup.members.map { it.uid }).distinct()
        val gearOf = people.associateWith { uid ->
            val own = GearJson.parse(meetup.members.firstOrNull { it.uid == uid }?.gearJson)
            if (own.isNotEmpty()) own else GearJson.parse(mochilas[uid])
        }
        val server = meetup.gearCoverage.associateBy { it.item }
        val coverage = items.map { g ->
            val key = GearJson.keyFor(g.item)
            val fromPeople = if (key == null) 0 else gearOf.values.sumOf { m ->
                val v = m[key] ?: 0
                if (g.item == MeetupGearItem.ROPE || g.item == MeetupGearItem.GRIGRI) minOf(v, 1) else v
            }
            val provided = maxOf(server[g.item]?.provided ?: 0, fromPeople)
            MeetupGearCoverage(g.item, g.qty, provided, provided >= g.qty)
        }
        return meetup.copy(requiredGear = items, gearCoverage = coverage)
    }
}
