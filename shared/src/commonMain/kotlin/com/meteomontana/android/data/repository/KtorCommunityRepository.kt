package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorCommunityApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.model.OrientationSummary
import com.meteomontana.android.domain.model.SunHours
import com.meteomontana.android.domain.repository.CommunityRepository

class KtorCommunityRepository(private val api: KtorCommunityApi) : CommunityRepository {

    @Throws(Exception::class)
    override suspend fun getOrientation(blockId: String): List<OrientationSummary> =
        api.getOrientation(blockId).map { it.toDomain() }

    @Throws(Exception::class)
    override suspend fun voteOrientation(blockId: String, photoIndex: Int?, aspect: String): List<OrientationSummary> =
        api.voteOrientation(blockId, photoIndex, aspect).map { it.toDomain() }

    @Throws(Exception::class)
    override suspend fun getSchoolOrientations(schoolId: String): Map<String, String> =
        api.getSchoolOrientations(schoolId)

    @Throws(Exception::class)
    override suspend fun getSunHours(blockId: String, photoIndex: Int?): SunHours =
        api.getSunHours(blockId, photoIndex).toDomain()

    @Throws(Exception::class)
    override suspend fun getGradeVotes(lineId: String): GradeSummary =
        api.getGradeVotes(lineId).toDomain()

    @Throws(Exception::class)
    override suspend fun voteGrade(lineId: String, grade: String): GradeSummary =
        api.voteGrade(lineId, grade).toDomain()
}
