package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.usecase.journal.Achievement
import com.meteomontana.android.domain.usecase.journal.AchievementKind
import com.meteomontana.android.domain.usecase.journal.GoalProgress
import com.meteomontana.android.domain.usecase.journal.StyleShare
import com.meteomontana.android.domain.usecase.journal.YearComparison
import com.meteomontana.android.domain.usecase.journal.YearGoal
import com.meteomontana.android.ui.screens.detail.GradePill
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.terraFillColor

private val GOAL_GRADES = listOf("5", "6", "7", "8").flatMap { n -> listOf("a", "a+", "b", "b+", "c", "c+").map { n + it } }

/** Se pregunta UNA vez al año por el objetivo; si dices "ahora no", queda un enlace discreto para ponerlo. */
private fun promptKey(year: Int) = "goal_prompt_dismissed_$year"

/**
 * OBJETIVO DEL AÑO (opcional, solo en tus estadísticas). Sin objetivo: tarjeta que lo propone (una vez al año) o un
 * enlace discreto. Con objetivo: cada meta con su barra de progreso.
 */
@Composable
fun GoalSection(goal: GoalProgress?, year: Int, onEdit: () -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("stats", android.content.Context.MODE_PRIVATE)
    var dismissed by remember { mutableStateOf(prefs.getBoolean(promptKey(year), false)) }
    if (goal == null) {
        if (dismissed) {
            Text(stringResource(R.string.goal_set_link, year), style = EyebrowTextStyle.copy(fontSize = 10.sp), color = Terra,
                modifier = Modifier.clickable(onClick = onEdit).padding(vertical = 6.dp))
        } else {
            Column(cardModifier(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.goal_title, year), style = EyebrowTextStyle, color = Terra)
                Text(stringResource(R.string.goal_prompt), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.goal_set).uppercase(), color = Color.White, fontFamily = Mono,
                        fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(terraFillColor())
                            .clickable(onClick = onEdit).padding(horizontal = 12.dp, vertical = 8.dp))
                    Text(stringResource(R.string.goal_not_now).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        modifier = Modifier.clickable {
                            prefs.edit().putBoolean(promptKey(year), true).apply(); dismissed = true
                        }.padding(vertical = 8.dp))
                }
            }
        }
        return
    }
    val g = goal.goal
    Column(cardModifier().clickable(onClick = onEdit), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.goal_title, year), style = EyebrowTextStyle, color = Terra, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.goal_edit), style = EyebrowTextStyle.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        g.grade?.let { target ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (g.discipline == "ROUTE") R.string.goal_grade_route else R.string.goal_grade_boulder),
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                GradePill(target)
                Text(if (goal.gradeReached) "✓" else goal.maxGradeThisYear?.let { stringResource(R.string.goal_now, it) } ?: "—",
                    color = if (goal.gradeReached) Ok else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        g.ascents?.let { ProgressLine(stringResource(R.string.goal_ascents), goal.ascentsDone, it) }
        g.days?.let { ProgressLine(stringResource(R.string.goal_days), goal.daysDone, it) }
    }
}

