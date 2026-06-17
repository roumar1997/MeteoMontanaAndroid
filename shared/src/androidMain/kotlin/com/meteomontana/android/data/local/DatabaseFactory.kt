package com.meteomontana.android.data.local

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.meteomontana.db.MeteoMontanaDb

actual class DatabaseFactory(private val context: Context) {
    actual fun create(): MeteoMontanaDb {
        val driver = AndroidSqliteDriver(
            schema = MeteoMontanaDb.Schema,
            context = context,
            name = "meteomontana_sql_v4.db"   // v4: SavedBlock.sectorBlockId (vínculo piedra↔sector offline)
        )
        return MeteoMontanaDb(driver)
    }
}
