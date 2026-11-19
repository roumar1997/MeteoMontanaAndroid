package com.meteomontana.android.domain.usecase.gym

import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymSessionSource
import com.meteomontana.android.domain.model.gym.GymStats
import com.meteomontana.android.domain.model.gym.GymStatsCalculator
import com.meteomontana.android.domain.repository.GymSessionRepository

/** «He entrenado aqui hoy»: apunta (o quita) el dia de hoy en un rocodromo. Idempotente. */
class RecordGymSessionUseCase(private val repo: GymSessionRepository) {
    @Throws(Exception::class)
    suspend fun execute(gymId: Long, source: GymSessionSource): GymSession = repo.checkInToday(gymId, source)

    @Throws(Exception::class)
    suspend fun undo(gymId: Long) = repo.undoToday(gymId)

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
