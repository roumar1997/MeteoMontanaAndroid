package com.meteomontana.android.domain.usecase.journal

import com.meteomontana.android.domain.repository.GoalRepository

/** Mi objetivo de ese año, o null si no me puse ninguno. */
class GetYearGoalUseCase(private val repository: GoalRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(year: Int): YearGoal? = repository.get(year)
}

class SaveYearGoalUseCase(private val repository: GoalRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(goal: YearGoal): YearGoal = repository.save(goal)
}

class DeleteYearGoalUseCase(private val repository: GoalRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(year: Int) = repository.delete(year)
}
