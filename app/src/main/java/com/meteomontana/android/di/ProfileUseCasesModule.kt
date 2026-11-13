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


/** Casos de uso de la persona: perfil, altura, restricciones, recompensas, logros, objetivo, notificaciones, diario y envíos. */
@Module
@InstallIn(SingletonComponent::class)
object ProfileUseCasesModule {
    // Profile
    @Provides @Singleton
    fun provideGetMyProfileUseCase(repo: ProfileRepository) = GetMyProfileUseCase(repo)

    // Mi altura (más o menos de 1,70 m), para agrupar mis votos de grado.
    @Provides @Singleton
    fun provideGetMyHeightUseCase(
        repo: ProfileRepository, pending: com.meteomontana.android.domain.port.PendingHeightStore,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ) = com.meteomontana.android.domain.usecase.profile.GetMyHeightUseCase(
        repo, pending, com.meteomontana.android.data.local.SharedPrefsKeyValueStore(context))

    // Sin red la altura se guarda en la cola offline y la sube el OutboxFlusher al volver la conexión.
    @Provides @Singleton
    fun provideSetMyHeightUseCase(
        repo: ProfileRepository, pending: com.meteomontana.android.domain.port.PendingHeightStore,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ) = com.meteomontana.android.domain.usecase.profile.SetMyHeightUseCase(
        repo, pending, com.meteomontana.android.data.local.SharedPrefsKeyValueStore(context))

    @Provides @Singleton
    fun providePendingHeightStore(outbox: com.meteomontana.android.data.outbox.OutboxRepository):
        com.meteomontana.android.domain.port.PendingHeightStore =
        com.meteomontana.android.data.outbox.OutboxPendingHeightStore(outbox)

    @Provides @Singleton
    fun provideFlushPendingHeightUseCase(repo: ProfileRepository, pending: com.meteomontana.android.domain.port.PendingHeightStore) =
        com.meteomontana.android.domain.usecase.profile.FlushPendingHeightUseCase(repo, pending)

    // Restricciones de escalada ("!" en sectores y bloques).
    @Provides @Singleton
    fun provideRestrictionRepository(client: io.ktor.client.HttpClient): RestrictionRepository =
        com.meteomontana.android.data.repository.KtorRestrictionRepository(
            com.meteomontana.android.data.api.KtorRestrictionApi(client))

    @Provides @Singleton
    fun provideGetActiveRestrictionsUseCase(repo: RestrictionRepository) =
        com.meteomontana.android.domain.usecase.restriction.GetActiveRestrictionsUseCase(repo)

    @Provides @Singleton
    fun provideGetSchoolActiveRestrictionsUseCase(repo: RestrictionRepository) =
        com.meteomontana.android.domain.usecase.restriction.GetSchoolActiveRestrictionsUseCase(repo)

    // Recompensas: nivel, título, insignias y viero/bloquero.
    @Provides @Singleton
    fun provideRewardsRepository(client: io.ktor.client.HttpClient): com.meteomontana.android.domain.repository.RewardsRepository =
        com.meteomontana.android.data.repository.KtorRewardsRepository(com.meteomontana.android.data.api.KtorRewardsApi(client))

    @Provides @Singleton
    fun provideGetUserRewardsUseCase(repo: com.meteomontana.android.domain.repository.RewardsRepository) =
        com.meteomontana.android.domain.usecase.profile.GetUserRewardsUseCase(repo)

    @Provides @Singleton
    fun provideGetMyRewardsUseCase(repo: com.meteomontana.android.domain.repository.RewardsRepository) =
        com.meteomontana.android.domain.usecase.profile.GetMyRewardsUseCase(repo)

    @Provides @Singleton
    fun provideUpdateMyRewardsUseCase(repo: com.meteomontana.android.domain.repository.RewardsRepository) =
        com.meteomontana.android.domain.usecase.profile.UpdateMyRewardsUseCase(repo)

    @Provides
    @Singleton
    fun provideGetUserBadgesUseCase(repo: com.meteomontana.android.domain.repository.RewardsRepository) =
        com.meteomontana.android.domain.usecase.profile.GetUserBadgesUseCase(repo)

