package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.data.api.KtorBlockApi

data class RatingResult(
    val avgStars: Float,
    val ratingCount: Long,
    val myStars: Int
)

class RateLineUseCase(private val api: KtorBlockApi) {
    @Throws(Exception::class)
    suspend fun rate(blockId: String, lineId: String, stars: Int): RatingResult {
        val dto = api.rateLine(blockId, lineId, stars)
        return RatingResult(dto.avgStars, dto.ratingCount, dto.myStars)
    }

    @Throws(Exception::class)
    suspend fun unrate(blockId: String, lineId: String): RatingResult {
        val dto = api.unrateLine(blockId, lineId)
        return RatingResult(dto.avgStars, dto.ratingCount, dto.myStars)
    }
}
