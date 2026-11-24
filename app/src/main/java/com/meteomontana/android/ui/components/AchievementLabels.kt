package com.meteomontana.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.meteomontana.android.R
import com.meteomontana.android.domain.usecase.journal.Achievement
import com.meteomontana.android.domain.usecase.journal.AchievementKind
import com.meteomontana.android.domain.usecase.journal.AchievementTier
import com.meteomontana.android.ui.theme.Ok

/** Color de cada dificultad: gris, verde, azul, morado y dorado. */
@Composable
fun tierColor(tier: AchievementTier): Color = when (tier) {
    AchievementTier.COMMON -> MaterialTheme.colorScheme.onSurfaceVariant
    AchievementTier.UNCOMMON -> Ok
    AchievementTier.RARE -> com.meteomontana.android.ui.theme.Rain
    AchievementTier.EPIC -> Color(0xFF8B5CF6)
    AchievementTier.LEGENDARY -> com.meteomontana.android.ui.theme.Warn
}

@Composable
fun achievementText(a: Achievement): String =
    achievementText(a, androidx.compose.ui.platform.LocalContext.current.resources)

/** Igual, fuera de la UI (el asistente lo usa para decir los siguientes logros). */
fun achievementText(a: Achievement, res: android.content.res.Resources): String {
    fun s(id: Int, vararg args: Any): String = res.getString(id, *args)
    return when (a.kind) {
        AchievementKind.GRADE -> s(
            if (a.discipline == "ROUTE") R.string.ach_grade_route else R.string.ach_grade_boulder, a.param)
        AchievementKind.DAY_GRADE -> s(
            if (a.discipline == "ROUTE") R.string.ach_day_route else R.string.ach_day_boulder, a.count, a.param)
        AchievementKind.TOTAL_GRADE -> s(
            if (a.discipline == "ROUTE") R.string.ach_total_route else R.string.ach_total_boulder, a.count, a.param)
        AchievementKind.VOLUME -> s(R.string.ach_volume, a.param)
        AchievementKind.FLASHES -> s(R.string.ach_flashes, a.param)
        AchievementKind.ONSIGHTS -> s(R.string.ach_onsights, a.param)
        AchievementKind.FLASH_MAX -> s(R.string.ach_flash_max)
        AchievementKind.ONSIGHT_MAX -> s(R.string.ach_onsight_max)
        AchievementKind.SCHOOLS -> s(R.string.ach_schools, a.param)
        AchievementKind.REGIONS -> s(R.string.ach_regions, a.param)
        AchievementKind.ABROAD -> s(R.string.ach_abroad)
        AchievementKind.WEEKENDS -> s(R.string.ach_weekends, a.param)
        AchievementKind.WEEK_STREAK -> s(R.string.ach_week_streak, a.param)
        AchievementKind.MONTH_STREAK -> s(R.string.ach_month_streak, a.param)
        AchievementKind.DAYS_IN_YEAR -> s(R.string.ach_days_year, a.param)
        AchievementKind.EVERY_MONTH -> s(R.string.ach_every_month)
        AchievementKind.PROJECTS -> if (a.param == "1") s(R.string.ach_project_one)
            else s(R.string.ach_projects, a.param)
    }
}
