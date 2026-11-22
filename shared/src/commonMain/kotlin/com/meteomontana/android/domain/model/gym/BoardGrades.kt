package com.meteomontana.android.domain.model.gym

/** One grade of the «GRADOS» row of a board: base grade, count and its share of the total (0..1) for the stacked bar. */
data class BoardGradeSlice(val grade: String, val count: Int, val share: Float)

/**
 * «GRADOS» row of each board in the Boards screen (§28): per-grade summary (easiest first) and the grade filter of the
 * problem list. Same on iOS and Android.
 */
object BoardGrades {

    /** Per-grade counts of [items] (already filtered by board and angle), easiest first, with their share of the total. */
    fun slices(items: List<GymAscent>): List<BoardGradeSlice> {
        val grades = GymBreakdown.byGrade(items)
        val total = grades.sumOf { it.second }
        if (total <= 0) return emptyList()
        return grades.map { (g, n) -> BoardGradeSlice(g, n, n.toFloat() / total) }
    }

    /** Hardest grade among [slices] (the «Top»); null when there are none. */
    fun top(slices: List<BoardGradeSlice>): String? = slices.maxByOrNull { GymAscentKeys.gradeRank(it.grade) }?.grade

    /** Largest count among [slices] (scale of the per-grade bars), at least 1. */
    fun maxCount(slices: List<BoardGradeSlice>): Int = slices.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    /** Tapping [tapped] selects it; tapping the selected one again clears it (null = all grades). */
    fun toggle(selected: String?, tapped: String): String? = if (sameGrade(selected, tapped)) null else tapped

    /** True when [grade] is the selected one. */
    fun isSelected(selected: String?, grade: String): Boolean = sameGrade(selected, grade)

    /** The rows whose base grade is [grade]; null = all. Applied on top of the angle filter of [BoardBlocks.rows]. */
    fun filter(rows: List<BoardBlockRow>, grade: String?): List<BoardBlockRow> =
        if (grade == null) rows else rows.filter { sameGrade(it.grade, grade) }

    private fun base(g: String): String = g.substringBefore('/').trim().lowercase()

    private fun sameGrade(a: String?, b: String): Boolean = a != null && base(a) == base(b)
}
