package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.domain.repository.BlockRepository
import com.meteomontana.android.domain.repository.LineRating

data class RatingResult(
    val avgStars: Float,
    val ratingCount: Long,
    val myStars: Int
)

/** Valoración de vías por estrellas. Habla con el PUERTO, no con la API. */
class RateLineUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend fun rate(blockId: String, lineId: String, stars: Int): RatingResult =
        repo.rateLine(blockId, lineId, stars).toResult()

    @Throws(Exception::class)
    suspend fun unrate(blockId: String, lineId: String): RatingResult =
        repo.unrateLine(blockId, lineId).toResult()

    private fun LineRating.toResult() = RatingResult(avgStars, ratingCount, myStars)
}
