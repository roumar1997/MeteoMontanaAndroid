package com.meteomontana.android.ui.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.local.SharedPrefsKeyValueStore
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymAscentKeys
import com.meteomontana.android.domain.model.gym.GymBreakdown
import com.meteomontana.android.domain.model.gym.GymSummary
import com.meteomontana.android.domain.usecase.gym.GetCachedGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymStatsUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymSummaryUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserJournalUseCase
import com.meteomontana.android.domain.usecase.journal.JournalStatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** «Tu rocódromo» elegido por la persona (solo en este móvil; el servidor aún no tiene el campo). */
class HomeGymPreference @Inject constructor(@ApplicationContext context: Context) {
    private val store = SharedPrefsKeyValueStore(context, "cumbre_profile_prefs")

    fun get(): Long? = store.getString(KEY)?.toLongOrNull()

    fun set(gymId: Long?) = store.putString(KEY, gymId?.toString().orEmpty())

    private companion object { const val KEY = "home_gym_id" }
}

data class ProfileClimbState(
    val summary: GymSummary = GymSummary(),
    /** Días distintos en roca (diario); null mientras no se sabe. */
    val rockDays: Int? = null,
    val segments: List<BarSeg> = emptyList(),
    val homeGym: Gym? = null,
    val streakWeeks: Int = 0,
    /** Grado máximo en roca y nº de escuelas del diario (para la línea «Roca» de Estadísticas). */
    val rockMax: String? = null,
    val schools: Int = 0
) {
    /** Días escalando en total: roca + sesiones de rocódromo (un día en cada sitio cuenta aparte). */
    val totalDays: Int get() = (rockDays ?: 0) + summary.sessions
}

/**
 * Lo del rocódromo en el perfil, propio o de otra persona. Propio: primero lo que hay en el móvil y luego el resumen del
 * servidor (que tiene todo el historial). Ajeno: el del servidor (la privacidad la pone él). La lógica de cálculo vive en
 * `shared` ([GymBreakdown]).
 */
@HiltViewModel
class ProfileClimbViewModel @Inject constructor(
    private val getSummary: GetGymSummaryUseCase,
    private val getCachedGyms: GetCachedGymsUseCase,
    private val getStats: GetGymStatsUseCase,
    private val getMyJournal: GetMyJournalUseCase,
    private val getUserJournal: GetUserJournalUseCase,
    private val getMyStats: com.meteomontana.android.domain.usecase.journal.GetMyJournalStatsUseCase,
    private val getUserStats: com.meteomontana.android.domain.usecase.journal.GetUserStatsUseCase,
    private val homeGymPref: HomeGymPreference
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileClimbState())
    val state: StateFlow<ProfileClimbState> = _state

    /** [uid] null = el perfil propio ([myUid] es mi uid para pedir el resumen al servidor). */
    fun load(uid: String?, myUid: String?) {
        val own = uid == null || uid == myUid
        val gyms = getCachedGyms().associateBy { it.id }
        if (own) _state.update { build(getSummary.local { gyms[it]?.name }, it.rockDays, gyms, own = true).copy(rockMax = it.rockMax, schools = it.schools) }
        viewModelScope.launch {
            val rock = async { runCatching { if (own) getMyJournal() else getUserJournal(uid!!) }.getOrNull()?.let { JournalStatsCalculator.rockDays(it) } }
            val jstats = async { runCatching { if (own) getMyStats() else getUserStats(uid!!) }.getOrNull() }
            if (own) runCatching { getSummary.refresh() }
            val target = if (own) myUid else uid
            val remote = target?.let { runCatching { getSummary.remote(it) }.getOrNull() }
            val summary = remote?.let { r -> r.copy(byGym = r.byGym.map { g -> g.copy(name = g.name ?: gyms[g.gymId]?.name) }) }
                ?: if (own) getSummary.local { gyms[it]?.name } else GymSummary()
            val js = jstats.await()
            _state.update { build(summary, rock.await() ?: it.rockDays, gyms, own).copy(rockMax = js?.maxGrade, schools = js?.schoolCount ?: it.schools) }
        }
    }

    /** Color (hex) de la escala de cualquier rocódromo conocido con ese nombre de color. */
    fun colorHex(key: String): String? =
        getCachedGyms().firstNotNullOfOrNull { it.scale?.takeIf { s -> s.isColors }?.levelNamed(key)?.hex }

    fun gym(id: Long): Gym? = getCachedGyms().firstOrNull { it.id == id }

    private fun build(summary: GymSummary, rockDays: Int?, gyms: Map<Long, Gym>, own: Boolean): ProfileClimbState {
        val colorHex = { key: String -> gyms.values.firstNotNullOfOrNull { it.scale?.takeIf { s -> s.isColors }?.levelNamed(key)?.hex } }
        val segs = GymBreakdown.byGrade(summary.items).map { (g, n) -> BarSeg(g, null, g.uppercase(), n, GymAscentKeys.gradeRank(g)) } +
            GymBreakdown.byColor(summary.items).mapNotNull { a ->
                colorHex(a.key)?.let { BarSeg(null, it, a.key.replaceFirstChar { c -> c.uppercase() }, a.count, 1000) }
            }
        return ProfileClimbState(
            summary = summary, rockDays = rockDays, segments = segs.sortedBy { it.order },
            homeGym = if (own) homeGymPref.get()?.let { gyms[it] } else null,
            streakWeeks = if (own) getStats.cached().streakWeeks else 0
        )
    }
}
