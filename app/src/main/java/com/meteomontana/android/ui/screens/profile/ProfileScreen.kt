@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.profile

import android.content.Context
import kotlinx.coroutines.CoroutineScope
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.People
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    onOpenSchoolEntries: (String) -> Unit = {},
    onOpenBoulders: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onOpenAllSchools: () -> Unit = {},
    onOpenMaxGrade: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    // Fila "Mis publicaciones" → pantalla dedicada con el feed propio (scope=mine).
    onOpenMyPosts: () -> Unit = {},
    /** En la pestaña keep-alive: true cuando la pestaña Perfil está visible.
     *  Recarga al hacerse visible (los ON_RESUME no saltan al cambiar de tab). */
    visible: Boolean = true,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var addBlockOpen by remember { mutableStateOf(false) }
    // Saveable: si navegas a un sub-ajuste y vuelves atrás, la hoja sigue abierta.
    var settingsOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible) viewModel.load()
    }

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

    val successState = state as? ProfileUiState.Success

    // Ajustes NO es un bottom-sheet (flotaba encima de la navegación: al tocar una
    // opción, la pantalla nueva cargaba DETRÁS y no se veía). Es una PANTALLA de
    // verdad dentro del perfil: pantalla completa, y al volver de un sub-ajuste
    // (que navega encima) `settingsOpen` sigue true (rememberSaveable) → vuelves
    // a Ajustes, no al perfil. El back del sistema cierra Ajustes.
    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    if (settingsOpen && successState != null) {
        ProfileSettingsScreen(
            onBack = { settingsOpen = false },
            isPrivate = !successState.profile.isPublic,
            onEdit = onEdit,
            onSavedSchools = onSavedSchools,
            onWeekendAlert = onWeekendAlert,
            onSubmissions = onSubmissions,
            onOpenFollowRequests = onOpenFollowRequests,
            onSignOut = { settingsOpen = false; viewModel.signOut() },
            onDeleteAccount = { settingsOpen = false; viewModel.deleteAccount() }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)) {
            // Cabecera: ayuda (izq) · título · compartir + ajustes (der). Compartir
            // y ajustes solo cuando el perfil ha cargado (necesitan sus datos).
            ProfileTopBar(
                title = stringResource(R.string.profile_title),
                showClose = showClose,
                onBack = onBack,
                onShare = successState?.let { s -> { shareProfile(ctx, scope, s.profile, s.stats) } },
                onSettings = successState?.let { { settingsOpen = true } }
            )
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
                    onAdmin = onAdmin,
                    onOpenFollowers = onOpenFollowers,
                    onOpenFollowing = onOpenFollowing,
                    onOpenBoulders = onOpenBoulders,
                    onOpenRoutes = onOpenRoutes,
                    onOpenAllSchools = onOpenAllSchools,
                    onOpenMaxGrade = onOpenMaxGrade,
                    onOpenProjects = onOpenProjects,
                    onOpenMyPosts = onOpenMyPosts
                )
            }
        }
    }

    if (addBlockOpen) {
        AddBlockSheet(
            onDismiss = { addBlockOpen = false },
            onSave = { req -> viewModel.addBlock(req) { addBlockOpen = false } }
        )
    }
}

/** Dispara la imagen 1080×1920 (formato historia) de "compartir perfil". */
private fun shareProfile(ctx: Context, scope: CoroutineScope, p: PrivateProfile, stats: JournalStats) {
    val handle = p.username ?: p.uid
    scope.launch {
        com.meteomontana.android.ui.share.shareProfileAsImage(
            ctx, handle,
            p.username?.let { "@$it" } ?: (p.displayName ?: "mi perfil"),
            username = p.username, photoUrl = p.photoUrl,
            topGrade = stats.maxGrade, bio = p.bio,
            boulders = stats.boulderCount, routes = stats.routeCount, schools = stats.schoolCount
        )
    }
}

@Composable
private fun ProfileTopBar(
    title: String,
    showClose: Boolean,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    onSettings: (() -> Unit)?
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center))
        com.meteomontana.android.ui.components.HelpButton(
            topicKey = "profile",
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onShare != null) IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = "Compartir perfil",
                    tint = MaterialTheme.colorScheme.primary)
            }
            if (onSettings != null) IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.profile_settings),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            if (showClose) TextButton(onClick = onBack) {
                Text(stringResource(R.string.common_close), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
            }
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
    onAdmin: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenBoulders: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenAllSchools: () -> Unit,
    onOpenMaxGrade: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenMyPosts: () -> Unit = {}
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
        // Cabecera centrada (avatar grande, nombre, @usuario, píldoras ADMIN/PREMIUM
        // y seguidores). El grado máx vive solo en las stats (decisión de Rodrigo).
        item { Header(profile, followers = followers, following = following,
            onClickFollowers = onOpenFollowers, onClickFollowing = onOpenFollowing) }
        item { StatsRow(stats, onOpenBoulders, onOpenRoutes, onOpenAllSchools, onOpenMaxGrade, onOpenProjects, onOpenMyPosts) }
        item { AddBlockButton(onClick = onAddBlock) }
        // Panel de admin — tarjeta terracota destacada (solo admins), con el nº de
        // propuestas pendientes de revisar.
        if (profile.isAdmin) {
            item { AdminPanelCard(pendingReview = pendingReview, onClick = onAdmin) }
        }
        // La lista de escuelas del diario NO se repite aquí (ya vive en la pestaña
        // Diario) — el perfil se queda con cabecera + stats + acciones.
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Tarjeta terracota del Panel de admin (solo visible para admins). */
@Composable
private fun AdminPanelCard(pendingReview: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null,
            tint = Color.White, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.profile_admin_panel).uppercase(),
            color = Color.White, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f))
        if (pendingReview > 0) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            ) {
                Text("$pendingReview", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = Color.White, modifier = Modifier.size(20.dp))
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
    // Foto a pantalla completa con zoom al tocar el avatar.
    var zoomPhoto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    zoomPhoto?.let { url ->
        com.meteomontana.android.ui.components.FullScreenPhotoDialog(url) { zoomPhoto = null }
    }
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
                        .clickable { zoomPhoto = p.photoUrl }
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

        // Píldoras ADMIN / PREMIUM (el grado máx ya está en las stats).
        if (p.isAdmin || p.isPremium) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.isAdmin) ProfilePill("ADMIN", MaterialTheme.colorScheme.onBackground)
                if (p.isPremium) ProfilePill("PREMIUM", MaterialTheme.colorScheme.primary)
            }
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

