package com.meteomontana.android.domain.usecase.gym

import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymAscentStats
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymSessionSource
import com.meteomontana.android.domain.model.gym.GymStats
import com.meteomontana.android.domain.model.gym.GymStatsCalculator
import com.meteomontana.android.domain.model.gym.GymTraining
import com.meteomontana.android.domain.model.gym.GymTrainingPeriod
import com.meteomontana.android.domain.model.gym.GymTrainingSummary
import kotlinx.datetime.LocalDate
import com.meteomontana.android.domain.repository.GymSessionRepository

/** «He entrenado aqui hoy»: apunta (o quita) el dia de hoy en un rocodromo. Idempotente. */
class RecordGymSessionUseCase(private val repo: GymSessionRepository) {
    @Throws(Exception::class)
    suspend fun execute(gymId: Long, source: GymSessionSource): GymSession = repo.checkInToday(gymId, source)

    @Throws(Exception::class)
    suspend fun undo(gymId: Long) = repo.undoToday(gymId)

    @Throws(Exception::class)
    suspend fun refresh() { repo.refresh() }

    /** Sube los entrenos apuntados sin red (al volver la conexion o al abrir la app). */
    @Throws(Exception::class)
    suspend fun syncPending() { repo.syncPending() }

    fun hasSessionToday(gymId: Long): Boolean {
        val today = repo.today()
        return repo.sessions().any { it.gymId == gymId && it.date == today }
    }
}

/** Estadisticas de la tarjeta «Rocodromo»: al instante desde el movil y luego mezcladas con el servidor. */
class GetGymStatsUseCase(private val repo: GymSessionRepository) {
    fun cached(): GymStats = GymStatsCalculator.compute(repo.sessions(), repo.today())

    @Throws(Exception::class)
    suspend fun refresh(): GymStats = GymStatsCalculator.compute(repo.refresh(), repo.today())
}

/** Pantalla «Entrenos»: sesiones del periodo (Semana/Mes/Año), lo hecho por color o grado y sus estadísticas. */
class GymTrainingUseCase(private val repo: GymSessionRepository) {
    fun today(): LocalDate = repo.today()

    /** Sesiones del periodo al instante, desde el móvil (sin red). */
    fun summary(period: GymTrainingPeriod, date: LocalDate): GymTrainingSummary = GymTraining.summary(repo.sessions(), period, date)

    /** La sesión de [gymId] ese día en el móvil (también la de «Solo board», gymId 0); null si no hay. */
    fun session(gymId: Long, date: LocalDate): GymSession? = repo.sessions().firstOrNull { it.gymId == gymId && it.date == date }

    @Throws(Exception::class)
    suspend fun refresh() { repo.refresh() }

    /** Apunta lo hecho en la sesión de ese día (sustituye lo anterior) y devuelve la sesión. */
    @Throws(Exception::class)
    suspend fun saveAscents(gymId: Long, date: LocalDate, ascents: List<GymAscent>,
                           boardProblems: List<com.meteomontana.android.domain.model.gym.BoardProblem>? = null): GymSession =
        repo.saveAscents(gymId, date, ascents, boardProblems)

    /** The logged board block as it is on this phone (angle filled in when the day has only one); null if it is not here. */
    fun boardEntry(entry: com.meteomontana.android.domain.model.gym.BoardEntry): com.meteomontana.android.domain.model.gym.BoardEntry? {
        val s = session(entry.gymId, entry.day) ?: return null
        val full = com.meteomontana.android.domain.model.gym.BoardEditing.withAngle(entry, s)
        return full.takeIf { com.meteomontana.android.domain.model.gym.BoardEditing.contains(com.meteomontana.android.domain.model.gym.BoardLog(s.ascents, s.boardProblems), it) }
    }

    /** Deletes a logged board block from its day (§26); local first and queued offline. False if it is not on this phone. */
    @Throws(Exception::class)
    suspend fun deleteBoardEntry(entry: com.meteomontana.android.domain.model.gym.BoardEntry): Boolean {
        val full = boardEntry(entry) ?: return false
        val s = session(full.gymId, full.day) ?: return false
        val rest = com.meteomontana.android.domain.model.gym.BoardEditing.remove(com.meteomontana.android.domain.model.gym.BoardLog(s.ascents, s.boardProblems), full)
        repo.saveAscents(full.gymId, full.day, rest.ascents, rest.problems)
        return true
    }

