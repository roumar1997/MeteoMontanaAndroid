package com.meteomontana.android.data.local

import com.meteomontana.db.MeteoMontanaDb

/**
 * Limpia las cachés locales al cerrar sesión. Borra SOLO lo derivado del
 * servidor (catálogo, meses, perfiles, quedadas) para que un cambio de fórmula
 * o de cuenta no arrastre datos viejos. PRESERVA a propósito:
 *  - Outbox: cambios offline pendientes de sincronizar (borrarlos = perder datos).
 *  - SavedSchool/Block/Forecast: lo que el usuario guardó para ver sin conexión.
 */
class LocalCacheCleaner(private val db: MeteoMontanaDb) {

    fun clearServerCaches() {
        db.schemaQueries.monthlyDeleteAll()
        db.schemaQueries.cachedSchoolsDeleteAll()
        db.schemaQueries.profilesDeleteAll()
        db.schemaQueries.meetupsDeleteAll()
    }
}
