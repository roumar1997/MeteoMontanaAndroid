package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.GymSummary
import com.meteomontana.android.ui.screens.gyms.parseHex
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.gradeChipStyle
import com.meteomontana.android.ui.theme.terraFillColor
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

// Bloques de las pantallas de estadísticas de rocódromo: «Por rocódromo», «Por grado» y «Por mes» (maqueta 4C).

@Composable
internal fun SectionTitle(text: String) =
    Text(text.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))

@Composable
internal fun CardBox(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

/** Una barra con relleno proporcional (de 0 a 1) sobre una pista gris. */
@Composable
internal fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.03f, 1f)).clip(CircleShape).background(color))
    }
}

/** Rocódromos de la persona: inicial, nombre, barra por ascensiones, cuántas y su grado máximo con color. */
@Composable
internal fun ByGymSection(summary: GymSummary, onOpenGym: ((Long) -> Unit)? = null) {
    if (summary.byGym.isEmpty()) return
    SectionTitle(stringResource(R.string.gym_by_gym))
    val top = summary.byGym.maxOf { it.ascents.coerceAtLeast(1) }
    CardBox {
        summary.byGym.take(8).forEach { g ->
            Row(
                Modifier.fillMaxWidth().let { m -> onOpenGym?.let { open -> m.clickable { open(g.gymId) } } ?: m },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(terraFillColor()), contentAlignment = Alignment.Center) {
                    Text((g.name ?: "?").take(1).uppercase(), color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(g.name ?: "—", fontSize = 13.sp, maxLines = 1, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(0.9f))
                Bar(g.ascents.toFloat() / top, Terra, Modifier.weight(1f))
                Text(g.ascents.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                MaxGradePill(g.maxGrade)
            }
        }
    }
}


/** Ascensiones por grado (paleta de grados de la app) y, aparte, por color de escala. */
@Composable
internal fun ByGradeSection(items: List<com.meteomontana.android.domain.model.gym.GymAscent>, colorHex: (String) -> String?, titled: Boolean = true) {
    val grades = com.meteomontana.android.domain.model.gym.GymBreakdown.byGrade(items)
    val colors = com.meteomontana.android.domain.model.gym.GymBreakdown.byColor(items)
    if (grades.isEmpty() && colors.isEmpty()) return
    if (titled) SectionTitle(stringResource(R.string.gym_by_grade))
    val top = (grades.map { it.second } + colors.map { it.count }).maxOrNull()?.coerceAtLeast(1) ?: 1
    CardBox {
        grades.forEach { (g, n) ->
            val st = gradeChipStyle(g)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    g.uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, color = if (st.dark) Color(0xFF1B1B1B) else Color.White,
                    modifier = Modifier.weight(0.26f).clip(RoundedCornerShape(9.dp)).background(st.stroke).padding(horizontal = 8.dp, vertical = 3.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Bar(n.toFloat() / top, st.stroke, Modifier.weight(1f))
                Text(n.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(0.2f))
            }
        }
        colors.forEach { a ->
            val c = colorHex(a.key)?.let { parseHex(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    a.key.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
                    color = if (c.luminance() > 0.6f) Color(0xFF1B1B1B) else Color.White,
                    modifier = Modifier.weight(0.26f).clip(RoundedCornerShape(9.dp)).background(c).padding(horizontal = 8.dp, vertical = 3.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Bar(a.count.toFloat() / top, c, Modifier.weight(1f))
                Text(a.count.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(0.2f))
            }
        }
    }
}

/** Barras de los últimos meses (las ascensiones de cada mes), con el nombre corto del mes debajo. */
@Composable
internal fun ByMonthSection(summary: GymSummary) {
    val months = summary.byMonth
    if (months.none { it.ascents > 0 }) return
    SectionTitle(stringResource(R.string.gym_by_month))
    val top = months.maxOf { it.ascents }.coerceAtLeast(1)
    CardBox {
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            months.forEach { m ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((m.ascents.toFloat() / top).coerceAtLeast(0.03f)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (m.ascents > 0) terraFillColor() else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            months.forEach { m ->
                val label = runCatching { Month.of(m.month.takeLast(2).toInt()).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }.getOrDefault("")
                Text(label.take(3).uppercase(), fontSize = 8.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}
