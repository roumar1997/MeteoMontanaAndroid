package com.meteomontana.android.ui.assistant

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.AssistantSnapshotCodec
import com.meteomontana.android.data.local.JournalDoneStore
import com.meteomontana.android.data.outbox.OutboxRepository
import com.meteomontana.android.data.outbox.OutboxType
import com.meteomontana.android.domain.model.AssistantAnswer
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
import com.meteomontana.android.util.AppLanguage
import com.meteomontana.android.util.CatalogLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Una acción propuesta en el chat. Nunca se ejecuta sin que el usuario la confirme con un toque. */
data class PendingAction(
    val id: Long,
    /** ADD_FAVORITE | REMOVE_FAVORITE | OPEN_SCHOOL | ENABLE_WEEKEND_ALERT | LOG_DONE | LOG_PROJECT */
    val kind: String,
    val schoolId: String,
    val schoolName: String,
    /** Para apuntar en el diario: la vía encontrada y el día ("yyyy-MM-dd"). */
    val hit: LineSearchHit? = null,
    val date: String? = null,
    val state: State = State.WAITING
) {
    enum class State { WAITING, DONE, CANCELLED }
}

/** Un mensaje del chat: del usuario o del asistente (con datos opcionales). */
data class AssistantMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val chips: List<String> = emptyList(),
    val recommendation: AssistantRecommendation? = null,
    val breakdown: AssistantBreakdown? = null,
    /** El tiempo de un sitio (pregunta de lluvia, viento, humedad…). */
    val weather: AssistantWeather? = null,
    /** Si la respuesta fue un fallo pasajero (ocupado, sin conexión…): la pregunta, para ofrecer "REINTENTAR". */
    val retryText: String? = null,
    /** Vías o bloques encontrados por una búsqueda simple, y cuántos hubo en total. */
    val hits: List<LineSearchHit> = emptyList(),
    val hitsTotal: Int = 0,
    /** Cuándo se escribió. Los mensajes recuperados de una sesión anterior enseñan sus tarjetas con un aviso de cuánto hace. */
    val createdAt: Long = System.currentTimeMillis(),
    val restored: Boolean = false,
    /** Opciones tocables de "¿a cuál te refieres?". */
    val options: List<AssistantOption> = emptyList(),
    /** Entradas de su diario que se enseñan como lista tocable, y cuántas cumplen en total. */
    val journalEntries: List<JournalSession> = emptyList(),
    val journalTotal: Int = 0,
    /** Lo que la app hará por el usuario SOLO si lo confirma. Varias cuando el nombre de una vía es ambiguo. */
    val pendingActions: List<PendingAction> = emptyList()
) {
    val hasCards: Boolean get() = recommendation != null || breakdown != null || weather != null || hits.isNotEmpty()
}

/** Algo que el usuario ha tocado en el chat y hay que abrir en la app (escuela y, si se sabe, su vía). */
data class AssistantOpenRequest(val schoolId: String, val viaId: String?)