    /**
     * Edits a logged board block (§26): it leaves its day and is logged again on [date] (the same day or another one, same gym)
     * with the new grade, angle, name, reps and attempts. False if it is not on this phone.
     */
    @Throws(Exception::class)
    suspend fun editBoardEntry(
        entry: com.meteomontana.android.domain.model.gym.BoardEntry, date: LocalDate, grade: String, angle: Int?, name: String, reps: Int, attempts: Int?
    ): Boolean {
        val full = boardEntry(entry) ?: return false
        val from = session(full.gymId, full.day) ?: return false
        val rest = com.meteomontana.android.domain.model.gym.BoardEditing.remove(com.meteomontana.android.domain.model.gym.BoardLog(from.ascents, from.boardProblems), full)
        val base = if (date == full.day) rest else {
            repo.saveAscents(full.gymId, full.day, rest.ascents, rest.problems)
            session(full.gymId, date).let { com.meteomontana.android.domain.model.gym.BoardLog(it?.ascents.orEmpty(), it?.boardProblems.orEmpty()) }
        }
        val log = com.meteomontana.android.domain.model.gym.BoardLogging.add(base, full.board, grade, angle, name, reps, attempts)
        repo.saveAscents(full.gymId, date, log.ascents, log.problems)
        return true
    }

    @Throws(Exception::class)
    suspend fun ascentStats(uid: String, period: GymTrainingPeriod, date: LocalDate, gymId: Long?): GymAscentStats =
        repo.ascentStats(uid, period, date, gymId)
}

/** «Tu rocódromo»: leer y fijar (o quitar) el rocódromo habitual en el servidor. */
class HomeGymUseCase(private val repo: GymSessionRepository) {
    @Throws(Exception::class)
    suspend fun mine(): com.meteomontana.android.domain.model.gym.HomeGym? = repo.myHomeGym()

    @Throws(Exception::class)
    suspend fun set(gymId: Long?): com.meteomontana.android.domain.model.gym.HomeGym? = repo.setHomeGym(gymId)
}

/** Orden de las categorias de Ascensiones: leer el mio y guardarlo en el servidor para que los demas lo vean. */
class AscentOrderUseCase(private val repo: GymSessionRepository) {
    @Throws(Exception::class)
    suspend fun mine(): String? = repo.myAscentOrder()

    @Throws(Exception::class)
    suspend fun set(order: String): String? = repo.setAscentOrder(order)
}

/** Orden de mis rocodromos: leerlo y guardarlo en el servidor para que los demas lo vean. */
class GymOrderUseCase(private val repo: GymSessionRepository) {
    @Throws(Exception::class)
    suspend fun mine(): String? = repo.myGymOrder()

    @Throws(Exception::class)
    suspend fun set(order: String): String? = repo.setGymOrder(order)
}

/** Resumen de TODO el historial de rocódromo (perfil propio y ajeno): primero el del móvil y luego el del servidor. */
class GetGymSummaryUseCase(private val repo: GymSessionRepository) {
    /** Calculado con las sesiones del móvil (sin red); [name] da el nombre de cada rocódromo. */
    fun local(name: (Long) -> String? = { null }): com.meteomontana.android.domain.model.gym.GymSummary =
        com.meteomontana.android.domain.model.gym.GymBreakdown.summarize(repo.sessions(), repo.today(), name)

    /** El del servidor para [uidOrUsername]; null si no se puede ver o no hay red. */
    @Throws(Exception::class)
    suspend fun remote(uidOrUsername: String): com.meteomontana.android.domain.model.gym.GymSummary? = repo.summary(uidOrUsername)

    /** Hay una copia guardada del resumen de esa persona (si no, sin red solo se sabría lo apuntado en el móvil). */
    fun hasOfflineCopy(uidOrUsername: String): Boolean = repo.cachedSummary(uidOrUsername) != null

    /**
     * Lo mejor que se sabe SIN red: la última copia buena del servidor (todo el historial) o, si el móvil tiene más sesiones
     * (apuntadas sin red desde entonces), lo calculado con ellas. Nunca ceros inventados si hubo una copia buena.
     */
    fun offline(uidOrUsername: String, name: (Long) -> String? = { null }): com.meteomontana.android.domain.model.gym.GymSummary {
        val local = local(name)
        val cached = repo.cachedSummary(uidOrUsername)?.let { c -> c.copy(byGym = c.byGym.map { g -> g.copy(name = g.name ?: name(g.gymId)) }) }
        return if (cached != null && cached.sessions >= local.sessions) cached else local
    }

    /** Sesiones propias, para refrescar antes de calcular. */
    @Throws(Exception::class)
    suspend fun refresh() { repo.refresh() }
}
