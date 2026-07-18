@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
            androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.model.Submission
import androidx.compose.runtime.key
import com.meteomontana.android.ui.components.FullScreenMapDialog
import com.meteomontana.android.ui.components.TopoPhotoCanvas
import com.meteomontana.android.ui.components.parseBloquesJson
import com.meteomontana.android.ui.components.toTopoLines
import com.meteomontana.android.ui.components.pinBitmap
import com.meteomontana.android.ui.components.pinBitmapBoulder
import org.maplibre.android.annotations.IconFactory
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Moss
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import org.json.JSONArray
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style


@Composable
internal fun PropuestasTab(
    submissions: List<Submission>,
    contributions: List<Contribution>,
    schoolBlocks: Map<String, List<com.meteomontana.android.domain.model.Block>>,
    onFetchSchoolBlocks: (String) -> Unit,
    onDeleteBlock: (String, String) -> Unit,
    onUpdateBlock: (String, String, com.meteomontana.android.data.api.dto.CreateBlockRequest, (Boolean) -> Unit) -> Unit,
    onApproveSubmission: (String) -> Unit,
    onRejectSubmission: (String, String?) -> Unit,
    onApproveContribution: (String) -> Unit,
    onApproveContributionEdited: (String, String) -> Unit = { id, _ -> onApproveContribution(id) },
    onRejectContribution: (String, String?) -> Unit
) {
    var filter by remember { mutableStateOf(ContribFilter.TODAS) }

    val filtered = contributions.filter { c ->
        when (filter) {
            ContribFilter.TODAS    -> true
            ContribFilter.PIEDRAS  -> c.type == "BOULDER"
            ContribFilter.SECTORES -> c.type == "SECTOR"
            ContribFilter.PARKINGS -> c.type == "PARKING"
            ContribFilter.MOVER    -> c.type == "POSITION_CORRECTION"
        }
    }

    // Agrupar por escuela
    val bySchool = filtered.groupBy { it.schoolName }
    val total = filtered.size + submissions.size

    LazyColumn(
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Contador
        item {
            Text(
                "$total propuestas pendientes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.xs)
            )
        }

        // Chips de filtro
        item {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                ContribFilter.entries.forEach { f ->
                    val sel = f == filter
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (sel) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .clickable { filter = f }
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    ) {
                        Text(
                            f.label,
                            style = EyebrowTextStyle,
                            color = if (sel) MaterialTheme.colorScheme.background
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        // School submissions (propuestas de escuelas nuevas) si filtro = TODAS
        if (filter == ContribFilter.TODAS && submissions.isNotEmpty()) {
            item {
                SchoolGroupHeader("ESCUELAS NUEVAS", submissions.size)
            }
            items(submissions) { s ->
                SubmissionCard(s, onApproveSubmission, onRejectSubmission)
            }
            item { Spacer(Modifier.height(Spacing.sm)) }
        }

        // Contributions agrupadas por escuela
        bySchool.forEach { (schoolName, items) ->
            item {
                SchoolGroupHeader(schoolName, items.size)
            }
            items(items) { c ->
                ContributionCard(
                    c = c,
                    existingBlocks = schoolBlocks[c.schoolId] ?: emptyList(),
                    onFetchBlocks = { onFetchSchoolBlocks(c.schoolId) },
                    onDeleteBlock = { blockId -> onDeleteBlock(blockId, c.schoolId) },
                    onUpdateBlock = { b, req -> onUpdateBlock(b.id, c.schoolId, req) {} },
                    onApprove = onApproveContribution,
                    onApproveEdited = onApproveContributionEdited,
                    onReject = onRejectContribution
                )
            }
            item { Spacer(Modifier.height(Spacing.xs)) }
        }

        if (filtered.isEmpty() && (filter != ContribFilter.TODAS || submissions.isEmpty())) {
            item {
                Box(Modifier.fillMaxWidth().padding(Spacing.xxl), Alignment.Center) {
                    Text("No hay propuestas pendientes",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SchoolGroupHeader(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "- ${name.uppercase()}",
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        // Badge con cantidad
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(Terra)
                .padding(horizontal = Spacing.sm, vertical = 2.dp)
        ) {
            Text("$count", style = EyebrowTextStyle, color = Color.White)
        }
    }
}
