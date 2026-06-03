package com.meteomontana.android.di

import com.meteomontana.android.data.repository.SchoolRepositoryImpl
import com.meteomontana.android.domain.repository.SchoolRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt: cuando alguien pida SchoolRepository, devuelve SchoolRepositoryImpl.
 * Esto permite cambiar la implementación (p.ej. Fake en tests) sin tocar
 * el resto del código.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSchoolRepository(impl: SchoolRepositoryImpl): SchoolRepository
}
