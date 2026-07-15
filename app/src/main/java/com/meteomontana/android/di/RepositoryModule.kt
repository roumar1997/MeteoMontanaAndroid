package com.meteomontana.android.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.meteomontana.android.data.api.KtorAdminApi
import com.meteomontana.android.data.api.KtorBlockApi
import com.meteomontana.android.data.api.KtorContributionApi
import com.meteomontana.android.data.api.KtorFavoritesApi
import com.meteomontana.android.data.api.KtorForecastApi
import com.meteomontana.android.data.api.KtorJournalApi
import com.meteomontana.android.data.api.KtorNotificationApi
import com.meteomontana.android.data.api.KtorNoteApi
import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.KtorSchoolApi
import com.meteomontana.android.data.api.KtorSocialApi
import com.meteomontana.android.data.api.KtorSubmissionApi
import com.meteomontana.android.data.auth.FirebaseAuthService
import com.meteomontana.android.data.chat.FirebaseChatService
import com.meteomontana.android.data.repository.KtorAdminRepository
import com.meteomontana.android.data.repository.KtorBlockRepository
import com.meteomontana.android.data.repository.KtorContributionRepository
import com.meteomontana.android.data.repository.KtorFavoritesRepository
import com.meteomontana.android.data.repository.KtorForecastRepository
import com.meteomontana.android.data.repository.KtorJournalRepository
import com.meteomontana.android.data.repository.KtorNotificationsRepository
import com.meteomontana.android.data.repository.KtorNoteRepository
import com.meteomontana.android.data.repository.KtorProfileRepository
import com.meteomontana.android.data.repository.KtorSchoolRepository
import com.meteomontana.android.data.repository.KtorSocialRepository
import com.meteomontana.android.data.repository.KtorSubmissionRepository
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
import com.meteomontana.android.domain.repository.JournalRepository
import com.meteomontana.android.domain.repository.NotificationsRepository
import com.meteomontana.android.domain.repository.NoteRepository
import com.meteomontana.android.domain.repository.ProfileRepository
import com.meteomontana.android.domain.repository.SchoolRepository
import com.meteomontana.android.domain.repository.SocialRepository
import com.meteomontana.android.domain.repository.SubmissionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // Firebase/Android platform services — live in shared/androidMain, no @Inject
    @Provides @Singleton
    fun provideAuthService(auth: FirebaseAuth): AuthService =
        FirebaseAuthService(auth)

    @Provides @Singleton
    fun provideChatService(firestore: FirebaseFirestore, auth: FirebaseAuth): ChatService =
        FirebaseChatService(firestore, auth)

    @Provides @Singleton
    fun providePhotoUploader(
        photoApi: com.meteomontana.android.data.api.KtorPhotoApi
    ): PhotoUploader = FirebaseStoragePhotoUploader(photoApi)

    @Provides @Singleton
    fun provideFileReader(@ApplicationContext context: Context): FileReader =
        AndroidFileReader(context)

    @Provides @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context):
        com.meteomontana.android.domain.port.NetworkMonitor =
        com.meteomontana.android.data.network.AndroidNetworkMonitor(context)

    // Ktor repositories live in commonMain without @Inject
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

    @Provides @Singleton
    fun provideJournalRepository(api: KtorJournalApi): JournalRepository =
        KtorJournalRepository(api)

    @Provides @Singleton
    fun provideSubmissionRepository(api: KtorSubmissionApi): SubmissionRepository =
        KtorSubmissionRepository(api)

    @Provides @Singleton
    fun provideSocialRepository(api: KtorSocialApi): SocialRepository =
        KtorSocialRepository(api)

    @Provides @Singleton
    fun provideFeedRepository(
        api: com.meteomontana.android.data.api.KtorFeedApi
    ): com.meteomontana.android.domain.repository.FeedRepository =
        com.meteomontana.android.data.repository.KtorFeedRepository(api)
}
