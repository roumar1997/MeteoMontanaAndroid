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
 * Las acciones que el asistente hace por ti tras CONFIRMAR: favoritas, avisos de fin de semana y de cada mañana, y
 * apuntar una vía en el diario (a la cola si no hay red). Devuelven la frase de respuesta. Sacado de
 * AssistantViewModel (2026-11-05).
 */
class AssistantActionRunner @Inject constructor(
    private val createJournalEntry: CreateJournalEntryUseCase,
    private val outboxRepo: OutboxRepository,
    private val networkMonitor: NetworkMonitor,
    private val journalDoneStore: JournalDoneStore,
    private val addFavoriteUseCase: AddFavoriteUseCase,
    private val removeFavoriteUseCase: RemoveFavoriteUseCase,
    private val getWeekendAlert: GetWeekendAlertUseCase,
    private val updateWeekendAlert: UpdateWeekendAlertUseCase
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun addFavorite(schoolId: String) = addFavoriteUseCase(schoolId)
    suspend fun removeFavorite(schoolId: String) = removeFavoriteUseCase(schoolId)

    suspend fun weekendAlert(action: PendingAction, p: AssistantPresenter): String {
        val change = AssistantActions.withSchoolAlert(getWeekendAlert(), action.schoolId)
        if (change.already) return p.alreadyInWeekendAlert(action.schoolName)
        val alert = change.alert ?: return p.weekendAlertFull()
        updateWeekendAlert(alert)
        return p.weekendAlertDone(action.schoolName)
    }

    suspend fun morningAlert(p: AssistantPresenter): String {
        val change = AssistantActions.withMorningAlert(getWeekendAlert())
        if (change.already) return p.morningAlertAlready()
        val alert = change.alert ?: throw IllegalStateException("aviso no disponible")
        updateWeekendAlert(alert)
        return p.morningAlertDone()
    }

    /** Apunta la vía como hecha o proyecto; sin red va a la cola y se sube al recuperar la conexión. */
    suspend fun log(action: PendingAction, p: AssistantPresenter): String {
        val hit = action.hit ?: throw IllegalStateException("falta la vía")
        val date = action.date ?: throw IllegalStateException("falta la fecha")
        val status = if (action.kind == "LOG_PROJECT") "PROJECT" else "DONE"
        val req = AssistantActions.journalRequest(hit, status, date)
        val sent = networkMonitor.isOnline.value && runCatching { createJournalEntry(req) }.isSuccess
        if (!sent) outboxRepo.enqueue(OutboxType.JOURNAL, hit.schoolId, json.encodeToString(req))
        if (status == "DONE") journalDoneStore.add(AssistantActions.doneKey(hit))
        val line = hit.lineName ?: hit.blockName
        return when {
            !sent -> p.loggedOffline(line)
            status == "DONE" -> p.loggedDone(line)
            else -> p.loggedProject(line)
        }
    }
}
