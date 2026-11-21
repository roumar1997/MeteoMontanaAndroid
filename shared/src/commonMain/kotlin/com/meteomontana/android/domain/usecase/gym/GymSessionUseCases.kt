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

    @Throws(Exception::class)
    suspend fun refresh() { repo.refresh() }

    /** Apunta lo hecho en la sesión de ese día (sustituye lo anterior) y devuelve la sesión. */
    @Throws(Exception::class)
    suspend fun saveAscents(gymId: Long, date: LocalDate, ascents: List<GymAscent>): GymSession =
        repo.saveAscents(gymId, date, ascents)

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

/** Resumen de TODO el historial de rocódromo (perfil propio y ajeno): primero el del móvil y luego el del servidor. */
class GetGymSummaryUseCase(private val repo: GymSessionRepository) {
    /** Calculado con las sesiones del móvil (sin red); [name] da el nombre de cada rocódromo. */
    fun local(name: (Long) -> String? = { null }): com.meteomontana.android.domain.model.gym.GymSummary =
        com.meteomontana.android.domain.model.gym.GymBreakdown.summarize(repo.sessions(), repo.today(), name)

    /** El del servidor para [uidOrUsername]; null si no se puede ver o no hay red. */
    @Throws(Exception::class)
    suspend fun remote(uidOrUsername: String): com.meteomontana.android.domain.model.gym.GymSummary? = repo.summary(uidOrUsername)

    /** Sesiones propias, para refrescar antes de calcular. */
    @Throws(Exception::class)
    suspend fun refresh() { repo.refresh() }
}
