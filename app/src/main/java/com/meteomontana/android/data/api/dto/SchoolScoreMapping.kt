package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.SchoolScore

fun SchoolScoreDto.toDomain() = SchoolScore(
    id = id,
    todayScore = todayScore,
    hourlyScores = hourlyScores,
    dryRock = dryRock,
    rainMm = rainMm,
    rainProb = rainProb
)
