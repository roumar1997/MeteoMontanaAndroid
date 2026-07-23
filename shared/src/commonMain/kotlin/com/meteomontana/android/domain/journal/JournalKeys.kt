package com.meteomontana.android.domain.journal

/**
 * Clave de diario de una vía: "escuela|#lineId" si tiene id (aguanta
 * homónimas — fix "La ola"), "escuela|nombre" como legado si no la tiene
 * (entradas antiguas anteriores a la migración a lineId).
 *
 * ÚNICA fuente del formato de clave, en `domain` compartido (lógica pura, sin
 * Compose/UIKit) para que la usen SIN duplicar ni violar capas: la UI de
 * Android (SchoolDetailViewModel, JournalTickController, SchoolMap), la cola
 * offline de Android (OutboxFlusher) y el contenedor de iOS
 * (IosDependencyContainer.flushJournalOutbox). Antes el formato estaba repartido
 * y el flush comparaba por NOMBRE mientras el encolado usaba la clave por ID →
 * el borrado offline nunca casaba y se perdía al reconectar (el ✓ reaparecía).
 */
fun journalViaKey(schoolId: String?, lineId: String?, viaName: String): String =
    if (!lineId.isNullOrBlank()) "${schoolId ?: ""}|#$lineId"
    else "${schoolId ?: ""}|${viaName.trim().lowercase()}"
