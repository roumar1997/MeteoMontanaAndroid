package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.usecase.journal.YearGoal

/** Objetivo del año (privado, en el servidor). */
interface GoalRepository {
    suspend fun get(year: Int): YearGoal?
    suspend fun save(goal: YearGoal): YearGoal
    suspend fun delete(year: Int)
}
