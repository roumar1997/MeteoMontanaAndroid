package com.meteomontana.android.ui.assistant

import com.meteomontana.android.presentation.assistant.AssistantLabels
import com.meteomontana.android.presentation.assistant.AssistantPresenter
import com.meteomontana.android.presentation.assistant.*
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.AssistantSnapshotCodec
import com.meteomontana.android.data.local.JournalDoneStore
import com.meteomontana.android.data.local.AssistantHomeStore
import com.meteomontana.android.domain.model.AssistantRestrictions
import com.meteomontana.android.domain.usecase.community.GetGradeVotesUseCase
import com.meteomontana.android.domain.usecase.journal.AchievementsCalculator
import com.meteomontana.android.domain.usecase.journal.GetYearGoalUseCase
import com.meteomontana.android.domain.usecase.journal.GoalCalculator
import com.meteomontana.android.domain.usecase.journal.SchoolPlace
import com.meteomontana.android.domain.usecase.schools.GetSchoolsUseCase
import com.meteomontana.android.data.outbox.OutboxRepository
import com.meteomontana.android.data.outbox.OutboxType
import com.meteomontana.android.domain.model.AssistantAnswer
import com.meteomontana.android.domain.model.AssistantLogItem
import com.meteomontana.android.domain.model.AssistantBreakdown
import com.meteomontana.android.domain.model.AssistantMessagePayload
import com.meteomontana.android.domain.model.AssistantOption
import com.meteomontana.android.domain.model.AssistantRecommendation
import com.meteomontana.android.domain.model.AssistantStatus
import com.meteomontana.android.domain.model.AssistantUnderstood
import com.meteomontana.android.domain.model.AssistantWeather
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.LineExploreCriteria
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.model.UserLocation
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.domain.port.NetworkMonitor
import com.meteomontana.android.domain.usecase.assistant.AskAssistantUseCase
import com.meteomontana.android.domain.usecase.assistant.AssistantActions
import com.meteomontana.android.domain.usecase.assistant.GetAssistantAccessUseCase
import com.meteomontana.android.domain.usecase.assistant.MineAnswerer
import com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase
import com.meteomontana.android.domain.usecase.journal.CreateJournalEntryUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import com.meteomontana.android.domain.usecase.meetups.GetMeetupsUseCase
import com.meteomontana.android.domain.usecase.profile.GetWeekendAlertUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateWeekendAlertUseCase
import com.meteomontana.android.domain.usecase.schools.ExploreLinesUseCase
import com.meteomontana.android.domain.usecase.schools.SearchLinesUseCase
import com.meteomontana.android.ui.assistant.photo.StoneCandidate
import com.meteomontana.android.ui.assistant.photo.StoneOutcome
import com.meteomontana.android.ui.assistant.photo.StonePhotoInput
import com.meteomontana.android.ui.assistant.photo.StonePhotoLocator
import com.meteomontana.android.ui.assistant.photo.StonePhotoMatcher
import com.meteomontana.android.ui.assistant.photo.StonePhotoWords
import com.meteomontana.android.ui.assistant.photo.StoneStage
import com.meteomontana.android.util.AppLanguage
import com.meteomontana.android.util.CatalogLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ViewModel del asistente (burbuja flotante + chat). Vive en la actividad (no en el chat): cerrar el chat, abrir una
 * piedra y volver a abrir el círculo no pierde la conversación. Espejo de `AssistantViewModel.swift` (iOS).
 *
 * El servidor entiende la frase y calcula; aquí solo se guarda la conversación y lo último entendido ([previous])
 * para poder refinar ("ahora solo 7a") sin repetirlo todo. Lo suyo (diario, favoritas, quedadas) se contesta AQUÍ, con
 * lo que la app ya tiene: el diario nunca sale del móvil.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val askAssistant: AskAssistantUseCase,
    private val getAssistantAccess: GetAssistantAccessUseCase,
    private val locationProvider: LocationProvider,
    private val stoneMatcher: StonePhotoMatcher,
    private val stoneLocator: StonePhotoLocator,
    private val homeStore: AssistantHomeStore,
    private val data: AssistantData,
    private val actionRunner: AssistantActionRunner,
    val speaker: AssistantSpeaker
) : ViewModel() {

    private val store = AssistantTranscriptStore(SharedTranscriptStorage(context))

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    /** Cuenta a la que pertenece la conversación; null = sin sesión. */
    private var uid: String? = null

    /**
     * ¿Puede esta cuenta usar el asistente? Decide si se ve la burbuja. Lo manda el servidor (admin, abierto a todos o
     * activado a mano por un admin) y se guarda lo último que se supo POR CUENTA, para que un arranque sin red no
     * esconda la burbuja a quien sí la tiene.
     */
    private val accessPrefs = context.getSharedPreferences("assistant_access", Context.MODE_PRIVATE)
    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()
    private var nextId = 1L
    private var busy = false

    /** La última pregunta enviada (para "REINTENTAR" si falla por algo pasajero). */
    private var lastQuestion = ""

    /** Lo último que entendió el servidor; se devuelve en el mensaje siguiente. */
    private var previous: AssistantUnderstood? = null

    /** Escuela que el usuario tiene abierta, si la hay (futuro: contexto de pantalla). */
    var contextSchoolId: String? = null

    /** Los textos en el idioma ACTUAL de la app (se pide en cada uso: el idioma puede cambiar en marcha). */
    val presenter: AssistantPresenter
        get() = AssistantPresenter(AppLanguage.effective(context), CatalogLabelsAdapter)

    // ── Conversación guardada en el móvil ─────────────────────────────────────────────────────────────────────────

    /**
     * La app avisa de qué cuenta está activa. Sin sesión se borra todo; con sesión, si el chat está vacío, se recupera
     * lo guardado de ESA cuenta (si no han pasado unas horas).
     */
    fun attachSession(newUid: String?) {
        if (newUid == uid) return
        uid = newUid
        queuedClear()
        if (newUid == null) {
            _state.update { it.copy(messages = emptyList(), input = "") }
            previous = null
            store.clear()
            _hasAccess.value = false
            return
        }
        refreshAccess()
        _state.update { it.copy(messages = emptyList()) }
        previous = null
        val saved = store.load(newUid) ?: return
        _state.update { it.copy(messages = AssistantTranscriptMapper.restore(saved) { nextId++ }) }
        previous = saved.previous?.toDomain()
    }

    private fun queuedClear() = _state.update { it.copy(queued = emptyList()) }

    /**
     * Vuelve a preguntar al servidor si esta cuenta tiene el asistente (al abrir la app y al volver a ella: así, si un
     * admin se lo activa o se lo quita, se nota sin reinstalar). Sin red o con un servidor antiguo se queda con lo último
     * que se supo de ESTA cuenta (de inicio, que no).
     */
    fun refreshAccess() {
        val account = uid ?: run { _hasAccess.value = false; return }
        _hasAccess.value = accessPrefs.getBoolean(account, false)
        viewModelScope.launch {
            runCatching { getAssistantAccess() }.onSuccess { enabled ->
                if (uid != account) return@onSuccess
                _hasAccess.value = enabled
                accessPrefs.edit().putBoolean(account, enabled).apply()
            }
        }
    }

    /** El servidor dijo que NO está activado para esta cuenta (se lo quitaron con el chat abierto): fuera la burbuja. */
    private fun accessRevoked() {
        val account = uid ?: return
        _hasAccess.value = false
        accessPrefs.edit().putBoolean(account, false).apply()
    }

    /** Guarda la conversación (textos, etiquetas y tarjetas) unas horas, solo en el móvil. */
    private fun persist() {
        val account = uid ?: return
        val messages = _state.value.messages
        if (messages.isEmpty()) return
        store.save(AssistantTranscriptMapper.toStored(account, messages, previous))
    }

    // ── Entrada ───────────────────────────────────────────────────────────────────────────────────────────────────

    fun setInput(text: String) = _state.update { it.copy(input = text.take(MAX_TEXT)) }
    fun requestOpen(schoolId: String, viaId: String? = null) = _state.update { it.copy(openRequest = AssistantOpenRequest(schoolId, viaId)) }
    fun consumeOpenRequest() = _state.update { it.copy(openRequest = null) }
    fun consumeMeetupRequest() = _state.update { it.copy(meetupRequest = null) }

    /** ¿Hay casa guardada? (menú «⋯» del chat: guardar u olvidar). */
    private val _hasHome = MutableStateFlow(homeStore.home != null)
    val hasHome: StateFlow<Boolean> = _hasHome.asStateFlow()

    fun forgetHome() {
        homeStore.clear()
        _hasHome.value = false
    }
    fun setListenOnOpen(on: Boolean) = _state.update { it.copy(listenOnOpen = on) }

    private fun append(m: AssistantMessage) = _state.update { it.copy(messages = it.messages + m) }
    private fun newId() = nextId++
    private fun reply(text: String, chips: List<String> = emptyList()) = append(AssistantMessage(newId(), false, text, chips))

    /** [spoken] = la pregunta se dictó: según el modo de voz, la respuesta se lee en voz alta. */
    fun send(text: String? = null, spoken: Boolean = false) {
        val raw = (text ?: _state.value.input).trim()
        if (raw.isEmpty()) return
        _state.update { it.copy(input = "") }
        if (busy) {
            // Ya se está contestando otra: esta se ve en el chat como "en cola" y espera su turno.
            _state.update { if (it.queued.size < MAX_QUEUED) it.copy(queued = it.queued + raw) else it }
            return
        }
        busy = true
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                respond(raw, spoken)
                // Sin ningún punto de espera entre una respuesta y la siguiente: una pregunta nueva no puede colarse en paralelo.
                while (_state.value.queued.isNotEmpty()) {
                    val next = _state.value.queued.first()
                    _state.update { it.copy(queued = it.queued.drop(1)) }
                    respond(next)
                }
            } finally {
                busy = false
                _state.update { it.copy(isLoading = false) }
                persist()
            }
        }
    }

    /** Contesta UNA pregunta (la envía al servidor y pinta la respuesta). */
    private suspend fun respond(raw: String, spoken: Boolean = false) {
        lastQuestion = raw
        append(AssistantMessage(newId(), true, raw))
        val location = currentLocation()
        val before = _state.value.messages.size
        try {
            val home = homeStore.home
            val answer = askAssistant(raw, previous, location?.lat, location?.lon, contextSchoolId, home?.lat, home?.lon)
            // "Cerca de casa" o "por Teruel": las búsquedas que hace la app también miden desde ahí.
            val u = answer.understood
            val placeLat = u?.placeLat
            val placeLon = u?.placeLon
            val place = when {
                placeLat != null && placeLon != null -> UserLocation(placeLat, placeLon)
                u?.useHome == true -> home ?: location
                else -> location
            }
            handle(answer, place)
            speakNewAnswer(before, spoken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            append(AssistantMessage(newId(), false, presenter.connectError(), retryText = raw))
        }
    }

    /**
     * Repite una pregunta que falló por algo pasajero: quita el intento fallido (su pregunta y su error) para no dejar el
     * chat lleno de duplicados, y la vuelve a enviar.
     */
    fun retry(text: String) {
        _state.update { s ->
            val m = s.messages
            if (m.size >= 2 && !m.last().isUser && m[m.size - 2].isUser && m[m.size - 2].text == text) s.copy(messages = m.dropLast(2)) else s
        }
        send(text)
    }

    /** Lee en voz alta la respuesta que acaba de llegar (la última del asistente), si el modo de voz lo pide. */
    private fun speakNewAnswer(after: Int, spoken: Boolean) {
        if (!speaker.mode.value.shouldSpeak(spoken)) return
        val answer = _state.value.messages.drop(after).lastOrNull { !it.isUser } ?: return
        speaker.speak(spokenText(answer, AppLanguage.effective(context)), answer.id)
    }

    // ── Foto de una piedra ────────────────────────────────────────────────────────────────────────────────────────

    /** La búsqueda de una foto en marcha: «Parar» la cancela. */
    private var photoJob: Job? = null

    /**
     * «¿Qué piedra es esta?»: dónde se hizo la foto (o dónde está el usuario) y, entre las piedras de alrededor, a
     * cuál se parece más. Todo en el móvil: la foto no se sube a ningún sitio. Espejo de `identifyPhoto` de iOS.
     */
    fun identifyPhoto(photo: StonePhotoInput) {
        if (busy) return
        busy = true
        val words = StonePhotoWords(AppLanguage.effective(context))
        append(AssistantMessage(newId(), true, words.userMessage))
        _state.update { it.copy(isLoading = true, loadingNote = if (photo.fromCamera) words.checkingWhere else words.readingWhere) }
        photoJob = viewModelScope.launch {
            try {
                searchStone(photo, words)
            } catch (e: CancellationException) {
                // «Parar»: no se pinta nada.
            } finally {
                busy = false
                _state.update { it.copy(isLoading = false, loadingNote = null) }
                persist()
            }
        }
    }

    /** Corta lo que se esté pensando o buscando. */
    fun stopThinking() {
        photoJob?.cancel()
        speaker.stop()
    }

    private suspend fun searchStone(photo: StonePhotoInput, words: StonePhotoWords) {
        val place = stoneLocator.locate(photo)
        var lastStage: String? = null
        val outcome = withTimeoutOrNull(StonePhotoMatcher.TOTAL_TIMEOUT_MS) {
            stoneMatcher.match(photo.uri, place.lat, place.lon, place.approximate) { stage ->
                lastStage = words.stage(stage)
                _state.update { it.copy(loadingNote = lastStage) }
            }
        }
        if (outcome == null) {
            reply(words.tookTooLong(lastStage))
            return
        }
        when (outcome) {
            StoneOutcome.NoLocation -> reply(words.noLocation)
            StoneOutcome.NoData -> reply(words.noData)
            is StoneOutcome.NoSchoolNear -> reply(words.noSchoolNear(place.approximate, outcome.closestName, outcome.closestKm))
            is StoneOutcome.NoStones -> reply(words.noStonesNear(outcome.schoolName))
            is StoneOutcome.Found -> {
                val first = outcome.top.firstOrNull() ?: return
                val weak = StonePhotoMatcher.isWeak(outcome.top)
                append(AssistantMessage(
                    newId(), false,
                    words.intro(outcome.sure && !weak, outcome.approximate, first.schoolName, weak),
                    photoMatches = outcome.top
                ))
                speakNewAnswer(_state.value.messages.size - 1, spoken = false)
            }
        }
    }

    fun reset() {
        speaker.stop()
        photoJob?.cancel()
        _state.update { AssistantUiState(listenOnOpen = it.listenOnOpen) }
        previous = null
        store.clear()
    }

    // ── Respuesta ─────────────────────────────────────────────────────────────────────────────────────────────────

    private suspend fun handle(answer: AssistantAnswer, location: UserLocation?) {
        val p = presenter
        val chips = answer.understood?.let { p.chips(it, answer.resolvedSchool?.name, answer.resolvedSector?.name) }.orEmpty()
        when (answer.status) {
            AssistantStatus.OK -> {
                previous = answer.understood ?: previous
                val u = answer.understood
                val weather = answer.weather
                val rec = answer.recommendation
                val summary = answer.summary
                val breakdown = answer.breakdown
                answer.restrictions?.let { r ->
                    append(AssistantMessage(newId(), false, p.restrictionsIntro(r, answer.resolvedSchool?.name), chips, restrictions = r))
                    return
                }
                if (rec != null && u?.trip == true) {
                    val plan = p.tripPlan(rec)
                    append(AssistantMessage(newId(), false, p.tripIntro(plan), chips,
                        recommendation = if (plan.isEmpty()) rec else null, trip = plan))
                    return
                }
                when {
                    weather != null -> append(AssistantMessage(newId(), false, p.weatherHeadline(weather), chips, weather = weather))
                    rec != null -> {
                        val compare = u?.intent == "COMPARE"
                        // "Compárame los bloques de 7a de X y de Y": además de la tarjeta de cada escuela (tiempo y cuántos hay),
                        // se ENSEÑAN los bloques, que es lo que se quería comparar.
                        val hits = if (compare && rec.filtered && rec.schools.isNotEmpty())
                            runCatching { data.loadHits(answer, location, presenter, rec.schools.map { it.schoolId }) }.getOrDefault(emptyList())
                        else emptyList()
                        append(AssistantMessage(
                            newId(), false, if (compare) p.compareIntro(rec) else p.recommendationIntro(rec), chips,
                            recommendation = rec, hits = hits, hitsTotal = hits.size))
                    }
                    u?.intent == "ACTION" && u.action != null -> proposeAction(u.action!!, answer, chips)
                    summary != null -> reply(p.summaryText(summary), chips)
                    u?.intent == "MINE" && (u.mineTopic == "GOAL" || u.mineTopic == "ACHIEVEMENTS") ->
                        reply(data.goalOrAchievements(u.mineTopic!!, presenter), chips)
                    u?.intent == "MINE" -> {
                        // Lo suyo se contesta AQUÍ, con lo que la app ya tiene: el diario nunca sale del móvil.
                        val mine = data.mineAnswer(answer)
                        val period = u.dateFrom?.let { p.dateRange(it, u.dateTo) }
                        append(AssistantMessage(newId(), false, p.mineText(mine, period), chips,
                            journalEntries = mine.entries, journalTotal = mine.count))
                    }
                    breakdown != null -> append(AssistantMessage(newId(), false, p.breakdownIntro(breakdown), chips, breakdown = breakdown))
                    else -> {
                        // Búsqueda simple: primero se ENSEÑAN las vías o bloques que cumplen, y cada una se puede tocar.
                        val hits = data.loadHits(answer, location, presenter)
                        if (u?.heightGrade == true && hits.isNotEmpty()) {
                            // "¿Es más dura para bajos?": el grado votado por cada altura, de las vías encontradas.
                            append(AssistantMessage(newId(), false, data.heightGradeText(hits, presenter), chips, hits = hits, hitsTotal = hits.size))
                            return
                        }
                        val needsLocation = u?.maxDistanceKm != null && location == null
                        val rated = u?.topRated == true || u?.minStars != null
                        append(AssistantMessage(newId(), false, p.hitsIntro(hits.size, needsLocation, rated), chips,
                            hits = hits, hitsTotal = hits.size))
                    }
                }
            }
            AssistantStatus.NEEDS_INPUT -> {
                // Se conserva lo entendido: al contestar "Albarracín" no hay que repetir la pregunta.
                previous = answer.understood ?: previous
                answer.clarification?.let { c ->
                    append(AssistantMessage(newId(), false, p.clarification(c), chips, options = c.options))
                }
            }
            else -> {
                // Ocupado o no disponible son fallos PASAJEROS: se ofrece reintentar con la misma pregunta.
                if (answer.status == AssistantStatus.NOT_ENABLED) accessRevoked()
                val transient = answer.status == AssistantStatus.BUSY || answer.status == AssistantStatus.UNAVAILABLE
                append(AssistantMessage(newId(), false, p.message(answer.status) ?: p.cantAnswer(),
                    retryText = if (transient) lastQuestion else null))
            }
        }
    }

    // ── Acciones confirmadas ──────────────────────────────────────────────────────────────────────────────────────

    /** Prepara la tarjeta de confirmación de una acción. NO hace nada todavía: solo propone. */
    private suspend fun proposeAction(kind: String, answer: AssistantAnswer, chips: List<String>) {
        val p = presenter
        val u = answer.understood
        when (kind) {
            "SET_HOME", "ENABLE_MORNING_ALERT" -> {
                append(AssistantMessage(newId(), false, p.actionPrompt(kind, ""), chips,
                    pendingActions = listOf(PendingAction(newId(), kind, "", ""))))
                return
            }
            "CREATE_MEETUP" -> {
                val school = answer.resolvedSchool ?: return
                append(AssistantMessage(newId(), false, p.meetupPrompt(school.name, u?.dateFrom, u?.time), chips,
                    pendingActions = listOf(PendingAction(newId(), kind, school.id, school.name, date = u?.dateFrom, time = u?.time))))
                return
            }
        }
        if ((kind == "LOG_DONE" || kind == "LOG_PROJECT") && (u?.logItems?.size ?: 0) > 1) {
            proposeSeveralLogs(u?.logItems.orEmpty(), answer, chips)
            return
        }
        if (kind != "LOG_DONE" && kind != "LOG_PROJECT") {
            val school = answer.resolvedSchool ?: return
            append(AssistantMessage(newId(), false, p.actionPrompt(kind, school.name), chips,
                pendingActions = listOf(PendingAction(newId(), kind, school.id, school.name))))
            return
        }
        // Apuntar en el diario: se busca la vía por su nombre y se pide confirmar CUÁL.
        val name = u?.q
        if (name.isNullOrEmpty()) { reply(p.whichLineToLog(), chips); return }
        val status = if (kind == "LOG_PROJECT") "PROJECT" else "DONE"
        val found = data.searchLines(name)
        val matches = AssistantActions.matchLines(found, name, answer.resolvedSchool?.id)
        if (matches.isEmpty()) { reply(p.noLineNamed(name), chips); return }
        val journal = runCatching { data.journal() }.getOrDefault(emptyList())
        val date = u.dateFrom ?: data.todayIso()
        val fresh = matches.filter { !AssistantActions.isLogged(journal, it, status) }
        if (fresh.isEmpty()) { reply(p.alreadyLogged(name), chips); return }
        val actions = fresh.map { PendingAction(newId(), kind, it.schoolId, it.schoolName, hit = it, date = date) }
        val text = if (fresh.size == 1) p.logPrompt(kind, fresh[0], date) else p.whichOfTheseToLog()
        append(AssistantMessage(newId(), false, text, chips, pendingActions = actions))
    }

    private fun updateMessage(id: Long, change: (AssistantMessage) -> AssistantMessage) =
        _state.update { s -> s.copy(messages = s.messages.map { if (it.id == id) change(it) else it }) }

    /**
     * El usuario tocó CONFIRMAR en una de las acciones: se hace y se avisa. Si falla, se dice y el botón sigue ahí para
     * reintentar. Al hacer una, las demás candidatas del mismo mensaje se descartan.
     */
    fun confirm(messageId: Long, actionId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val action = message.pendingActions.firstOrNull { it.id == actionId } ?: return
        if (action.state != PendingAction.State.WAITING) return
        viewModelScope.launch {
            try {
                val text = perform(action)
                updateMessage(messageId) { m ->
                    m.copy(pendingActions = m.pendingActions.map {
                        it.copy(state = if (it.id == actionId) PendingAction.State.DONE else PendingAction.State.CANCELLED)
                    })
                }
                reply(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reply(presenter.couldNotDo())
            }
            persist()
        }
    }

    /** Hace la acción y devuelve lo que se le dice al usuario. */
    private suspend fun perform(action: PendingAction): String {
        val p = presenter
        when (action.kind) {
            "ADD_FAVORITE" -> actionRunner.addFavorite(action.schoolId)
            "REMOVE_FAVORITE" -> actionRunner.removeFavorite(action.schoolId)
            "ENABLE_WEEKEND_ALERT" -> return actionRunner.weekendAlert(action, p)
            "ENABLE_MORNING_ALERT" -> return actionRunner.morningAlert(p)
            "SET_HOME" -> {
                val here = currentLocation() ?: return p.homeNoLocation()
                homeStore.save(here.lat, here.lon)
                _hasHome.value = true
                return p.homeSaved()
            }
            "CREATE_MEETUP" -> {
                val prefill = com.meteomontana.android.ui.screens.meetups.MeetupPrefill(
                    action.schoolId, action.schoolName, action.date,
                    p.meetupName(action.schoolName, action.date, action.time), action.time?.let { p.meetupTime(it) })
                _state.update { it.copy(meetupRequest = prefill) }
                return p.meetupOpening()
            }
            "LOG_DONE", "LOG_PROJECT" -> return actionRunner.log(action, p)
            else -> requestOpen(action.schoolId)
        }
        return p.actionDone(action.kind, action.schoolName)
    }

    /**
     * "Hoy hice Zarzamora, la Ola y probé el Techo": se busca cada una y se propone apuntarlas TODAS de una vez (la mejor
     * coincidencia de cada nombre, en la escuela dicha si la hay). Las que no aparecen se dicen.
     */
    private suspend fun proposeSeveralLogs(items: List<AssistantLogItem>, answer: AssistantAnswer, chips: List<String>) {
        val p = presenter
        val date = answer.understood?.dateFrom ?: data.todayIso()
        val journal = runCatching { data.journal() }.getOrDefault(emptyList())
        val actions = mutableListOf<PendingAction>()
        val missing = mutableListOf<String>()
        for (item in items) {
            val status = if (item.project) "PROJECT" else "DONE"
            val found = runCatching { data.searchLines(item.name) }.getOrDefault(emptyList())
            val hit = AssistantActions.matchLines(found, item.name, answer.resolvedSchool?.id)
                .firstOrNull { !AssistantActions.isLogged(journal, it, status) }
            if (hit == null) { missing += item.name; continue }
            actions += PendingAction(newId(), if (item.project) "LOG_PROJECT" else "LOG_DONE", hit.schoolId, hit.schoolName, hit = hit, date = date)
        }
        if (actions.isEmpty()) { reply(p.noneOfTheseFound(missing), chips); return }
        append(AssistantMessage(newId(), false, p.multiLogPrompt(actions.size, date, missing), chips,
            pendingActions = actions, confirmAll = true))
    }

    /** «Apuntar las N»: apunta cada una de las propuestas del mensaje. Las que fallen se dicen al final. */
    fun confirmAll(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        viewModelScope.launch {
            val lines = mutableListOf<String>()
            for (action in message.pendingActions.filter { it.state == PendingAction.State.WAITING }) {
                val text = try { perform(action) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                if (text != null) {
                    updateMessage(messageId) { m ->
                        m.copy(pendingActions = m.pendingActions.map { if (it.id == action.id) it.copy(state = PendingAction.State.DONE) else it })
                    }
                    lines += text
                } else {
                    lines += presenter.couldNotLog(action.hit?.let { it.lineName ?: it.blockName } ?: action.schoolName)
                }
            }
            reply(lines.joinToString("\n"))
            persist()
        }
    }

    /** Su objetivo del año o sus logros: se calculan AQUÍ con su diario (no sale del móvil). */
    fun cancel(messageId: Long) = updateMessage(messageId) { m ->
        m.copy(pendingActions = m.pendingActions.map {
            if (it.state == PendingAction.State.WAITING) it.copy(state = PendingAction.State.CANCELLED) else it
        })
    }

    /** Ubicación: la actual si hay permiso (la usa el servidor solo para medir distancias). */
    private suspend fun currentLocation(): UserLocation? =
        if (!locationProvider.hasPermission()) null else runCatching { locationProvider.current() }.getOrNull()

    companion object {
        /** Tope de caracteres de una pregunta (el servidor recorta a lo mismo). */
        const val MAX_TEXT = 200
        /** Máximo de preguntas esperando: protege el cupo diario y el límite por minuto del servidor. */
        const val MAX_QUEUED = 5
    }
}
