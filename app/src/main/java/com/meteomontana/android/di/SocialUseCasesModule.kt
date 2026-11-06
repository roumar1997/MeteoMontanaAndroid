package com.meteomontana.android.di

import com.meteomontana.android.domain.repository.RestrictionRepository
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
import com.meteomontana.android.domain.usecase.blocks.CreateBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.DeleteBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.ReorderBlocksUseCase
import com.meteomontana.android.domain.usecase.blocks.AutoReorderBlocksUseCase
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


/** Casos de uso sociales: perfiles y follows, Comunidad, quedadas y «Estoy aquí». (antes todo en un UseCasesModule de 773 líneas). */
@Module
@InstallIn(SingletonComponent::class)
object SocialUseCasesModule {
    // Social
    @Provides @Singleton
    fun provideGetPublicProfileUseCase(
        repo: SocialRepository,
        db: com.meteomontana.db.MeteoMontanaDb,
        networkMonitor: com.meteomontana.android.domain.port.NetworkMonitor
    ) = GetPublicProfileUseCase(
        repo,
        com.meteomontana.android.data.saved.ProfileCacheRepository(db),
        networkMonitor
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
        cache: com.meteomontana.android.data.saved.MeetupCacheRepository,
        networkMonitor: com.meteomontana.android.domain.port.NetworkMonitor
    ): com.meteomontana.android.domain.repository.MeetupRepository =
        com.meteomontana.android.data.repository.KtorMeetupRepository(api, cache, networkMonitor)

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
    fun provideGetMeetupInviteLinkUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = com.meteomontana.android.domain.usecase.meetups.GetMeetupInviteLinkUseCase(repo)

    @Provides @Singleton
    fun provideUpdateMyGearUseCase(
        repo: com.meteomontana.android.domain.repository.MeetupRepository
    ) = com.meteomontana.android.domain.usecase.meetups.UpdateMyGearUseCase(repo)

    // "Estoy aquí" — presencia en una escuela. Puerto SchoolPresenceRepository.
    @Provides @Singleton
    fun provideSchoolPresenceRepository(
        api: com.meteomontana.android.data.api.KtorSchoolPresenceApi
    ): com.meteomontana.android.domain.repository.SchoolPresenceRepository =
        com.meteomontana.android.data.repository.KtorSchoolPresenceRepository(api)

    @Provides @Singleton
    fun provideGetSchoolPresenceUseCase(
        repo: com.meteomontana.android.domain.repository.SchoolPresenceRepository
    ) = com.meteomontana.android.domain.usecase.presence.GetSchoolPresenceUseCase(repo)

    @Provides @Singleton
    fun provideMarkSchoolPresenceUseCase(
        repo: com.meteomontana.android.domain.repository.SchoolPresenceRepository
    ) = com.meteomontana.android.domain.usecase.presence.MarkSchoolPresenceUseCase(repo)

    @Provides @Singleton
    fun provideClearSchoolPresenceUseCase(
        repo: com.meteomontana.android.domain.repository.SchoolPresenceRepository
    ) = com.meteomontana.android.domain.usecase.presence.ClearSchoolPresenceUseCase(repo)
}
