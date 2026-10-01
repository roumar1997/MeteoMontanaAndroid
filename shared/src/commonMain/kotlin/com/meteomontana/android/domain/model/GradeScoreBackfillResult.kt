package com.meteomontana.android.domain.model

/** Resultado de rellenar grade_score en las vías ya existentes — BLOCK_SEARCH_DESIGN.md §2/§8. */
data class GradeScoreBackfillResult(val total: Int, val updated: Int, val unrecognized: Int)
