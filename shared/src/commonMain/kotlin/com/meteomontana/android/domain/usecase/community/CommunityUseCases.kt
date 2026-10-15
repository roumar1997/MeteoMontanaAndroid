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

/** Quita mi voto de grado de una vía. */
class RemoveGradeVoteUseCase(private val repo: CommunityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(lineId: String): GradeSummary = repo.removeGradeVote(lineId)
}

/** Los grados partidos por altura de una lista de vías (buscador entre escuelas, diario). */
class GetGradeSplitsUseCase(private val repo: CommunityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(lineIds: List<String>): Map<String, com.meteomontana.android.domain.model.HeightSplit> =
        repo.getGradeSplits(lineIds)
}

/** Los grados partidos por altura de toda una escuela (lineId → {alta, baja}), para pintar las listas. */
class GetSchoolGradeSplitsUseCase(private val repo: CommunityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String): Map<String, com.meteomontana.android.domain.model.HeightSplit> =
        repo.getSchoolGradeSplits(schoolId)
}
