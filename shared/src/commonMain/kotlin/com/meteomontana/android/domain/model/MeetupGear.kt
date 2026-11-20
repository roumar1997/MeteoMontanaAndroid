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

    /** Material que tiene sentido pedir segun la modalidad: vias: cuerda, gri-gri y cintas; bloque: crashpads. */
    fun forDiscipline(discipline: String?): List<String> = when (discipline) {
        "ROUTE" -> listOf(ROPE, GRIGRI, QUICKDRAWS)
        "BOULDER" -> listOf(CRASHPAD)
        else -> emptyList()
    }
}
