package com.meteomontana.android.domain.model

import kotlinx.datetime.LocalDate

/** Cerrado (no se puede escalar), regulado (con condiciones) o aviso (acceso, aparcamiento…). */
enum class RestrictionKind {
    CLOSED, REGULATED, NOTICE;

    companion object {
        fun parse(raw: String?): RestrictionKind = entries.firstOrNull { it.name == raw } ?: NOTICE
    }
}

/**
 * Restricción de escalada en una escuela: un sector entero ([sectorBlockId] sin [blockIds]) o bloques sueltos.
 * Las fechas van como "yyyy-MM-dd" (así Swift las recibe como texto). [yearly] = se repite cada año (solo cuentan
 * día y mes); [permanent] = sin fechas.
 */
data class Restriction(
    val id: String,
    val schoolId: String,
    val sectorBlockId: String?,
    val blockIds: List<String>,
    val kind: RestrictionKind,
    val reason: String,
    val startsOn: String?,
    val endsOn: String?,
    val yearly: Boolean,
    val permanent: Boolean,
    val sourceName: String,
    val sourceUrl: String?,
    val verifiedOn: String?,
) {
    /** Afecta a todo el sector (no a bloques sueltos). */
    val isWholeSector: Boolean get() = blockIds.isEmpty() && sectorBlockId != null

    /** Día y mes del fin (para "hasta el 31 jul"); null si es permanente. */
    val endMonth: Int? get() = endsOn?.let { runCatching { LocalDate.parse(it).monthNumber }.getOrNull() }
    val endDay: Int? get() = endsOn?.let { runCatching { LocalDate.parse(it).dayOfMonth }.getOrNull() }
    val startMonth: Int? get() = startsOn?.let { runCatching { LocalDate.parse(it).monthNumber }.getOrNull() }
    val startDay: Int? get() = startsOn?.let { runCatching { LocalDate.parse(it).dayOfMonth }.getOrNull() }

    /** ¿Afecta a este bloque? [sectorOfBlock] = id del sector al que pertenece (null si es suelto). */
    fun affects(blockId: String, sectorOfBlock: String?): Boolean =
        if (blockIds.isNotEmpty()) blockId in blockIds
        else sectorBlockId != null && (blockId == sectorBlockId || sectorOfBlock == sectorBlockId)
}

/**
 * Lo que una pantalla necesita saber de las restricciones en vigor de UNA escuela: qué sectores y bloques llevan "!".
 * Si un bloque cae en varias, gana la más fuerte (cerrado > regulado > aviso).
 */
class SchoolRestrictions(val schoolId: String, val active: List<Restriction>) {

    val isEmpty: Boolean get() = active.isEmpty()

    /** Las que afectan a ese bloque o sector, la más fuerte primero. */
    fun forBlock(blockId: String, sectorOfBlock: String?): List<Restriction> =
        active.filter { it.affects(blockId, sectorOfBlock) }.sortedBy { it.kind.ordinal }

    fun strongestFor(blockId: String, sectorOfBlock: String?): Restriction? =
        forBlock(blockId, sectorOfBlock).firstOrNull()

    fun strongestForBlock(block: Block): Restriction? = strongestFor(block.id, block.sectorBlockId)

    /**
     * Lo más fuerte que hay dentro de un sector: el sector entero o cualquiera de sus piedras (así el chip "S" del
     * sector avisa aunque solo estén cerradas unas vías). [blocks] = todos los de la escuela.
     */
    fun strongestInSector(sectorId: String, blocks: List<Block>): Restriction? {
        val inSector = blocks.filter { it.sectorBlockId == sectorId }.map { it.id }.toSet()
        return active.filter { r -> r.sectorBlockId == sectorId || r.blockIds.any { it in inSector } }
            .minByOrNull { it.kind.ordinal }
    }

    /** Lo mismo buscando el sector por NOMBRE (la pestaña BLOQUES agrupa por nombre de sector). */
    fun strongestInSectorNamed(name: String?, blocks: List<Block>): Restriction? {
        if (name.isNullOrBlank()) return null
        val sector = blocks.firstOrNull { it.type == "ZONE" && it.name == name } ?: return null
        return strongestInSector(sector.id, blocks)
    }

    /** Número de sectores o grupos de bloques cerrados (para la pastilla "! 2 cerrados" junto al nombre). */
    val closedCount: Int get() = active.count { it.kind == RestrictionKind.CLOSED }

    companion object {
        fun of(schoolId: String, all: List<Restriction>): SchoolRestrictions =
            SchoolRestrictions(schoolId, all.filter { it.schoolId == schoolId })
    }
}
