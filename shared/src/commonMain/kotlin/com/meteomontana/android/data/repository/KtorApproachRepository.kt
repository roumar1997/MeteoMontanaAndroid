package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorApproachApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Approach
import com.meteomontana.android.domain.repository.ApproachRepository

class KtorApproachRepository(private val api: KtorApproachApi) : ApproachRepository {
    override suspend fun getApproaches(schoolId: String): List<Approach> =
        api.getApproaches(schoolId).map { it.toDomain() }
}
