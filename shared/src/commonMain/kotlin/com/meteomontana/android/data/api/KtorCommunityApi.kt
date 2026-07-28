package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.GradeSummaryDto
import com.meteomontana.android.data.api.dto.GradeVoteRequest
import com.meteomontana.android.data.api.dto.OrientationSummaryDto
import com.meteomontana.android.data.api.dto.OrientationVoteRequest
import com.meteomontana.android.data.api.dto.SunHoursDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody

/** Votación comunitaria: orientación de paredes, sol/sombra y grado por consenso. */
class KtorCommunityApi(private val client: HttpClient) {

    suspend fun getOrientation(blockId: String): List<OrientationSummaryDto> =
        client.get("blocks/$blockId/orientation").body()

    suspend fun voteOrientation(blockId: String, photoIndex: Int?, aspect: String): List<OrientationSummaryDto> =
        client.put("blocks/$blockId/orientation") {
            setBody(OrientationVoteRequest(photoIndex, aspect))
        }.body()

    suspend fun getSunHours(blockId: String, photoIndex: Int?): SunHoursDto =
        client.get("blocks/$blockId/sun-hours") {
            if (photoIndex != null) parameter("photoIndex", photoIndex)
        }.body()

    suspend fun getGradeVotes(lineId: String): GradeSummaryDto =
        client.get("lines/$lineId/grade-votes").body()

    suspend fun voteGrade(lineId: String, grade: String): GradeSummaryDto =
        client.put("lines/$lineId/grade-votes") { setBody(GradeVoteRequest(grade)) }.body()
}
