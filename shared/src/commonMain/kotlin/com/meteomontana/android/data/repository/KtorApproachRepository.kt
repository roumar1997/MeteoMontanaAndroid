package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorApproachApi
import com.meteomontana.android.data.api.dto.AddApproachPinRequest
import com.meteomontana.android.data.api.dto.CreateApproachRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Approach
import com.meteomontana.android.domain.model.ApproachPin
import com.meteomontana.android.domain.repository.ApproachRepository

class KtorApproachRepository(private val api: KtorApproachApi) : ApproachRepository {
    override suspend fun getApproaches(schoolId: String): List<Approach> =
        api.getApproaches(schoolId).map { it.toDomain() }

    override suspend fun createApproach(schoolId: String, req: CreateApproachRequest): Approach =
        api.createApproach(schoolId, req).toDomain()

    override suspend fun addPin(approachId: String, req: AddApproachPinRequest): ApproachPin =
        api.addPin(approachId, req).toDomain()

    override suspend fun deleteApproach(approachId: String) = api.deleteApproach(approachId)

    override suspend fun deletePin(pinId: String) = api.deletePin(pinId)
}
