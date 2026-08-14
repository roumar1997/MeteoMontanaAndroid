package com.meteomontana.android.domain.repository

import com.meteomontana.android.data.api.dto.AddApproachPinRequest
import com.meteomontana.android.data.api.dto.CreateApproachRequest
import com.meteomontana.android.domain.model.Approach
import com.meteomontana.android.domain.model.ApproachPin

interface ApproachRepository {
    suspend fun getApproaches(schoolId: String): List<Approach>

    /** SOLO ADMIN — el backend responde 403 a cualquier otro uid. */
    suspend fun createApproach(schoolId: String, req: CreateApproachRequest): Approach

    /** Cualquier usuario (hoy solo se llama desde la UI de admin — §2.6/§10). */
    suspend fun addPin(approachId: String, req: AddApproachPinRequest): ApproachPin

    /** SOLO ADMIN. */
    suspend fun deleteApproach(approachId: String)

    /** SOLO ADMIN. */
    suspend fun deletePin(pinId: String)
}
