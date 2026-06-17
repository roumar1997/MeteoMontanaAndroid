package com.meteomontana.android.di

import com.meteomontana.android.data.api.KtorFavoritesApi
import com.meteomontana.android.data.api.KtorForecastApi
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

    private val schoolApi = KtorSchoolApi(httpClient)
    private val forecastApi = KtorForecastApi(httpClient)
    private val favoritesApi = KtorFavoritesApi(httpClient)
    private val noteApi = KtorNoteApi(httpClient)
    private val profileApi = KtorProfileApi(httpClient)
    private val notificationApi = KtorNotificationApi(httpClient)
    private val socialApi = KtorSocialApi(httpClient)
    private val submissionApi = KtorSubmissionApi(httpClient)
    private val contributionApi = KtorContributionApi(httpClient)

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
    private val submissionRepository = KtorSubmissionRepository(submissionApi)
    private val contributionRepository = KtorContributionRepository(contributionApi)
    private val journalRepository = KtorJournalRepository(KtorJournalApi(httpClient))
    private val blockRepository = KtorBlockRepository(KtorBlockApi(httpClient))
    private val adminRepository = KtorAdminRepository(KtorAdminApi(httpClient))

    // Use cases públicos del MVP (sin auth). Se irán añadiendo más a medida
    // que las pantallas iOS los necesiten.
    val getSchools = GetSchoolsUseCase(schoolRepository)
    val getSchoolById = GetSchoolByIdUseCase(schoolRepository)
    val searchSchools = SearchSchoolsUseCase(schoolRepository)
    val getForecast = GetForecastUseCase(forecastRepository)
    val getForecastByLocation = GetForecastByLocationUseCase(forecastRepository)
    val getTodayScores = GetTodayScoresUseCase(forecastRepository)
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

    // Perfil privado (JIT provisioning en el primer getMyProfile).
    val getMyProfile = GetMyProfileUseCase(profileRepository)
    val updateMyProfile = UpdateMyProfileUseCase(profileRepository)
    val deleteMyAccount = DeleteMyAccountUseCase(profileRepository)
    val updateFcmToken = com.meteomontana.android.domain.usecase.profile.UpdateFcmTokenUseCase(profileRepository)

    // Alerta de tiempo (preferencias en /api/me/weekend-alert).
    val getWeekendAlert = GetWeekendAlertUseCase(profileApi)
    val updateWeekendAlert = UpdateWeekendAlertUseCase(profileApi)

    // Notificaciones / inbox.
    val getMyNotifications = GetMyNotificationsUseCase(notificationsRepository)
    val markNotificationRead = MarkNotificationReadUseCase(notificationsRepository)
    val markAllNotificationsRead = MarkAllNotificationsReadUseCase(notificationsRepository)

    // Social: buscar usuarios, perfil público, seguir/dejar de seguir,
    // seguidores/seguidos y solicitudes de seguimiento.
    val searchUsers = SearchUsersUseCase(socialRepository)
    val getPublicProfile = GetPublicProfileUseCase(socialRepository)
    val getFollowStatus = GetFollowStatusUseCase(socialRepository)
    val followUser = FollowUserUseCase(socialRepository)
    val unfollowUser = UnfollowUserUseCase(socialRepository)
    val removeFollower = com.meteomontana.android.domain.usecase.social.RemoveFollowerUseCase(socialRepository)
    val getFollowers = GetFollowersUseCase(socialRepository)
    val getFollowing = GetFollowingUseCase(socialRepository)
    val getMyFollowRequests = GetMyFollowRequestsUseCase(socialRepository)
    val acceptFollowRequest = AcceptFollowRequestUseCase(socialRepository)
    val rejectFollowRequest = RejectFollowRequestUseCase(socialRepository)

    // Mis propuestas de escuela y mis contribuciones de mejora (estado pending/
    // approved/rejected) — accesibles desde el perfil.
    val getMySubmissions = GetMySubmissionsUseCase(submissionRepository)
    val submitSchool = SubmitSchoolUseCase(submissionRepository)
    val getMyContributions = GetMyContributionsUseCase(contributionRepository)
    val submitContribution = SubmitContributionUseCase(contributionRepository)

    // Diario de escalada: entradas, stats (bloques/escuelas/grado máximo), crear/borrar.
    // Bloques de una escuela (para autocompletar el diario con vías/sectores reales).
    val getBlocks = GetBlocksUseCase(blockRepository)

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
    val updateBlock = UpdateBlockUseCase(blockRepository)
    val deleteBlock = DeleteBlockUseCase(blockRepository)

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

    // ─── Cola offline (outbox) — vías marcadas como hechas sin conexión ──────
    // Comparte la tabla Outbox de SQLDelight. Permite marcar una vía sin red:
    // se encola y se sube al volver internet (igual filosofía que Android).
    private val outbox: com.meteomontana.android.data.outbox.OutboxRepository? =
        database?.let { com.meteomontana.android.data.outbox.OutboxRepository(it) }
    private val outboxJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    /** Encola una vía marcada como hecha (sin red) para subirla más tarde. */
    @Throws(Exception::class)
    suspend fun enqueueJournal(req: com.meteomontana.android.data.api.dto.CreateJournalRequest) {
        outbox?.enqueue(
            com.meteomontana.android.data.outbox.OutboxType.JOURNAL,
            req.schoolId ?: "",
            outboxJson.encodeToString(
                com.meteomontana.android.data.api.dto.CreateJournalRequest.serializer(), req)
        )
    }

    /** Quita de la cola la CREACIÓN pendiente con esa clave "escuela|vía"
     *  (al desmarcar). Devuelve true si había una (marcada offline, sin subir). */
    @Throws(Exception::class)
    suspend fun dequeueJournal(key: String): Boolean {
        val repo = outbox ?: return false
        var removed = false
        repo.all().filter { it.type == com.meteomontana.android.data.outbox.OutboxType.JOURNAL }
            .forEach { row ->
                val match = runCatching {
                    outboxJson.decodeFromString(
                        com.meteomontana.android.data.api.dto.CreateJournalRequest.serializer(), row.payloadJson)
                }.getOrNull()?.let { "${it.schoolId ?: ""}|${it.blockName.trim().lowercase()}" == key } ?: false
                if (match) { repo.delete(row.id); removed = true }
            }
        return removed
    }

    /** Encola un BORRADO de vía (desmarcada sin red); se aplica al volver online. */
    @Throws(Exception::class)
    suspend fun enqueueJournalDelete(key: String) {
        outbox?.enqueue(com.meteomontana.android.data.outbox.OutboxType.JOURNAL_DELETE, "", key)
    }

    /** Cancela un borrado pendiente de esa vía (al re-marcarla). */
    @Throws(Exception::class)
    suspend fun dequeueJournalDelete(key: String) {
        val repo = outbox ?: return
        repo.all().filter { it.type == com.meteomontana.android.data.outbox.OutboxType.JOURNAL_DELETE }
            .forEach { row -> if (row.payloadJson == key) repo.delete(row.id) }
    }

    /** Claves "escuela|vía" de las vías encoladas para CREAR (✓ sin red aún). */
    @Throws(Exception::class)
    suspend fun pendingJournalKeys(): Set<String> {
        val pend = outbox?.all() ?: return emptySet()
        return pend.filter { it.type == com.meteomontana.android.data.outbox.OutboxType.JOURNAL }
            .mapNotNull { row ->
                runCatching {
                    outboxJson.decodeFromString(
                        com.meteomontana.android.data.api.dto.CreateJournalRequest.serializer(), row.payloadJson)
                }.getOrNull()
            }
            .map { "${it.schoolId ?: ""}|${it.blockName.trim().lowercase()}" }
            .toSet()
    }

    /** Claves "escuela|vía" con BORRADO pendiente (desmarcadas sin red). */
    @Throws(Exception::class)
    suspend fun pendingJournalDeleteKeys(): Set<String> {
        val pend = outbox?.all() ?: return emptySet()
        return pend.filter { it.type == com.meteomontana.android.data.outbox.OutboxType.JOURNAL_DELETE }
            .map { it.payloadJson }.toSet()
    }

    /** Encola un BORRADO de entrada de diario por su uid (borrada sin red desde el
     *  perfil). Se aplica al volver la conexión. Borra por id → no puede tocar otra. */
    @Throws(Exception::class)
    suspend fun enqueueJournalDeleteById(id: String) {
        outbox?.enqueue(com.meteomontana.android.data.outbox.OutboxType.JOURNAL_DELETE_ID, "", id)
    }

    /** uids de entradas de diario con BORRADO pendiente (borradas sin red). */
    @Throws(Exception::class)
    suspend fun pendingJournalDeleteIds(): Set<String> {
        val pend = outbox?.all() ?: return emptySet()
        return pend.filter { it.type == com.meteomontana.android.data.outbox.OutboxType.JOURNAL_DELETE_ID }
            .map { it.payloadJson }.toSet()
    }

    /**
     * Sube las vías encoladas (las que se marcaron sin red). Llamar al abrir o
     * activar la app. Borra cada entrada al subirla con éxito; las que fallen se
     * quedan en la cola para el próximo intento.
     */
    @Throws(Exception::class)
    suspend fun flushJournalOutbox() {
        val repo = outbox ?: return
        repo.all().forEach { row ->
            when (row.type) {
                com.meteomontana.android.data.outbox.OutboxType.JOURNAL -> {
                    val ok = runCatching {
                        val req = outboxJson.decodeFromString(
                            com.meteomontana.android.data.api.dto.CreateJournalRequest.serializer(), row.payloadJson)
                        // Idempotente: no crear si esa vía ya está en el diario.
                        val key = "${req.schoolId ?: ""}|${req.blockName.trim().lowercase()}"
                        val exists = getMyJournal().any { e ->
                            "${e.schoolId ?: ""}|${e.blockName.trim().lowercase()}" == key
                        }
                        if (!exists) createJournalEntry(req)
                        true
                    }.isSuccess
                    if (ok) repo.delete(row.id)
                }
                com.meteomontana.android.data.outbox.OutboxType.JOURNAL_DELETE -> {
                    // payload = clave "escuela|vía"; resolvemos el id real y borramos.
                    val ok = runCatching {
                        val entry = getMyJournal().firstOrNull { e ->
                            "${e.schoolId ?: ""}|${e.blockName.trim().lowercase()}" == row.payloadJson
                        }
                        if (entry != null) deleteJournalEntry(entry.id)
                        true   // si no existe ya, también se considera hecho
                    }.isSuccess
                    if (ok) repo.delete(row.id)
                }
                com.meteomontana.android.data.outbox.OutboxType.JOURNAL_DELETE_ID -> {
                    // payload = uid exacto de la entrada; solo borra esa.
                    val ok = runCatching {
                        val exists = getMyJournal().any { it.id == row.payloadJson }
                        if (exists) deleteJournalEntry(row.payloadJson)
                        true   // si ya no está, también hecho
                    }.isSuccess
                    if (ok) repo.delete(row.id)
                }
                else -> {}
            }
        }
    }
}
