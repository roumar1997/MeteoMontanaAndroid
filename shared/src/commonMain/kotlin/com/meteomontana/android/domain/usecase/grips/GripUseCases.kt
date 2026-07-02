package com.meteomontana.android.domain.usecase.grips

import com.meteomontana.android.data.api.dto.CreateGripMeasureSessionRequest
import com.meteomontana.android.data.api.dto.CreateGripWorkoutRequest
import com.meteomontana.android.data.api.dto.SaveGripMaxRequest
import com.meteomontana.android.domain.model.GripMaxRecord
import com.meteomontana.android.domain.model.GripMeasureSession
import com.meteomontana.android.domain.model.GripType
import com.meteomontana.android.domain.model.GripWorkout
import com.meteomontana.android.domain.repository.GripRepository

class GetGripTypesUseCase(private val repo: GripRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<GripType> = repo.getGripTypes()
}

class GetMyGripMaxesUseCase(private val repo: GripRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<GripMaxRecord> = repo.getMyGripMaxes()
}

class SaveGripMaxUseCase(private val repo: GripRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(req: SaveGripMaxRequest): GripMaxRecord = repo.saveGripMax(req)
}

class GetMyGripMeasureSessionsUseCase(private val repo: GripRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(gripTypeId: Int? = null, hand: String? = null): List<GripMeasureSession> =
        repo.getMyGripMeasureSessions(gripTypeId, hand)
}

class CreateGripMeasureSessionUseCase(private val repo: GripRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(req: CreateGripMeasureSessionRequest): GripMeasureSession =
        repo.createGripMeasureSession(req)
}

class GetMyGripWorkoutsUseCase(private val repo: GripRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<GripWorkout> = repo.getMyGripWorkouts()
}

class GetGripWorkoutUseCase(private val repo: GripRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String): GripWorkout = repo.getGripWorkout(id)
}

class CreateGripWorkoutUseCase(private val repo: GripRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(req: CreateGripWorkoutRequest): GripWorkout = repo.createGripWorkout(req)
}

class UpdateGripWorkoutUseCase(private val repo: GripRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String, req: CreateGripWorkoutRequest): GripWorkout =
        repo.updateGripWorkout(id, req)
}

class DeleteGripWorkoutUseCase(private val repo: GripRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String) = repo.deleteGripWorkout(id)
}
