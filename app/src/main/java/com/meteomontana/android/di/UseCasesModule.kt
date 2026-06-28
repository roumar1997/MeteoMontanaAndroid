package com.meteomontana.android.di

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
import com.meteomontana.android.domain.usecase.admin.ApproveContributionUseCase
import com.meteomontana.android.domain.usecase.admin.ApproveSubmissionUseCase
import com.meteomontana.android.domain.usecase.admin.GetAdminLogsUseCase
import com.meteomontana.android.domain.usecase.admin.GetAdminStatsUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingContributionsUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingSubmissionsUseCase
import com.meteomontana.android.domain.usecase.admin.RejectContributionUseCase
import com.meteomontana.android.domain.usecase.admin.RejectSubmissionUseCase
import com.meteomontana.android.domain.usecase.admin.SendPushUseCase
import com.meteomontana.android.domain.usecase.blocks.CreateBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.DeleteBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.GetBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.blocks.UpdateBlockUseCase
import com.meteomontana.android.domain.usecase.contributions.GetMyContributionsUseCase
import com.meteomontana.android.domain.usecase.contributions.SubmitContributionUseCase
import com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastByLocationUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.domain.usecase.journal.CreateJournalEntryUseCase
import com.meteomontana.android.domain.usecase.journal.DeleteJournalEntryUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalStatsUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserJournalUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserStatsUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import com.meteomontana.android.domain.usecase.social.AcceptFollowRequestUseCase
import com.meteomontana.android.domain.usecase.social.FollowUserUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowersUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowingUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowStatusUseCase
import com.meteomontana.android.domain.usecase.social.GetMyFollowRequestsUseCase
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import com.meteomontana.android.domain.usecase.social.RejectFollowRequestUseCase
import com.meteomontana.android.domain.usecase.social.SearchUsersUseCase
import com.meteomontana.android.domain.usecase.social.UnfollowUserUseCase
import com.meteomontana.android.domain.usecase.submissions.GetMySubmissionsUseCase
import com.meteomontana.android.domain.usecase.submissions.SubmitSchoolUseCase
import com.meteomontana.android.domain.usecase.notes.CreateNoteUseCase
import com.meteomontana.android.domain.usecase.notes.GetNotesUseCase
import com.meteomontana.android.domain.usecase.notifications.GetMyNotificationsUseCase
import com.meteomontana.android.domain.usecase.notifications.MarkAllNotificationsReadUseCase
import com.meteomontana.android.domain.usecase.notifications.MarkNotificationReadUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateFcmTokenUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateMyProfileUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolByIdUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolsUseCase
import com.meteomontana.android.domain.usecase.schools.SearchSchoolsUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import com.meteomontana.android.domain.usecase.meetups.GetMeetupsUseCase
import com.meteomontana.android.domain.usecase.meetups.GetMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.CreateMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.JoinMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.LeaveMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.KickMeetupMemberUseCase
import com.meteomontana.android.domain.usecase.meetups.ReportMeetupUseCase
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
    fun provideGetSchoolCatalogUseCase(repo: SchoolRepository) =
        com.meteomontana.android.domain.usecase.schools.GetSchoolCatalogUseCase(repo)

    @Provides @Singleton
    fun provideGetSchoolByIdUseCase(repo: SchoolRepository) = GetSchoolByIdUseCase(repo)

    @Provides @Singleton
    fun provideGetTodayScoresUseCase(repo: ForecastRepository) = GetTodayScoresUseCase(repo)

    @Provides @Singleton
    fun provideGetRangeScoresUseCase(repo: ForecastRepository) =
        com.meteomontana.android.domain.usecase.schools.GetRangeScoresUseCase(repo)

    @Provides @Singleton
    fun provideSearchSchoolsUseCase(repo: SchoolRepository) = SearchSchoolsUseCase(repo)

    // Forecast
    @Provides @Singleton
    fun provideGetForecastUseCase(repo: ForecastRepository) = GetForecastUseCase(repo)

    @Provides @Singleton
    fun provideGetForecastByLocationUseCase(repo: ForecastRepository) =
        GetForecastByLocationUseCase(repo)

    // Widget Favoritas — la lógica de datos vive en shared/commonMain.
    @Provides @Singleton
    fun provideGetFavoritesWidgetDataUseCase(
        getMyFavorites: GetMyFavoritesUseCase,
        getTodayScores: GetTodayScoresUseCase,
        cachedSchools: com.meteomontana.android.data.saved.CachedSchoolsRepository,
        locationProvider: com.meteomontana.android.domain.port.LocationProvider
    ) = com.meteomontana.android.domain.usecase.widget.GetFavoritesWidgetDataUseCase(
        getMyFavorites, getTodayScores, cachedSchools, locationProvider
    )

    // Blocks
    @Provides @Singleton
    fun provideGetBlocksUseCase(repo: BlockRepository) = GetBlocksUseCase(repo)

    @Provides @Singleton
    fun provideGetBlockUseCase(repo: BlockRepository) = GetBlockUseCase(repo)

    @Provides @Singleton
    fun provideCreateBlockUseCase(repo: BlockRepository) = CreateBlockUseCase(repo)

    @Provides @Singleton
    fun provideUpdateBlockUseCase(repo: BlockRepository) = UpdateBlockUseCase(repo)

    @Provides @Singleton
    fun provideDeleteBlockUseCase(repo: BlockRepository) = DeleteBlockUseCase(repo)

    @Provides @Singleton
    fun provideRateLineUseCase(api: com.meteomontana.android.data.api.KtorBlockApi) =
        com.meteomontana.android.domain.usecase.blocks.RateLineUseCase(api)

    // Contributions
    @Provides @Singleton
    fun provideSubmitContributionUseCase(repo: ContributionRepository) =
        SubmitContributionUseCase(repo)

    @Provides @Singleton
    fun provideGetMyContributionsUseCase(repo: ContributionRepository) =
        GetMyContributionsUseCase(repo)

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

    @Provides @Singleton
    fun provideGetFavoritesGridUseCase(repo: FavoritesRepository) =
        com.meteomontana.android.domain.usecase.favorites.GetFavoritesGridUseCase(repo)

    // Profile
    @Provides @Singleton
    fun provideGetMyProfileUseCase(repo: ProfileRepository) = GetMyProfileUseCase(repo)

    @Provides @Singleton
    fun provideUpdateMyProfileUseCase(repo: ProfileRepository) = UpdateMyProfileUseCase(repo)

    @Provides @Singleton
    fun provideUpdateFcmTokenUseCase(repo: ProfileRepository) = UpdateFcmTokenUseCase(repo)

    @Provides @Singleton
    fun provideDeleteMyAccountUseCase(repo: ProfileRepository) =
        com.meteomontana.android.domain.usecase.profile.DeleteMyAccountUseCase(repo)

    // Notifications
    @Provides @Singleton
    fun provideGetMyNotificationsUseCase(repo: NotificationsRepository) =
        GetMyNotificationsUseCase(repo)

    @Provides @Singleton
    fun provideMarkNotificationReadUseCase(repo: NotificationsRepository) =
        MarkNotificationReadUseCase(repo)

    @Provides @Singleton
    fun provideMarkAllNotificationsReadUseCase(repo: NotificationsRepository) =
        MarkAllNotificationsReadUseCase(repo)

    @Provides @Singleton
    fun provideDeleteNotificationUseCase(repo: NotificationsRepository) =
        com.meteomontana.android.domain.usecase.notifications.DeleteNotificationUseCase(repo)

    @Provides @Singleton
    fun provideDeleteAllNotificationsUseCase(repo: NotificationsRepository) =
        com.meteomontana.android.domain.usecase.notifications.DeleteAllNotificationsUseCase(repo)

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

    @Provides @Singleton
    fun provideGetPendingReportsUseCase(repo: AdminRepository) =
        com.meteomontana.android.domain.usecase.admin.GetPendingReportsUseCase(repo)

    @Provides @Singleton
    fun provideResolveReportUseCase(repo: AdminRepository) =
        com.meteomontana.android.domain.usecase.admin.ResolveReportUseCase(repo)

    // Journal
    @Provides @Singleton
    fun provideGetMyJournalUseCase(repo: JournalRepository) = GetMyJournalUseCase(repo)

    @Provides @Singleton
    fun provideGetMyJournalStatsUseCase(repo: JournalRepository) = GetMyJournalStatsUseCase(repo)

    @Provides @Singleton
    fun provideGetUserStatsUseCase(repo: JournalRepository) = GetUserStatsUseCase(repo)

    @Provides @Singleton
    fun provideGetUserJournalUseCase(repo: JournalRepository) = GetUserJournalUseCase(repo)

    @Provides @Singleton
    fun provideCreateJournalEntryUseCase(repo: JournalRepository) = CreateJournalEntryUseCase(repo)

    @Provides @Singleton
    fun provideDeleteJournalEntryUseCase(repo: JournalRepository) = DeleteJournalEntryUseCase(repo)

    @Provides @Singleton
    fun provideGetJournalViaInfoUseCase(repo: BlockRepository) =
        com.meteomontana.android.domain.usecase.journal.GetJournalViaInfoUseCase(repo)

    // Submissions
    @Provides @Singleton
    fun provideGetMySubmissionsUseCase(repo: SubmissionRepository) = GetMySubmissionsUseCase(repo)

    @Provides @Singleton
    fun provideSubmitSchoolUseCase(repo: SubmissionRepository) = SubmitSchoolUseCase(repo)

    // Social
    @Provides @Singleton
    fun provideGetPublicProfileUseCase(
        repo: SocialRepository,
        db: com.meteomontana.db.MeteoMontanaDb
    ) = GetPublicProfileUseCase(
        repo,
        com.meteomontana.android.data.saved.ProfileCacheRepository(db)
    )

    @Provides @Singleton
    fun provideGetFollowStatusUseCase(repo: SocialRepository) = GetFollowStatusUseCase(repo)

    @Provides @Singleton
    fun provideSearchUsersUseCase(repo: SocialRepository) = SearchUsersUseCase(repo)

    @Provides @Singleton
    fun provideGetFollowersUseCase(repo: SocialRepository) = GetFollowersUseCase(repo)

    @Provides @Singleton
    fun provideGetFollowingUseCase(repo: SocialRepository) = GetFollowingUseCase(repo)

    @Provides @Singleton
    fun provideFollowUserUseCase(repo: SocialRepository) = FollowUserUseCase(repo)

    @Provides @Singleton
    fun provideUnfollowUserUseCase(repo: SocialRepository) = UnfollowUserUseCase(repo)

    @Provides @Singleton
    fun provideRemoveFollowerUseCase(repo: SocialRepository) =
        com.meteomontana.android.domain.usecase.social.RemoveFollowerUseCase(repo)

    @Provides @Singleton
    fun provideGetMyFollowRequestsUseCase(repo: SocialRepository) = GetMyFollowRequestsUseCase(repo)

    @Provides @Singleton
    fun provideAcceptFollowRequestUseCase(repo: SocialRepository) = AcceptFollowRequestUseCase(repo)

    @Provides @Singleton
    fun provideRejectFollowRequestUseCase(repo: SocialRepository) = RejectFollowRequestUseCase(repo)

    // Meetups (quedadas)
    @Provides @Singleton
    fun provideGetMeetupsUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi,
        cache: com.meteomontana.android.data.saved.MeetupCacheRepository
    ) = GetMeetupsUseCase(api, cache)

    @Provides @Singleton
    fun provideGetMeetupUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi,
        cache: com.meteomontana.android.data.saved.MeetupCacheRepository
    ) = GetMeetupUseCase(api, cache)

    @Provides @Singleton
    fun provideCreateMeetupUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi,
        cache: com.meteomontana.android.data.saved.MeetupCacheRepository
    ) = CreateMeetupUseCase(api, cache)

    @Provides @Singleton
    fun provideUpdateMeetupUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi,
        cache: com.meteomontana.android.data.saved.MeetupCacheRepository
    ) = com.meteomontana.android.domain.usecase.meetups.UpdateMeetupUseCase(api, cache)

    @Provides @Singleton
    fun provideGetMeetupByConversationUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi
    ) = com.meteomontana.android.domain.usecase.meetups.GetMeetupByConversationUseCase(api)

    @Provides @Singleton
    fun provideJoinMeetupUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi,
        cache: com.meteomontana.android.data.saved.MeetupCacheRepository
    ) = JoinMeetupUseCase(api, cache)

    @Provides @Singleton
    fun provideLeaveMeetupUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi,
        cache: com.meteomontana.android.data.saved.MeetupCacheRepository
    ) = LeaveMeetupUseCase(api, cache)

    @Provides @Singleton
    fun provideKickMeetupMemberUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi
    ) = KickMeetupMemberUseCase(api)

    @Provides @Singleton
    fun provideDeleteMeetupUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi
    ) = com.meteomontana.android.domain.usecase.meetups.DeleteMeetupUseCase(api)

    @Provides @Singleton
    fun provideReportMeetupUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi
    ) = ReportMeetupUseCase(api)

    @Provides @Singleton
    fun provideGetMeetupAlertUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi
    ) = com.meteomontana.android.domain.usecase.meetups.GetMeetupAlertUseCase(api)

    @Provides @Singleton
    fun provideSetMeetupAlertUseCase(
        api: com.meteomontana.android.data.api.KtorMeetupApi
    ) = com.meteomontana.android.domain.usecase.meetups.SetMeetupAlertUseCase(api)
}
