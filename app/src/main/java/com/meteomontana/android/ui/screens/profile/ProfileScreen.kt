@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.profile

import com.meteomontana.android.ui.components.SkeletonProfile
import com.meteomontana.android.ui.components.VerifiedCheck

import com.meteomontana.android.ui.components.userRing

import androidx.compose.material.icons.outlined.Language
import com.meteomontana.android.util.AppText
import android.content.Context
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Backpack
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
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
    onOpenStats: () -> Unit = {},
    /** En la pestaña keep-alive: true cuando la pestaña Perfil está visible.
     *  Recarga al hacerse visible (los ON_RESUME no saltan al cambiar de tab). */
    visible: Boolean = true,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    var gearOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = gearOpen) { gearOpen = false }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    if (gearOpen && successState != null) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { gearOpen = false },
            // Entera desde el principio: a media altura la cabecera queda fuera
            // y con ella el boton de guardar (lo cazo Rodrigo en el Xiaomi).
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(
                skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background
        ) {
            val myHeight by viewModel.myHeight.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadMyHeight() }
            MyGearSheet(
                gearJson = successState.profile.gearJson,
                height = myHeight,
                onSetHeight = { viewModel.setMyHeight(it) },
                onClose = { gearOpen = false },
                onSave = { json -> viewModel.saveGear(json) { gearOpen = false } }
            )
        }
    }

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
        // Como iOS: el perfil pasa POR DEBAJO de las pastillas de arriba (que flotan), en vez de cortarse contra una
        // cabecera fija. La foto empieza a la altura de las pastillas, entre las de la izquierda y las de la derecha.
        Box(modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                ProfileUiState.Loading -> SkeletonProfile()
                is ProfileUiState.Error -> CenterBox { Text(stringResource(R.string.profile_screen_v2_error_1_s, s.message), color = MaterialTheme.colorScheme.error) }
                is ProfileUiState.Success -> Content(
                    profile = s.profile,
                    stats = s.stats,
                    followers = s.followers,
                    following = s.following,
                    offline = s.offline,
                    onAddBlock = { addBlockOpen = true },
                    onOpenFollowers = onOpenFollowers,
                    onOpenFollowing = onOpenFollowing,
                    onOpenBoulders = onOpenBoulders,
                    onOpenRoutes = onOpenRoutes,
                    onOpenAllSchools = onOpenAllSchools,
                    onOpenMaxGrade = onOpenMaxGrade,
                    onOpenProjects = onOpenProjects,
                    onOpenMyPosts = onOpenMyPosts,
                    onOpenStats = onOpenStats,
                    onPhotoChanged = viewModel::load
                )
            }
            }
            // Cabecera: ayuda (izq) · compartir + ajustes (der). Compartir y ajustes solo cuando el perfil ha cargado.
            ProfileTopBar(
                // Sin titulo: debajo ya estan tu foto, tu nombre y tu @usuario.
                // Poner "Cuenta" encima solo repite donde estas y roba altura.
                title = "",
                showClose = showClose,
                onBack = onBack,
                onGear = successState?.let { { gearOpen = true } },
                onShare = successState?.let { s -> { shareProfile(ctx, scope, s.profile, s.stats) } },
                onSettings = successState?.let { { settingsOpen = true } },
                onAdmin = successState?.takeIf { it.profile.isAdmin }?.let { { onAdmin() } },
                pendingReview = successState?.pendingReview ?: 0
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

/** Dispara la imagen 1080×1920 (formato historia) de "compartir perfil". */
internal fun shareProfile(ctx: Context, scope: CoroutineScope, p: PrivateProfile, stats: JournalStats) {
    val handle = p.username ?: p.uid
    scope.launch {
        com.meteomontana.android.ui.share.shareProfileAsImage(
            ctx, handle,
            p.username?.let { "@$it" } ?: (p.displayName ?: AppText.get(R.string.profile_screen_v4_mi_perfil)),
            username = p.username, photoUrl = p.photoUrl,
            topGrade = stats.maxGrade, bio = p.bio,
            boulders = stats.boulderCount, routes = stats.routeCount, schools = stats.schoolCount
        )
    }
}

@Composable
internal fun ProfileTopBar(
    title: String,
    showClose: Boolean,
    onBack: () -> Unit,
    onGear: (() -> Unit)? = null,
    onShare: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    onAdmin: (() -> Unit)? = null,
    pendingReview: Int = 0
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center))
        // Izquierda: "? AYUDA" (ya es una pastilla: NO se mete dentro de otra, se veía el borde doble y no le cabía)
        // y, solo para admins, el acceso al panel con el nº de pendientes en SU pastilla.
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.meteomontana.android.ui.components.HelpButton(topicKey = "profile")
            if (onAdmin != null) com.meteomontana.android.ui.components.CumbrePillGroup {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onAdmin)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Outlined.AdminPanelSettings, contentDescription = stringResource(R.string.profile_screen_v2_panel_de_admin),
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    if (pendingReview > 0) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("$pendingReview", color = Color.White,
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        // Derecha: compartir + ajustes, también en pastilla (en iOS son las dos
        // que van juntas arriba a la derecha).
        com.meteomontana.android.ui.components.CumbrePillGroup(
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            // Mi material: primero de los tres, igual que en iOS.
            if (onGear != null) IconButton(onClick = onGear) {
                Icon(
                    Icons.Outlined.Backpack,
                    contentDescription = stringResource(R.string.profile_screen_v2_mi_material),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (onShare != null) IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.profile_screen_v2_compartir_perfil),
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
    // SIN linea bajo las pastillas: en iOS no hay separador ahi, y con los
    // botones ya agrupados en su capsula la raya solo parte la pantalla en dos
    // sin separar nada (lo pidio Rodrigo).
}

@Composable
internal fun Content(
    profile: PrivateProfile,
    stats: JournalStats,
    followers: Long,
    following: Long,
    offline: Boolean = false,
    onAddBlock: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenBoulders: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenAllSchools: () -> Unit,
    onOpenMaxGrade: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenMyPosts: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onPhotoChanged: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Sin esto no se llega al boton de anadir bloque/via.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 6.dp,
            bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current)
    ) {
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
            onClickFollowers = onOpenFollowers, onClickFollowing = onOpenFollowing,
            canChangePhoto = !offline, onPhotoChanged = onPhotoChanged) }
        item { StatsRow(stats, onOpenBoulders, onOpenRoutes, onOpenAllSchools, onOpenMaxGrade, onOpenProjects, onOpenMyPosts, onOpenStats) }
        // Admin ya no va aquí: es un botón arriba a la izquierda (junto al "?").
        // La lista de escuelas tampoco (ya vive en la pestaña Diario).
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun Header(
    p: PrivateProfile,
    followers: Long = 0,
    following: Long = 0,
    onClickFollowers: () -> Unit = {},
    onClickFollowing: () -> Unit = {},
    /** false sin conexión: el toque solo amplía la foto, como antes. */
    canChangePhoto: Boolean = true,
    onPhotoChanged: () -> Unit = {}
) {
    // Foto a pantalla completa con zoom al tocar el avatar.
    var zoomPhoto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    zoomPhoto?.let { url ->
        com.meteomontana.android.ui.components.FullScreenPhotoDialog(url) { zoomPhoto = null }
    }
    // Tocar tu foto: menú «Cambiar foto / Ver foto» → selector → recorte → se sube (Álvaro, 2026-10-23; espejo de iOS).
    val photoVm: ProfilePhotoViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val uploading by photoVm.uploading.collectAsStateWithLifecycle()
    val photoFailed by photoVm.failed.collectAsStateWithLifecycle()
    var photoMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val openPicker = rememberProfilePhotoPicker { uri -> photoVm.change(uri, onPhotoChanged) }
    if (photoFailed) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = photoVm::dismissError,
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = photoVm::dismissError) { Text(stringResource(android.R.string.ok)) }
            },
            title = { Text(stringResource(R.string.profile_photo_error_title)) },
            text = { Text(stringResource(R.string.profile_photo_error_body)) }
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar centrado (algo más pequeño: que el diario y ESTADÍSTICAS se vean sin bajar).
        val onAvatarTap = {
            if (canChangePhoto) photoMenu = true else if (p.photoUrl != null) zoomPhoto = p.photoUrl
        }
        Box {
            if (p.photoUrl != null) {
                androidx.compose.runtime.key(p.photoUrl) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(p.photoUrl)
                            .memoryCachePolicy(coil.request.CachePolicy.WRITE_ONLY)
                            .crossfade(200)
                            .build(),
                        contentDescription = stringResource(R.string.profile_photo_title),
                        modifier = Modifier.userRing(p.uid, 76.dp).size(76.dp).clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(onClick = onAvatarTap)
                    )
                }
            } else {
                Image(
                    painter = painterResource(R.drawable.logo_cumbre),
                    contentDescription = stringResource(R.string.profile_photo_title),
                    modifier = Modifier.size(76.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(enabled = canChangePhoto, onClick = onAvatarTap)
                )
            }
            if (uploading) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) }
            }
            if (canChangePhoto) {
                // Icono de cámara: que se vea que la foto se puede tocar.
                Box(
                    Modifier.align(Alignment.BottomEnd).size(24.dp)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.background)
                        .padding(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.PhotoCamera, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(12.dp)
                    )
                }
            }
            androidx.compose.material3.DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_photo_change)) },
                    onClick = { photoMenu = false; openPicker() }
                )
                if (p.photoUrl != null) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_photo_view)) },
                        onClick = { photoMenu = false; zoomPhoto = p.photoUrl }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Nombre grande serif.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                p.displayName ?: p.username ?: stringResource(R.string.school_presence_row_v3_tu),
                fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            VerifiedCheck(p.uid, 19.dp)
        }
        // @usuario y, al lado, ADMIN/PREMIUM (antes desplazaban los seguidores a la izquierda).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "@${p.username ?: p.displayName?.lowercase()?.replace(" ", "_") ?: "tu_username"}",
                fontFamily = com.meteomontana.android.ui.theme.Mono, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (p.isPremium) ProfilePill("PREMIUM", MaterialTheme.colorScheme.primary)
        }
        // Título, insignias y nivel por aportaciones (tocar = elegir título y viero/bloquero).
        com.meteomontana.android.ui.components.RewardsBlock(uid = null)

        // La bio, colocada como en iOS: DESPUÉS del email y con la tinta
        // normal, no en gris de dato secundario.
        //
        // Es lo que el usuario ha escrito de su puño; el correo es un dato del
        // sistema. Ponerla antes y en gris —como estaba en el primer intento—
        // la hacía parecer una línea más de la ficha en vez de su presentación.
        // Comparado con capturas de las dos apps a la vez.
        p.bio?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }

        // Seguidores / siguiendo y las píldoras ADMIN / PREMIUM en UNA fila (antes, dos bloques apilados).
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.guideTarget("profile.follows")) {
            FollowCount(followers, stringResource(R.string.profile_followers).uppercase(), onClickFollowers)
            FollowCount(following, stringResource(R.string.profile_following).uppercase(), onClickFollowing)
        }
    }
}

@Composable
internal fun ProfilePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, color, RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
internal fun FollowCount(value: Long, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$value", fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold,
            fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
