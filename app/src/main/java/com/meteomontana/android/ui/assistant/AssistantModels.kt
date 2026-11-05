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

// Modelos del chat del asistente: acción pendiente, mensaje, petición de abrir escuela y estado de la pantalla.

/** Una acción propuesta en el chat. Nunca se ejecuta sin que el usuario la confirme con un toque. */
data class PendingAction(
    val id: Long,
    /** ADD_FAVORITE | REMOVE_FAVORITE | OPEN_SCHOOL | ENABLE_WEEKEND_ALERT | ENABLE_MORNING_ALERT | SET_HOME | CREATE_MEETUP | LOG_DONE | LOG_PROJECT */
    val kind: String,
    val schoolId: String,
    val schoolName: String,
    /** Para apuntar en el diario: la vía encontrada y el día ("yyyy-MM-dd"). */
    val hit: LineSearchHit? = null,
    val date: String? = null,
    /** Hora de una quedada ("09:00"), si se dijo. */
    val time: String? = null,
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
    val pendingActions: List<PendingAction> = emptyList(),
    /** Las piedras más probables de una foto («¿Qué piedra es esta?»). No se guardan con la conversación. */
    val photoMatches: List<StoneCandidate> = emptyList(),
    /** Respuesta a "¿se puede escalar en X?": las restricciones de la zona o de la escuela. */
    val restrictions: AssistantRestrictions? = null,
    /** Plan de un viaje de varios días: la mejor escuela de cada día. */
    val trip: List<AssistantPresenter.TripDay> = emptyList(),
    /** Varias vías del día para apuntar de una vez con un solo toque. */
    val confirmAll: Boolean = false
) {
    val hasCards: Boolean get() = recommendation != null || breakdown != null || weather != null || hits.isNotEmpty()
}

/** Algo que el usuario ha tocado en el chat y hay que abrir en la app (escuela y, si se sabe, su vía). */
data class AssistantOpenRequest(val schoolId: String, val viaId: String?)

data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val input: String = "",
    val isLoading: Boolean = false,
    /** Qué paso de la búsqueda de una foto va (se enseña junto a los puntos de «escribiendo»). */
    val loadingNote: String? = null,
    /** Preguntas escritas o dictadas mientras aún se contestaba otra: esperan su turno y se contestan POR ORDEN. */
    val queued: List<String> = emptyList(),
    val openRequest: AssistantOpenRequest? = null,
    /** La burbuja se mantuvo pulsada: el chat, al abrirse, empieza a escuchar directamente. */
    val listenOnOpen: Boolean = false,
    /** El usuario confirmó preparar una quedada: el chat se cierra y se abre el formulario ya relleno. */
    val meetupRequest: com.meteomontana.android.ui.screens.meetups.MeetupPrefill? = null
) {
    val canSend: Boolean get() = input.isNotBlank() && queued.size < AssistantViewModel.MAX_QUEUED
}
