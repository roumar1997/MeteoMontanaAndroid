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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.meteomontana.android.data.api.dto.AdminLogDto
import com.meteomontana.android.data.api.dto.AdminStatsDto
import com.meteomontana.android.data.api.dto.SubmissionDto

private enum class AdminTab(val label: String) {
    Propuestas("PROPUESTAS"),
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

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null,
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
                items = state.pending,
                onApprove = viewModel::approve,
                onReject = viewModel::reject
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
@Composable
private fun PropuestasTab(
    items: List<SubmissionDto>,
    onApprove: (String) -> Unit,
    onReject: (String, String?) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No hay propuestas pendientes",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp),
               verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { s ->
            SubmissionCard(s, onApprove, onReject)
        }
    }
}

@Composable
private fun SubmissionCard(
    s: SubmissionDto,
    onApprove: (String) -> Unit,
    onReject: (String, String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("ESCUELA", color = Color.White,
                    style = MaterialTheme.typography.labelMedium)
            }
            Text(s.proposedName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                s.proposedRockType?.let { append(it.uppercase()) }
                s.proposedRegion?.let {
                    if (this.isNotEmpty()) append(" · ")
                    append(it)
                }
                if (this.isNotEmpty()) append(" · ")
                append("${"%.4f".format(s.proposedLat)}, ${"%.4f".format(s.proposedLon)}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        s.notes?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                .clickable { onReject(s.id, null) }
                .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("RECHAZAR", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge)
            }
            Box(modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF3F6B4A), RoundedCornerShape(2.dp))
                .clickable { onApprove(s.id) }
                .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("APROBAR", color = Color.White,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ─────────────────────────── STATS ────────────────────────────
@Composable
private fun StatsTab(stats: AdminStatsDto?) {
    if (stats == null) return
    Column(modifier = Modifier.fillMaxSize().padding(16.dp),
           verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("USUARIOS", stats.totalUsers, Modifier.weight(1f))
            StatCard("ADMINS", stats.totalAdmins, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("ESCUELAS", stats.totalSchools, Modifier.weight(1f))
            StatCard("NOTAS", stats.totalNotes, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("PENDING", stats.submissionsPending, Modifier.weight(1f))
            StatCard("APROBADAS", stats.submissionsApproved, Modifier.weight(1f))
            StatCard("RECHAZADAS", stats.submissionsRejected, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(),
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─────────────────────────── ACTIVITY ────────────────────────────
@Composable
private fun ActivityTab(logs: List<AdminLogDto>) {
    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Sin actividad",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn {
        items(logs) { log ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(log.action,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("${log.targetType}/${log.targetId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                log.details?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Text(log.createdAt.take(16),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ─────────────────────────── PUSH ────────────────────────────
@Composable
private fun PushTab(
    busy: Boolean,
    result: String?,
    onSend: (String?, String, String) -> Unit
) {
    var target by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp),
           verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Enviar push", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Text("Si dejas Target UID vacío, se manda a TODOS los usuarios con token.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(value = target, onValueChange = { target = it },
            placeholder = { Text("Target UID (opcional)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = title, onValueChange = { title = it },
            placeholder = { Text("Título") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = body, onValueChange = { body = it },
            placeholder = { Text("Mensaje") },
            modifier = Modifier.fillMaxWidth().height(120.dp))

        Button(
            onClick = { onSend(target.takeIf { it.isNotBlank() }, title, body) },
            enabled = !busy && title.isNotBlank() && body.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1C1C1A), contentColor = Color.White
            ),
            shape = MaterialTheme.shapes.small
        ) {
            Text(if (busy) "Enviando..." else "ENVIAR PUSH")
        }
        result?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
