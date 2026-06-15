package com.meteomontana.android.di

import com.meteomontana.android.data.api.KtorFavoritesApi
import com.meteomontana.android.data.api.KtorForecastApi
import com.meteomontana.android.data.api.KtorNoteApi
import com.meteomontana.android.data.api.KtorNotificationApi
import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.KtorSchoolApi
import com.meteomontana.android.data.api.buildApiHttpClient
import com.meteomontana.android.data.repository.KtorFavoritesRepository
import com.meteomontana.android.data.repository.KtorForecastRepository
import com.meteomontana.android.data.repository.KtorNoteRepository
import com.meteomontana.android.data.repository.KtorNotificationsRepository
import com.meteomontana.android.data.repository.KtorProfileRepository
import com.meteomontana.android.data.repository.KtorSchoolRepository
import com.meteomontana.android.data.saved.CachedSchoolsRepository
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.db.MeteoMontanaDb
import com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase
import com.meteomontana.android.domain.usecase.notes.CreateNoteUseCase
import com.meteomontana.android.domain.usecase.notes.GetNotesUseCase
import com.meteomontana.android.domain.usecase.notifications.GetMyNotificationsUseCase
import com.meteomontana.android.domain.usecase.notifications.MarkAllNotificationsReadUseCase
import com.meteomontana.android.domain.usecase.notifications.MarkNotificationReadUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateMyProfileUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastByLocationUseCase
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
    authService: AuthService? = null,
    /**
     * Ubicación del usuario. En iOS se pasa un [IosLocationProvider]
     * (envoltorio del bridge Swift con CLLocationManager). Null → la pantalla
     * de Tiempo cae a una ubicación por defecto (Madrid).
     */
    val locationProvider: LocationProvider? = null,
    /**
     * BD SQLDelight para el caché local del catálogo. La construye el lado
     * Swift con `DatabaseFactory().create()` (driver nativo) y la pasa aquí.
     * Null → la lista funciona sin caché (solo red).
     */
    database: MeteoMontanaDb? = null
) {
    private val httpClient = buildApiHttpClient(baseUrl) {
        authService?.currentIdToken(false)
    }

    private val schoolApi = KtorSchoolApi(httpClient)
    private val forecastApi = KtorForecastApi(httpClient)
    private val favoritesApi = KtorFavoritesApi(httpClient)
    private val noteApi = KtorNoteApi(httpClient)
    private val profileApi = KtorProfileApi(httpClient)
    private val notificationApi = KtorNotificationApi(httpClient)

    private val schoolRepository = KtorSchoolRepository(schoolApi)
    private val forecastRepository = KtorForecastRepository(forecastApi)
    private val favoritesRepository = KtorFavoritesRepository(favoritesApi)
    private val noteRepository = KtorNoteRepository(noteApi)
    private val profileRepository = KtorProfileRepository(profileApi)
    private val notificationsRepository = KtorNotificationsRepository(notificationApi)

    // Use cases públicos del MVP (sin auth). Se irán añadiendo más a medida
    // que las pantallas iOS los necesiten.
    val getSchools = GetSchoolsUseCase(schoolRepository)
    val getSchoolById = GetSchoolByIdUseCase(schoolRepository)
    val searchSchools = SearchSchoolsUseCase(schoolRepository)
    val getForecast = GetForecastUseCase(forecastRepository)
    val getForecastByLocation = GetForecastByLocationUseCase(forecastRepository)
    val getTodayScores = GetTodayScoresUseCase(forecastRepository)

    // Favoritas (requieren sesión; el token lo aporta el authService del
    // httpClient). Estrella en lista/detalle + grid en el tab Tiempo.
    val getMyFavorites = GetMyFavoritesUseCase(favoritesRepository)
    val addFavorite = AddFavoriteUseCase(favoritesRepository)
    val removeFavorite = RemoveFavoriteUseCase(favoritesRepository)

    // Notas comunitarias del detalle de escuela (leer público, crear requiere
    // sesión). Foto adjunta pendiente del bridge de Firebase Storage.
    val getNotes = GetNotesUseCase(noteRepository)
    val createNote = CreateNoteUseCase(noteRepository)

    // Perfil privado (JIT provisioning en el primer getMyProfile).
    val getMyProfile = GetMyProfileUseCase(profileRepository)
    val updateMyProfile = UpdateMyProfileUseCase(profileRepository)

    // Notificaciones / inbox.
    val getMyNotifications = GetMyNotificationsUseCase(notificationsRepository)
    val markNotificationRead = MarkNotificationReadUseCase(notificationsRepository)
    val markAllNotificationsRead = MarkAllNotificationsReadUseCase(notificationsRepository)

    // Caché local del catálogo (stale-while-revalidate): la lista pinta desde
    // aquí al instante y refresca desde red después. Null si no hay BD.
    val cachedSchools: CachedSchoolsRepository? = database?.let { CachedSchoolsRepository(it) }
}
