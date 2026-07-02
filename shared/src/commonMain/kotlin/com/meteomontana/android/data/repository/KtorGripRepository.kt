package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorGripApi
import com.meteomontana.android.data.api.dto.CreateGripMeasureSessionRequest
import com.meteomontana.android.data.api.dto.CreateGripWorkoutRequest
import com.meteomontana.android.data.api.dto.SaveGripMaxRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.GripMaxRecord
import com.meteomontana.android.domain.model.GripMeasureSession
import com.meteomontana.android.domain.model.GripType
import com.meteomontana.android.domain.model.GripWorkout
import com.meteomontana.android.domain.repository.GripRepository

class KtorGripRepository(private val api: KtorGripApi) : GripRepository {
    override suspend fun getGripTypes(): List<GripType> =
        api.getGripTypes().map { it.toDomain() }
    override suspend fun getMyGripMaxes(): List<GripMaxRecord> =
        api.getMyGripMaxes().map { it.toDomain() }
    override suspend fun saveGripMax(req: SaveGripMaxRequest): GripMaxRecord =
        api.saveGripMax(req).toDomain()
    override suspend fun getMyGripMeasureSessions(gripTypeId: Int?, hand: String?): List<GripMeasureSession> =
        api.getMyGripMeasureSessions(gripTypeId, hand).map { it.toDomain() }
    override suspend fun createGripMeasureSession(req: CreateGripMeasureSessionRequest): GripMeasureSession =
        api.createGripMeasureSession(req).toDomain()
    override suspend fun getMyGripWorkouts(): List<GripWorkout> =
        api.getMyGripWorkouts().map { it.toDomain() }
    override suspend fun getGripWorkout(id: String): GripWorkout =
        api.getGripWorkout(id).toDomain()
    override suspend fun createGripWorkout(req: CreateGripWorkoutRequest): GripWorkout =
        api.createGripWorkout(req).toDomain()
    override suspend fun updateGripWorkout(id: String, req: CreateGripWorkoutRequest): GripWorkout =
        api.updateGripWorkout(id, req).toDomain()
    override suspend fun deleteGripWorkout(id: String) =
        api.deleteGripWorkout(id)
}
