package com.meteomontana.android.di

import android.content.Context
import com.meteomontana.android.data.local.SharedPrefsKeyValueStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

/**
 * Rocódromos: el grafo entero vive en `shared` ([GymsModule], el mismo que usa iOS); aquí solo se
 * crea una vez (con copia sin conexión en SharedPreferences) y se reparten sus use cases.
 */
@Module
@InstallIn(SingletonComponent::class)
object GymsHiltModule {

    @Provides @Singleton
    fun provideGymsModule(client: HttpClient, @ApplicationContext context: Context): GymsModule =
        GymsModule(client, SharedPrefsKeyValueStore(context, "cumbre_gyms"))

    @Provides fun provideGetCachedGyms(m: GymsModule) = m.getCachedGyms
    @Provides fun provideGetGyms(m: GymsModule) = m.getGyms
    @Provides fun provideGetGym(m: GymsModule) = m.getGym
    @Provides fun provideFindGymsNear(m: GymsModule) = m.findGymsNear
    @Provides fun provideFilterGyms(m: GymsModule) = m.filterGyms
    @Provides fun provideClusterGyms(m: GymsModule) = m.clusterGyms
    @Provides fun provideShouldSuggestGyms(m: GymsModule) = m.shouldSuggestGyms
    @Provides fun provideGetGymFavorites(m: GymsModule) = m.getGymFavorites
    @Provides fun provideSetGymFavorite(m: GymsModule) = m.setGymFavorite
    @Provides fun provideSubmitGymCorrection(m: GymsModule) = m.submitGymCorrection
    @Provides fun provideSubmitNewGym(m: GymsModule) = m.submitNewGym
    @Provides fun provideUploadGymReportPhoto(m: GymsModule) = m.uploadGymReportPhoto
    @Provides fun provideGetGymPresence(m: GymsModule) = m.getGymPresence
    @Provides fun provideMarkGymPresence(m: GymsModule) = m.markGymPresence
    @Provides fun provideClearGymPresence(m: GymsModule) = m.clearGymPresence
    @Provides fun provideRecordGymSession(m: GymsModule) = m.recordGymSession
    @Provides fun provideGetGymStats(m: GymsModule) = m.getGymStats
    @Provides fun provideGymTraining(m: GymsModule) = m.gymTraining
    @Provides fun provideGetGymSummary(m: GymsModule) = m.getGymSummary
    @Provides fun provideHomeGym(m: GymsModule) = m.homeGym
    @Provides fun provideAscentOrder(m: GymsModule) = m.ascentOrder
    @Provides fun provideGymOrder(m: GymsModule) = m.gymOrder
}
