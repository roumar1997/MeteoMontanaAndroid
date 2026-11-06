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


/** Casos de uso del catálogo: escuelas, radar, tiempo, piedras, beta, aportaciones, chat, notas, aproximaciones y favoritas. (antes todo en un UseCasesModule de 773 líneas). */
@Module
@InstallIn(SingletonComponent::class)
object CatalogUseCasesModule {
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
    fun provideExploreLinesUseCase(repo: SchoolRepository) =
        com.meteomontana.android.domain.usecase.schools.ExploreLinesUseCase(repo)

    @Provides @Singleton
    fun provideConfirmProcessionaryUseCase(repo: SchoolRepository) =
        com.meteomontana.android.domain.usecase.schools.ConfirmProcessionaryUseCase(repo)

    @Provides @Singleton
    fun provideRetractProcessionaryUseCase(repo: SchoolRepository) =
        com.meteomontana.android.domain.usecase.schools.RetractProcessionaryUseCase(repo)

    @Provides @Singleton
    fun provideReportProcessionaryActiveNowUseCase(repo: SchoolRepository) =
        com.meteomontana.android.domain.usecase.schools.ReportProcessionaryActiveNowUseCase(repo)

    @Provides @Singleton
    fun provideClearProcessionaryActiveNowUseCase(repo: SchoolRepository) =
        com.meteomontana.android.domain.usecase.schools.ClearProcessionaryActiveNowUseCase(repo)

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

    @Provides @Singleton
    fun provideGetRainOutlookUseCase(repo: ForecastRepository) =
        com.meteomontana.android.domain.usecase.schools.GetRainOutlookUseCase(repo)

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

    // Numerar piedras por sector + arreglar el orden a mano o por GPS (Álvaro, 2026-09-14).
    @Provides @Singleton
    fun provideReorderBlocksUseCase(repo: BlockRepository) = ReorderBlocksUseCase(repo)

    @Provides @Singleton
    fun provideAutoReorderBlocksUseCase(repo: BlockRepository) = AutoReorderBlocksUseCase(repo)

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

    // Enlaces de beta directos, varios por vía con categoría de altura (Álvaro, 2026-09-15).
    @Provides @Singleton
    fun provideGetBetaLinksUseCase(repo: BlockRepository) =
        com.meteomontana.android.domain.usecase.blocks.GetBetaLinksUseCase(repo)
    @Provides @Singleton
    fun provideAddBetaLinkUseCase(repo: BlockRepository) =
        com.meteomontana.android.domain.usecase.blocks.AddBetaLinkUseCase(repo)
    @Provides @Singleton
    fun provideDeleteBetaLinkUseCase(repo: BlockRepository) =
        com.meteomontana.android.domain.usecase.blocks.DeleteBetaLinkUseCase(repo)

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

    @Provides @Singleton
    fun provideNotifyGroupMessageUseCase(
        repo: com.meteomontana.android.domain.repository.ChatPushRepository
    ) = com.meteomontana.android.domain.usecase.chat.NotifyGroupMessageUseCase(repo)

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
    fun provideGetMountainBulletinUseCase(
        repo: com.meteomontana.android.domain.repository.MountainRepository
    ) = com.meteomontana.android.domain.usecase.weather.GetMountainBulletinUseCase(repo)

    @Provides @Singleton
    fun provideCreateNoteUseCase(repo: NoteRepository) = CreateNoteUseCase(repo)

    @Provides @Singleton
    fun provideSubmitSuggestionUseCase(
        repo: com.meteomontana.android.domain.repository.SuggestionRepository
    ) = com.meteomontana.android.domain.usecase.suggestion.SubmitSuggestionUseCase(repo)

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

}
