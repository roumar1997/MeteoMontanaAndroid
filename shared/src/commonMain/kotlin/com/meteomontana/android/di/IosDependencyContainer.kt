package com.meteomontana.android.di

import com.meteomontana.android.data.api.KtorFavoritesApi
import com.meteomontana.android.data.api.KtorForecastApi
import com.meteomontana.android.data.api.KtorMountainApi
import com.meteomontana.android.data.api.KtorRadarApi
import com.meteomontana.android.data.api.KtorNoteApi
import com.meteomontana.android.data.api.KtorAdminApi
import com.meteomontana.android.data.api.KtorBlockApi
import com.meteomontana.android.data.api.KtorChatPushApi
import com.meteomontana.android.data.api.KtorContributionApi
import com.meteomontana.android.data.api.KtorJournalApi
import com.meteomontana.android.data.api.KtorNotificationApi
import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.KtorSchoolApi
import com.meteomontana.android.data.api.KtorSocialApi
import com.meteomontana.android.data.api.KtorSubmissionApi
import com.meteomontana.android.data.api.buildApiHttpClient
import com.meteomontana.android.data.repository.KtorFavoritesRepository
import com.meteomontana.android.data.repository.KtorForecastRepository
import com.meteomontana.android.data.repository.KtorNoteRepository
import com.meteomontana.android.data.repository.KtorNotificationsRepository
import com.meteomontana.android.data.repository.KtorAdminRepository
import com.meteomontana.android.data.repository.KtorBlockRepository
import com.meteomontana.android.data.repository.KtorContributionRepository
import com.meteomontana.android.data.repository.KtorJournalRepository
import com.meteomontana.android.data.repository.KtorProfileRepository
import com.meteomontana.android.data.repository.KtorSchoolRepository
import com.meteomontana.android.data.repository.KtorSocialRepository
import com.meteomontana.android.data.repository.KtorSubmissionRepository
import com.meteomontana.android.data.saved.CachedSchoolsRepository
import com.meteomontana.android.data.saved.SavedSchoolRepository
import com.meteomontana.android.data.stats.MonthlyStatsRepository
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.db.MeteoMontanaDb
import com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase
import com.meteomontana.android.domain.usecase.favorites.GetFavoritesGridUseCase
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase
import com.meteomontana.android.domain.usecase.notes.CreateNoteUseCase
import com.meteomontana.android.domain.usecase.notes.GetNotesUseCase
import com.meteomontana.android.domain.usecase.notifications.GetMyNotificationsUseCase
import com.meteomontana.android.domain.usecase.notifications.MarkAllNotificationsReadUseCase
import com.meteomontana.android.domain.usecase.notifications.MarkNotificationReadUseCase
import com.meteomontana.android.domain.usecase.notifications.DeleteNotificationUseCase
import com.meteomontana.android.domain.usecase.notifications.DeleteAllNotificationsUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.profile.DeleteMyAccountUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateMyProfileUseCase
import com.meteomontana.android.domain.usecase.profile.GetWeekendAlertUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateWeekendAlertUseCase
import com.meteomontana.android.domain.usecase.social.FollowUserUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowStatusUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowersUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowingUseCase
import com.meteomontana.android.domain.usecase.social.GetMyFollowRequestsUseCase
import com.meteomontana.android.domain.usecase.social.AcceptFollowRequestUseCase
import com.meteomontana.android.domain.usecase.social.RejectFollowRequestUseCase
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import com.meteomontana.android.domain.usecase.social.SearchUsersUseCase
import com.meteomontana.android.domain.usecase.social.UnfollowUserUseCase
import com.meteomontana.android.domain.usecase.submissions.GetMySubmissionsUseCase
import com.meteomontana.android.domain.usecase.submissions.SubmitSchoolUseCase
import com.meteomontana.android.domain.usecase.contributions.GetMyContributionsUseCase
import com.meteomontana.android.domain.usecase.contributions.SubmitContributionUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalStatsUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserJournalUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserStatsUseCase
import com.meteomontana.android.domain.usecase.journal.CreateJournalEntryUseCase
import com.meteomontana.android.domain.usecase.journal.DeleteJournalEntryUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingSubmissionsUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingContributionsUseCase
import com.meteomontana.android.domain.usecase.admin.ApproveSubmissionUseCase
import com.meteomontana.android.domain.usecase.admin.RejectSubmissionUseCase
import com.meteomontana.android.domain.usecase.admin.ApproveContributionUseCase
import com.meteomontana.android.domain.usecase.admin.RejectContributionUseCase
import com.meteomontana.android.domain.usecase.admin.GetAdminStatsUseCase
import com.meteomontana.android.domain.usecase.admin.GetAdminLogsUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingReportsUseCase
import com.meteomontana.android.domain.usecase.admin.ResolveReportUseCase
import com.meteomontana.android.domain.usecase.admin.SendPushUseCase
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.blocks.UpdateBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.DeleteBlockUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastByLocationUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolByIdUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolsUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import com.meteomontana.android.domain.usecase.schools.GetRangeScoresUseCase
import com.meteomontana.android.domain.usecase.schools.SearchSchoolsUseCase
import com.meteomontana.android.data.api.KtorMeetupApi
import com.meteomontana.android.data.saved.MeetupCacheRepository
import com.meteomontana.android.domain.usecase.meetups.GetMeetupsUseCase
import com.meteomontana.android.domain.usecase.meetups.GetMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.CreateMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.JoinMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.LeaveMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.UpdateMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.GetMeetupByConversationUseCase
import com.meteomontana.android.domain.usecase.meetups.GetMeetupAlertUseCase
import com.meteomontana.android.domain.usecase.meetups.KickMeetupMemberUseCase
import com.meteomontana.android.domain.usecase.meetups.ReportMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.SetMeetupAlertUseCase
import com.meteomontana.android.domain.usecase.meetups.UpdateMyGearUseCase

