package com.meteomontana.android.di

import com.meteomontana.android.data.auth.FirebaseAuthService
import com.meteomontana.android.data.chat.FirebaseChatService
import com.meteomontana.android.data.repository.SchoolRepositoryImpl
import com.meteomontana.android.data.storage.AndroidFileReader
import com.meteomontana.android.data.storage.FirebaseStoragePhotoUploader
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.domain.port.PhotoUploader
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
    abstract fun bindPhotoUploader(impl: FirebaseStoragePhotoUploader): PhotoUploader

    @Binds @Singleton
    abstract fun bindAuthService(impl: FirebaseAuthService): AuthService

    @Binds @Singleton
    abstract fun bindChatService(impl: FirebaseChatService): ChatService

    @Binds @Singleton
    abstract fun bindFileReader(impl: AndroidFileReader): FileReader
}
