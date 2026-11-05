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
 * Lo que el asistente consulta en la propia app: buscar vías, el diario, lo suyo (favoritas, quedadas, estadísticas),
 * el objetivo del año, los logros y el grado por altura. Sacado de AssistantViewModel (2026-11-05): el ViewModel
 * lleva la conversación y esto los datos.
 */
class AssistantData @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchLinesUseCase: SearchLinesUseCase,
    private val exploreLines: ExploreLinesUseCase,
    private val getMyJournal: GetMyJournalUseCase,
    private val getMyFavorites: GetMyFavoritesUseCase,
    private val getMeetups: GetMeetupsUseCase,
    private val getYearGoal: GetYearGoalUseCase,
    private val getSchools: GetSchoolsUseCase,
    private val getGradeVotes: GetGradeVotesUseCase
) {
    suspend fun searchLines(name: String): List<LineSearchHit> = searchLinesUseCase(name)
    suspend fun journal() = getMyJournal()

    suspend fun goalOrAchievements(topic: String, p: AssistantPresenter): String {
        val journal = getMyJournal()
        val today = todayIso()
        if (topic == "GOAL") {
            val year = today.take(4).toInt()
            val goal = getYearGoal(year) ?: return p.noGoalText(year)
            return p.goalText(GoalCalculator.progress(goal, journal))
        }
        val schools = runCatching { getSchools() }.getOrDefault(emptyList())
        val places = schools.associate { it.id to SchoolPlace(it.region, it.country) }
        return p.achievementsText(AchievementsCalculator.compute(journal, places, today)) {
            com.meteomontana.android.ui.screens.profile.achievementText(it, AppLanguage.wrap(context).resources)
        }
    }

    /** Grado votado por altura de las vías encontradas (las 4 primeras con vía concreta). */
    suspend fun heightGradeText(hits: List<LineSearchHit>, p: AssistantPresenter): String {
        val lines = hits.filter { it.lineId != null }.take(4).mapNotNull { hit ->
            val summary = runCatching { getGradeVotes(hit.lineId!!) }.getOrNull() ?: return@mapNotNull null
            p.heightGradeLine(hit.lineName ?: hit.blockName, summary)
        }
        return if (lines.isEmpty()) p.noHeightVotes() else lines.joinToString("\n")
    }

    // ── Lo suyo (diario, favoritas, quedadas) ─────────────────────────────────────────────────────────────────────

    suspend fun mineAnswer(answer: AssistantAnswer): MineAnswerer.MineAnswer {
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
    fun todayIso(): String = LocalDate.now(ZoneId.of("Europe/Madrid")).toString()

    // ── Resultados de una búsqueda simple ─────────────────────────────────────────────────────────────────────────

    /**
     * Pide al buscador de la app lo que entendió el asistente: con filtros (grado, modalidad, roca, orientación, escuela,
     * distancia, estrellas) usa el modo "explorar"; con solo un nombre, la búsqueda de siempre por nombre. Si la frase
     * nombró un sector, se quedan los de ese sector. Si pidió lo mejor valorado, se piden también las estrellas y se
     * ordena por ellas.
     */
    suspend fun loadHits(answer: AssistantAnswer, location: UserLocation?, p: AssistantPresenter, explicit: List<String>? = null): List<LineSearchHit> {
        val u = answer.understood ?: return emptyList()
        // Una escuela, o varias ("los 7a de Albarracín y Zarzalejo"). Con escuela dicha, ella manda: no se filtra por
        // distancia ni se mandan las coordenadas (las piedras sin coordenadas desaparecerían).
        val schoolIds: List<String>? = explicit ?: answer.resolvedSchools.map { it.id }.takeIf { it.isNotEmpty() }
            ?: answer.resolvedSchool?.let { listOf(it.id) }
        val schoolId = schoolIds?.firstOrNull()
        val rated = u.topRated || u.minStars != null
        val hasFilter = u.gradeMin != null || u.gradeMax != null || u.discipline != null || u.rockTypes.isNotEmpty() ||
            u.orientations.isNotEmpty() || u.maxDistanceKm != null || schoolId != null || rated ||
            (u.useMyLocation && location != null) || u.beta != null || u.startType != null || u.withTopo || u.notDone || u.mostRepeated

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
                // "Las clásicas": por cuánta gente la ha hecho (el servidor ya ordena y cuenta las repeticiones).
                sort = if (u.mostRepeated) "MOST_REPEATED" else "DISTANCE", offset = 0, withRatings = rated, beta = u.beta,
                startType = u.startType, withTopo = u.withTopo, withRepetitions = u.mostRepeated
            )
            hits = exploreLines(criteria)
        } else if (!u.q.isNullOrEmpty()) {
            hits = searchLinesUseCase(u.q!!)
        }
        if (u.notDone) runCatching { getMyJournal() }.getOrNull()?.let { hits = MineAnswerer.excludeDone(hits, it) }
        answer.resolvedSector?.name?.let { sector -> hits = hits.filter { (it.sectorName ?: "").equals(sector, ignoreCase = true) } }
        if (rated) hits = p.rankByRating(hits, u.minStars)
        return hits
    }

}
