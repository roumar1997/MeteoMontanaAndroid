package com.meteomontana.android.domain.model

/**
 * Un cambio del catálogo para el panel de admin. [entityType] BLOCK (piedra, muro, sector, parking) o LINE (vía).
 * [source]: ADMIN, USER, CONTRIBUTION (propuesta aprobada), TRUSTED (de confianza, sin revisión), RESTORE o SYSTEM.
 * [before] / [after]: campo → valor (vacío = no existía / se borró).
 */
data class ContentChangeItem(
    val id: String,
    val entityType: String,
    val entityId: String,
    val blockId: String?,
    val schoolId: String?,
    val actorUid: String?,
    val actorName: String?,
    val source: String,
    val contributionId: String?,
    val before: Map<String, String>,
    val after: Map<String, String>,
    val createdAt: String,
) {
    val isCreation: Boolean get() = before.isEmpty()
    val isDeletion: Boolean get() = after.isEmpty()

    /** Campos que cambiaron (para pintar la diferencia), en el orden del estado. */
    val changedFields: List<String>
        get() = (before.keys + after.keys).distinct().filter { before[it] != after[it] }

    /** Nombre de la piedra o vía (el de después, o el de antes si se borró). */
    val title: String get() = after["name"] ?: before["name"] ?: entityId
}

/** Aportaciones de una persona y si es de confianza (se publica sin revisión). */
data class ContributorItem(
    val uid: String,
    val name: String?,
    val approved: Int,
    val rejected: Int,
    val pending: Int,
    val lastAt: String?,
    val trusted: Boolean,
)
