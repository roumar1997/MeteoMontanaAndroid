package com.meteomontana.android.ui.screens.profile

import android.content.Context
import com.meteomontana.android.domain.model.gym.GymOrder
import com.meteomontana.android.domain.usecase.gym.GetGymFavoritesUseCase
import com.meteomontana.android.domain.usecase.gym.GymOrderUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.local.SharedPrefsKeyValueStore
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymAscentKeys
import com.meteomontana.android.domain.model.gym.GymBreakdown
import com.meteomontana.android.domain.model.gym.GymSummary
import com.meteomontana.android.domain.model.gym.HomeGym
import com.meteomontana.android.domain.usecase.gym.HomeGymUseCase
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

/** «Tu rocódromo» elegido por la persona: caché local del id; la verdad vive en el servidor (`me/home-gym`). */
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
    val homeGym: HomeGym? = null,
    /** Orden de las categorias de Ascensiones («BOARDS,BLOCKS,ROUTES»): el mio (movil + servidor) o el que eligio la otra persona. */
    val ascentOrder: String? = null,
    /** Orden de rocodromos (ids «5,3,9»): el mio (movil + servidor) o el de la otra persona; ya aplicado a [summary].byGym. */
    val gymOrder: String? = null,
    /** Mis rocodromos favoritos: van primero mientras no se haya ordenado a mano. */
    val favorites: Set<Long> = emptySet(),
    /** Tableros plegados en la pestaña Boards (nombres de [com.meteomontana.android.domain.model.gym.BoardKind]). */
    val expandedBoards: Set<String> = emptySet(),
    val streakWeeks: Int = 0,
    /** Grado máximo en roca y nº de escuelas del diario (para la línea «Roca» de Estadísticas). */
    val rockMax: String? = null,
    val schools: Int = 0,
    /** Roca del diario: bloques y vías hechos y el grado máximo de cada modalidad (mitad «Roca» de la tarjeta). */
    val rockBoulders: Int = 0,
    val rockRoutes: Int = 0,
    val rockMaxBoulder: String? = null,
    val rockMaxRoute: String? = null
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
    private val homeGymPref: HomeGymPreference,
    private val homeGymUc: HomeGymUseCase,
    private val ascentOrderPref: AscentOrderPreference,
    private val ascentOrderUc: com.meteomontana.android.domain.usecase.gym.AscentOrderUseCase,
    private val gymOrderPref: GymOrderPreference,
    private val gymOrderUc: GymOrderUseCase,
    private val getFavorites: GetGymFavoritesUseCase,
    private val boardsExpandedPref: BoardsExpandedPreference,
    /** Copia local de las stats del diario: sin conexión el perfil enseña lo último que se vio, no ceros. */
    private val profileCache: com.meteomontana.android.data.local.ProfileCache
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileClimbState())
    val state: StateFlow<ProfileClimbState> = _state

    /** [uid] null = el perfil propio ([myUid] es mi uid para pedir el resumen al servidor). */
    fun load(uid: String?, myUid: String?) {
        val own = uid == null || uid == myUid
        val gyms = getCachedGyms().associateBy { it.id }
        if (own) _state.update { build(getSummary.local { gyms[it]?.name }, it.rockDays, gyms, own = true, it.favorites).copy(rockMax = it.rockMax, schools = it.schools, rockBoulders = it.rockBoulders, rockRoutes = it.rockRoutes, rockMaxBoulder = it.rockMaxBoulder, rockMaxRoute = it.rockMaxRoute) }
        _state.update { it.copy(expandedBoards = boardsExpandedPref.get()) }
        viewModelScope.launch {
            val favs = runCatching { getFavorites.execute() }.getOrDefault(_state.value.favorites)
            val rock = async { runCatching { if (own) getMyJournal() else getUserJournal(uid!!) }.getOrNull()?.let { JournalStatsCalculator.rockDays(it) } }
            val jstats = async { runCatching { if (own) getMyStats() else getUserStats(uid!!) }.getOrNull() }
            if (own) runCatching { getSummary.refresh() }
            val target = if (own) myUid else uid
            val remote = target?.let { runCatching { getSummary.remote(it) }.getOrNull() }
            val summary = remote?.let { r -> r.copy(byGym = r.byGym.map { g -> g.copy(name = g.name ?: gyms[g.gymId]?.name) }) }
                ?: if (own) getSummary.local { gyms[it]?.name } else GymSummary()
            if (own) { syncHomeGym(remote); syncAscentOrder(remote); syncGymOrder(remote) }
            // Sin conexión las llamadas fallan: lo propio cae a la última copia guardada (antes salían ceros).
            val js = jstats.await() ?: if (own) runCatching { profileCache.load()?.stats }.getOrNull() else null
            val rockDays = rock.await()?.also { if (own) runCatching { profileCache.saveRockDays(it) } }
                ?: if (own) runCatching { profileCache.loadRockDays() }.getOrNull() else null
            _state.update { build(summary, rockDays ?: it.rockDays, gyms, own, favs).copy(
                rockMax = js?.maxGrade, schools = js?.schoolCount ?: it.schools, rockBoulders = js?.boulderCount ?: it.rockBoulders,
                rockRoutes = js?.routeCount ?: it.rockRoutes, rockMaxBoulder = js?.maxBoulderGrade, rockMaxRoute = js?.maxRouteGrade
            ) }
        }
    }

    /** Color (hex) de la escala de cualquier rocódromo conocido con ese nombre de color. */
    fun colorHex(key: String): String? =
        getCachedGyms().firstNotNullOfOrNull { it.scale?.takeIf { s -> s.isColors }?.levelNamed(key)?.hex }

    /** Color (hex) de un nivel de la escala de ESE rocódromo; si no se conoce su escala, el de cualquier rocódromo con ese nombre. */
    fun colorHexFor(gymId: Long, key: String): String? =
        gym(gymId)?.scale?.takeIf { it.isColors }?.levelNamed(key)?.hex ?: colorHex(key)

    /** Propio: si el servidor tiene mi rocódromo lo guardo en el móvil; si no lo tiene y yo sí (elegido antes o sin red), lo subo. */
    private suspend fun syncHomeGym(remote: GymSummary?) {
        if (remote == null) return
        val server = remote.homeGym
        val local = homeGymPref.get()
        if (server != null) homeGymPref.set(server.id)
        else if (local != null) runCatching { homeGymUc.set(local) }
    }

    /** Propio: el servidor manda (lo guardo en el movil); si no tiene nada o hay un cambio sin subir, subo el del movil. */
    private suspend fun syncAscentOrder(remote: GymSummary?) {
        if (remote != null) ascentOrderPref.sync(remote.ascentOrder) { ascentOrderUc.set(it) }
    }

    /** Igual con el orden de rocodromos; si manda el servidor, la lista se recoloca. */
    private suspend fun syncGymOrder(remote: GymSummary?) {
        if (remote != null) gymOrderPref.sync(remote.gymOrder) { gymOrderUc.set(it) }
    }

    /** Guarda mi orden de categorias: se ve ya, queda en el movil y se sube al servidor (si no hay red, se sube mas tarde). */
    fun setAscentOrder(order: String) {
        _state.update { it.copy(ascentOrder = order) }
        viewModelScope.launch { ascentOrderPref.save(order) { ascentOrderUc.set(it) } }
    }

    /** Guarda mi orden de rocodromos (ids completos, en el orden elegido): se ve ya, queda en el movil y se sube. */
    fun setGymOrder(ids: List<Long>) {
        val order = GymOrder.serialize(ids)
        _state.update { it.copy(gymOrder = order, summary = it.summary.copy(byGym = GymOrder.sort(it.summary.byGym, ids, it.favorites))) }
        viewModelScope.launch { gymOrderPref.save(order) { gymOrderUc.set(it) } }
    }

    /** Pliega o despliega un tablero de la pestaña Boards y lo recuerda. */
    fun toggleBoard(kind: com.meteomontana.android.domain.model.gym.BoardKind) {
        val next = _state.value.expandedBoards.let { if (kind.name in it) it - kind.name else it + kind.name }
        boardsExpandedPref.set(next)
        _state.update { it.copy(expandedBoards = next) }
    }

    /** Elige (o quita, con null) mi rocódromo desde el hueco del perfil: se ve ya y se sube al servidor. */
    fun setHomeGym(gym: Gym?) {
        homeGymPref.set(gym?.id)
        _state.update { it.copy(homeGym = gym?.let { g -> HomeGym(g.id, g.name, g.city) }) }
        viewModelScope.launch { runCatching { homeGymUc.set(gym?.id) } }
    }

    fun gym(id: Long): Gym? = getCachedGyms().firstOrNull { it.id == id }

    private fun build(summary: GymSummary, rockDays: Int?, gyms: Map<Long, Gym>, own: Boolean, favorites: Set<Long>): ProfileClimbState {
        val colorHex = { key: String -> gyms.values.firstNotNullOfOrNull { it.scale?.takeIf { s -> s.isColors }?.levelNamed(key)?.hex } }
        val segs = GymBreakdown.byGrade(summary.items).map { (g, n) -> BarSeg(g, null, g.uppercase(), n, GymAscentKeys.gradeRank(g)) } +
            GymBreakdown.byColor(summary.items).mapNotNull { a ->
                colorHex(a.key)?.let { BarSeg(null, it, com.meteomontana.android.util.CatalogLabels.holdColor(a.key), a.count, 1000) }
            }
        val gymOrder = if (own) gymOrderPref.get() ?: summary.gymOrder else summary.gymOrder
        val sorted = summary.copy(byGym = GymOrder.sort(summary.byGym, GymOrder.parse(gymOrder), favorites))
        return ProfileClimbState(
            summary = sorted, gymOrder = gymOrder, favorites = favorites, expandedBoards = boardsExpandedPref.get(), rockDays = rockDays, segments = segs.sortedBy { it.order },
            homeGym = summary.homeGym ?: if (own) homeGymPref.get()?.let { gyms[it] }?.let { HomeGym(it.id, it.name, it.city) } else null,
            ascentOrder = if (own) ascentOrderPref.get() ?: summary.ascentOrder else summary.ascentOrder,
            streakWeeks = if (own) getStats.cached().streakWeeks else 0
        )
    }
}
