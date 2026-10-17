package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.GoalDto
import com.meteomontana.android.data.api.KtorGoalApi
import com.meteomontana.android.domain.repository.GoalRepository
import com.meteomontana.android.domain.usecase.journal.YearGoal

class KtorGoalRepository(private val api: KtorGoalApi) : GoalRepository {
    override suspend fun get(year: Int): YearGoal? = api.get(year)?.toDomain()
    override suspend fun save(goal: YearGoal): YearGoal =
        api.put(GoalDto(goal.year, goal.discipline, goal.grade, goal.ascents, goal.days)).toDomain()
    override suspend fun delete(year: Int) = api.delete(year)

    private fun GoalDto.toDomain() = YearGoal(year, discipline, grade, ascents, days)
}
