package com.meteomontana.android.di

import com.meteomontana.android.data.api.KtorAdminApi
import com.meteomontana.android.data.api.KtorBlockApi
import com.meteomontana.android.data.api.KtorContributionApi
import com.meteomontana.android.data.api.KtorFavoritesApi
import com.meteomontana.android.data.api.KtorForecastApi
import com.meteomontana.android.data.api.KtorNotificationApi
import com.meteomontana.android.data.api.KtorNoteApi
import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.KtorSchoolApi
import com.meteomontana.android.data.auth.FirebaseAuthService
import com.meteomontana.android.data.chat.FirebaseChatService
import com.meteomontana.android.data.repository.KtorAdminRepository
import com.meteomontana.android.data.repository.KtorBlockRepository
import com.meteomontana.android.data.repository.KtorContributionRepository
import com.meteomontana.android.data.repository.KtorFavoritesRepository
import com.meteomontana.android.data.repository.KtorForecastRepository
import com.meteomontana.android.data.repository.KtorNotificationsRepository
import com.meteomontana.android.data.repository.KtorNoteRepository
import com.meteomontana.android.data.repository.KtorProfileRepository
import com.meteomontana.android.data.repository.KtorSchoolRepository
import com.meteomontana.android.data.storage.AndroidFileReader
import com.meteomontana.android.data.storage.FirebaseStoragePhotoUploader
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.domain.port.PhotoUploader
import com.meteomontana.android.domain.repository.AdminRepository
import com.meteomontana.android.domain.repository.BlockRepository
import com.meteomontana.android.domain.repository.ContributionRepository
import com.meteomontana.android.domain.repository.FavoritesRepository
import com.meteomontana.android.domain.repository.ForecastRepository
import com.meteomontana.android.domain.repository.NotificationsRepository
import com.meteomontana.android.domain.repository.NoteRepository
import com.meteomontana.android.domain.repository.ProfileRepository
import com.meteomontana.android.domain.repository.SchoolRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // Firebase/Android platform services keep @Binds (they have @Inject constructors)
    @Binds @Singleton
    abstract fun bindPhotoUploader(impl: FirebaseStoragePhotoUploader): PhotoUploader

    @Binds @Singleton
    abstract fun bindAuthService(impl: FirebaseAuthService): AuthService

    @Binds @Singleton
    abstract fun bindChatService(impl: FirebaseChatService): ChatService

    @Binds @Singleton
    abstract fun bindFileReader(impl: AndroidFileReader): FileReader

    companion object {
        // Ktor repositories live in commonMain without @Inject → use @Provides
        @Provides @Singleton
        fun provideSchoolRepository(api: KtorSchoolApi): SchoolRepository =
            KtorSchoolRepository(api)

        @Provides @Singleton
        fun provideForecastRepository(api: KtorForecastApi): ForecastRepository =
            KtorForecastRepository(api)

        @Provides @Singleton
        fun provideBlockRepository(api: KtorBlockApi): BlockRepository =
            KtorBlockRepository(api)

        @Provides @Singleton
        fun provideNoteRepository(api: KtorNoteApi): NoteRepository =
            KtorNoteRepository(api)

        @Provides @Singleton
        fun provideContributionRepository(api: KtorContributionApi): ContributionRepository =
            KtorContributionRepository(api)

        @Provides @Singleton
        fun provideFavoritesRepository(api: KtorFavoritesApi): FavoritesRepository =
            KtorFavoritesRepository(api)

        @Provides @Singleton
        fun provideProfileRepository(api: KtorProfileApi): ProfileRepository =
            KtorProfileRepository(api)

        @Provides @Singleton
        fun provideNotificationsRepository(api: KtorNotificationApi): NotificationsRepository =
            KtorNotificationsRepository(api)

        @Provides @Singleton
        fun provideAdminRepository(api: KtorAdminApi): AdminRepository =
            KtorAdminRepository(api)
    }
}
