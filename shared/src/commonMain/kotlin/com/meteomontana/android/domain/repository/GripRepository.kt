package com.meteomontana.android.domain.repository

import com.meteomontana.android.data.api.dto.CreateGripMeasureSessionRequest
import com.meteomontana.android.data.api.dto.CreateGripWorkoutRequest
import com.meteomontana.android.data.api.dto.SaveGripMaxRequest
import com.meteomontana.android.domain.model.GripMaxRecord
import com.meteomontana.android.domain.model.GripMeasureSession
import com.meteomontana.android.domain.model.GripType
import com.meteomontana.android.domain.model.GripWorkout

interface GripRepository {
    suspend fun getGripTypes(): List<GripType>
    suspend fun getMyGripMaxes(): List<GripMaxRecord>
    suspend fun saveGripMax(req: SaveGripMaxRequest): GripMaxRecord
    suspend fun getMyGripMeasureSessions(gripTypeId: Int?, hand: String?): List<GripMeasureSession>
    suspend fun createGripMeasureSession(req: CreateGripMeasureSessionRequest): GripMeasureSession
    suspend fun getMyGripWorkouts(): List<GripWorkout>
    suspend fun getGripWorkout(id: String): GripWorkout
    suspend fun createGripWorkout(req: CreateGripWorkoutRequest): GripWorkout
    suspend fun updateGripWorkout(id: String, req: CreateGripWorkoutRequest): GripWorkout
    suspend fun deleteGripWorkout(id: String)
}
