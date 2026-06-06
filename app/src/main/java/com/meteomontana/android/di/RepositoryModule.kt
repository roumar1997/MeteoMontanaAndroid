package com.meteomontana.android.di

import com.meteomontana.android.data.auth.FirebaseAuthService
import com.meteomontana.android.data.chat.FirebaseChatService
import com.meteomontana.android.data.repository.RetrofitAdminRepository
import com.meteomontana.android.data.repository.RetrofitBlockRepository
import com.meteomontana.android.data.repository.RetrofitFavoritesRepository
import com.meteomontana.android.data.repository.RetrofitForecastRepository
import com.meteomontana.android.data.repository.RetrofitNotificationsRepository
import com.meteomontana.android.data.repository.RetrofitNoteRepository
import com.meteomontana.android.data.repository.RetrofitProfileRepository
import com.meteomontana.android.data.repository.SchoolRepositoryImpl
import com.meteomontana.android.data.storage.AndroidFileReader
import com.meteomontana.android.data.storage.FirebaseStoragePhotoUploader
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.domain.port.PhotoUploader
import com.meteomontana.android.domain.repository.AdminRepository
import com.meteomontana.android.domain.repository.BlockRepository
import com.meteomontana.android.domain.repository.FavoritesRepository
import com.meteomontana.android.domain.repository.ForecastRepository
import com.meteomontana.android.domain.repository.NotificationsRepository
import com.meteomontana.android.domain.repository.NoteRepository
import com.meteomontana.android.domain.repository.ProfileRepository
import com.meteomontana.android.domain.repository.SchoolRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindSchoolRepository(impl: SchoolRepositoryImpl): SchoolRepository

    @Binds @Singleton
    abstract fun bindForecastRepository(impl: RetrofitForecastRepository): ForecastRepository

    @Binds @Singleton
    abstract fun bindBlockRepository(impl: RetrofitBlockRepository): BlockRepository

    @Binds @Singleton
    abstract fun bindNoteRepository(impl: RetrofitNoteRepository): NoteRepository

    @Binds @Singleton
    abstract fun bindFavoritesRepository(impl: RetrofitFavoritesRepository): FavoritesRepository

    @Binds @Singleton
    abstract fun bindProfileRepository(impl: RetrofitProfileRepository): ProfileRepository

    @Binds @Singleton
    abstract fun bindNotificationsRepository(impl: RetrofitNotificationsRepository): NotificationsRepository

    @Binds @Singleton
    abstract fun bindAdminRepository(impl: RetrofitAdminRepository): AdminRepository

    @Binds @Singleton
    abstract fun bindPhotoUploader(impl: FirebaseStoragePhotoUploader): PhotoUploader

    @Binds @Singleton
    abstract fun bindAuthService(impl: FirebaseAuthService): AuthService

    @Binds @Singleton
    abstract fun bindChatService(impl: FirebaseChatService): ChatService

    @Binds @Singleton
    abstract fun bindFileReader(impl: AndroidFileReader): FileReader
}
