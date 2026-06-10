package com.meteomontana.android.di

import android.content.Context
import com.meteomontana.android.data.local.DatabaseFactory
import com.meteomontana.android.data.saved.CachedSchoolsRepository
import com.meteomontana.android.data.saved.SavedSchoolRepository
import com.meteomontana.android.data.api.KtorSchoolApi
import com.meteomontana.android.data.stats.MonthlyStatsRepository
import com.meteomontana.db.MeteoMontanaDb
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): MeteoMontanaDb =
        DatabaseFactory(ctx).create()

    @Provides @Singleton
    fun provideMonthlyStatsRepository(
        db: MeteoMontanaDb,
        api: KtorSchoolApi
    ): MonthlyStatsRepository = MonthlyStatsRepository(db, api)

    @Provides @Singleton
    fun provideSavedSchoolRepository(db: MeteoMontanaDb): SavedSchoolRepository =
        SavedSchoolRepository(db)

    @Provides @Singleton
    fun provideCachedSchoolsRepository(db: MeteoMontanaDb): CachedSchoolsRepository =
        CachedSchoolsRepository(db)

    @Provides @Singleton
    fun provideOfflineTileManager(@ApplicationContext ctx: Context):
        com.meteomontana.android.data.map.OfflineTileManager =
        com.meteomontana.android.data.map.OfflineTileManager(ctx)

    @Provides @Singleton
    fun provideOutboxRepository(db: MeteoMontanaDb):
        com.meteomontana.android.data.outbox.OutboxRepository =
        com.meteomontana.android.data.outbox.OutboxRepository(db)
}
