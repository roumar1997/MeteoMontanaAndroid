package com.meteomontana.android.domain.usecase.forecast

import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.repository.ForecastRepository
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * «Mojada · seca en ~X h» de las tarjetas de lista y mapa. Recuerda el resultado por escuela
 * solo [ttlMillis]: pasado ese tiempo vuelve a pedir la previsión (la roca se seca con las horas).
 * Si la red falla devuelve el último que tenga, aunque esté caducado, o null.
 */
class GetRockDryOutlookUseCase(
    private val getForecast: suspend (String) -> Forecast,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
) {
    constructor(repository: ForecastRepository) : this({ repository.getForecast(it) })

    private class Entry(val outlook: RockDryOutlook, val savedAtMillis: Long)

    private val cache = HashMap<String, Entry>()

    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String): RockDryOutlook? {
        val cached = cache[schoolId]
        if (cached != null && nowMillis() - cached.savedAtMillis < ttlMillis) return cached.outlook
        return try {
            val f = getForecast(schoolId)
            val outlook = RockDryOutlook.of(f.current, f.days, today())
            cache[schoolId] = Entry(outlook, nowMillis())
            outlook
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            cached?.outlook
        }
    }

    /** Tirar para refrescar: la próxima consulta vuelve a pedir la previsión. */
    fun clear() = cache.clear()

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 30L * 60 * 1000
    }
}
