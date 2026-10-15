package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.model.HeightSplit
import com.meteomontana.android.domain.model.OrientationSummary
import com.meteomontana.android.domain.model.SunHour
import com.meteomontana.android.domain.model.SunHours
import kotlinx.serialization.Serializable

/** DTOs de la votación comunitaria (espejo de CommunityVoteController). */

@Serializable
data class OrientationSummaryDto(
    val photoIndex: Int? = null,
    val votes: Map<String, Int> = emptyMap(),
    val consensus: String? = null,
    val myVote: String? = null
)

@Serializable
data class OrientationVoteRequest(val photoIndex: Int? = null, val aspect: String)

@Serializable
data class GradeSummaryDto(
    val lineId: String,
    val votes: Map<String, Int> = emptyMap(),
    val setterGrade: String? = null,
    val displayedGrade: String? = null,
    val myVote: String? = null,
    val votesByHeight: Map<String, Map<String, Int>> = emptyMap(),
    val heightSplit: HeightSplitDto? = null,
    val myHeight: String? = null
)

@Serializable
data class HeightSplitDto(val tallGrade: String, val shortGrade: String)

/** Mi altura: {"group": "TALL" | "SHORT" | "NONE"}; group null = aún no he elegido. */
@Serializable
data class HeightDto(val group: String? = null)

@Serializable
data class GradeVoteRequest(val grade: String)

@Serializable
data class GradeSplitsRequest(val lineIds: List<String>)

@Serializable
data class SunHourDto(val time: String, val inSun: Boolean)

@Serializable
data class SunHoursDto(val aspect: String? = null, val hours: List<SunHourDto> = emptyList())

fun OrientationSummaryDto.toDomain() = OrientationSummary(photoIndex, votes, consensus, myVote)
fun GradeSummaryDto.toDomain() = GradeSummary(
    lineId, votes, setterGrade, displayedGrade, myVote,
    votesByHeight, heightSplit?.let { HeightSplit(it.tallGrade, it.shortGrade) }, myHeight
)
fun SunHoursDto.toDomain() = SunHours(aspect, hours.map { SunHour(it.time, it.inSun) })
