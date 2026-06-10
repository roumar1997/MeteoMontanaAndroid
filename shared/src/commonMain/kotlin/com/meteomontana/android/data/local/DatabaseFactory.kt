package com.meteomontana.android.data.local

import com.meteomontana.db.MeteoMontanaDb

/**
 * Cada plataforma construye su propia SqlDriver y lo envuelve en un MeteoMontanaDb.
 * Android usa AndroidSqliteDriver(context, schema, "meteomontana.db").
 * iOS usará NativeSqliteDriver(schema, "meteomontana.db").
 */
expect class DatabaseFactory {
    fun create(): MeteoMontanaDb
}
