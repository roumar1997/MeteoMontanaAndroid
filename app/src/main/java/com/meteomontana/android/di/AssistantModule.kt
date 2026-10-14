package com.meteomontana.android.di

import com.meteomontana.android.data.api.KtorAssistantApi
import com.meteomontana.android.data.repository.KtorAssistantRepository
import com.meteomontana.android.domain.repository.AdminRepository
import com.meteomontana.android.domain.repository.AssistantRepository
import com.meteomontana.android.domain.usecase.admin.GetUserAssistantAccessUseCase
import com.meteomontana.android.domain.usecase.admin.SetUserAssistantAccessUseCase
import com.meteomontana.android.domain.usecase.assistant.AskAssistantUseCase
import com.meteomontana.android.domain.usecase.assistant.GetAssistantAccessUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

/** El asistente de búsqueda con IA (POST /api/assistant/ask): el cliente, el repositorio y el caso de uso. */
@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {

    @Provides @Singleton
    fun provideAssistantRepository(client: HttpClient): AssistantRepository =
        KtorAssistantRepository(KtorAssistantApi(client))

    @Provides @Singleton
    fun provideAskAssistantUseCase(repo: AssistantRepository) = AskAssistantUseCase(repo)

    @Provides @Singleton
    fun provideGetAssistantAccessUseCase(repo: AssistantRepository) = GetAssistantAccessUseCase(repo)

    // Admin: activar el asistente a un usuario concreto desde su perfil.
    @Provides @Singleton
    fun provideGetUserAssistantAccessUseCase(repo: AdminRepository) = GetUserAssistantAccessUseCase(repo)

    @Provides @Singleton
    fun provideSetUserAssistantAccessUseCase(repo: AdminRepository) = SetUserAssistantAccessUseCase(repo)
}
