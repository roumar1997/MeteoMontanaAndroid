package com.meteomontana.android.domain.usecase.community

import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.model.OrientationSummary
import com.meteomontana.android.domain.model.SunHours
import com.meteomontana.android.domain.repository.CommunityRepository

/** Casos de uso de la votación comunitaria (orientación, sol/sombra, grado). */

class GetOrientationUseCase(private val repo: CommunityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(blockId: String): List<OrientationSummary> =
        repo.getOrientation(blockId)
}

class VoteOrientationUseCase(private val repo: CommunityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(blockId: String, photoIndex: Int?, aspect: String): List<OrientationSummary> =
        repo.voteOrientation(blockId, photoIndex, aspect)
}

class GetSchoolOrientationsUseCase(private val repo: CommunityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String): Map<String, String> =
        repo.getSchoolOrientations(schoolId)
}

class GetSunHoursUseCase(private val repo: CommunityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(blockId: String, photoIndex: Int?): SunHours =
        repo.getSunHours(blockId, photoIndex)
}

class GetGradeVotesUseCase(private val repo: CommunityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(lineId: String): GradeSummary = repo.getGradeVotes(lineId)
}

class VoteGradeUseCase(private val repo: CommunityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(lineId: String, grade: String): GradeSummary =
        repo.voteGrade(lineId, grade)
}
