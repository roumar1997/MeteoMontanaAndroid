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
    /** Mitades de la tarjeta de degradado: Estadisticas abierta en la pestaña "rock" o "gym". */
    onOpenStatsTab: (String) -> Unit = {},
    onOpenGymTraining: () -> Unit = {},
    onOpenGym: (Long) -> Unit = {},
    /** Sesiones, rocódromos o ascensiones de la tarjeta: la pantalla de ascensiones. */
    onOpenGymAscents: () -> Unit = {},
    /** Ficha «Boards» del perfil: todos los boards de todos los rocódromos juntos. */
    onOpenBoards: () -> Unit = {},
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
                    onOpenStatsTab = onOpenStatsTab,
                    onOpenGymTraining = onOpenGymTraining,
                    onOpenGym = onOpenGym,
                    onOpenGymAscents = onOpenGymAscents,
                    onOpenBoards = onOpenBoards,
                    visible = visible,
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
                onSettings = successState?.let { { settingsOpen = true } }
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
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center))
        // Derecha, todo junto en la misma pastilla: ayuda (?), mi material, compartir y ajustes.
        com.meteomontana.android.ui.components.CumbrePillGroup(
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            com.meteomontana.android.ui.components.HelpButton(topicKey = "profile", compact = true, tint = MaterialTheme.colorScheme.primary)
            // Mi material: primero de los tres, igual que en iOS.
            if (onGear != null) IconButton(onClick = onGear) {
                Icon(
                    Icons.Outlined.Backpack,
                    contentDescription = stringResource(R.string.profile_screen_v2_mi_material),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (onShare != null) IconButton(onClick = onShare) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_share_ios), contentDescription = stringResource(R.string.profile_screen_v2_compartir_perfil),
                    tint = MaterialTheme.colorScheme.primary)
            }
            if (onSettings != null) IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.profile_settings),
                    tint = MaterialTheme.colorScheme.primary)
            }
            if (showClose) TextButton(onClick = onBack) {
                Text(stringResource(com.meteomontana.android.core.ui.R.string.common_close), color = MaterialTheme.colorScheme.primary,
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
    onOpenStatsTab: (String) -> Unit = {},
    onOpenGymTraining: () -> Unit = {},
    onOpenGym: (Long) -> Unit = {},
    onOpenGymAscents: () -> Unit = {},
    /** Ficha «Boards» del perfil: todos los boards de todos los rocódromos juntos. */
    onOpenBoards: () -> Unit = {},
    /** Pestaña Perfil visible: la tarjeta de rocódromo se relee cada vez (la pestaña no se recompone al volver). */
    visible: Boolean = true,
    onPhotoChanged: () -> Unit = {}
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val headerIndex = 0
    val gymsOn = com.meteomontana.android.ui.components.LocalFeatureFlags.current.isGymsEnabled
    val climbVm: ProfileClimbViewModel = hiltViewModel()
    val climb by climbVm.state.collectAsStateWithLifecycle()
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    // La pestaña Perfil se queda viva: se relee cada vez que se hace visible.
    androidx.compose.runtime.LaunchedEffect(visible) { if (visible && gymsOn) climbVm.load(null, myUid) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            onPhotoChanged()
            if (gymsOn) climbVm.load(null, myUid)
            scope.launch { kotlinx.coroutines.delay(900); refreshing = false }
        },
        modifier = Modifier.fillMaxSize()
    ) {
    com.meteomontana.android.ui.components.UnclippedAvatarHost(
        slotOffsetPx = {
            // Y del hueco del avatar: la cabecera + el margen superior de la lista + su propio padding.
            val info = listState.layoutInfo
            info.visibleItemsInfo.firstOrNull { it.index == headerIndex }
                ?.let { it.offset + info.beforeContentPadding + with(density) { 10.dp.roundToPx() } }
        },
        avatar = { ProfileAvatar(profile, canChangePhoto = !offline, onPhotoChanged = onPhotoChanged) },
        modifier = Modifier.fillMaxSize(),
        startPadding = ProfileAvatarStart
    ) { avatarSlot ->
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Sin esto no se llega al boton de anadir bloque/via.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 6.dp,
            bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current)
    ) {
        // Sin franja propia de «sin conexión»: ya la pone la franja global de la app (NetworkBanner).
        // Cabecera compacta (maqueta A): avatar a la izquierda; nombre, @usuario y seguidos · seguidores a su lado. Sin
        // descripción. Debajo, el título con sus efectos (tocar = elegir título y viero/bloquero).
        item {
            Column {
                CompactProfileHeader(
                    name = profile.displayName ?: profile.username ?: stringResource(com.meteomontana.android.feature.chat.R.string.school_presence_row_v3_tu),
                    handle = "@${profile.username ?: profile.displayName?.lowercase()?.replace(" ", "_") ?: "tu_username"}",
                    uid = profile.uid, followers = followers, following = following,
                    onFollowers = onOpenFollowers, onFollowing = onOpenFollowing, avatarSlot = avatarSlot,
                    modifier = Modifier.guideTarget("profile.follows")
                )
                com.meteomontana.android.ui.components.RewardsBlock(uid = null)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).guideTarget("profile.diary"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileCells(stats.boulderCount, stats.routeCount, stats.maxBoulderGrade, stats.maxRouteGrade, onOpenBoulders, onOpenRoutes, onOpenMaxGrade)
                ProfileMiniTiles(
                    com.meteomontana.android.ui.screens.community.rememberPostCount(null, visible), onOpenMyPosts, stats.schoolCount, onOpenAllSchools, stats.projectCount, onOpenProjects,
                    boards = if (gymsOn) com.meteomontana.android.domain.model.gym.GymBreakdown.boardCount(climb.summary.items) else null, onBoards = onOpenBoards
                )
                // Entrenos en rocódromo (solo con el interruptor `gyms_enabled` encendido).
                if (gymsOn) {
                    Box(Modifier.guideTarget("profile.stats")) {
                        ClimbGradientCard(
                            climb, stats.maxGrade, onStats = onOpenStats, onSessions = { onOpenStatsTab("gym") }, onGyms = onOpenGymAscents,
                            onAscents = onOpenGymAscents, onRock = { onOpenStatsTab("rock") }
                        )
                    }
                    var pickingHome by remember { mutableStateOf(false) }
                    HomeGymAndStreak(
                        climb.homeGym, climb.streakWeeks, onOpenGym, onPick = { pickingHome = true },
                        trailing = { LogWorkoutButton(compact = true, onSaved = { climbVm.load(null, myUid) }) }
                    )
                    if (pickingHome) com.meteomontana.android.ui.screens.gyms.GymPickerSheet(
                        onPick = { g, _ -> pickingHome = false; climbVm.setHomeGym(g) },
                        onDismiss = { pickingHome = false }
                    )
                } else {
                    Box(Modifier.guideTarget("profile.stats")) { com.meteomontana.android.ui.components.StatsEntryButton(onClick = onOpenStats) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    }
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
    avatarSlot: androidx.compose.ui.unit.Dp = 76.dp
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hueco del avatar: el avatar real se dibuja por encima del scroll (ProfileAvatar) para que su efecto no se corte.
        Spacer(Modifier.height(avatarSlot))
        Spacer(Modifier.height(8.dp))
        // Nombre grande serif.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                p.displayName ?: p.username ?: stringResource(com.meteomontana.android.feature.chat.R.string.school_presence_row_v3_tu),
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