@Composable
private fun ProgressLine(label: String, done: Int, target: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("$done / $target", color = if (done >= target) Ok else MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        LinearProgressIndicator(
            progress = { (done.toFloat() / target).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (done >= target) Ok else terraFillColor(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun cardModifier() = Modifier.fillMaxWidth()
    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
    .padding(Spacing.md)

/** Hoja para poner o cambiar el objetivo: grado (bloque o vía), ascensiones y días. Todo opcional menos uno. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalSheet(year: Int, initial: YearGoal?, onSave: (String?, String?, Int?, Int?) -> Unit, onRemove: () -> Unit,
              onDismiss: () -> Unit) {
    var discipline by remember { mutableStateOf(initial?.discipline ?: "BOULDER") }
    var grade by remember { mutableStateOf(initial?.grade) }
    var ascents by remember { mutableStateOf(initial?.ascents?.toString() ?: "") }
    var days by remember { mutableStateOf(initial?.days?.toString() ?: "") }
    val ascentsN = ascents.toIntOrNull()?.takeIf { it in 1..10000 }
    val daysN = days.toIntOrNull()?.takeIf { it in 1..366 }
    val valid = grade != null || ascentsN != null || daysN != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.goal_title, year), style = EyebrowTextStyle, color = Terra)
            Text(stringResource(R.string.goal_sheet_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

            Text(stringResource(R.string.goal_grade_label), style = EyebrowTextStyle.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("BOULDER" to R.string.w_boulder_caps2, "ROUTE" to R.string.add_block_sheet_v3_via).forEach { (d, label) ->
                    val on = discipline == d
                    Text(stringResource(label), color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        modifier = Modifier.clip(CircleShape).background(if (on) terraFillColor() else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (on) terraFillColor() else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { discipline = d }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GOAL_GRADES.forEach { g ->
                    val on = grade == g
                    GradePill(g, large = true, modifier = Modifier.alpha(if (on || grade == null) 1f else 0.45f)
                        .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                        .clickable { grade = if (on) null else g })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(ascents, { ascents = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f),
                    label = { Text(stringResource(R.string.goal_ascents)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(days, { days = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f),
                    label = { Text(stringResource(R.string.goal_days)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)) {
                Text(stringResource(R.string.grade_vote_save), color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, modifier = Modifier.alpha(if (valid) 1f else 0.4f).clip(RoundedCornerShape(4.dp))
                        .background(terraFillColor()).clickable(enabled = valid) {
                            onSave(if (grade != null) discipline else null, grade, ascentsN, daysN)
                        }.padding(horizontal = 16.dp, vertical = 10.dp))
                if (initial != null) {
                    Text(stringResource(R.string.goal_remove).uppercase(), color = MaterialTheme.colorScheme.error,
                        fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        modifier = Modifier.clickable(onClick = onRemove).padding(vertical = 10.dp))
                }
            }
        }
    }
}

/** % de flash y a vista de lo filtrado, y el año elegido frente al anterior. */
@Composable
fun StyleAndYearSection(share: StyleShare?, comparison: YearComparison?) {
    if (share != null && share.total > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SmallMetric(stringResource(R.string.stats_flash_pct), "${share.flashPct}%", Modifier.weight(1f))
            SmallMetric(stringResource(R.string.stats_onsight_pct), "${share.onsightPct}%", Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.sm))
    }
    comparison?.let { c ->
        Column(cardModifier(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.stats_vs_year, c.year, (c.year.toInt() - 1).toString()), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompareLine(stringResource(R.string.goal_ascents), "${c.ascents}", "${c.previousAscents}", c.ascents - c.previousAscents)
            CompareLine(stringResource(R.string.goal_days), "${c.days}", "${c.previousDays}", c.days - c.previousDays)
            CompareLine(stringResource(R.string.stats_max_grade), c.maxGrade ?: "—", c.previousMaxGrade ?: "—", null)
        }
        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun CompareLine(label: String, now: String, before: String, delta: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(before, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontSize = 12.sp)
        Text("  →  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(now, color = MaterialTheme.colorScheme.onSurface, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        delta?.takeIf { it != 0 }?.let {
            Text(if (it > 0) "  +$it" else "  $it", color = if (it > 0) Ok else MaterialTheme.colorScheme.error,
                fontFamily = Mono, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SmallMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = EyebrowTextStyle.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** LOGROS: conseguidos en color (con la fecha) y los que faltan en gris. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AchievementsSection(items: List<Achievement>) {
    if (items.isEmpty()) return
    val got = items.count { it.unlocked }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.achievements_title), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("$got / ${items.size}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Terra)
    }
    Spacer(Modifier.height(Spacing.xs))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.sortedByDescending { it.unlocked }.forEach { a ->
            val text = achievementText(a)
            Column(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (a.unlocked) Terra.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (a.unlocked) Terra else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                    .alpha(if (a.unlocked) 1f else 0.55f)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text((if (a.unlocked) "★ " else "☆ ") + text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                a.unlockedOn?.takeIf { a.unlocked }?.let {
                    Text(it, fontFamily = Mono, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun achievementText(a: Achievement): String = when (a.kind) {
    AchievementKind.GRADE -> stringResource(
        if (a.discipline == "ROUTE") R.string.ach_grade_route else R.string.ach_grade_boulder, a.param)
    AchievementKind.VOLUME -> stringResource(R.string.ach_volume, a.param)
    AchievementKind.FLASHES -> stringResource(R.string.ach_flashes, a.param)
    AchievementKind.ONSIGHTS -> stringResource(R.string.ach_onsights, a.param)
    AchievementKind.FLASH_MAX -> stringResource(R.string.ach_flash_max)
    AchievementKind.ONSIGHT_MAX -> stringResource(R.string.ach_onsight_max)
    AchievementKind.SCHOOLS -> stringResource(R.string.ach_schools, a.param)
    AchievementKind.REGIONS -> stringResource(R.string.ach_regions, a.param)
    AchievementKind.ABROAD -> stringResource(R.string.ach_abroad)
    AchievementKind.WEEKENDS -> stringResource(R.string.ach_weekends, a.param)
    AchievementKind.DAYS_IN_YEAR -> stringResource(R.string.ach_days_year, a.param)
    AchievementKind.EVERY_MONTH -> stringResource(R.string.ach_every_month)
    AchievementKind.PROJECTS -> if (a.param == "1") stringResource(R.string.ach_project_one)
        else stringResource(R.string.ach_projects, a.param)
}