    // Logros: sincronizar los míos y cuánta gente tiene cada uno.
    @Provides @Singleton
    fun provideAchievementsRepository(client: io.ktor.client.HttpClient): com.meteomontana.android.domain.repository.AchievementsRepository =
        com.meteomontana.android.data.repository.KtorAchievementsRepository(com.meteomontana.android.data.api.KtorAchievementsApi(client))

    @Provides @Singleton
    fun provideSyncMyAchievementsUseCase(repo: com.meteomontana.android.domain.repository.AchievementsRepository) =
        com.meteomontana.android.domain.usecase.journal.SyncMyAchievementsUseCase(repo)

    @Provides @Singleton
    fun provideGetMyAchievementIdsUseCase(repo: com.meteomontana.android.domain.repository.AchievementsRepository) =
        com.meteomontana.android.domain.usecase.journal.GetMyAchievementIdsUseCase(repo)

    @Provides @Singleton
    fun provideGetAchievementRarityUseCase(repo: com.meteomontana.android.domain.repository.AchievementsRepository) =
        com.meteomontana.android.domain.usecase.journal.GetAchievementRarityUseCase(repo)

    // Objetivo del año (pantalla de Estadísticas).
    @Provides @Singleton
    fun provideGoalRepository(client: io.ktor.client.HttpClient): com.meteomontana.android.domain.repository.GoalRepository =
        com.meteomontana.android.data.repository.KtorGoalRepository(com.meteomontana.android.data.api.KtorGoalApi(client))

    @Provides @Singleton
    fun provideGetYearGoalUseCase(repo: com.meteomontana.android.domain.repository.GoalRepository) =
        com.meteomontana.android.domain.usecase.journal.GetYearGoalUseCase(repo)

    @Provides @Singleton
    fun provideSaveYearGoalUseCase(repo: com.meteomontana.android.domain.repository.GoalRepository) =
        com.meteomontana.android.domain.usecase.journal.SaveYearGoalUseCase(repo)

    @Provides @Singleton
    fun provideDeleteYearGoalUseCase(repo: com.meteomontana.android.domain.repository.GoalRepository) =
        com.meteomontana.android.domain.usecase.journal.DeleteYearGoalUseCase(repo)

    // Mejor hora por sector (fila plegable de la tarjeta de la nota).
    @Provides @Singleton
    fun provideGetSectorHoursUseCase(client: io.ktor.client.HttpClient) =
        com.meteomontana.android.domain.usecase.forecast.GetSectorHoursUseCase(
            com.meteomontana.android.data.repository.KtorSectorHoursRepository(
                com.meteomontana.android.data.api.KtorSectorHoursApi(client)))

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

    // Journal
    @Provides @Singleton
    fun provideGetMyJournalUseCase(repo: JournalRepository) = GetMyJournalUseCase(repo)

    @Provides
    fun provideUpdateJournalDateUseCase(repo: JournalRepository) =
        com.meteomontana.android.domain.usecase.journal.UpdateJournalDateUseCase(repo)

    @Provides
    fun provideUpdateJournalStyleUseCase(repo: JournalRepository) =
        com.meteomontana.android.domain.usecase.journal.UpdateJournalStyleUseCase(repo)

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
    fun provideGetGradeVotesUseCase(
        repo: com.meteomontana.android.domain.repository.CommunityRepository,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
    ) = com.meteomontana.android.domain.usecase.community.GetGradeVotesUseCase(
        repo, com.meteomontana.android.data.local.SharedPrefsKeyValueStore(context))

    @Provides
    fun provideVoteGradeUseCase(repo: com.meteomontana.android.domain.repository.CommunityRepository) =
        com.meteomontana.android.domain.usecase.community.VoteGradeUseCase(repo)

    @Provides @Singleton
    fun provideRemoveGradeVoteUseCase(repo: com.meteomontana.android.domain.repository.CommunityRepository) =
        com.meteomontana.android.domain.usecase.community.RemoveGradeVoteUseCase(repo)

    @Provides @Singleton
    fun provideGetGradeSplitsUseCase(repo: com.meteomontana.android.domain.repository.CommunityRepository) =
        com.meteomontana.android.domain.usecase.community.GetGradeSplitsUseCase(repo)

    @Provides @Singleton
    fun provideGetSchoolGradeSplitsUseCase(repo: com.meteomontana.android.domain.repository.CommunityRepository) =
        com.meteomontana.android.domain.usecase.community.GetSchoolGradeSplitsUseCase(repo)

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

}
