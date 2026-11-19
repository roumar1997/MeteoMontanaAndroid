package com.meteomontana.android.di

import com.meteomontana.android.data.api.KtorGymAdminApi
import com.meteomontana.android.data.api.KtorGymApi
import com.meteomontana.android.data.api.KtorGymPresenceApi
import com.meteomontana.android.data.api.KtorGymReportApi
import com.meteomontana.android.data.cache.KeyValueGymCatalogCache
import com.meteomontana.android.data.repository.KtorGymAdminRepository
import com.meteomontana.android.data.repository.KtorGymFavoriteRepository
import com.meteomontana.android.data.repository.KtorGymPresenceRepository
import com.meteomontana.android.data.repository.KtorGymReportRepository
import com.meteomontana.android.data.repository.KtorGymRepository
import com.meteomontana.android.domain.port.KeyValueStore
import com.meteomontana.android.domain.usecase.gym.AdminEditGymUseCase
import com.meteomontana.android.domain.usecase.gym.ApplyGymReportUseCase
import com.meteomontana.android.domain.usecase.gym.ClearGymPresenceUseCase
import com.meteomontana.android.domain.usecase.gym.ClusterGymsUseCase
import com.meteomontana.android.domain.usecase.gym.FilterGymsUseCase
import com.meteomontana.android.domain.usecase.gym.FindGymsNearUseCase
import com.meteomontana.android.domain.usecase.gym.GetCachedGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymFavoritesUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymPresenceUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymsUseCase
import com.meteomontana.android.domain.usecase.gym.ListPendingGymReportsUseCase
import com.meteomontana.android.domain.usecase.gym.MarkGymPresenceUseCase
import com.meteomontana.android.domain.usecase.gym.RejectGymReportUseCase
import com.meteomontana.android.domain.usecase.gym.SetGymFavoriteUseCase
import com.meteomontana.android.domain.usecase.gym.ShouldSuggestGymsUseCase
import com.meteomontana.android.domain.usecase.gym.SubmitGymCorrectionUseCase
import com.meteomontana.android.domain.usecase.gym.SubmitNewGymUseCase
import com.meteomontana.android.domain.usecase.gym.UploadGymReportPhotoUseCase
import io.ktor.client.HttpClient

/**
 * Rocodromos: el grafo completo en un modulo aparte (como [FeatureFlagsModule]) para que
 * [IosDependencyContainer] no siga creciendo. Android puede usar este mismo modulo desde Hilt.
 * Sin almacen, el catalogo vive solo en memoria (no hay copia sin conexion).
 */
class GymsModule(httpClient: HttpClient, store: KeyValueStore? = null) {
    private val api = KtorGymApi(httpClient)
    private val catalog = KtorGymRepository(api, KeyValueGymCatalogCache(store ?: InMemoryKeyValueStore()))
    private val favorites = KtorGymFavoriteRepository(api)
    private val reports = KtorGymReportRepository(KtorGymReportApi(httpClient))
    private val presence = KtorGymPresenceRepository(KtorGymPresenceApi(httpClient))
    private val admin = KtorGymAdminRepository(KtorGymAdminApi(httpClient))

    val getCachedGyms = GetCachedGymsUseCase(catalog)
    val getGyms = GetGymsUseCase(catalog)
    val getGym = GetGymUseCase(catalog)
    val findGymsNear = FindGymsNearUseCase(catalog)
    val filterGyms = FilterGymsUseCase()
    val clusterGyms = ClusterGymsUseCase()
    val shouldSuggestGyms = ShouldSuggestGymsUseCase()
    val getGymFavorites = GetGymFavoritesUseCase(favorites)
    val setGymFavorite = SetGymFavoriteUseCase(favorites)
    val submitGymCorrection = SubmitGymCorrectionUseCase(reports)
    val submitNewGym = SubmitNewGymUseCase(reports)
    val uploadGymReportPhoto = UploadGymReportPhotoUseCase(reports)
    val getGymPresence = GetGymPresenceUseCase(presence)
    val markGymPresence = MarkGymPresenceUseCase(presence)
    val clearGymPresence = ClearGymPresenceUseCase(presence)

    // Admin (solo iOS)
    val listPendingGymReports = ListPendingGymReportsUseCase(admin)
    val applyGymReport = ApplyGymReportUseCase(admin)
    val rejectGymReport = RejectGymReportUseCase(admin)
    val adminEditGym = AdminEditGymUseCase(admin)
}
