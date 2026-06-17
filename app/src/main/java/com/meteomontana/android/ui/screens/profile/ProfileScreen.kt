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
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.model.SchoolStats

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onSubmissions: () -> Unit = {},
    onAdmin: () -> Unit = {},
    onSavedSchools: () -> Unit = {},
    onWeekendAlert: () -> Unit = {},
    onOpenFollowers: () -> Unit = {},
    onOpenFollowing: () -> Unit = {},
    onOpenFollowRequests: () -> Unit = {},
    onOpenSchoolEntries: (String) -> Unit = {},
    onOpenAllBlocks: () -> Unit = {},
    onOpenAllSchools: () -> Unit = {},
    onOpenMaxGrade: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var addBlockOpen by remember { mutableStateOf(false) }

    // Recarga el perfil cada vez que la pantalla vuelve a primer plano
    // (p.ej. al cerrar EditarPerfil con foto nueva).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("Mi Diario", onBack)
        when (val s = state) {
            ProfileUiState.Loading -> CenterBox { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            is ProfileUiState.Error -> CenterBox { Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error) }
            is ProfileUiState.Success -> Content(
                profile = s.profile,
                stats = s.stats,
                followers = s.followers,
                following = s.following,
                offline = s.offline,
                onAddBlock = { addBlockOpen = true },
                onEdit = onEdit,
                onSubmissions = onSubmissions,
                onAdmin = onAdmin,
                onSavedSchools = onSavedSchools,
                onWeekendAlert = onWeekendAlert,
                onOpenFollowers = onOpenFollowers,
                onOpenFollowing = onOpenFollowing,
                onOpenFollowRequests = onOpenFollowRequests,
                onOpenSchoolEntries = onOpenSchoolEntries,
                onOpenAllBlocks = onOpenAllBlocks,
                onOpenAllSchools = onOpenAllSchools,
                onOpenMaxGrade = onOpenMaxGrade,
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
    profile: PrivateProfile,
    stats: JournalStats,
    followers: Long,
    following: Long,
    offline: Boolean = false,
    onAddBlock: () -> Unit,
    onEdit: () -> Unit,
    onSubmissions: () -> Unit,
    onAdmin: () -> Unit,
    onSavedSchools: () -> Unit,
    onWeekendAlert: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenFollowRequests: () -> Unit,
    onOpenSchoolEntries: (String) -> Unit,
    onOpenAllBlocks: () -> Unit,
    onOpenAllSchools: () -> Unit,
    onOpenMaxGrade: () -> Unit,
    onSignOut: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (offline) {
            item {
                Text(
                    "SIN CONEXIÓN · datos guardados",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        item { Header(profile, followers = followers, following = following,
            onClickFollowers = onOpenFollowers, onClickFollowing = onOpenFollowing) }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniButton("Editar perfil", onClick = onEdit, modifier = Modifier.weight(1f))
                MiniButton("Mis propuestas", onClick = onSubmissions, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                MiniButton("Escuelas guardadas (offline)", onClick = onSavedSchools, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                MiniButton("⛰ Alerta de tiempo", onClick = onWeekendAlert, modifier = Modifier.weight(1f))
            }
            if (!profile.isPublic) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    MiniButton("Solicitudes de seguimiento", onClick = onOpenFollowRequests, modifier = Modifier.weight(1f))
                }
            }
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
        item { TogglesSection(profile) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
        item { StatsRow(stats, onOpenAllBlocks, onOpenAllSchools, onOpenMaxGrade) }
        item {
            AddBlockButton(onClick = onAddBlock)
        }
        if (stats.bySchool.isNotEmpty()) {
            items(stats.bySchool) { entry ->
                SchoolEntryRow(entry, onClick = { onOpenSchoolEntries(entry.schoolName) })
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
        if (profile.isAdmin) {
            item {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(onClick = onAdmin)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("PANEL ADMIN", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge)
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
private fun Header(
    p: PrivateProfile,
    followers: Long = 0,
    following: Long = 0,
    onClickFollowers: () -> Unit = {},
    onClickFollowing: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (p.photoUrl != null) {
            androidx.compose.runtime.key(p.photoUrl) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(p.photoUrl)
                        .memoryCachePolicy(coil.request.CachePolicy.WRITE_ONLY)
                        .crossfade(200)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
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
            Spacer(Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.clickable(onClick = onClickFollowers),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("$followers ",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("seguidores",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(modifier = Modifier.clickable(onClick = onClickFollowing),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("$following ",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("siguiendo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TogglesSection(p: PrivateProfile) {
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
private fun StatsRow(
    stats: JournalStats,
    onBlocks: () -> Unit,
    onSchools: () -> Unit,
    onMax: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCell("BLOQUES", stats.blockCount.toString(), Modifier.weight(1f).clickable(onClick = onBlocks))
        StatCell("ESCUELAS", stats.schoolCount.toString(), Modifier.weight(1f).clickable(onClick = onSchools))
        StatCell("MÁXIMO", stats.maxGrade ?: "—", Modifier.weight(1f).clickable(onClick = onMax))
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
private fun SchoolEntryRow(entry: SchoolStats, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
