package com.meteomontana.android.ui.screens.schools

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonSchoolRow
import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.ui.theme.terraFillColor
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import com.meteomontana.android.ui.guide.guideTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.ui.components.SchoolListItem
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.TerraBg
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import kotlinx.coroutines.flow.first

// Resumen de filtros aplicados y selector de días de Escuelas.

/** "8 escuelas · Hasta 50 km · Bloque": lo aplicado, sin desplegar los filtros. */
@Composable
internal fun filtersSummary(
    filters: SchoolFilters,
    tab: ExploreTab,
    gradeMin: String?,
    gradeMax: String?,
    orientations: Set<String>,
    exploreSort: ExploreSortBy,
    schoolCount: Int?
): String {
    val parts = mutableListOf<String>()
    if (schoolCount != null) parts += stringResource(R.string.schools_count, schoolCount)
    parts += filters.maxDistanceKm?.let { stringResource(R.string.schools_summary_upto_km, it.toInt()) }
        ?: stringResource(R.string.schools_summary_any_distance)
    if (filters.style != StyleFilter.All) parts += stringResource(filters.style.labelRes)
    if (filters.rockTypes.isNotEmpty()) parts += filters.rockTypes.joinToString("/") { com.meteomontana.android.util.CatalogLabels.rock(it) }
    if (tab == ExploreTab.Schools) {
        if (filters.onlyFavorites) parts += stringResource(R.string.w_favourites)
        else if (filters.onlySavedOffline) parts += stringResource(R.string.w_saved)
    } else {
        if (gradeMin != null || gradeMax != null) parts += "${gradeMin ?: "…"}–${gradeMax ?: "…"}"
        if (orientations.isNotEmpty()) parts += orientations.sorted().joinToString("/")
        if (exploreSort != ExploreSortBy.Distance) parts += stringResource(exploreSort.labelRes)
    }
    return parts.joinToString(" · ")
}

/**
 * Selector de días: los próximos 7 días (hoy incluido) como chips "LUN 17".
 * Toca para elegir hasta 5; con ≥1 elegido la lista pasa a modo tramo y se
 * ordena por las mejores condiciones de esos días. Un chip extra "Hoy" (ninguno
 * elegido) representa el modo de siempre.
 */
@Composable
internal fun DaySelectorRow(
    selectedDays: Set<Int>,           // ISO 1-7
    onToggleDay: (Int) -> Unit
) {
    val today = remember { java.time.LocalDate.now() }
    val days = remember(today) { (0..6).map { today.plusDays(it.toLong()) } }
    val dayLetters = CalendarLabels.daysShortMonFirst().map { it.uppercase() }  // ISO 1=lunes

    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Text(
            text = if (selectedDays.isEmpty()) stringResource(R.string.school_list_screen_v3_dias_elige_hasta_5_para)
                   else stringResource(R.string.school_list_screen_v3_dias_elegido, selectedDays.size, if (selectedDays.size > 1) "s" else ""),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            days.forEach { d ->
                val iso = d.dayOfWeek.value
                val selected = iso in selectedDays
                Column(
                    // Radio intermedio, NO píldora: es una celda casi cuadrada
                    // (día + número apilados), y una píldora completa ahí se ve
                    // como un óvalo forzado — mismo motivo que las stat cards.
                    modifier = Modifier
                        .clip(com.meteomontana.android.ui.theme.CumbreStatCardShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            com.meteomontana.android.ui.theme.CumbreStatCardShape
                        )
                        .clickable { onToggleDay(iso) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayLetters[iso - 1],
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Sub-componentes del header                                                 */
/* ─────────────────────────────────────────────────────────────────────────── */

