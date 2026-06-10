package com.meteomontana.android.data.saved

import com.meteomontana.android.domain.model.School
import com.meteomontana.db.MeteoMontanaDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caché local del catálogo completo de escuelas (stale-while-revalidate):
 * la lista se pinta desde aquí al instante y se refresca desde red después.
 */
class CachedSchoolsRepository(
    private val db: MeteoMontanaDb
) {
    private val q get() = db.schemaQueries

    suspend fun load(): List<School> = withContext(Dispatchers.Default) {
        q.cachedSchoolsAll().executeAsList().map {
            School(
                id = it.id, name = it.name, location = it.location,
                region = it.region, style = it.style, rockType = it.rockType,
                lat = it.lat, lon = it.lon, source = it.source
            )
        }
    }

    suspend fun replaceAll(schools: List<School>) = withContext(Dispatchers.Default) {
        q.transaction {
            q.cachedSchoolsDeleteAll()
            schools.forEach {
                q.cachedSchoolInsert(
                    id = it.id, name = it.name, location = it.location,
                    region = it.region, style = it.style, rockType = it.rockType,
                    lat = it.lat, lon = it.lon, source = it.source
                )
            }
        }
    }
}
