package com.meteomontana.android.di

import com.meteomontana.android.domain.repository.AdminRepository
import com.meteomontana.android.domain.repository.BlockRepository
import com.meteomontana.android.domain.repository.FavoritesRepository
import com.meteomontana.android.domain.repository.ForecastRepository
import com.meteomontana.android.domain.repository.NotificationsRepository
import com.meteomontana.android.domain.repository.NoteRepository
import com.meteomontana.android.domain.repository.ProfileRepository
import com.meteomontana.android.domain.repository.SchoolRepository
import com.meteomontana.android.domain.usecase.admin.ApproveContributionUseCase
import com.meteomontana.android.domain.usecase.admin.ApproveSubmissionUseCase
import com.meteomontana.android.domain.usecase.admin.GetAdminLogsUseCase
import com.meteomontana.android.domain.usecase.admin.GetAdminStatsUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingContributionsUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingSubmissionsUseCase
import com.meteomontana.android.domain.usecase.admin.RejectContributionUseCase
import com.meteomontana.android.domain.usecase.admin.RejectSubmissionUseCase
import com.meteomontana.android.domain.usecase.admin.SendPushUseCase
import com.meteomontana.android.domain.usecase.blocks.DeleteBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.domain.usecase.notes.CreateNoteUseCase
import com.meteomontana.android.domain.usecase.notes.GetNotesUseCase
import com.meteomontana.android.domain.usecase.notifications.GetMyNotificationsUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolByIdUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolsUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCasesModule {

    // Schools
    @Provides @Singleton
    fun provideGetSchoolsUseCase(repo: SchoolRepository) = GetSchoolsUseCase(repo)

    @Provides @Singleton
    fun provideGetSchoolByIdUseCase(repo: SchoolRepository) = GetSchoolByIdUseCase(repo)

    @Provides @Singleton
    fun provideGetTodayScoresUseCase(repo: ForecastRepository) = GetTodayScoresUseCase(repo)

    // Forecast
    @Provides @Singleton
    fun provideGetForecastUseCase(repo: ForecastRepository) = GetForecastUseCase(repo)

    // Blocks
    @Provides @Singleton
    fun provideGetBlocksUseCase(repo: BlockRepository) = GetBlocksUseCase(repo)

    @Provides @Singleton
    fun provideDeleteBlockUseCase(repo: BlockRepository) = DeleteBlockUseCase(repo)

    // Notes
    @Provides @Singleton
    fun provideGetNotesUseCase(repo: NoteRepository) = GetNotesUseCase(repo)

    @Provides @Singleton
    fun provideCreateNoteUseCase(repo: NoteRepository) = CreateNoteUseCase(repo)

    // Favorites
    @Provides @Singleton
    fun provideGetMyFavoritesUseCase(repo: FavoritesRepository) = GetMyFavoritesUseCase(repo)

    @Provides @Singleton
    fun provideAddFavoriteUseCase(repo: FavoritesRepository) = AddFavoriteUseCase(repo)

    @Provides @Singleton
    fun provideRemoveFavoriteUseCase(repo: FavoritesRepository) = RemoveFavoriteUseCase(repo)

    // Profile
    @Provides @Singleton
    fun provideGetMyProfileUseCase(repo: ProfileRepository) = GetMyProfileUseCase(repo)

    // Notifications
    @Provides @Singleton
    fun provideGetMyNotificationsUseCase(repo: NotificationsRepository) =
        GetMyNotificationsUseCase(repo)

    // Admin
    @Provides @Singleton
    fun provideGetAdminStatsUseCase(repo: AdminRepository) = GetAdminStatsUseCase(repo)

    @Provides @Singleton
    fun provideGetPendingSubmissionsUseCase(repo: AdminRepository) =
        GetPendingSubmissionsUseCase(repo)

    @Provides @Singleton
    fun provideGetPendingContributionsUseCase(repo: AdminRepository) =
        GetPendingContributionsUseCase(repo)

    @Provides @Singleton
    fun provideGetAdminLogsUseCase(repo: AdminRepository) = GetAdminLogsUseCase(repo)

    @Provides @Singleton
    fun provideApproveSubmissionUseCase(repo: AdminRepository) = ApproveSubmissionUseCase(repo)

    @Provides @Singleton
    fun provideRejectSubmissionUseCase(repo: AdminRepository) = RejectSubmissionUseCase(repo)

    @Provides @Singleton
    fun provideApproveContributionUseCase(repo: AdminRepository) =
        ApproveContributionUseCase(repo)

    @Provides @Singleton
    fun provideRejectContributionUseCase(repo: AdminRepository) =
        RejectContributionUseCase(repo)

    @Provides @Singleton
    fun provideSendPushUseCase(repo: AdminRepository) = SendPushUseCase(repo)
}
