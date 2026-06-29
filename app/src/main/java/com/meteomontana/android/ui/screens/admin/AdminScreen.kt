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
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
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

private enum class AdminTab(val label: String) {
    Propuestas("PROPUESTAS"),
    Gestionar("GESTIONAR"),
    Denuncias("DENUNCIAS"),
    Stats("STATS"),
    Activity("ACTIVIDAD"),
    Push("PUSH")
}

@Composable
fun AdminScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(AdminTab.Propuestas) }

    // Refresca al entrar (el VM sobrevive a la navegación; sin esto, las propuestas
    // nuevas no aparecen hasta matar la app).
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Admin",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Menú de tabs como en la PWA: lista vertical de selectores
        TabSelector(tab) { tab = it }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (state.loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }
        state.error?.let { err ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(err, color = MaterialTheme.colorScheme.error)
            }
            return@Column
        }

        when (tab) {
            AdminTab.Propuestas -> PropuestasTab(
                submissions = state.pending,
                contributions = state.contributions,
                schoolBlocks = state.schoolBlocks,
                onFetchSchoolBlocks = viewModel::fetchSchoolBlocks,
                onDeleteBlock = viewModel::deleteBlock,
                onUpdateBlock = viewModel::updateBlock,
                onApproveSubmission = viewModel::approve,
                onRejectSubmission = viewModel::reject,
                onApproveContribution = viewModel::approveContribution,
                onRejectContribution = viewModel::rejectContribution
            )
            AdminTab.Gestionar -> GestionarTab(
                allSchools = state.allSchools,
                loading = state.schoolsLoading,
                schoolBlocks = state.schoolBlocks,
                onLoadSchools = viewModel::loadAllSchools,
                onFetchSchoolBlocks = viewModel::fetchSchoolBlocks,
                onDeleteBlock = viewModel::deleteBlock,
                onUpdateBlock = viewModel::updateBlock
            )
            AdminTab.Denuncias -> DenunciasTab(
                reports = state.reports,
                onResolve = { id -> viewModel.resolveReport(id, "resolve") },
                onDismiss = { id -> viewModel.resolveReport(id, "dismiss") }
            )
            AdminTab.Stats -> StatsTab(state.stats)
            AdminTab.Activity -> ActivityTab(state.logs)
            AdminTab.Push -> PushTab(
                busy = state.pushBusy,
                result = state.pushResult,
                onSend = viewModel::sendPush
            )
        }
    }
}

@Composable
private fun TabSelector(current: AdminTab, onChange: (AdminTab) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        AdminTab.entries.forEach { tab ->
            val selected = tab == current
            val bg = if (selected) MaterialTheme.colorScheme.primary
                     else MaterialTheme.colorScheme.surface
            val fg = if (selected) Color.White
                     else MaterialTheme.colorScheme.onSurface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg, RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable { onChange(tab) }
                    .padding(12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(tab.label, color = fg, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

// ─────────────────────────── PROPUESTAS ────────────────────────────

internal enum class ContribFilter(val label: String) {
    TODAS("TODAS"), PIEDRAS("PIEDRAS"), SECTORES("SECTORES"),
    PARKINGS("PARKINGS"), MOVER("MOVER ESCUELA")
}

