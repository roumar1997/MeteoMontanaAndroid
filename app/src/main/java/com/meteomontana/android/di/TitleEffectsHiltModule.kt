package com.meteomontana.android.di

import android.content.Context
import com.meteomontana.android.data.api.KtorTitleEffectApi
import com.meteomontana.android.data.local.SharedPrefsKeyValueStore
import com.meteomontana.android.data.repository.TitleEffectRepositoryImpl
import com.meteomontana.android.domain.repository.TitleEffectRepository
import com.meteomontana.android.domain.usecase.titleeffects.ObserveTitleEffectsUseCase
import com.meteomontana.android.domain.usecase.titleeffects.RefreshTitleEffectsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

/** Catálogo de efectos de título (GET /api/title-effects), con copia en SharedPreferences. */
@Module
@InstallIn(SingletonComponent::class)
object TitleEffectsHiltModule {

    @Provides @Singleton
    fun provideTitleEffectRepository(client: HttpClient, @ApplicationContext context: Context): TitleEffectRepository =
        TitleEffectRepositoryImpl(KtorTitleEffectApi(client), SharedPrefsKeyValueStore(context))

    @Provides @Singleton
    fun provideObserveTitleEffects(repo: TitleEffectRepository) = ObserveTitleEffectsUseCase(repo)

    @Provides @Singleton
    fun provideRefreshTitleEffects(repo: TitleEffectRepository) = RefreshTitleEffectsUseCase(repo)
}
