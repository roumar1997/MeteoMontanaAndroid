package com.meteomontana.android.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.meteomontana.android.BuildConfig
import com.meteomontana.android.data.api.KtorAdminApi
import com.meteomontana.android.data.api.KtorBlockApi
import com.meteomontana.android.data.api.KtorModerationApi
import com.meteomontana.android.data.api.KtorContributionApi
import com.meteomontana.android.data.api.KtorFavoritesApi
import com.meteomontana.android.data.api.KtorForecastApi
import com.meteomontana.android.data.api.KtorMountainApi
import com.meteomontana.android.data.api.KtorRadarApi
import com.meteomontana.android.data.api.KtorJournalApi
import com.meteomontana.android.data.api.KtorNotificationApi
import com.meteomontana.android.data.api.KtorNoteApi
import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.KtorSchoolApi
import com.meteomontana.android.data.api.KtorSocialApi
import com.meteomontana.android.data.api.KtorSubmissionApi
import com.meteomontana.android.data.api.buildApiHttpClient
import com.meteomontana.android.domain.port.AuthService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = Firebase.storage

    @Provides
    @Singleton
    fun provideHttpClient(authService: AuthService): HttpClient =
        buildApiHttpClient(BuildConfig.API_BASE_URL) {
            authService.currentIdToken(forceRefresh = false)
        }

    @Provides @Singleton
    fun provideKtorSchoolApi(client: HttpClient) = KtorSchoolApi(client)

    @Provides @Singleton
    fun provideKtorForecastApi(client: HttpClient) = KtorForecastApi(client)

    @Provides @Singleton
    fun provideKtorRadarApi(client: HttpClient) = KtorRadarApi(client)

    @Provides @Singleton
    fun provideKtorMountainApi(client: HttpClient) = KtorMountainApi(client)

    @Provides @Singleton
    fun provideKtorBlockApi(client: HttpClient) = KtorBlockApi(client)

    @Provides @Singleton
    fun provideKtorModerationApi(client: HttpClient) = KtorModerationApi(client)

    @Provides @Singleton
    fun provideKtorNoteApi(client: HttpClient) = KtorNoteApi(client)

    @Provides @Singleton
    fun provideKtorContributionApi(client: HttpClient) = KtorContributionApi(client)

    @Provides @Singleton
    fun provideKtorFavoritesApi(client: HttpClient) = KtorFavoritesApi(client)

    @Provides @Singleton
    fun provideKtorProfileApi(client: HttpClient) = KtorProfileApi(client)

    @Provides @Singleton
    fun provideKtorNotificationApi(client: HttpClient) = KtorNotificationApi(client)

    @Provides @Singleton
    fun provideKtorAdminApi(client: HttpClient) = KtorAdminApi(client)

    @Provides @Singleton
    fun provideKtorSubmissionApi(client: HttpClient) = KtorSubmissionApi(client)

    @Provides @Singleton
    fun provideKtorSocialApi(client: HttpClient) = KtorSocialApi(client)

    @Provides @Singleton
    fun provideKtorFeedApi(client: HttpClient) =
        com.meteomontana.android.data.api.KtorFeedApi(client)

    @Provides @Singleton
    fun provideKtorJournalApi(client: HttpClient) = KtorJournalApi(client)

    @Provides @Singleton
    fun provideKtorChatPushApi(client: HttpClient) =
        com.meteomontana.android.data.api.KtorChatPushApi(client)

    @Provides @Singleton
    fun provideKtorMeetupApi(client: HttpClient) =
        com.meteomontana.android.data.api.KtorMeetupApi(client)
}
