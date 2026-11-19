package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.gym.GymCorrection
import com.meteomontana.android.domain.model.gym.GymDiscipline
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymProposal
import com.meteomontana.android.domain.model.gym.GymReport
import com.meteomontana.android.domain.model.gym.GymReportKind
import com.meteomontana.android.domain.model.gym.GymReportStatus
import com.meteomontana.android.domain.model.gym.NewGymDraft
import kotlinx.serialization.Serializable

@Serializable
data class GymReportRequestDto(
    val issues: List<String> = emptyList(),
    val comment: String? = null,
    val photoUrl: String? = null
)

@Serializable
data class NewGymRequestDto(
    val name: String,
    val lat: Double,
    val lon: Double,
    val kind: String? = null,
    val discipline: String? = null,
    val outdoor: Boolean? = null,
    val comment: String? = null,
    val photoUrl: String? = null
)

@Serializable
data class GymProposalDto(
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val kind: String? = null,
    val discipline: String? = null,
    val outdoor: Boolean = false
)

@Serializable
data class GymReportDto(
    val id: Long,
    val gymId: Long? = null,
    val gymName: String? = null,
    val authorUid: String = "",
    val kind: String? = null,
    val issues: List<String> = emptyList(),
    val comment: String? = null,
    val photoUrl: String? = null,
    val proposed: GymProposalDto? = null,
    val status: String? = null,
    val createdAt: String = ""
)

@Serializable
data class GymPhotoUrlDto(val url: String)

fun GymCorrection.toDto() = GymReportRequestDto(issues.map { it.code }, comment?.trim()?.ifEmpty { null }, photoUrl)

fun NewGymDraft.toDto() = NewGymRequestDto(
    name = name.trim(), lat = lat, lon = lon, kind = kind?.name, discipline = discipline?.name,
    outdoor = outdoor, comment = comment?.trim()?.ifEmpty { null }, photoUrl = photoUrl
)

fun GymReportDto.toDomain() = GymReport(
    id = id, gymId = gymId, gymName = gymName, authorUid = authorUid,
    kind = GymReportKind.parse(kind), issues = issues, comment = comment, photoUrl = photoUrl,
    proposed = proposed?.let {
        GymProposal(it.name, it.lat, it.lon, GymKind.parse(it.kind), GymDiscipline.parse(it.discipline), it.outdoor)
    },
    status = GymReportStatus.parse(status), createdAt = createdAt
)
