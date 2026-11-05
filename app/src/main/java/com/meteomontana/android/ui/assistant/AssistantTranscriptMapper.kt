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

/** Pasa la conversación del asistente a lo que se guarda en el móvil y de vuelta (con sus tarjetas). */
internal object AssistantTranscriptMapper {

    /** Los mensajes guardados como mensajes del chat; [newId] da un id nuevo a cada uno. */
    fun restore(saved: StoredAssistantConversation, newId: () -> Long): List<AssistantMessage> = saved.messages.map { m ->
        val payload = m.payload?.let { runCatching { AssistantSnapshotCodec.decode(it) }.getOrNull() }
        AssistantMessage(
            id = newId(), isUser = m.isUser, text = m.text, chips = m.chips,
            recommendation = payload?.recommendation, breakdown = payload?.breakdown, weather = payload?.weather,
            hits = payload?.hits.orEmpty(), hitsTotal = payload?.hitsTotal ?: 0,
            createdAt = m.createdAt, restored = true
        )
    }

    /** La conversación (textos, etiquetas y tarjetas) lista para guardarse unas horas, solo en el móvil. */
    fun toStored(uid: String, messages: List<AssistantMessage>, previous: AssistantUnderstood?) = StoredAssistantConversation(
        uid = uid, savedAt = System.currentTimeMillis(),
        messages = messages.map { StoredAssistantConversation.Message(it.isUser, it.text, it.chips, payload(it), it.createdAt) },
        previous = previous?.let { StoredAssistantConversation.Understood.from(it) }
    )

    /** Tarjetas del mensaje como texto (null si no tiene). Se guardan TODAS las vías que se enseñan (hasta 300). */
    private fun payload(m: AssistantMessage): String? {
        if (!m.hasCards) return null
        val p = AssistantMessagePayload(m.recommendation, m.breakdown, m.hits.take(300), m.hitsTotal, m.weather)
        return runCatching { AssistantSnapshotCodec.encode(p) }.getOrNull()
    }
}

/** Etiquetas del catálogo (roca, región, orientación) en el idioma de la app, para el presentador. */
internal object CatalogLabelsAdapter : AssistantLabels {
    override fun rock(raw: String) = CatalogLabels.rock(raw)
    override fun region(raw: String) = CatalogLabels.region(raw)
    override fun aspect(code: String) = CatalogLabels.aspect(code)
}
