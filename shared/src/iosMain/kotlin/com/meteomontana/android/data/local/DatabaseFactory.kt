package com.meteomontana.android.data.local

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.meteomontana.db.MeteoMontanaDb

actual class DatabaseFactory {
    actual fun create(): MeteoMontanaDb {
        val driver = NativeSqliteDriver(
            schema = MeteoMontanaDb.Schema,
            name = "meteomontana_sql_v9.db"   // v9: SavedBlockFace (caras offline)
        )
        return MeteoMontanaDb(driver)
    }
}
