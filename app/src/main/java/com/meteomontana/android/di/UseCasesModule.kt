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
    fun provideSearchLinesUseCase(repo: SchoolRepository) =
        com.meteomontana.android.domain.usecase.schools.SearchLinesUseCase(repo)

    @Provides @Singleton
    fun provideGetTodayScoresUseCase(repo: ForecastRepository) = GetTodayScoresUseCase(repo)

    // Radar (frames + PNG) — antes RadarViewModel usaba KtorRadarApi directo.
    @Provides @Singleton
    fun provideGetRadarFramesUseCase(
        repo: com.meteomontana.android.domain.repository.RadarRepository
    ) = com.meteomontana.android.domain.usecase.radar.GetRadarFramesUseCase(repo)

    @Provides @Singleton
    fun provideGetRadarFramePngUseCase(
        repo: com.meteomontana.android.domain.repository.RadarRepository
    ) = com.meteomontana.android.domain.usecase.radar.GetRadarFramePngUseCase(repo)

    // Moderación (consola de admin) — antes AdminViewModel usaba KtorModerationApi directo.
    @Provides @Singleton
    fun provideGetContentReportsUseCase(r: com.meteomontana.android.domain.repository.ModerationRepository) =
        com.meteomontana.android.domain.usecase.admin.GetContentReportsUseCase(r)
    @Provides @Singleton
    fun provideResolveContentReportUseCase(r: com.meteomontana.android.domain.repository.ModerationRepository) =
        com.meteomontana.android.domain.usecase.admin.ResolveContentReportUseCase(r)
    @Provides @Singleton
    fun provideGetAdminUsersUseCase(r: com.meteomontana.android.domain.repository.ModerationRepository) =
        com.meteomontana.android.domain.usecase.admin.GetAdminUsersUseCase(r)
    @Provides @Singleton
    fun provideGetAdminNotesUseCase(r: com.meteomontana.android.domain.repository.ModerationRepository) =
        com.meteomontana.android.domain.usecase.admin.GetAdminNotesUseCase(r)
    @Provides @Singleton
    fun provideGetUserModerationUseCase(r: com.meteomontana.android.domain.repository.ModerationRepository) =
        com.meteomontana.android.domain.usecase.admin.GetUserModerationUseCase(r)
    @Provides @Singleton
    fun provideWarnUserUseCase(r: com.meteomontana.android.domain.repository.ModerationRepository) =
        com.meteomontana.android.domain.usecase.admin.WarnUserUseCase(r)
    @Provides @Singleton
    fun provideSuspendUserUseCase(r: com.meteomontana.android.domain.repository.ModerationRepository) =
        com.meteomontana.android.domain.usecase.admin.SuspendUserUseCase(r)
    @Provides @Singleton
    fun provideBanUserUseCase(r: com.meteomontana.android.domain.repository.ModerationRepository) =
        com.meteomontana.android.domain.usecase.admin.BanUserUseCase(r)
    @Provides @Singleton
    fun provideUnbanUserUseCase(r: com.meteomontana.android.domain.repository.ModerationRepository) =
        com.meteomontana.android.domain.usecase.admin.UnbanUserUseCase(r)

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
    fun provideRateLineUseCase(repo: com.meteomontana.android.domain.repository.BlockRepository) =
        com.meteomontana.android.domain.usecase.blocks.RateLineUseCase(repo)

    // Comentarios de piedras/vías — antes LineCommentsViewModel usaba KtorBlockApi.
    @Provides @Singleton
    fun provideGetLineCommentsUseCase(repo: BlockRepository) =
        com.meteomontana.android.domain.usecase.blocks.GetLineCommentsUseCase(repo)
    @Provides @Singleton
    fun provideAddLineCommentUseCase(repo: BlockRepository) =
        com.meteomontana.android.domain.usecase.blocks.AddLineCommentUseCase(repo)
    @Provides @Singleton
    fun provideVoteLineCommentUseCase(repo: BlockRepository) =
        com.meteomontana.android.domain.usecase.blocks.VoteLineCommentUseCase(repo)
    @Provides @Singleton
    fun provideDeleteLineCommentUseCase(repo: BlockRepository) =
        com.meteomontana.android.domain.usecase.blocks.DeleteLineCommentUseCase(repo)

    // Alerta de tiempo — antes WeekendAlertViewModel usaba KtorProfileApi.
    @Provides @Singleton
    fun provideGetWeekendAlertUseCase(repo: ProfileRepository) =
        com.meteomontana.android.domain.usecase.profile.GetWeekendAlertUseCase(repo)
    @Provides @Singleton
    fun provideUpdateWeekendAlertUseCase(repo: ProfileRepository) =
        com.meteomontana.android.domain.usecase.profile.UpdateWeekendAlertUseCase(repo)

    // Contributions
    @Provides @Singleton
    fun provideSubmitContributionUseCase(repo: ContributionRepository) =
        SubmitContributionUseCase(repo)

    @Provides @Singleton
    fun provideGetMyContributionsUseCase(repo: ContributionRepository) =
        GetMyContributionsUseCase(repo)

    // Chat push (crear conversación + notificar) — antes ChatViewModel usaba
    // KtorChatPushApi directamente (bypass de capas).
    @Provides @Singleton
    fun provideStartConversationUseCase(
        repo: com.meteomontana.android.domain.repository.ChatPushRepository
    ) = com.meteomontana.android.domain.usecase.chat.StartConversationUseCase(repo)

    @Provides @Singleton
    fun provideNotifyChatMessageUseCase(
        repo: com.meteomontana.android.domain.repository.ChatPushRepository
    ) = com.meteomontana.android.domain.usecase.chat.NotifyChatMessageUseCase(repo)

    // Notes
    @Provides @Singleton
    fun provideGetNotesUseCase(repo: NoteRepository) = GetNotesUseCase(repo)

    // Approaches (aproximaciones)
    @Provides @Singleton
    fun provideGetApproachesUseCase(
        repo: com.meteomontana.android.domain.repository.ApproachRepository
    ) = com.meteomontana.android.domain.usecase.approach.GetApproachesUseCase(repo)

    @Provides @Singleton
    fun provideCreateApproachUseCase(
        repo: com.meteomontana.android.domain.repository.ApproachRepository
    ) = com.meteomontana.android.domain.usecase.approach.CreateApproachUseCase(repo)

    @Provides @Singleton
    fun provideAddApproachPinUseCase(
        repo: com.meteomontana.android.domain.repository.ApproachRepository
    ) = com.meteomontana.android.domain.usecase.approach.AddApproachPinUseCase(repo)

    @Provides @Singleton
    fun provideDeleteApproachUseCase(
        repo: com.meteomontana.android.domain.repository.ApproachRepository
    ) = com.meteomontana.android.domain.usecase.approach.DeleteApproachUseCase(repo)

    @Provides @Singleton
    fun provideVoteNoteUseCase(repo: NoteRepository) =
        com.meteomontana.android.domain.usecase.notes.VoteNoteUseCase(repo)

    @Provides @Singleton
    fun provideMoveSchoolUseCase(
        repo: com.meteomontana.android.domain.repository.AdminRepository
    ) = com.meteomontana.android.domain.usecase.admin.MoveSchoolUseCase(repo)

    @Provides @Singleton
    fun provideGetMountainBulletinUseCase(
        repo: com.meteomontana.android.domain.repository.MountainRepository
    ) = com.meteomontana.android.domain.usecase.weather.GetMountainBulletinUseCase(repo)

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

    @Provides
    fun provideUpdateJournalDateUseCase(repo: JournalRepository) =
        com.meteomontana.android.domain.usecase.journal.UpdateJournalDateUseCase(repo)

    @Provides
    fun provideGetOrientationUseCase(repo: com.meteomontana.android.domain.repository.CommunityRepository) =
        com.meteomontana.android.domain.usecase.community.GetOrientationUseCase(repo)

    @Provides
    fun provideVoteOrientationUseCase(repo: com.meteomontana.android.domain.repository.CommunityRepository) =
        com.meteomontana.android.domain.usecase.community.VoteOrientationUseCase(repo)

    @Provides
    fun provideGetSchoolOrientationsUseCase(repo: com.meteomontana.android.domain.repository.CommunityRepository) =
        com.meteomontana.android.domain.usecase.community.GetSchoolOrientationsUseCase(repo)

    @Provides
    fun provideGetSunHoursUseCase(repo: com.meteomontana.android.domain.repository.CommunityRepository) =
        com.meteomontana.android.domain.usecase.community.GetSunHoursUseCase(repo)

    @Provides
    fun provideGetGradeVotesUseCase(repo: com.meteomontana.android.domain.repository.CommunityRepository) =
        com.meteomontana.android.domain.usecase.community.GetGradeVotesUseCase(repo)

    @Provides
    fun provideVoteGradeUseCase(repo: com.meteomontana.android.domain.repository.CommunityRepository) =
        com.meteomontana.android.domain.usecase.community.VoteGradeUseCase(repo)

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
    fun provideGetTopContributorsUseCase(repo: SocialRepository) =
        com.meteomontana.android.domain.usecase.social.GetTopContributorsUseCase(repo)

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

    // Feed social "Comunidad"
    @Provides @Singleton
    fun provideGetFeedPageUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.GetFeedPageUseCase(repo)

    @Provides @Singleton
    fun provideGetFeedPostUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.GetFeedPostUseCase(repo)

    @Provides @Singleton
    fun providePublishFeedPostUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.PublishFeedPostUseCase(repo)

    @Provides @Singleton
    fun provideUploadFeedPhotoUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.UploadFeedPhotoUseCase(repo)

    @Provides @Singleton
    fun provideDeleteFeedPostUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.DeleteFeedPostUseCase(repo)

    @Provides @Singleton
    fun provideLikeFeedPostUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.LikeFeedPostUseCase(repo)

    @Provides @Singleton
    fun provideUnlikeFeedPostUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.UnlikeFeedPostUseCase(repo)

    @Provides @Singleton
    fun provideGetFeedCommentsUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.GetFeedCommentsUseCase(repo)

    @Provides @Singleton
    fun provideAddFeedCommentUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.AddFeedCommentUseCase(repo)

    @Provides @Singleton
    fun provideDeleteFeedCommentUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.DeleteFeedCommentUseCase(repo)

    @Provides @Singleton
    fun provideLikeFeedCommentUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.LikeFeedCommentUseCase(repo)

    @Provides @Singleton
    fun provideUnlikeFeedCommentUseCase(repo: com.meteomontana.android.domain.repository.FeedRepository) =
        com.meteomontana.android.domain.usecase.feed.UnlikeFeedCommentUseCase(repo)

    // Meetups (quedadas) — los use cases dependen del PUERTO MeetupRepository.
    @Provides @Singleton
    fun provideMeetupRepository(
        api: com.meteomontana.android.data.api.KtorMeetupApi,
        cache: com.meteomontana.android.data.saved.MeetupCacheRepository
    ): com.meteomontana.android.domain.repository.MeetupRepository =
        com.meteomontana.android.data.repository.KtorMeetupRepository(api, cache)

    @Provides @Singleton
    fun provideGetMeetupsUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = GetMeetupsUseCase(repo)

    @Provides @Singleton
    fun provideGetMeetupUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = GetMeetupUseCase(repo)

    @Provides @Singleton
    fun provideCreateMeetupUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = CreateMeetupUseCase(repo)

    @Provides @Singleton
    fun provideUpdateMeetupUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = com.meteomontana.android.domain.usecase.meetups.UpdateMeetupUseCase(repo)

    @Provides @Singleton
    fun provideGetMeetupByConversationUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = com.meteomontana.android.domain.usecase.meetups.GetMeetupByConversationUseCase(repo)

    @Provides @Singleton
    fun provideJoinMeetupUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = JoinMeetupUseCase(repo)

    @Provides @Singleton
    fun provideLeaveMeetupUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = LeaveMeetupUseCase(repo)

    @Provides @Singleton
    fun provideKickMeetupMemberUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = KickMeetupMemberUseCase(repo)

    @Provides @Singleton
    fun provideDeleteMeetupUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = com.meteomontana.android.domain.usecase.meetups.DeleteMeetupUseCase(repo)

    @Provides @Singleton
    fun provideReportMeetupUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = ReportMeetupUseCase(repo)

    @Provides @Singleton
    fun provideGetMeetupAlertUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = com.meteomontana.android.domain.usecase.meetups.GetMeetupAlertUseCase(repo)

    @Provides @Singleton
    fun provideSetMeetupAlertUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = com.meteomontana.android.domain.usecase.meetups.SetMeetupAlertUseCase(repo)

    @Provides @Singleton
    fun provideUpdateMyGearUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = com.meteomontana.android.domain.usecase.meetups.UpdateMyGearUseCase(repo)
}