/**
 * Hoja de ajustes (⚙️): agrupa lo que antes ocupaba el perfil — cuenta, mi
 * actividad, preferencias y sesión. Bottom sheet (paridad iOS).
 */
@Composable
private fun ProfileSettingsScreen(
    onBack: () -> Unit,
    isPrivate: Boolean,
    onEdit: () -> Unit,
    onSavedSchools: () -> Unit,
    onWeekendAlert: () -> Unit,
    onSubmissions: () -> Unit,
    onOpenFollowRequests: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Cabecera: flecha atrás + "Ajustes".
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Atrás",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(stringResource(R.string.profile_settings),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        Column(Modifier.fillMaxWidth()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(bottom = 24.dp)) {
            SettingsSectionLabel(stringResource(R.string.profile_section_account))
            MenuRow(Icons.Outlined.Edit, stringResource(R.string.profile_edit), onEdit)

            SettingsSectionLabel(stringResource(R.string.profile_section_activity))
            MenuRow(Icons.Outlined.Download, stringResource(R.string.profile_saved_schools), onSavedSchools)
            MenuRow(Icons.Outlined.Place, stringResource(R.string.profile_my_contributions), onSubmissions)
            if (isPrivate) {
                MenuRow(Icons.Outlined.PersonAdd, stringResource(R.string.profile_follow_requests), onOpenFollowRequests)
            }

            SettingsSectionLabel(stringResource(R.string.profile_section_prefs))
            MenuRow(Icons.Outlined.Notifications, stringResource(R.string.profile_weather_alert), onWeekendAlert)
            FeedPublishSettingRow()
            val ctx = LocalContext.current
            MenuRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.profile_show_hints)) {
                com.meteomontana.android.ui.components.resetAllHints(ctx)
                android.widget.Toast.makeText(ctx, "Pistas reactivadas — entra en cada pantalla para verlas", android.widget.Toast.LENGTH_SHORT).show()
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
            MenuRow(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.profile_logout), onSignOut)
            var showDelete by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showDelete = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.profile_delete_account),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
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
private fun SettingsSectionLabel(text: String) {
    Text(text.uppercase(),
        style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 2.dp))
}

/**
 * Fila de ajuste "Publicar ascensos en el feed": muestra el valor actual y
 * abre un diálogo con Preguntar / Siempre / Nunca (FeedPublishPrefs).
 */
@Composable
private fun FeedPublishSettingRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var mode by remember {
        androidx.compose.runtime.mutableStateOf(
            com.meteomontana.android.data.local.FeedPublishPrefs.get(ctx))
    }
    var showDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    val labelOf: @Composable (com.meteomontana.android.data.local.FeedPublishMode) -> String = {
        stringResource(
            when (it) {
                com.meteomontana.android.data.local.FeedPublishMode.ASK -> R.string.feed_pref_ask
                com.meteomontana.android.data.local.FeedPublishMode.ALWAYS -> R.string.feed_pref_always
                com.meteomontana.android.data.local.FeedPublishMode.NEVER -> R.string.feed_pref_never
            }
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(Icons.Outlined.People, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.profile_feed_publish), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Text(labelOf(mode), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.profile_feed_publish)) },
            text = {
                Column {
                    com.meteomontana.android.data.local.FeedPublishMode.entries.forEach { m ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    com.meteomontana.android.data.local.FeedPublishPrefs.set(ctx, m)
                                    mode = m
                                    showDialog = false
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = mode == m,
                                onClick = {
                                    com.meteomontana.android.data.local.FeedPublishPrefs.set(ctx, m)
                                    mode = m
                                    showDialog = false
                                }
                            )
                            Text(labelOf(m), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
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
    onProjects: () -> Unit,
    onMyPosts: () -> Unit
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
        // Fila 3: PROYECTOS + MIS PUBLICACIONES — mismas celdas pulsables que
        // el resto (decisión de Rodrigo: publicaciones con el estilo de stats,
        // no como fila de menú). Publicaciones no tiene contador barato → "›".
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(stringResource(R.string.profile_projects), stats.projectCount.toString(), Modifier.weight(1f).clickable(onClick = onProjects))
            StatCell(stringResource(R.string.feed_my_posts_section), "›", Modifier.weight(1f).clickable(onClick = onMyPosts))
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
private fun CenterBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
