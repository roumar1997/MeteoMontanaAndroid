package com.meteomontana.android.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.meteomontana.android.BuildConfig
import com.meteomontana.android.data.api.AdminApi
import com.meteomontana.android.data.api.BlockApi
import com.meteomontana.android.data.api.ContributionApi
import com.meteomontana.android.data.api.FavoritesApi
import com.meteomontana.android.data.api.ForecastApi
import com.meteomontana.android.data.api.JournalApi
import com.meteomontana.android.data.api.NotificationApi
import com.meteomontana.android.data.api.NoteApi
import com.meteomontana.android.data.api.ProfileApi
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.SocialApi
import com.meteomontana.android.data.api.SubmissionApi
import com.meteomontana.android.data.auth.AuthInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
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
    fun provideMoshi(): Moshi =
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides @Singleton
    fun provideSchoolApi(retrofit: Retrofit): SchoolApi = retrofit.create(SchoolApi::class.java)

    @Provides @Singleton
    fun provideAdminApi(retrofit: Retrofit): AdminApi = retrofit.create(AdminApi::class.java)

    @Provides @Singleton
    fun provideForecastApi(retrofit: Retrofit): ForecastApi = retrofit.create(ForecastApi::class.java)

    @Provides @Singleton
    fun provideBlockApi(retrofit: Retrofit): BlockApi = retrofit.create(BlockApi::class.java)

    @Provides @Singleton
    fun provideNoteApi(retrofit: Retrofit): NoteApi = retrofit.create(NoteApi::class.java)

    @Provides @Singleton
    fun provideContributionApi(retrofit: Retrofit): ContributionApi = retrofit.create(ContributionApi::class.java)

    @Provides @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApi = retrofit.create(ProfileApi::class.java)

    @Provides @Singleton
    fun provideFavoritesApi(retrofit: Retrofit): FavoritesApi = retrofit.create(FavoritesApi::class.java)

    @Provides @Singleton
    fun provideJournalApi(retrofit: Retrofit): JournalApi = retrofit.create(JournalApi::class.java)

    @Provides @Singleton
    fun provideSubmissionApi(retrofit: Retrofit): SubmissionApi = retrofit.create(SubmissionApi::class.java)

    @Provides @Singleton
    fun provideSocialApi(retrofit: Retrofit): SocialApi = retrofit.create(SocialApi::class.java)

    @Provides @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApi = retrofit.create(NotificationApi::class.java)
}
