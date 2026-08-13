package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.Approach

interface ApproachRepository {
    suspend fun getApproaches(schoolId: String): List<Approach>
}
