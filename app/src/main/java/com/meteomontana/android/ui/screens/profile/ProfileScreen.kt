package com.meteomontana.android.ui.screens.profile

import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
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
    // false cuando Perfil vive como pestaña (no hay nada que cerrar).
    showClose: Boolean = true,
    onEdit: () -> Unit = {},
    onSubmissions: () -> Unit = {},
    onAdmin: () -> Unit = {},
    onSavedSchools: () -> Unit = {},
    onWeekendAlert: () -> Unit = {},
    onOpenFollowers: () -> Unit = {},
    onOpenFollowing: () -> Unit = {},
    onOpenFollowRequests: () -> Unit = {},
    onOpenCommunity: () -> Unit = {},
    onOpenSchoolEntries: (String) -> Unit = {},
    onOpenBoulders: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onOpenAllSchools: () -> Unit = {},
    onOpenMaxGrade: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
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

    // Se presenta como bottom-sheet (paridad iOS) → altura acotada para que el
    // LazyColumn interior tenga restricción vertical (si no, crashea).
    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        SheetHeader(stringResource(R.string.profile_title), if (showClose) onBack else null)
        when (val s = state) {
            ProfileUiState.Loading -> CenterBox { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            is ProfileUiState.Error -> CenterBox { Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error) }
            is ProfileUiState.Success -> Content(
                profile = s.profile,
                stats = s.stats,
                followers = s.followers,
                following = s.following,
                offline = s.offline,
                pendingReview = s.pendingReview,
                onAddBlock = { addBlockOpen = true },
                onEdit = onEdit,
                onSubmissions = onSubmissions,
                onAdmin = onAdmin,
                onSavedSchools = onSavedSchools,
                onWeekendAlert = onWeekendAlert,
                onOpenFollowers = onOpenFollowers,
                onOpenFollowing = onOpenFollowing,
                onOpenFollowRequests = onOpenFollowRequests,
                onOpenCommunity = onOpenCommunity,
                onOpenSchoolEntries = onOpenSchoolEntries,
                onOpenBoulders = onOpenBoulders,
                onOpenRoutes = onOpenRoutes,
                onOpenAllSchools = onOpenAllSchools,
                onOpenMaxGrade = onOpenMaxGrade,
                onOpenProjects = onOpenProjects,
                onSignOut = viewModel::signOut,
                onDeleteAccount = { viewModel.deleteAccount() }
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
private fun SheetHeader(title: String, onClose: (() -> Unit)?) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center))
        com.meteomontana.android.ui.components.HelpButton(
            topicKey = "profile",
            modifier = Modifier.align(Alignment.CenterStart)
        )
        if (onClose != null) TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
            Text(stringResource(R.string.common_close), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge)
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
    pendingReview: Int = 0,
    onAddBlock: () -> Unit,
    onEdit: () -> Unit,
    onSubmissions: () -> Unit,
    onAdmin: () -> Unit,
    onSavedSchools: () -> Unit,
    onWeekendAlert: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenFollowRequests: () -> Unit,
    onOpenCommunity: () -> Unit = {},
    onOpenSchoolEntries: (String) -> Unit,
    onOpenBoulders: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenAllSchools: () -> Unit,
    onOpenMaxGrade: () -> Unit,
    onOpenProjects: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (offline) {
            item {
                Text(
                    stringResource(R.string.common_offline_data),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        // Cabecera centrada (avatar grande, nombre, @usuario, email, píldoras
        // TOPE/ADMIN y seguidores) — paridad con AccountView de iOS.
        item { Header(profile, topGrade = stats.maxGrade, followers = followers, following = following,
            onClickFollowers = onOpenFollowers, onClickFollowing = onOpenFollowing) }
        item { StatsRow(stats, onOpenBoulders, onOpenRoutes, onOpenAllSchools, onOpenMaxGrade, onOpenProjects) }
        item { AddBlockButton(onClick = onAddBlock) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
        // Menú con iconos terracota (filas tipo iOS).
        item {
            ProfileMenu(
                onEdit = onEdit,
                onSavedSchools = onSavedSchools,
                onWeekendAlert = onWeekendAlert,
                onSubmissions = onSubmissions,
                showRequests = !profile.isPublic,
                onOpenFollowRequests = onOpenFollowRequests,
                onOpenCommunity = onOpenCommunity,
                shareHandle = profile.username ?: profile.uid,
                shareLabel = profile.username?.let { "@$it" } ?: (profile.displayName ?: "mi perfil"),
                shareDisplayName = profile.displayName ?: profile.username ?: "Escalador/a",
                shareUsername = profile.username,
                sharePhotoUrl = profile.photoUrl,
                shareTopGrade = profile.topGrade,
                shareBio = profile.bio,
                shareStats = stats
            )
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.profile_admin_panel).uppercase(), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge)
                        // Aviso: nº de propuestas/contribuciones pendientes de revisar.
                        if (pendingReview > 0) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "$pendingReview PENDIENTE${if (pendingReview == 1) "" else "S"}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Text(stringResource(R.string.profile_logout).uppercase(), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
        item {
            var showDelete by remember { mutableStateOf(false) }
            TextButton(onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(stringResource(R.string.profile_delete_account),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (showDelete) {
                AlertDialog(
                    onDismissRequest = { showDelete = false },
                    title = { Text("¿Eliminar tu cuenta?") },
                    text = { Text("Se borrarán tu perfil, diario, favoritas, seguimientos y propuestas de forma permanente. Esta acción no se puede deshacer.") },
                    confirmButton = {
                        TextButton(onClick = { showDelete = false; onDeleteAccount() }) {
                            Text("ELIMINAR", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.common_cancel)) }
                    }
                )
            }
        }
    }
}

@Composable
private fun Header(
    p: PrivateProfile,
    topGrade: String? = null,
    followers: Long = 0,
    following: Long = 0,
    onClickFollowers: () -> Unit = {},
    onClickFollowing: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar centrado y grande.
        if (p.photoUrl != null) {
            androidx.compose.runtime.key(p.photoUrl) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(p.photoUrl)
                        .memoryCachePolicy(coil.request.CachePolicy.WRITE_ONLY)
                        .crossfade(200)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        } else {
            Image(
                painter = painterResource(R.drawable.logo_cumbre),
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
        Spacer(Modifier.height(12.dp))
        // Nombre grande serif.
        Text(
            p.displayName ?: p.username ?: "Tú",
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "@${p.username ?: p.displayName?.lowercase()?.replace(" ", "_") ?: "tu_username"}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        p.email?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Píldoras TOPE / ADMIN.
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            topGrade?.let { ProfilePill("TOPE $it", MaterialTheme.colorScheme.primary) }
            if (p.isAdmin) ProfilePill("ADMIN", MaterialTheme.colorScheme.onBackground)
            if (p.isPremium) ProfilePill("PREMIUM", MaterialTheme.colorScheme.primary)
        }

        // Seguidores / siguiendo centrados.
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            FollowCount(followers, stringResource(R.string.profile_followers).uppercase(), onClickFollowers)
            FollowCount(following, stringResource(R.string.profile_following).uppercase(), onClickFollowing)
        }
    }
}

@Composable
private fun ProfilePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, color, RoundedCornerShape(2.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun FollowCount(value: Long, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$value", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Menú de filas con icono terracota + chevron (estilo iOS). */
@Composable
private fun ProfileMenu(
    onEdit: () -> Unit,
    onSavedSchools: () -> Unit,
    onWeekendAlert: () -> Unit,
    onSubmissions: () -> Unit,
    showRequests: Boolean,
    onOpenFollowRequests: () -> Unit,
    onOpenCommunity: () -> Unit = {},
    shareHandle: String? = null,
    shareLabel: String = "",
    shareDisplayName: String = "",
    shareUsername: String? = null,
    sharePhotoUrl: String? = null,
    shareTopGrade: String? = null,
    shareBio: String? = null,
    shareStats: com.meteomontana.android.domain.model.JournalStats? = null
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        if (shareHandle != null) {
            MenuRow(Icons.Outlined.Share, "Compartir mi perfil") {
                // Imagen 1080×1920 (formato historia) → Instagram Stories, WhatsApp...
                scope.launch {
                    com.meteomontana.android.ui.share.shareProfileAsImage(
                        ctx, shareHandle, shareLabel,
                        username = shareUsername, photoUrl = sharePhotoUrl,
                        topGrade = shareTopGrade, bio = shareBio,
                        boulders = shareStats?.boulderCount,
                        routes = shareStats?.routeCount,
                        schools = shareStats?.schoolCount
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        MenuRow(Icons.Outlined.EmojiEvents, "Comunidad", onOpenCommunity)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MenuRow(Icons.Outlined.Edit, stringResource(R.string.profile_edit), onEdit)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MenuRow(Icons.Outlined.Download, stringResource(R.string.profile_saved_schools), onSavedSchools)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MenuRow(Icons.Outlined.Notifications, stringResource(R.string.profile_weather_alert), onWeekendAlert)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MenuRow(Icons.Outlined.Place, stringResource(R.string.profile_my_proposals), onSubmissions)
        if (showRequests) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            MenuRow(Icons.Outlined.PersonAdd, stringResource(R.string.profile_follow_requests), onOpenFollowRequests)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MenuRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.profile_show_hints)) {
            com.meteomontana.android.ui.components.resetAllHints(ctx)
            android.widget.Toast.makeText(ctx, "Pistas reactivadas — entra en cada pantalla para verlas", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatsRow(
    stats: JournalStats,
    onBoulders: () -> Unit,
    onRoutes: () -> Unit,
    onSchools: () -> Unit,
    onMax: () -> Unit,
    onProjects: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Fila 1: contadores BLOQUES / VÍAS / ESCUELAS.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(stringResource(R.string.profile_blocks), stats.boulderCount.toString(), Modifier.weight(1f).clickable(onClick = onBoulders))
            StatCell(stringResource(R.string.profile_routes), stats.routeCount.toString(), Modifier.weight(1f).clickable(onClick = onRoutes))
            StatCell(stringResource(R.string.profile_schools), stats.schoolCount.toString(), Modifier.weight(1f).clickable(onClick = onSchools))
        }
        // Fila 2: grado máximo separado por modalidad (escalas distintas).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(stringResource(R.string.profile_max_boulder), stats.maxBoulderGrade ?: "—", Modifier.weight(1f).clickable(onClick = onMax))
            StatCell(stringResource(R.string.profile_max_route), stats.maxRouteGrade ?: "—", Modifier.weight(1f).clickable(onClick = onMax))
        }
        // Fila 3: PROYECTOS — misma celda pulsable que el resto (mismo caché
        // offline: viene en la misma llamada de stats).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(stringResource(R.string.profile_projects), stats.projectCount.toString(), Modifier.weight(1f).clickable(onClick = onProjects))
        }
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
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.profile_add_block), color = Color.White,
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
private fun CenterBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
