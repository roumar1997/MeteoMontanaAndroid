package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.CreateGripMeasureSessionRequest
import com.meteomontana.android.data.api.dto.CreateGripWorkoutRequest
import com.meteomontana.android.data.api.dto.GripMaxRecordDto
import com.meteomontana.android.data.api.dto.GripMeasureSessionDto
import com.meteomontana.android.data.api.dto.GripTypeDto
import com.meteomontana.android.data.api.dto.GripWorkoutDto
import com.meteomontana.android.data.api.dto.SaveGripMaxRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class KtorGripApi(private val client: HttpClient) {

    suspend fun getGripTypes(): List<GripTypeDto> = client.get("grips/types").body()

    suspend fun getMyGripMaxes(): List<GripMaxRecordDto> = client.get("me/grip-maxes").body()

    suspend fun saveGripMax(req: SaveGripMaxRequest): GripMaxRecordDto =
        client.post("me/grip-maxes") { setBody(req) }.body()

    suspend fun getMyGripMeasureSessions(gripTypeId: Int?, hand: String?): List<GripMeasureSessionDto> =
        client.get("me/grip-measure-sessions") {
            gripTypeId?.let { parameter("gripTypeId", it) }
            hand?.let { parameter("hand", it) }
        }.body()

    suspend fun createGripMeasureSession(req: CreateGripMeasureSessionRequest): GripMeasureSessionDto =
        client.post("me/grip-measure-sessions") { setBody(req) }.body()

    suspend fun getMyGripWorkouts(): List<GripWorkoutDto> = client.get("me/grip-workouts").body()

    suspend fun getGripWorkout(id: String): GripWorkoutDto = client.get("me/grip-workouts/$id").body()

    suspend fun createGripWorkout(req: CreateGripWorkoutRequest): GripWorkoutDto =
        client.post("me/grip-workouts") { setBody(req) }.body()

    suspend fun updateGripWorkout(id: String, req: CreateGripWorkoutRequest): GripWorkoutDto =
        client.put("me/grip-workouts/$id") { setBody(req) }.body()

    suspend fun deleteGripWorkout(id: String) { client.delete("me/grip-workouts/$id") }
}
