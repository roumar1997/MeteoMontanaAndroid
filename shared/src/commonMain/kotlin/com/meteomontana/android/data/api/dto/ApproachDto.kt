package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.Approach
import com.meteomontana.android.domain.model.ApproachPin
import kotlinx.serialization.Serializable

@Serializable
data class ApproachPinDto(
    val id: String,
    val lat: Double,
    val lon: Double,
    val positionIdx: Int,
    val kind: String,
    val message: String? = null,
    val photoPath: String? = null,
    val authorUid: String,
    val status: String
)

@Serializable
data class ApproachDto(
    val id: String,
    val schoolId: String,
    val fromBlockId: String? = null,
    val toBlockId: String? = null,
    val name: String? = null,
    val pathJson: String,
    val distanceM: Int? = null,
    val ascentM: Int? = null,
    val durationMin: Int? = null,
    val source: String,
    val status: String,
    val authorUid: String,
    val pins: List<ApproachPinDto> = emptyList()
)

fun ApproachDto.toDomain() = Approach(
    id = id, schoolId = schoolId, fromBlockId = fromBlockId, toBlockId = toBlockId,
    name = name, pathJson = pathJson, distanceM = distanceM, ascentM = ascentM,
    durationMin = durationMin, source = source, status = status, authorUid = authorUid,
    pins = pins.map { it.toDomain() }
)

fun ApproachPinDto.toDomain() = ApproachPin(
    id = id, lat = lat, lon = lon, positionIdx = positionIdx, kind = kind,
    message = message, photoPath = photoPath, authorUid = authorUid, status = status
)

@Serializable
data class CreateApproachRequest(
    val fromBlockId: String? = null,
    val toBlockId: String? = null,
    val name: String? = null,
    val pathJson: String,
    val distanceM: Int? = null,
    val ascentM: Int? = null,
    val durationMin: Int? = null,
    val source: String = "RECORDED"
)

@Serializable
data class AddApproachPinRequest(
    val lat: Double,
    val lon: Double,
    val positionIdx: Int = 0,
    val kind: String,
    val message: String? = null,
    val photoPath: String? = null
)
