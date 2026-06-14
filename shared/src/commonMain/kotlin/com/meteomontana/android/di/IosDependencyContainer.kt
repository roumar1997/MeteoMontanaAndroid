package com.meteomontana.android.di

import com.meteomontana.android.data.api.KtorForecastApi
import com.meteomontana.android.data.api.KtorSchoolApi
import com.meteomontana.android.data.api.buildApiHttpClient
import com.meteomontana.android.data.repository.KtorForecastRepository
import com.meteomontana.android.data.repository.KtorSchoolRepository
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolByIdUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolsUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import com.meteomontana.android.domain.usecase.schools.SearchSchoolsUseCase

/**
 * Grafo de dependencias para iOS, construido en Kotlin (commonMain).
 *
 * Por qué en Kotlin y no en Swift: toda la fontanería `suspend`/`StateFlow`
 * (p. ej. el tokenProvider del HttpClient) queda del lado Kotlin, donde es
 * trivial. Swift solo instancia este contenedor y coge use cases ya listos
 * (que SKIE expone como funciones `async`). Es el equivalente iOS de los
 * módulos Hilt de Android (NetworkModule + UseCasesModule).
 *
 * Para el MVP público (escuelas + forecast, endpoints sin auth) `authService`
 * puede ser null. Cuando se añada login, se pasa el `AuthService` (impl Swift
 * con Firebase) y el tokenProvider lo usará.
 *
 * Este contenedor compila también para androidTarget, así que se verifica en
 * Windows aunque su uso real sea iOS.
 */
class IosDependencyContainer(
    baseUrl: String,
    authService: AuthService? = null
) {
    private val httpClient = buildApiHttpClient(baseUrl) {
        authService?.currentIdToken(false)
    }

    private val schoolApi = KtorSchoolApi(httpClient)
    private val forecastApi = KtorForecastApi(httpClient)

    private val schoolRepository = KtorSchoolRepository(schoolApi)
    private val forecastRepository = KtorForecastRepository(forecastApi)

    // Use cases públicos del MVP (sin auth). Se irán añadiendo más a medida
    // que las pantallas iOS los necesiten.
    val getSchools = GetSchoolsUseCase(schoolRepository)
    val getSchoolById = GetSchoolByIdUseCase(schoolRepository)
    val searchSchools = SearchSchoolsUseCase(schoolRepository)
    val getForecast = GetForecastUseCase(forecastRepository)
    val getTodayScores = GetTodayScoresUseCase(forecastRepository)
}