data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val input: String = "",
    val isLoading: Boolean = false,
    /** Preguntas escritas o dictadas mientras aún se contestaba otra: esperan su turno y se contestan POR ORDEN. */
    val queued: List<String> = emptyList(),
    val openRequest: AssistantOpenRequest? = null,
    /** La burbuja se mantuvo pulsada: el chat, al abrirse, empieza a escuchar directamente. */
    val listenOnOpen: Boolean = false
) {
    val canSend: Boolean get() = input.isNotBlank() && queued.size < AssistantViewModel.MAX_QUEUED
}

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
    private val searchLines: SearchLinesUseCase,
    private val exploreLines: ExploreLinesUseCase,
    private val getMyJournal: GetMyJournalUseCase,
    private val createJournalEntry: CreateJournalEntryUseCase,
    private val outboxRepo: OutboxRepository,
    private val networkMonitor: NetworkMonitor,
    private val journalDoneStore: JournalDoneStore,
    private val getMyFavorites: GetMyFavoritesUseCase,
    private val addFavorite: AddFavoriteUseCase,
    private val removeFavorite: RemoveFavoriteUseCase,
    private val getWeekendAlert: GetWeekendAlertUseCase,
    private val updateWeekendAlert: UpdateWeekendAlertUseCase,
    private val getMeetups: GetMeetupsUseCase,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val store = AssistantTranscriptStore(SharedTranscriptStorage(context))
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
        val restored = saved.messages.map { m ->
            val payload = m.payload?.let { runCatching { AssistantSnapshotCodec.decode(it) }.getOrNull() }
            AssistantMessage(
                id = nextId++, isUser = m.isUser, text = m.text, chips = m.chips,
                recommendation = payload?.recommendation, breakdown = payload?.breakdown, weather = payload?.weather,
                hits = payload?.hits.orEmpty(), hitsTotal = payload?.hitsTotal ?: 0,
                createdAt = m.createdAt, restored = true
            )
        }
        _state.update { it.copy(messages = restored) }
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

    /** Tarjetas del mensaje como texto (null si no tiene). Se guardan TODAS las vías que se enseñan (hasta 300). */
    private fun payload(m: AssistantMessage): String? {
        if (!m.hasCards) return null
        val p = AssistantMessagePayload(m.recommendation, m.breakdown, m.hits.take(300), m.hitsTotal, m.weather)
        return runCatching { AssistantSnapshotCodec.encode(p) }.getOrNull()
    }

    /** Guarda la conversación (textos, etiquetas y tarjetas) unas horas, solo en el móvil. */
    private fun persist() {
        val account = uid ?: return
        val messages = _state.value.messages
        if (messages.isEmpty()) return
        store.save(
            StoredAssistantConversation(
                uid = account, savedAt = System.currentTimeMillis(),
                messages = messages.map {
                    StoredAssistantConversation.Message(it.isUser, it.text, it.chips, payload(it), it.createdAt)
                },
                previous = previous?.let { StoredAssistantConversation.Understood.from(it) }
            )
        )
    }

    // ── Entrada ───────────────────────────────────────────────────────────────────────────────────────────────────

    fun setInput(text: String) = _state.update { it.copy(input = text.take(MAX_TEXT)) }
    fun requestOpen(schoolId: String, viaId: String? = null) = _state.update { it.copy(openRequest = AssistantOpenRequest(schoolId, viaId)) }
    fun consumeOpenRequest() = _state.update { it.copy(openRequest = null) }
    fun setListenOnOpen(on: Boolean) = _state.update { it.copy(listenOnOpen = on) }

    private fun append(m: AssistantMessage) = _state.update { it.copy(messages = it.messages + m) }
    private fun newId() = nextId++
    private fun reply(text: String, chips: List<String> = emptyList()) = append(AssistantMessage(newId(), false, text, chips))

    fun send(text: String? = null) {
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
                respond(raw)
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
    private suspend fun respond(raw: String) {
        lastQuestion = raw
        append(AssistantMessage(newId(), true, raw))
        val location = currentLocation()
        try {
            val answer = askAssistant(raw, previous, location?.lat, location?.lon, contextSchoolId)
            handle(answer, location)
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

    fun reset() {
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
                when {
                    weather != null -> append(AssistantMessage(newId(), false, p.weatherHeadline(weather), chips, weather = weather))
                    rec != null -> {
                        val compare = u?.intent == "COMPARE"
                        // "Compárame los bloques de 7a de X y de Y": además de la tarjeta de cada escuela (tiempo y cuántos hay),
                        // se ENSEÑAN los bloques, que es lo que se quería comparar.
                        val hits = if (compare && rec.filtered && rec.schools.isNotEmpty())
                            runCatching { loadHits(answer, location, rec.schools.map { it.schoolId }) }.getOrDefault(emptyList())
                        else emptyList()
                        append(AssistantMessage(
                            newId(), false, if (compare) p.compareIntro(rec) else p.recommendationIntro(rec), chips,
                            recommendation = rec, hits = hits, hitsTotal = hits.size))
                    }
                    u?.intent == "ACTION" && u.action != null -> proposeAction(u.action!!, answer, chips)
                    summary != null -> reply(p.summaryText(summary), chips)
                    u?.intent == "MINE" -> {
                        // Lo suyo se contesta AQUÍ, con lo que la app ya tiene: el diario nunca sale del móvil.
                        val mine = mineAnswer(answer)
                        val period = u.dateFrom?.let { p.dateRange(it, u.dateTo) }
                        append(AssistantMessage(newId(), false, p.mineText(mine, period), chips,
                            journalEntries = mine.entries, journalTotal = mine.count))
                    }
                    breakdown != null -> append(AssistantMessage(newId(), false, p.breakdownIntro(breakdown), chips, breakdown = breakdown))
                    else -> {
                        // Búsqueda simple: primero se ENSEÑAN las vías o bloques que cumplen, y cada una se puede tocar.
                        val hits = loadHits(answer, location)
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
        val found = searchLines(name)
        val matches = AssistantActions.matchLines(found, name, answer.resolvedSchool?.id)
        if (matches.isEmpty()) { reply(p.noLineNamed(name), chips); return }
        val journal = runCatching { getMyJournal() }.getOrDefault(emptyList())
        val date = u.dateFrom ?: todayIso()
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
            "ADD_FAVORITE" -> addFavorite(action.schoolId)
            "REMOVE_FAVORITE" -> removeFavorite(action.schoolId)
            "ENABLE_WEEKEND_ALERT" -> {
                val change = AssistantActions.withSchoolAlert(getWeekendAlert(), action.schoolId)
                if (change.already) return p.alreadyInWeekendAlert(action.schoolName)
                val alert = change.alert ?: return p.weekendAlertFull()
                updateWeekendAlert(alert)
                return p.weekendAlertDone(action.schoolName)
            }
            "LOG_DONE", "LOG_PROJECT" -> {
                val hit = action.hit ?: throw IllegalStateException("falta la vía")
                val date = action.date ?: throw IllegalStateException("falta la fecha")
                val status = if (action.kind == "LOG_PROJECT") "PROJECT" else "DONE"
                val req = AssistantActions.journalRequest(hit, status, date)
                val sent = networkMonitor.isOnline.value && runCatching { createJournalEntry(req) }.isSuccess
                // Sin red → a la cola, como en la ficha: se sube al recuperar la conexión.
                if (!sent) outboxRepo.enqueue(OutboxType.JOURNAL, hit.schoolId, json.encodeToString(req))
                if (status == "DONE") journalDoneStore.add(AssistantActions.doneKey(hit))
                val line = hit.lineName ?: hit.blockName
                return when {
                    !sent -> p.loggedOffline(line)
                    status == "DONE" -> p.loggedDone(line)
                    else -> p.loggedProject(line)
                }
            }
            else -> requestOpen(action.schoolId)
        }
        return p.actionDone(action.kind, action.schoolName)
    }

    fun cancel(messageId: Long) = updateMessage(messageId) { m ->
        m.copy(pendingActions = m.pendingActions.map {
            if (it.state == PendingAction.State.WAITING) it.copy(state = PendingAction.State.CANCELLED) else it
        })
    }

    // ── Lo suyo (diario, favoritas, quedadas) ─────────────────────────────────────────────────────────────────────

    private suspend fun mineAnswer(answer: AssistantAnswer): MineAnswerer.MineAnswer {
        val u = answer.understood
        val schoolId = answer.resolvedSchool?.id
        val schoolName = answer.resolvedSchool?.name
        return when (u?.mineTopic ?: "STATS") {
            "FAVORITES" -> MineAnswerer.favorites(getMyFavorites())
            "LAST_VISIT" -> MineAnswerer.lastVisit(getMyJournal(), schoolId, schoolName)
            "MEETUPS" -> MineAnswerer.meetups(getMeetups.execute(schoolId = schoolId), todayIso(), schoolId, joinedOnly = false)
            else -> MineAnswerer.stats(
                getMyJournal(), u?.discipline, u?.gradeMin, u?.gradeMax, u?.year, u?.dateFrom, u?.dateTo, schoolId, schoolName)
        }
    }

    /** "yyyy-MM-dd" de hoy en Madrid (las quedadas se guardan con sus días en ese formato). */
    private fun todayIso(): String = LocalDate.now(ZoneId.of("Europe/Madrid")).toString()

    // ── Resultados de una búsqueda simple ─────────────────────────────────────────────────────────────────────────

    /**
     * Pide al buscador de la app lo que entendió el asistente: con filtros (grado, modalidad, roca, orientación, escuela,
     * distancia, estrellas) usa el modo "explorar"; con solo un nombre, la búsqueda de siempre por nombre. Si la frase
     * nombró un sector, se quedan los de ese sector. Si pidió lo mejor valorado, se piden también las estrellas y se
     * ordena por ellas.
     */
    private suspend fun loadHits(answer: AssistantAnswer, location: UserLocation?, explicit: List<String>? = null): List<LineSearchHit> {
        val u = answer.understood ?: return emptyList()
        // Una escuela, o varias ("los 7a de Albarracín y Zarzalejo"). Con escuela dicha, ella manda: no se filtra por
        // distancia ni se mandan las coordenadas (las piedras sin coordenadas desaparecerían).
        val schoolIds: List<String>? = explicit ?: answer.resolvedSchools.map { it.id }.takeIf { it.isNotEmpty() }
            ?: answer.resolvedSchool?.let { listOf(it.id) }
        val schoolId = schoolIds?.firstOrNull()
        val rated = u.topRated || u.minStars != null
        val hasFilter = u.gradeMin != null || u.gradeMax != null || u.discipline != null || u.rockTypes.isNotEmpty() ||
            u.orientations.isNotEmpty() || u.maxDistanceKm != null || schoolId != null || rated ||
            (u.useMyLocation && location != null) || u.beta != null || u.startType != null || u.withTopo || u.notDone

        var hits: List<LineSearchHit> = emptyList()
        if (hasFilter) {
            val criteria = LineExploreCriteria(
                gradeMin = u.gradeMin, gradeMax = u.gradeMax, discipline = u.discipline,
                rockTypes = u.rockTypes.ifEmpty { null },
                schoolIds = schoolIds,
                orientations = u.orientations.ifEmpty { null },
                lat = if (schoolIds == null) location?.lat else null,
                lon = if (schoolIds == null) location?.lon else null,
                // "cerca de mí" sin cifra = 50 km, como en el servidor.
                maxDistanceKm = if (schoolIds != null) null else (u.maxDistanceKm ?: if (u.useMyLocation && location != null) 50.0 else null),
                sort = "DISTANCE", offset = 0, withRatings = rated, beta = u.beta,
                startType = u.startType, withTopo = u.withTopo, withRepetitions = false
            )
            hits = exploreLines(criteria)
        } else if (!u.q.isNullOrEmpty()) {
            hits = searchLines(u.q!!)
        }
        if (u.notDone) runCatching { getMyJournal() }.getOrNull()?.let { hits = MineAnswerer.excludeDone(hits, it) }
        answer.resolvedSector?.name?.let { sector -> hits = hits.filter { (it.sectorName ?: "").equals(sector, ignoreCase = true) } }
        if (rated) hits = presenter.rankByRating(hits, u.minStars)
        return hits
    }

    // ── Ubicación ─────────────────────────────────────────────────────────────────────────────────────────────────

    /** Ubicación actual si hay permiso (la usa el servidor solo para medir distancias). */
    private suspend fun currentLocation(): UserLocation? =
        if (!locationProvider.hasPermission()) null else runCatching { locationProvider.current() }.getOrNull()

    companion object {
        /** Tope de caracteres de una pregunta (el servidor recorta a lo mismo). */
        const val MAX_TEXT = 200
        /** Máximo de preguntas esperando: protege el cupo diario y el límite por minuto del servidor. */
        const val MAX_QUEUED = 5
    }
}

/** Etiquetas del catálogo (roca, región, orientación) en el idioma de la app, para el presentador. */
internal object CatalogLabelsAdapter : AssistantLabels {
    override fun rock(raw: String) = CatalogLabels.rock(raw)
    override fun region(raw: String) = CatalogLabels.region(raw)
    override fun aspect(code: String) = CatalogLabels.aspect(code)
}
