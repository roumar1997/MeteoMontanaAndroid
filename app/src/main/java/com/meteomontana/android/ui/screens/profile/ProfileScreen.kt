package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.data.api.dto.PrivateProfileDto
import com.meteomontana.android.data.api.dto.JournalStatsDto
import com.meteomontana.android.data.api.dto.SchoolStatsDto

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onSubmissions: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var addBlockOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("Mi Diario", onBack)
        when (val s = state) {
            ProfileUiState.Loading -> CenterBox { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            is ProfileUiState.Error -> CenterBox { Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error) }
            is ProfileUiState.Success -> Content(
                profile = s.profile,
                stats = s.stats,
                onAddBlock = { addBlockOpen = true },
                onEdit = onEdit,
                onSubmissions = onSubmissions,
                onSignOut = viewModel::signOut
            )
        }
    }

    if (addBlockOpen) {
        AddBlockSheet(
            onDismiss = { addBlockOpen = false },
            onSave = { req -> viewModel.addBlock(req) { addBlockOpen = false } }
        )
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onBackground)
        }
        Column {
            Text("PERFIL", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
}

@Composable
private fun Content(
    profile: PrivateProfileDto,
    stats: JournalStatsDto,
    onAddBlock: () -> Unit,
    onEdit: () -> Unit,
    onSubmissions: () -> Unit,
    onSignOut: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Header(profile) }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniButton("Editar perfil", onClick = onEdit, modifier = Modifier.weight(1f))
                MiniButton("Mis propuestas", onClick = onSubmissions, modifier = Modifier.weight(1f))
            }
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
        item { TogglesSection(profile) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
        item { StatsRow(stats) }
        item {
            AddBlockButton(onClick = onAddBlock)
        }
        if (stats.bySchool.isNotEmpty()) {
            items(stats.bySchool) { entry ->
                SchoolEntryRow(entry)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        } else {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Aún no has añadido bloques",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp)) {
                Text("CERRAR SESIÓN", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Header(p: PrivateProfileDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (p.photoUrl != null) {
            AsyncImage(
                model = p.photoUrl,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        } else {
            Image(
                painter = painterResource(R.drawable.logo_cumbre),
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
        Spacer(Modifier.padding(start = 16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "@${p.username ?: p.displayName?.lowercase()?.replace(" ", "_") ?: "tu_username"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                p.bio ?: "Sin biografía",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TogglesSection(p: PrivateProfileDto) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        ToggleRow("Perfil público", p.isPublic)
        Spacer(Modifier.height(8.dp))
        ToggleRow("Email", false, secondaryText = p.email ?: "—")
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, secondaryText: String? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Text(
            secondaryText ?: if (value) "Sí" else "No",
            style = MaterialTheme.typography.labelLarge,
            color = if (value) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatsRow(stats: JournalStatsDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCell("BLOQUES", stats.blockCount.toString(), Modifier.weight(1f))
        StatCell("ESCUELAS", stats.schoolCount.toString(), Modifier.weight(1f))
        StatCell("MÁXIMO", stats.maxGrade ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddBlockButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(48.dp)
            .background(Color(0xFF1C1C1A), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("+ AÑADIR BLOQUE", color = Color.White,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SchoolEntryRow(entry: SchoolStatsDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(entry.schoolName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text(
                "${entry.blockCount} ${if (entry.blockCount == 1) "bloque" else "bloques"}" +
                        (entry.maxGrade?.let { " · máx $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("›", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
