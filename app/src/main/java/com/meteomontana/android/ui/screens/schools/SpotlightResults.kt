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

/** Resultados del buscador de arriba (≥2 letras): ESCUELAS y VÍAS Y BLOQUES, cada uno con su cabecera aunque esté vacío. */
@Composable
internal fun SpotlightResults(
    schoolMatches: List<com.meteomontana.android.domain.model.School>,
    viaHits: List<com.meteomontana.android.domain.model.LineSearchHit>,
    openSchool: (String) -> Unit,
    openVia: (String, String?, String?) -> Unit
) {
    Column(Modifier.padding(horizontal = Spacing.lg)) {
        Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface,
                    MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.small)
        ) {
            Text(stringResource(R.string.school_list_screen_v2_escuelas),
                style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            if (schoolMatches.isEmpty()) {
                Text(stringResource(R.string.school_list_screen_v2_sin_resultados_2),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp))
            } else {
                schoolMatches.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { openSchool(s.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1)
                            s.region?.takeIf { it.isNotBlank() }?.let {
                                Text(it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1)
                            }
                        }
                        Text("▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(stringResource(R.string.school_list_screen_vias_y_bloques),
                style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            if (viaHits.isEmpty()) {
                Text(stringResource(R.string.school_list_screen_v2_sin_resultados),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp))
            }
            viaHits.forEach { h ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { openVia(h.schoolId, h.lineId, h.lineName ?: h.blockName) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    h.lineName ?: h.blockName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                com.meteomontana.android.ui.screens.detail.GradePill(
                                    h.grade, small = true, lineId = h.lineId)
                            }
                            Text(
                                listOf(h.blockName.takeIf { h.lineName != null },
                                       h.sectorName, h.schoolName)
                                    .filterNotNull().filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1)
                        }
                        Text("▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Mini-topo: la foto de la cara con la línea dibujada
                    // (solo si el backend mandó foto; la piedra sale sin trazo).
                    h.photoPath?.takeIf { it.isNotBlank() }?.let { photo ->
                        // P8: dedup de puntos consecutivos casi identicos — en
                        // trazos a mano antiguos los duplicados fusionaban los
                        // guiones y la linea salia CONTINUA solo aqui.
                        val rawStroke = com.meteomontana.android.ui.screens.topo
                            .parseLineStroke(h.linePath)
                        val stroke = rawStroke.copy(points = rawStroke.points
                            .filterIndexed { i, pt ->
                                i == 0 || kotlin.math.abs(pt.x - rawStroke.points[i - 1].x) +
                                    kotlin.math.abs(pt.y - rawStroke.points[i - 1].y) > 0.004f
                            })
                        val topoLines = if (stroke.points.size >= 2) listOf(
                            com.meteomontana.android.ui.components.TopoLine(
                                name = h.lineName, grade = h.grade,
                                startType = h.startType, points = stroke.points)
                        ) else emptyList()
                        com.meteomontana.android.ui.components.TopoPhotoCanvas(
                            photoUrl = photo,
                            lines = topoLines,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