/**
 * Grafo de dependencias para iOS, construido en Kotlin (commonMain).
 *
 * Por qué en Kotlin y no en Swift: toda la fontanería `suspend`/`StateFlow`
 * (p. ej. el tokenProvider del HttpClient) queda del lado Kotlin, donde es
 * trivial. Swift solo instancia este contenedor y coge use cases ya listos
 * (que SKIE expone como funciones `async`). Es el equivalente iOS de los
 * módulos Hilt de Android (NetworkModule + UseCasesModule).
 *
 * Para el MVP público (escuelas + forecast, endpoints sin auth) `authService`
 * puede ser null. Cuando se añada login, se pasa el `AuthService` (impl Swift
 * con Firebase) y el tokenProvider lo usará.
 *
 * Este contenedor compila también para androidTarget, así que se verifica en
 * Windows aunque su uso real sea iOS.
 */
class IosDependencyContainer(
    baseUrl: String,
    authService: AuthService? = null,
    /**
     * Ubicación del usuario. En iOS se pasa un [IosLocationProvider]
     * (envoltorio del bridge Swift con CLLocationManager). Null → la pantalla
     * de Tiempo cae a una ubicación por defecto (Madrid).
     */
    val locationProvider: LocationProvider? = null,
    /**
     * BD SQLDelight para el caché local del catálogo. La construye el lado
     * Swift con `DatabaseFactory().create()` (driver nativo) y la pasa aquí.
     * Null → la lista funciona sin caché (solo red).
     */
    database: MeteoMontanaDb? = null,
    /**
     * Chat 1-a-1 (Firestore). En iOS se pasa un [com.meteomontana.android.data.chat.IosChatService]
     * (envoltorio del bridge Swift con FirebaseFirestore). Null → sin chat.
     */
    val chatService: ChatService? = null
) {
    private val httpClient = buildApiHttpClient(baseUrl) {
        authService?.currentIdToken(false)
    }

    // Público: iOS lo usa para el buscador global de vías/bloques.
    val schoolApi = KtorSchoolApi(httpClient)
    private val forecastApi = KtorForecastApi(httpClient)
    val radarApi = KtorRadarApi(httpClient)
    val mountainApi = KtorMountainApi(httpClient)
    val appVersionApi = com.meteomontana.android.data.api.KtorAppVersionApi(httpClient)
    /** Catalogo de paises y regiones para el desplegable de proponer escuela. */
    val geoApi = com.meteomontana.android.data.api.KtorGeoApi(httpClient)
    val getCountries = com.meteomontana.android.domain.usecase.geo.GetCountriesUseCase(
        com.meteomontana.android.data.repository.KtorCountryRepository(geoApi))
    private val favoritesApi = KtorFavoritesApi(httpClient)
    val noteApi = KtorNoteApi(httpClient)
    private val profileApi = KtorProfileApi(httpClient)
    private val notificationApi = KtorNotificationApi(httpClient)
    private val socialApi = KtorSocialApi(httpClient)
    private val feedApi = com.meteomontana.android.data.api.KtorFeedApi(httpClient)
    // Subida de fotos Tipo A a R2 vía backend (perfil/piedra/nota/quedada).
    val photoApi = com.meteomontana.android.data.api.KtorPhotoApi(httpClient)
    private val submissionApi = KtorSubmissionApi(httpClient)
    private val contributionApi = KtorContributionApi(httpClient)
    // Botón "?" de ayuda → "Sugerir algo / reportar un fallo".
    private val suggestionApi = com.meteomontana.android.data.api.KtorSuggestionApi(httpClient)

    /** Chat: iniciar conversación (autorización + creación del doc en el backend)
     *  y disparar la push del receptor. Expuesto a Swift para el chat iOS. */
    val chatPushApi = KtorChatPushApi(httpClient)

    private val schoolRepository = KtorSchoolRepository(schoolApi)
    private val forecastRepository = KtorForecastRepository(forecastApi)
    private val favoritesRepository = KtorFavoritesRepository(favoritesApi)
    private val noteRepository = KtorNoteRepository(noteApi)
    private val profileRepository = KtorProfileRepository(profileApi)
    private val notificationsRepository = KtorNotificationsRepository(notificationApi)
    private val socialRepository = KtorSocialRepository(socialApi)
    private val feedRepository = com.meteomontana.android.data.repository.KtorFeedRepository(feedApi, database)
    private val submissionRepository = KtorSubmissionRepository(submissionApi)
    private val contributionRepository = KtorContributionRepository(contributionApi)
    private val suggestionRepository =
        com.meteomontana.android.data.repository.KtorSuggestionRepository(suggestionApi)
    private val journalRepository = KtorJournalRepository(KtorJournalApi(httpClient))
    // Público: iOS lo usa directo para los comentarios de piedras/vías.
    val blockApi = KtorBlockApi(httpClient)
    // Moderación UGC (denunciar/bloquear) — requisito App Store.
    val moderationApi = com.meteomontana.android.data.api.KtorModerationApi(httpClient)
    private val moderationRepository =
        com.meteomontana.android.data.repository.KtorModerationRepository(moderationApi)
    private val blockRepository = KtorBlockRepository(blockApi, database)
    // Aproximaciones (caminos). Lectura pública; alta SOLO ADMIN por ahora
    // (ver APPROACH_DESIGN.md §2.6/§10) — iOS llama a approachApi directo
    // para el alta, sin repositorio intermedio (patrón admin simple).
    val approachApi = com.meteomontana.android.data.api.KtorApproachApi(httpClient)
    private val approachRepository = com.meteomontana.android.data.repository
        .KtorApproachRepository(approachApi)
    private val communityRepository = com.meteomontana.android.data.repository
        .KtorCommunityRepository(com.meteomontana.android.data.api.KtorCommunityApi(httpClient))
    private val adminRepository = KtorAdminRepository(KtorAdminApi(httpClient))
    val meetupApi = KtorMeetupApi(httpClient)
    private val meetupCache: MeetupCacheRepository? = database?.let { MeetupCacheRepository(it) }

    // Use cases públicos del MVP (sin auth). Se irán añadiendo más a medida
    // que las pantallas iOS los necesiten.
    val getSchools = GetSchoolsUseCase(schoolRepository)
    val getSchoolById = GetSchoolByIdUseCase(schoolRepository)
    val searchSchools = SearchSchoolsUseCase(schoolRepository)
    val searchLines = com.meteomontana.android.domain.usecase.schools.SearchLinesUseCase(schoolRepository)
    val getForecast = GetForecastUseCase(forecastRepository)
    val getForecastByLocation = GetForecastByLocationUseCase(forecastRepository)
    val getTodayScores = GetTodayScoresUseCase(forecastRepository)
    // Votación comunitaria (C2/C5) + fecha del diario (C3).
    val getOrientation =
        com.meteomontana.android.domain.usecase.community.GetOrientationUseCase(communityRepository)
    val voteOrientation =
        com.meteomontana.android.domain.usecase.community.VoteOrientationUseCase(communityRepository)
    val getSchoolOrientations =
        com.meteomontana.android.domain.usecase.community.GetSchoolOrientationsUseCase(communityRepository)
    val getSunHours =
        com.meteomontana.android.domain.usecase.community.GetSunHoursUseCase(communityRepository)
    val getGradeVotes =
        com.meteomontana.android.domain.usecase.community.GetGradeVotesUseCase(communityRepository)
    val voteGrade =
        com.meteomontana.android.domain.usecase.community.VoteGradeUseCase(communityRepository)
    val updateJournalDate =
        com.meteomontana.android.domain.usecase.journal.UpdateJournalDateUseCase(journalRepository)
    val updateJournalStyle =
        com.meteomontana.android.domain.usecase.journal.UpdateJournalStyleUseCase(journalRepository)
    val getAdminUsers =
        com.meteomontana.android.domain.usecase.admin.GetAdminUsersUseCase(moderationRepository)
    val getAdminNotes =
        com.meteomontana.android.domain.usecase.admin.GetAdminNotesUseCase(moderationRepository)
    val getAdminSuggestions =
        com.meteomontana.android.domain.usecase.admin.GetAdminSuggestionsUseCase(moderationRepository)
    val respondToSuggestion =
        com.meteomontana.android.domain.usecase.admin.RespondToSuggestionUseCase(moderationRepository)
    val getRangeScores = GetRangeScoresUseCase(forecastRepository)

    // Favoritas (requieren sesión; el token lo aporta el authService del
    // httpClient). Estrella en lista/detalle + grid en el tab Tiempo.
    val getMyFavorites = GetMyFavoritesUseCase(favoritesRepository)
    val getFavoritesGrid = GetFavoritesGridUseCase(favoritesRepository)
    val addFavorite = AddFavoriteUseCase(favoritesRepository)
    val removeFavorite = RemoveFavoriteUseCase(favoritesRepository)

    // Notas comunitarias del detalle de escuela (leer público, crear requiere
    // sesión). Foto adjunta pendiente del bridge de Firebase Storage.
    val getNotes = GetNotesUseCase(noteRepository)
    val createNote = CreateNoteUseCase(noteRepository)

    // Botón "?" de ayuda → "Sugerir algo / reportar un fallo".
    val submitSuggestion =
        com.meteomontana.android.domain.usecase.suggestion.SubmitSuggestionUseCase(suggestionRepository)

    // Perfil privado (JIT provisioning en el primer getMyProfile).
    val getMyProfile = GetMyProfileUseCase(profileRepository)
    val updateMyProfile = UpdateMyProfileUseCase(profileRepository)
    val deleteMyAccount = DeleteMyAccountUseCase(profileRepository)
    val updateFcmToken = com.meteomontana.android.domain.usecase.profile.UpdateFcmTokenUseCase(profileRepository)

    // Alerta de tiempo (preferencias en /api/me/weekend-alert).
    val getWeekendAlert = GetWeekendAlertUseCase(profileRepository)
    val updateWeekendAlert = UpdateWeekendAlertUseCase(profileRepository)

    // Notificaciones / inbox.
    val getMyNotifications = GetMyNotificationsUseCase(notificationsRepository)
    val markNotificationRead = MarkNotificationReadUseCase(notificationsRepository)
    val markAllNotificationsRead = MarkAllNotificationsReadUseCase(notificationsRepository)
    val deleteNotification = DeleteNotificationUseCase(notificationsRepository)
    val deleteAllNotifications = DeleteAllNotificationsUseCase(notificationsRepository)

    // Social: buscar usuarios, perfil público, seguir/dejar de seguir,
    // seguidores/seguidos y solicitudes de seguimiento.
    // Caché de perfiles públicos (nombre/foto offline en el chat). Null si no hay BD.
    private val profileCache: com.meteomontana.android.data.saved.ProfileCacheRepository? =
        database?.let { com.meteomontana.android.data.saved.ProfileCacheRepository(it) }

    /** Catálogo de ayuda contextual (mismo copy que Android). iOS lo lee por clave. */
    fun helpTopic(key: String): com.meteomontana.android.help.HelpTopic? =
        com.meteomontana.android.help.HelpCatalog.byKey(key)

    val searchUsers = SearchUsersUseCase(socialRepository)
    val getTopContributors =
        com.meteomontana.android.domain.usecase.social.GetTopContributorsUseCase(socialRepository)
    val getPublicProfile = GetPublicProfileUseCase(socialRepository, profileCache)
    val getFollowStatus = GetFollowStatusUseCase(socialRepository)
    val followUser = FollowUserUseCase(socialRepository)
    val unfollowUser = UnfollowUserUseCase(socialRepository)
    val removeFollower = com.meteomontana.android.domain.usecase.social.RemoveFollowerUseCase(socialRepository)
    val getFollowers = GetFollowersUseCase(socialRepository)
    val getFollowing = GetFollowingUseCase(socialRepository)
    val getMyFollowRequests = GetMyFollowRequestsUseCase(socialRepository)
    val acceptFollowRequest = AcceptFollowRequestUseCase(socialRepository)
    val rejectFollowRequest = RejectFollowRequestUseCase(socialRepository)

    // Feed social "Comunidad" (pestaña nueva): página del feed, publicar/borrar
    // ascensos, likes y comentarios. Patrón exacto de Social.
    val getFeedPage = com.meteomontana.android.domain.usecase.feed.GetFeedPageUseCase(feedRepository)
    val getFeedPost = com.meteomontana.android.domain.usecase.feed.GetFeedPostUseCase(feedRepository)
    val publishFeedPost = com.meteomontana.android.domain.usecase.feed.PublishFeedPostUseCase(feedRepository)
    val uploadFeedPhoto = com.meteomontana.android.domain.usecase.feed.UploadFeedPhotoUseCase(feedRepository)
    val deleteFeedPost = com.meteomontana.android.domain.usecase.feed.DeleteFeedPostUseCase(feedRepository)
    val likeFeedPost = com.meteomontana.android.domain.usecase.feed.LikeFeedPostUseCase(feedRepository)
    val unlikeFeedPost = com.meteomontana.android.domain.usecase.feed.UnlikeFeedPostUseCase(feedRepository)
    val getFeedComments = com.meteomontana.android.domain.usecase.feed.GetFeedCommentsUseCase(feedRepository)
    val addFeedComment = com.meteomontana.android.domain.usecase.feed.AddFeedCommentUseCase(feedRepository)
    val deleteFeedComment = com.meteomontana.android.domain.usecase.feed.DeleteFeedCommentUseCase(feedRepository)
    val likeFeedComment = com.meteomontana.android.domain.usecase.feed.LikeFeedCommentUseCase(feedRepository)
    val unlikeFeedComment = com.meteomontana.android.domain.usecase.feed.UnlikeFeedCommentUseCase(feedRepository)

    // Mis propuestas de escuela y mis contribuciones de mejora (estado pending/
    // approved/rejected) — accesibles desde el perfil.
    val getMySubmissions = GetMySubmissionsUseCase(submissionRepository)
    val submitSchool = SubmitSchoolUseCase(submissionRepository)
    val getMyContributions = GetMyContributionsUseCase(contributionRepository)
    val submitContribution = SubmitContributionUseCase(contributionRepository)

    // Diario de escalada: entradas, stats (bloques/escuelas/grado máximo), crear/borrar.
    // Bloques de una escuela (para autocompletar el diario con vías/sectores reales).
    val getBlocks = GetBlocksUseCase(blockRepository)
    val getApproaches = com.meteomontana.android.domain.usecase.approach
        .GetApproachesUseCase(approachRepository)

    // Admin: cola de propuestas/contribuciones pendientes + aprobar/rechazar.
    val getPendingSubmissions = GetPendingSubmissionsUseCase(adminRepository)
    val getPendingContributions = GetPendingContributionsUseCase(adminRepository)
    val approveSubmission = ApproveSubmissionUseCase(adminRepository)
    val rejectSubmission = RejectSubmissionUseCase(adminRepository)
    val approveContribution = ApproveContributionUseCase(adminRepository)
    val rejectContribution = RejectContributionUseCase(adminRepository)
    // Admin avanzado (iOS): stats, logs de auditoría, push manual y edición de
    // bloques desde GESTIONAR (mover/editar/borrar reutilizando los use cases).
    val getAdminStats = GetAdminStatsUseCase(adminRepository)
    val getAdminLogs = GetAdminLogsUseCase(adminRepository)
    val sendPush = SendPushUseCase(adminRepository)
    val getPendingReports = GetPendingReportsUseCase(adminRepository)
    val resolveReport = ResolveReportUseCase(adminRepository)
    val updateBlock = UpdateBlockUseCase(blockRepository)
    val deleteBlock = DeleteBlockUseCase(blockRepository)
    val rateLine = com.meteomontana.android.domain.usecase.blocks.RateLineUseCase(blockRepository)
    // Comentarios de piedras/vías por use case (regla DI: nada de blockApi directo).
    val getLineComments = com.meteomontana.android.domain.usecase.blocks.GetLineCommentsUseCase(blockRepository)
    val addLineComment = com.meteomontana.android.domain.usecase.blocks.AddLineCommentUseCase(blockRepository)
    val voteLineComment = com.meteomontana.android.domain.usecase.blocks.VoteLineCommentUseCase(blockRepository)
    val deleteLineComment = com.meteomontana.android.domain.usecase.blocks.DeleteLineCommentUseCase(blockRepository)

    val getMyJournal = GetMyJournalUseCase(journalRepository)
    val getMyJournalStats = GetMyJournalStatsUseCase(journalRepository)
    // Diario y stats de OTRO usuario (perfil público de quien sigues).
    val getUserJournal = GetUserJournalUseCase(journalRepository)
    val getUserStats = GetUserStatsUseCase(journalRepository)
    val createJournalEntry = CreateJournalEntryUseCase(journalRepository)
    val deleteJournalEntry = DeleteJournalEntryUseCase(journalRepository)
    // Nº de piedra + sector de cada vía del diario, resueltos en vivo del catálogo.
    val getJournalViaInfo =
        com.meteomontana.android.domain.usecase.journal.GetJournalViaInfoUseCase(blockRepository)


    // Caché local del catálogo (stale-while-revalidate): la lista pinta desde
    // aquí al instante y refresca desde red después. Null si no hay BD.
    val cachedSchools: CachedSchoolsRepository? = database?.let { CachedSchoolsRepository(it) }

    // Escuelas guardadas para OFFLINE (detalle + bloques + vías + forecast). La
    // lógica está en commonMain; iOS solo cachea aparte las FOTOS (ImageCache Swift).
    val savedSchools: SavedSchoolRepository? = database?.let { SavedSchoolRepository(it) }

    // Stats mensuales (mejores meses del año por escuela). Requiere BD para la
    // caché; el cálculo lo hace el backend. Null si no hay BD.
    val monthlyStats: MonthlyStatsRepository? = database?.let { MonthlyStatsRepository(it, schoolApi) }

    // Limpieza de cachés al cerrar sesión (preserva outbox y guardados offline).
    val localCacheCleaner: com.meteomontana.android.data.local.LocalCacheCleaner? =
        database?.let { com.meteomontana.android.data.local.LocalCacheCleaner(it) }

    // Quedadas (meetups): lista, detalle, crear, unirse, salir, expulsar.
    // Puerto MeetupRepository (orquesta red + caché SQLDelight para offline);
    // los use cases dependen de él, no del KtorMeetupApi concreto. Se conservan
    // las puertas `meetupCache?.let` para no cambiar la nulabilidad que Swift ya
    // consume (los que necesitan caché siguen siendo opcionales).
    private val meetupRepo: com.meteomontana.android.domain.repository.MeetupRepository =
        com.meteomontana.android.data.repository.KtorMeetupRepository(meetupApi, meetupCache)
    val getMeetups: GetMeetupsUseCase? = meetupCache?.let { GetMeetupsUseCase(meetupRepo) }
    val getMeetup: GetMeetupUseCase? = meetupCache?.let { GetMeetupUseCase(meetupRepo) }
    val createMeetup: CreateMeetupUseCase? = meetupCache?.let { CreateMeetupUseCase(meetupRepo) }
    val joinMeetup: JoinMeetupUseCase? = meetupCache?.let { JoinMeetupUseCase(meetupRepo) }
    val leaveMeetup: LeaveMeetupUseCase? = meetupCache?.let { LeaveMeetupUseCase(meetupRepo) }
    val updateMeetup: UpdateMeetupUseCase? = meetupCache?.let { UpdateMeetupUseCase(meetupRepo) }
    val getMeetupByConversation = GetMeetupByConversationUseCase(meetupRepo)
    val kickMeetupMember  = KickMeetupMemberUseCase(meetupRepo)
    val reportMeetup      = ReportMeetupUseCase(meetupRepo)
    val updateMyGear      = UpdateMyGearUseCase(meetupRepo)
    val getMeetupAlert    = GetMeetupAlertUseCase(meetupRepo)
    val setMeetupAlert    = SetMeetupAlertUseCase(meetupRepo)

    // "Estoy aquí" (presencia en una escuela). Sin caché: es información en
    // vivo, no tiene sentido offline como el resto de quedadas.
    private val presenceApi = com.meteomontana.android.data.api.KtorSchoolPresenceApi(httpClient)
    private val presenceRepo: com.meteomontana.android.domain.repository.SchoolPresenceRepository =
        com.meteomontana.android.data.repository.KtorSchoolPresenceRepository(presenceApi)
    val getSchoolPresence   = com.meteomontana.android.domain.usecase.presence.GetSchoolPresenceUseCase(presenceRepo)
    val markSchoolPresence  = com.meteomontana.android.domain.usecase.presence.MarkSchoolPresenceUseCase(presenceRepo)
    val clearSchoolPresence = com.meteomontana.android.domain.usecase.presence.ClearSchoolPresenceUseCase(presenceRepo)

    // ─── Cola offline (outbox) ───────────────────────────────────────────────
    // La lógica vive en OutboxSyncService (SRP); aquí solo se instancia y se
    // exponen delegadores con la MISMA firma que Swift ya llama con `try?`.
    private val outbox: com.meteomontana.android.data.outbox.OutboxRepository? =
        database?.let { com.meteomontana.android.data.outbox.OutboxRepository(it) }
    private val outboxSync = com.meteomontana.android.data.outbox.OutboxSyncService(
        outbox = outbox,
        submitContribution = { schoolId, req -> submitContribution.invoke(schoolId, req) },
        getMyJournal = { getMyJournal() },
        createJournalEntry = { createJournalEntry(it) },
        deleteJournalEntry = { deleteJournalEntry(it) },
        addFavorite = { addFavorite(it) },
        removeFavorite = { removeFavorite(it) },
    )

    @Throws(Exception::class)
    suspend fun enqueueJournal(req: com.meteomontana.android.data.api.dto.CreateJournalRequest) =
        outboxSync.enqueueJournal(req)

    @Throws(Exception::class)
    suspend fun dequeueJournal(key: String): Boolean = outboxSync.dequeueJournal(key)

    @Throws(Exception::class)
    suspend fun enqueueJournalDelete(key: String) = outboxSync.enqueueJournalDelete(key)

    @Throws(Exception::class)
    suspend fun dequeueJournalDelete(key: String) = outboxSync.dequeueJournalDelete(key)

    @Throws(Exception::class)
    suspend fun pendingJournalKeys(): Set<String> = outboxSync.pendingJournalKeys()

    @Throws(Exception::class)
    suspend fun pendingJournalKeysByStatus(status: String): Set<String> =
        outboxSync.pendingJournalKeysByStatus(status)

    @Throws(Exception::class)
    suspend fun pendingJournalDeleteKeys(): Set<String> = outboxSync.pendingJournalDeleteKeys()

    @Throws(Exception::class)
    suspend fun enqueueContribution(schoolId: String, requestJson: String) =
        outboxSync.enqueueContribution(schoolId, requestJson)

    @Throws(Exception::class)
    suspend fun enqueueBoulderContribution(schoolId: String, payloadJson: String) =
        outboxSync.enqueueBoulderContribution(schoolId, payloadJson)

    @Throws(Exception::class)
    suspend fun flushSimpleContributions(): Int = outboxSync.flushSimpleContributions()

    @Throws(Exception::class)
    suspend fun pendingBoulderContributions(): List<com.meteomontana.android.data.outbox.PendingContributionRow> =
        outboxSync.pendingBoulderContributions()

    /** Edición de una piedra existente guardada sin cobertura (iOS). */
    @Throws(Exception::class)
    suspend fun enqueueBlockEditContribution(schoolId: String, payloadJson: String) =
        outboxSync.enqueueBlockEditContribution(schoolId, payloadJson)

    @Throws(Exception::class)
    suspend fun pendingBlockEditContributions(): List<com.meteomontana.android.data.outbox.PendingContributionRow> =
        outboxSync.pendingBlockEditContributions()

    @Throws(Exception::class)
    suspend fun deleteOutboxRow(id: Long) = outboxSync.deleteOutboxRow(id)

    @Throws(Exception::class)
    suspend fun enqueueJournalDeleteById(id: String) = outboxSync.enqueueJournalDeleteById(id)

    @Throws(Exception::class)
    suspend fun pendingJournalDeleteIds(): Set<String> = outboxSync.pendingJournalDeleteIds()

    @Throws(Exception::class)
    suspend fun enqueueFavorite(schoolId: String, favorite: Boolean) =
        outboxSync.enqueueFavorite(schoolId, favorite)

    /**
     * SchoolScore derivado del forecast cacheado de una escuela (offline), o null.
     * Lo usa la lista iOS para pintar el score de las guardadas/visitadas sin red
     * (en vez de "—"). Se construye en Kotlin para no armar listas desde Swift.
     */
    @Throws(Exception::class)
    suspend fun cachedTodayScore(schoolId: String): com.meteomontana.android.domain.model.SchoolScore? {
        val forecast = savedSchools?.loadCachedForecast(schoolId)?.first ?: return null
        return com.meteomontana.android.domain.model.SchoolScore(
            id = schoolId,
            todayScore = forecast.current.score,
            hourlyScores = forecast.hours.map { it.score },
            dryRock = forecast.current.dryRock,
            rainMm = forecast.current.precip24h,
            rainProb = forecast.current.precipitationProbability
        )
    }

    @Throws(Exception::class)
    suspend fun pendingFavoriteIds(): Set<String> = outboxSync.pendingFavoriteIds()

    @Throws(Exception::class)
    suspend fun pendingFavoriteDeleteIds(): Set<String> = outboxSync.pendingFavoriteDeleteIds()

    @Throws(Exception::class)
    suspend fun flushJournalOutbox() = outboxSync.flushJournalOutbox()

    /**
     * Refresca TODAS las escuelas guardadas offline (re-descarga bloques +
     * forecast y reemplaza el snapshot), para que offline no muestre datos
     * viejos. Llamar al abrir la app y al volver a primer plano. Sin red, cada
     * escuela conserva lo guardado (no se borra).
     */
    suspend fun syncSavedSchools() {
        val repo = savedSchools ?: return
        runCatching {
            repo.syncAllSaved(
                fetchBlocks = { getBlocks(it) },
                fetchForecast = { runCatching { getForecast(it) }.getOrNull() }
            )
        }
    }
}
