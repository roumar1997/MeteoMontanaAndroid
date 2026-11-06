package com.meteomontana.android.di

import android.content.Context
import com.meteomontana.android.data.api.KtorFeatureFlagApi
import com.meteomontana.android.data.local.SharedPrefsKeyValueStore
import com.meteomontana.android.data.repository.FeatureFlagRepositoryImpl
import com.meteomontana.android.domain.repository.FeatureFlagRepository
import com.meteomontana.android.domain.usecase.featureflags.ObserveFeatureFlagsUseCase
import com.meteomontana.android.domain.usecase.featureflags.RefreshFeatureFlagsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

/** Interruptores de funciones del servidor (GET /api/feature-flags), con copia en SharedPreferences. */
@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagsHiltModule {

    @Provides @Singleton
    fun provideFeatureFlagRepository(client: HttpClient, @ApplicationContext context: Context): FeatureFlagRepository =
        FeatureFlagRepositoryImpl(KtorFeatureFlagApi(client), SharedPrefsKeyValueStore(context))

    @Provides @Singleton
    fun provideObserveFeatureFlags(repo: FeatureFlagRepository) = ObserveFeatureFlagsUseCase(repo)

    @Provides @Singleton
    fun provideRefreshFeatureFlags(repo: FeatureFlagRepository) = RefreshFeatureFlagsUseCase(repo)
}
