@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.navigation.Tab
import com.meteomontana.android.navigation.mainTabs
import com.meteomontana.android.ui.screens.admin.AdminScreen
import com.meteomontana.android.ui.screens.chat.ChatListScreen
import com.meteomontana.android.ui.screens.chat.ChatScreen
import com.meteomontana.android.ui.screens.chat.GroupChatScreen
import com.meteomontana.android.ui.screens.chat.NewGroupScreen
import com.meteomontana.android.ui.screens.day.DayDetailScreen
import com.meteomontana.android.ui.screens.detail.SchoolDetailScreen
import com.meteomontana.android.ui.screens.saved.SavedSchoolsScreen
import com.meteomontana.android.ui.screens.notifications.NotificationsScreen
import com.meteomontana.android.ui.screens.profile.EditProfileScreen
import com.meteomontana.android.ui.screens.profile.JournalEntriesScreen
import com.meteomontana.android.ui.screens.profile.JournalSchoolsScreen
import com.meteomontana.android.ui.screens.profile.JournalSectorsScreen
import com.meteomontana.android.ui.screens.profile.ProjectsScreen
import com.meteomontana.android.ui.screens.profile.ProfileScreen
import com.meteomontana.android.ui.screens.radar.RadarScreen
import com.meteomontana.android.ui.screens.schools.SchoolListScreen
import com.meteomontana.android.ui.screens.submissions.MySubmissionsScreen
import com.meteomontana.android.ui.screens.submissions.SubmitSchoolScreen
import com.meteomontana.android.ui.screens.topo.TopoEditorScreen
import com.meteomontana.android.ui.screens.users.FollowListScreen
import com.meteomontana.android.ui.screens.users.FollowRequestsScreen
import com.meteomontana.android.ui.screens.users.PublicProfileScreen
import com.meteomontana.android.ui.screens.users.SearchUsersScreen
import com.meteomontana.android.ui.screens.meetups.CreateMeetupScreen
import com.meteomontana.android.ui.screens.meetups.MeetupAlertScreen
import com.meteomontana.android.ui.screens.meetups.MeetupDetailScreen
import com.meteomontana.android.ui.screens.meetups.MeetupsScreen
import com.meteomontana.android.ui.screens.weather.WeatherScreen

/** Ruta raíz (vacía) del NavHost interno del sheet: el sheet se abre vacío y se
 *  navega al destino real; al volver a ella se cierra la tarjeta. */
private const val SHEET_ROOT = "sheet_root"

/**
 * Conmutador TIEMPO ⇄ RADAR de la primera pestaña (segmented estilo Cumbre:
 * cápsula con borde Rule, segmento activo Terra).
 */
@Composable
private fun WeatherRadarToggle(
    showRadar: Boolean,
    onSelect: (radar: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
        ) {
            @Composable
            fun segment(label: String, active: Boolean, onClick: () -> Unit) {
                Text(
                    label,
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = if (active) androidx.compose.ui.graphics.Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable(onClick = onClick)
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
            segment(
                androidx.compose.ui.res.stringResource(
                    com.meteomontana.android.R.string.weather_toggle_weather),
                !showRadar
            ) { onSelect(false) }
            segment(
                androidx.compose.ui.res.stringResource(
                    com.meteomontana.android.R.string.weather_toggle_radar),
                showRadar
            ) { onSelect(true) }
        }
    }
}


/** Ruta del host de pestañas (las 5 viven compuestas dentro). */
private const val TABS_HOST = "tabs"

@Composable
fun MainScreen(
    deepLink: com.meteomontana.android.DeepLinkTarget? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    // Navegación principal (pantallas a pantalla completa por debajo del sheet):
    // tabs, detalle de escuela, editor topo y admin.
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // Pestaña activa. Las 5 pestañas viven COMPUESTAS a la vez (como las tabs
    // de SwiftUI en iOS): cambiar de pestaña solo alterna visibilidad — sin
    // recrear mapas ni flashes. La navegación real (detalle, admin...) sigue
    // en el NavHost, apilada sobre el host "tabs".
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(Tab.Schools.route)
    }
    // La primera pestaña ("Radar") aloja Radar + Tiempo (conmutador
    // TIEMPO ⇄ RADAR). true = Radar visible — ES la vista por defecto (la
    // pestaña se llama Radar). Sobrevive al cambio de pestaña (keep-alive)
    // y a recreaciones (saveable).
    var weatherShowsRadar by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(true)
    }
    // Compat: la antigua pestaña "radar" ya no existe — cualquier estado/enlace
    // que apuntara a ella cae en Tiempo con el Radar abierto.
    if (selectedTab == "radar") {
        selectedTab = Tab.Weather.route
        weatherShowsRadar = true
    }

    // ── Sheet único estilo Apple ──
    // Una sola tarjeta (ModalBottomSheet) que sube una vez; dentro, un NavHost
    // propio (sheetNav) navega entre las pantallas overlay (Perfil, Chats,
    // conversación, Notificaciones, Buscar, etc.) DESLIZANDO LATERALMENTE, sin
    // repetir la animación arriba-abajo en cada paso ni atrás. Al volver a la
    // raíz, la tarjeta baja. Un solo sheet → sin apilar ModalBottomSheets.
    val sheetNav = rememberNavController()
    var sheetVisible by remember { androidx.compose.runtime.mutableStateOf(false) }
    // Ruta pendiente de navegar en el sheet. NO podemos navegar el sheetNav aquí:
    // su grafo lo registra el NavHost interno, que solo existe cuando el sheet es
    // visible. Guardamos la ruta, mostramos el sheet, y un LaunchedEffect DENTRO
    // del contenido del sheet (con el NavHost ya compuesto) hace la navegación.
    var pendingSheetRoute by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    // Abre el sheet en un destino.
    val openSheet: (String) -> Unit = { route ->
        pendingSheetRoute = route
        sheetVisible = true
    }
    // Oculta el overlay y limpia el backstack del sheetNav. Ya no hay
    // ModalBottomSheet: basta con bajar la bandera (AnimatedVisibility hace la
    // animación de salida) y resetear el NavHost interno a su raíz.
    val dismissSheet: () -> Unit = {
        sheetVisible = false
        sheetNav.popBackStack(SHEET_ROOT, inclusive = false)
    }
    // Atrás dentro del sheet: si hay pila interna por encima de la primera
    // pantalla, desliza atrás (lateral); si no, baja la tarjeta.
    val popSheetOrDismiss: () -> Unit = {
        // Contamos cuántas entradas hay por encima de SHEET_ROOT
        val entries = sheetNav.currentBackStack.value
        val aboveRoot = entries.count { it.destination.route != null && it.destination.route != SHEET_ROOT }
        if (aboveRoot > 1) {
            sheetNav.popBackStack()
        } else {
            dismissSheet()
        }
    }
    // Cierra el sheet y abre una pantalla completa (las que viven por debajo).
    val openFullScreen: (String) -> Unit = { route ->
        navController.navigate(route)
        dismissSheet()
    }

    // Consume el deep link entrante del push. Las pantallas overlay se abren en
    // el sheet; el detalle de escuela es pantalla completa.
    androidx.compose.runtime.LaunchedEffect(deepLink) {
        if (deepLink != null) {
            when (deepLink.targetType) {
                "school", "school_detail" ->
                    deepLink.targetId?.let { openSheet(Routes.schoolDetail(it)) }
                // Enlace compartido de una vía/bloque: "escuela|lineId".
                "via" -> deepLink.targetId?.let { packed ->
                    val school = packed.substringBefore('|')
                    val lineId = packed.substringAfter('|', "")
                    openSheet(Routes.schoolDetail(school, viaId = lineId.ifBlank { null }))
                }
                "user"        -> deepLink.targetId?.let { openSheet(Routes.publicProfile(it)) }
                "chat", "message" -> deepLink.targetId?.let { openSheet(Routes.chat(it)) }
                "group" -> deepLink.targetId?.let { openSheet(Routes.groupChat(it)) }
                // Enlace de invitación a una quedada: abre su detalle (el join
                // usará el token pendiente si lo hay).
                "meetup" -> deepLink.targetId?.let { openSheet(Routes.meetupDetail(it)) }
                // Push de denuncia nueva → abre el panel de admin.
                "admin_reports" -> openFullScreen(Routes.ADMIN)
                "submission", "contribution" -> openSheet(Routes.MY_SUBMISSIONS)
                // Push de actividad del feed Comunidad → detalle del post.
                "feed_post" -> deepLink.targetId?.let { openSheet(Routes.feedPost(it)) }
                // Deep link antiguo a la pestaña Radar → Tiempo en modo radar.
                "radar" -> { selectedTab = Tab.Weather.route; weatherShowsRadar = true }
                "notifications" -> openSheet(Routes.NOTIFICATIONS)
                "follow_request" -> openSheet(Routes.FOLLOW_REQUESTS)
                "compare" -> deepLink.targetId?.let { openSheet("compare/$it") }
            }
            onDeepLinkConsumed()
        }
    }

    val showBottomBar = currentRoute == TABS_HOST

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                // Cápsula flotante (estilo iOS) en vez de la barra plana a todo el ancho.
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(30.dp))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        mainTabs.forEach { tab ->
                            val selected = selectedTab == tab.route
                            val tint = if (selected) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .clickable {
                                        // Con el overlay abierto, pulsar una
                                        // pestaña lo cierra y cambia de tab.
                                        if (sheetVisible) dismissSheet()
                                        if (!selected) selectedTab = tab.route
                                    }
                                    // Con 4 tabs no caben los 4 rótulos: solo el
                                    // seleccionado enseña su nombre (estilo iOS).
                                    .padding(
                                        horizontal = if (selected) 16.dp else 13.dp,
                                        vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(tab.icon, contentDescription = tab.label,
                                    tint = tint, modifier = Modifier.size(20.dp))
                                if (selected) {
                                    Text(tab.label, style = MaterialTheme.typography.labelMedium,
                                        color = tint, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // En Radar el mapa ocupa TODA la pantalla y la cápsula de tabs flota
        // encima (el player del radar ya deja hueco). En el resto, padding normal.
        val effectivePadding = if (currentRoute == TABS_HOST &&
            selectedTab == Tab.Weather.route && weatherShowsRadar)
            androidx.compose.foundation.layout.PaddingValues(0.dp) else padding
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().padding(effectivePadding)) {
            com.meteomontana.android.ui.components.NetworkBanner()
            NavHost(
                navController = navController,
                startDestination = TABS_HOST,
                // Sin crossfade entre pestañas: durante el fundido convivían la
                // pantalla saliente (mapa congelado = "imagen fantasma") y la
                // entrante aún sin pintar. Corte limpio sobre fondo Cumbre.
                enterTransition = { androidx.compose.animation.EnterTransition.None },
                exitTransition = { androidx.compose.animation.ExitTransition.None },
                popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                popExitTransition = { androidx.compose.animation.ExitTransition.None },
                modifier = Modifier.weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                composable(TABS_HOST) {
                    // Las 5 pestañas SIEMPRE compuestas (lazy: cada una entra la
                    // primera vez que se visita y ya no se destruye). La activa
                    // va encima (zIndex) y las demás quedan invisibles debajo.
                    val visited = androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
                    }
                    visited[selectedTab] = true
                    Box(Modifier.fillMaxSize()) {
                        @androidx.compose.runtime.Composable
                        fun tabContainer(route: String, content: @androidx.compose.runtime.Composable () -> Unit) {
                            if (visited[route] == true) {
                                val sel = selectedTab == route
                                Box(
                                    Modifier.fillMaxSize()
                                        .zIndex(if (sel) 1f else 0f)
                                        .graphicsLayer { alpha = if (sel) 1f else 0f }
                                ) { content() }
                            }
                        }
                        tabContainer(Tab.Weather.route) {
                    // Tiempo + Radar conviven en la primera pestaña (conmutador
                    // TIEMPO ⇄ RADAR). Mismo truco keep-alive que las tabs: las
                    // dos capas quedan compuestas (el radar entra lazy la primera
                    // vez) y el toggle solo alterna visibilidad — sin recrear el
                    // mapa del radar ni flashes.
                    var radarVisited by androidx.compose.runtime.saveable.rememberSaveable {
                        androidx.compose.runtime.mutableStateOf(false)
                    }
                    if (weatherShowsRadar) radarVisited = true
                    Box(Modifier.fillMaxSize()) {
                        // ── Capa TIEMPO ──
                        Box(
                            Modifier.fillMaxSize()
                                .zIndex(if (!weatherShowsRadar) 1f else 0f)
                                .graphicsLayer { alpha = if (!weatherShowsRadar) 1f else 0f }
                        ) {
                            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                                WeatherRadarToggle(
                                    showRadar = false,
                                    onSelect = { weatherShowsRadar = it },
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                WeatherScreen(
                                    onDayClick = { schoolId, lat, lon, idx ->
                                        if (schoolId != null) openSheet(Routes.dayDetail(schoolId, idx))
                                        else openSheet(Routes.dayDetailByLocation(lat, lon, idx))
                                    }
                                )
                            }
                        }
                        // ── Capa RADAR (mapa a pantalla completa) ──
                        if (radarVisited) {
                            Box(
                                Modifier.fillMaxSize()
                                    .zIndex(if (weatherShowsRadar) 1f else 0f)
                                    .graphicsLayer { alpha = if (weatherShowsRadar) 1f else 0f }
                            ) {
                                RadarScreen(onSchoolDetail = { id ->
                                    openSheet(Routes.schoolDetail(id))
                                })
                                WeatherRadarToggle(
                                    showRadar = true,
                                    onSelect = { weatherShowsRadar = it },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .statusBarsPadding()
                                        .padding(top = 8.dp)
                                        .zIndex(2f)
                                )
                            }
                        }
                    }
                }
                        tabContainer(Tab.Schools.route) {
                    SchoolListScreen(
                        onSchoolClick = { id -> openSheet(Routes.schoolDetail(id)) },
                        onViaHit = { schoolId, viaId, viaName ->
                            openSheet(Routes.schoolDetail(schoolId, via = viaName, viaId = viaId))
                        },
                        onProfileClick = { openSheet(Routes.PROFILE) },
                        onSubmitSchool = { openSheet(Routes.SUBMIT_SCHOOL) },
                        onSearchUsers = { openSheet(Routes.SEARCH_USERS) },
                        onNotifications = { openSheet(Routes.NOTIFICATIONS) },
                        onChats = { openSheet(Routes.CHAT_LIST) },
                        onCompare = { ids -> openSheet(Routes.compare(ids)) }
                    )
                }
                        tabContainer(Tab.Profile.route) {
                    // Perfil como pestaña: mismo contenido que el sheet, pero sin CERRAR.
                    com.meteomontana.android.ui.screens.profile.ProfileScreen(
                        onBack = {},
                        showClose = false,
                        onEdit = { openSheet(Routes.EDIT_PROFILE) },
                        onSubmissions = { openSheet(Routes.MY_SUBMISSIONS) },
                        onAdmin = { openFullScreen(Routes.ADMIN) },
                        onSavedSchools = { openSheet(Routes.SAVED_SCHOOLS) },
                        onWeekendAlert = { openSheet(Routes.WEEKEND_ALERT) },
                        onOpenFollowers = {
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                                openSheet(Routes.followList(uid, "followers"))
                            }
                        },
                        onOpenFollowing = {
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                                openSheet(Routes.followList(uid, "following"))
                            }
                        },
                        onOpenFollowRequests = { openSheet(Routes.FOLLOW_REQUESTS) },
                        onOpenSchoolEntries = { schoolName -> openSheet(Routes.journalSectors(schoolName)) },
                        onOpenBoulders = { openSheet(Routes.journalEntries("discipline:BOULDER")) },
                        onOpenRoutes = { openSheet(Routes.journalEntries("discipline:ROUTE")) },
                        onOpenAllSchools = { openSheet(Routes.journalSchools(null)) },
                        onOpenMaxGrade = { openSheet(Routes.journalEntries("grade-max")) },
                        onOpenProjects = { openSheet(Routes.projects(null)) },
                        onOpenMyPosts = { openSheet(Routes.MY_POSTS) }
                    )
                }
                        tabContainer(Tab.Community.route) {
                    // Feed social Comunidad (SIGUIENDO | TODOS | RANKING).
                    com.meteomontana.android.ui.screens.community.FeedScreen(
                        onOpenSchool = { schoolId, lineId, lineName ->
                            openSheet(Routes.schoolDetail(schoolId, via = lineName, viaId = lineId))
                        },
                        onOpenUser = { uid -> openSheet(Routes.publicProfile(uid)) },
                        onSearchUsers = { openSheet(Routes.SEARCH_USERS) },
                        onOpenPost = { postId -> openSheet(Routes.feedPost(postId)) }
                    )
                }
                        tabContainer(Tab.Meetups.route) {
                    MeetupsScreen(
                        onMeetupClick = { id -> openSheet(Routes.meetupDetail(id)) },
                        onOpenChat = { convId -> openSheet(Routes.groupChat(convId)) },
                        onCreateMeetup = { openSheet(Routes.CREATE_MEETUP) },
                        onOpenAlert = { openSheet(Routes.MEETUP_ALERT) }
                    )
                }
                        // ── Overlay estilo Apple, DENTRO del contenido ──
                        // Vive encima de las pestañas (zIndex 2) pero por DEBAJO de
                        // la cápsula de tabs (slot bottomBar del Scaffold, siempre
                        // encima del contenido) → la barra queda visible y pulsable.
                        // En función aparte para evitar la ambigüedad de overload de
                        // AnimatedVisibility con el ColumnScope que lo envuelve.
                        SheetOverlay(
                            sheetVisible = sheetVisible,
                            onHide = { sheetVisible = false },
                            pendingSheetRoute = pendingSheetRoute,
                            onPendingConsumed = { pendingSheetRoute = null },
                            sheetNav = sheetNav,
                            navController = navController,
                            openSheet = openSheet,
                            openFullScreen = openFullScreen,
                            popSheetOrDismiss = popSheetOrDismiss,
                            // Lo que el Scaffold ya reservó abajo (cápsula de tabs +
                            // navbar): las pantallas del overlay con teclado se lo
                            // descuentan al imePadding para pegarse al teclado.
                            bottomInset = effectivePadding.calculateBottomPadding()
                        )
                    }
                }
                composable(Routes.ADMIN) {
                    AdminScreen(onBack = { navController.popBackStack() },
                        // El detalle de escuela vive ahora en el overlay del host
                        // de pestañas: salimos de admin (full screen) y lo abrimos.
                        onOpenSchool = { id -> navController.popBackStack(); openSheet(Routes.schoolDetail(id)) },
                        onOpenUser = { uid -> openSheet(Routes.publicProfile(uid)) },
                        onOpenMeetup = { id -> openSheet(Routes.meetupDetail(id)) },
                        onOpenFeedPost = { id -> openSheet(Routes.feedPost(id)) })
                }
                composable(
                    route = Routes.TOPO_EDITOR,
                    arguments = listOf(navArgument("blockId") { type = NavType.StringType })
                ) {
                    TopoEditorScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }

    // El overlay daba el "atrás" via ModalBottomSheet; ahora se maneja a mano.
    BackHandler(enabled = sheetVisible) { popSheetOrDismiss() }
}

/**
 * Overlay del host de pestañas: se anima de entrada/salida (slide + fade) y aloja
 * el NavHost interno. En función propia para que AnimatedVisibility resuelva al
 * overload sin receptor (no al de ColumnScope del contenedor que lo envuelve).
 */
@Composable
private fun SheetOverlay(
    sheetVisible: Boolean,
    onHide: () -> Unit,
    pendingSheetRoute: String?,
    onPendingConsumed: () -> Unit,
    sheetNav: androidx.navigation.NavHostController,
    navController: androidx.navigation.NavHostController,
    openSheet: (String) -> Unit,
    openFullScreen: (String) -> Unit,
    popSheetOrDismiss: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    AnimatedVisibility(
        visible = sheetVisible,
        enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(280)),
        exit = slideOutHorizontally(tween(280)) { it } + fadeOut(tween(280)),
        modifier = Modifier.fillMaxSize().zIndex(2f)
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Navega al destino pendiente una vez el NavHost interno está
            // compuesto (grafo registrado).
            androidx.compose.runtime.LaunchedEffect(pendingSheetRoute) {
                val r = pendingSheetRoute
                if (r != null) {
                    sheetNav.navigate(r) {
                        popUpTo(SHEET_ROOT) { inclusive = false }
                        launchSingleTop = true
                    }
                    onPendingConsumed()
                }
            }
            // Auto-cierre: si el NavHost interno vuelve a SHEET_ROOT (back del
            // sistema u otro pop) y NO hay ruta pendiente, ocultamos el overlay.
            val sheetEntry by sheetNav.currentBackStackEntryAsState()
            androidx.compose.runtime.LaunchedEffect(sheetEntry) {
                if (sheetEntry?.destination?.route == SHEET_ROOT && pendingSheetRoute == null) {
                    onHide()
                }
            }
            SheetNavHost(
                sheetNav = sheetNav,
                navController = navController,
                openSheet = openSheet,
                openFullScreen = openFullScreen,
                popSheetOrDismiss = popSheetOrDismiss,
                bottomInset = bottomInset
            )
        }
    }
}

/**
 * NavHost interno del overlay (antes vivía dentro del ModalBottomSheet). Desliza
 * lateralmente entre pantallas (Perfil, Chats, detalle de escuela, etc.) como iOS.
 * Se extrae a una función aparte para no anidar en exceso dentro de MainScreen.
 */
@Composable
private fun SheetNavHost(
    sheetNav: androidx.navigation.NavHostController,
    navController: androidx.navigation.NavHostController,
    openSheet: (String) -> Unit,
    openFullScreen: (String) -> Unit,
    popSheetOrDismiss: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
                NavHost(
                    navController = sheetNav,
                    startDestination = SHEET_ROOT,
                    modifier = Modifier.fillMaxSize(),
                    // Deslizado lateral (push) entre pantallas del sheet, como iOS.
                    enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) },
                    exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) },
                    popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) },
                    popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) }
                ) {
                    // Detalle de escuela: ahora vive en el overlay (con la barra
                    // de pestañas visible), no como pantalla completa aparte.
                    composable(
                        route = Routes.SCHOOL_DETAIL,
                        arguments = listOf(
                            navArgument("schoolId") { type = NavType.StringType },
                            navArgument("via") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("viaId") { type = NavType.StringType; nullable = true; defaultValue = null }
                        )
                    ) { entry ->
                        val schoolId = entry.arguments?.getString("schoolId") ?: ""
                        SchoolDetailScreen(
                            onBack = popSheetOrDismiss,
                            // El editor de topos SÍ es pantalla completa (sin barra):
                            // navegamos en el navController principal sin cerrar el
                            // overlay, para volver al detalle al terminar.
                            onOpenBlock = { blockId -> navController.navigate(Routes.topoEditor(blockId)) },
                            onMyProposals = { sheetNav.navigate(Routes.MY_SUBMISSIONS) },
                            onDayClick = { idx -> sheetNav.navigate(Routes.dayDetail(schoolId, idx)) }
                        )
                    }
                    composable(
                        SHEET_ROOT,
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) { Box(Modifier.fillMaxSize()) }

                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            onBack = popSheetOrDismiss,
                            onEdit = { sheetNav.navigate(Routes.EDIT_PROFILE) },
                            onSubmissions = { sheetNav.navigate(Routes.MY_SUBMISSIONS) },
                            onAdmin = { openFullScreen(Routes.ADMIN) },
                            onSavedSchools = { sheetNav.navigate(Routes.SAVED_SCHOOLS) },
                            onWeekendAlert = { sheetNav.navigate(Routes.WEEKEND_ALERT) },
                            onOpenFollowers = {
                                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                                    sheetNav.navigate(Routes.followList(uid, "followers"))
                                }
                            },
                            onOpenFollowing = {
                                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                                    sheetNav.navigate(Routes.followList(uid, "following"))
                                }
                            },
                            onOpenFollowRequests = { sheetNav.navigate(Routes.FOLLOW_REQUESTS) },
                            onOpenSchoolEntries = { schoolName -> sheetNav.navigate(Routes.journalSectors(schoolName)) },
                            onOpenBoulders = { sheetNav.navigate(Routes.journalEntries("discipline:BOULDER")) },
                            onOpenRoutes = { sheetNav.navigate(Routes.journalEntries("discipline:ROUTE")) },
                            onOpenAllSchools = { sheetNav.navigate(Routes.journalSchools(null)) },
                            onOpenMaxGrade = { sheetNav.navigate(Routes.journalEntries("grade-max")) },
                            onOpenProjects = { sheetNav.navigate(Routes.projects(null)) },
                            onOpenMyPosts = { sheetNav.navigate(Routes.MY_POSTS) }
                        )
                    }
                    // "Mis publicaciones": feed propio en pantalla dedicada.
                    composable(Routes.MY_POSTS) {
                        com.meteomontana.android.ui.screens.community.MyPostsScreen(
                            onBack = popSheetOrDismiss,
                            onOpenUser = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) },
                            onOpenSchool = { schoolId, lineId, lineName ->
                                sheetNav.navigate(Routes.schoolDetail(schoolId, via = lineName, viaId = lineId))
                            }
                        )
                    }
                    composable(Routes.EDIT_PROFILE) {
                        EditProfileScreen(onBack = popSheetOrDismiss)
                    }
                    composable(Routes.MY_SUBMISSIONS) {
                        MySubmissionsScreen(onBack = popSheetOrDismiss)
                    }
                    composable(Routes.SUBMIT_SCHOOL) {
                        SubmitSchoolScreen(onBack = popSheetOrDismiss)
                    }
                    composable(Routes.SAVED_SCHOOLS) {
                        SavedSchoolsScreen(
                            onBack = popSheetOrDismiss,
                            onOpen = { id -> sheetNav.navigate(Routes.schoolDetail(id)) }
                        )
                    }
                    composable(Routes.WEEKEND_ALERT) {
                        com.meteomontana.android.ui.screens.profile.WeekendAlertScreen(onBack = popSheetOrDismiss)
                    }
                    composable(Routes.COMPARE) {
                        com.meteomontana.android.ui.screens.compare.CompareScreen(
                            onBack = popSheetOrDismiss,
                            onSchoolDetail = { id -> sheetNav.navigate(Routes.schoolDetail(id)) }
                        )
                    }

                    composable(
                        route = Routes.DAY_DETAIL,
                        arguments = listOf(
                            navArgument("schoolId") { type = NavType.StringType },
                            navArgument("dayIndex") { type = NavType.StringType }
                        )
                    ) { DayDetailScreen(onBack = popSheetOrDismiss) }
                    composable(
                        route = Routes.DAY_DETAIL_BY_LOCATION,
                        arguments = listOf(
                            navArgument("lat") { type = NavType.StringType },
                            navArgument("lon") { type = NavType.StringType },
                            navArgument("dayIndex") { type = NavType.StringType }
                        )
                    ) { DayDetailScreen(onBack = popSheetOrDismiss) }

                    composable(
                        route = Routes.JOURNAL_ENTRIES,
                        arguments = listOf(
                            navArgument("filter") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null }
                        )
                    ) {
                        JournalEntriesScreen(
                            onBack = popSheetOrDismiss,
                            onOpenSchool = { id, via, viaId -> sheetNav.navigate(Routes.schoolDetail(id, via, viaId)) }
                        )
                    }
                    composable(
                        route = Routes.JOURNAL_SCHOOLS,
                        arguments = listOf(navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { entry ->
                        val uid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() }
                        JournalSchoolsScreen(
                            onBack = popSheetOrDismiss,
                            // Antes iba directo al listado plano; ahora pasa por
                            // "sectores" (JOURNAL_SECTORS) para no mezclar todos los
                            // bloques/vías de la escuela de golpe.
                            onSchoolClick = { schoolName ->
                                sheetNav.navigate(Routes.journalSectors(schoolName, uid))
                            }
                        )
                    }

                    composable(
                        route = Routes.JOURNAL_SECTORS,
                        arguments = listOf(
                            navArgument("school") { type = NavType.StringType; defaultValue = "" },
                            navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null }
                        )
                    ) { entry ->
                        val uid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() }
                        JournalSectorsScreen(
                            onBack = popSheetOrDismiss,
                            onSectorClick = { schoolName, sectorName ->
                                sheetNav.navigate(
                                    Routes.journalEntries(
                                        filter = "school:$schoolName|sector:$sectorName",
                                        uid = uid
                                    )
                                )
                            },
                            onOpenSchool = { id, via, viaId -> sheetNav.navigate(Routes.schoolDetail(id, via, viaId)) }
                        )
                    }

                    composable(
                        route = Routes.PROJECTS,
                        arguments = listOf(navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { entry ->
                        val uid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() }
                        ProjectsScreen(
                            onBack = popSheetOrDismiss,
                            onOpenBoulders = { sheetNav.navigate(Routes.journalEntries(filter = "project:BOULDER", uid = uid)) },
                            onOpenRoutes = { sheetNav.navigate(Routes.journalEntries(filter = "project:ROUTE", uid = uid)) }
                        )
                    }

                    composable(
                        route = Routes.PUBLIC_PROFILE,
                        arguments = listOf(navArgument("uid") { type = NavType.StringType })
                    ) {
                        PublicProfileScreen(
                            onBack = popSheetOrDismiss,
                            onFollowersClick = { uid -> sheetNav.navigate(Routes.followList(uid, "followers")) },
                            onFollowingClick = { uid -> sheetNav.navigate(Routes.followList(uid, "following")) },
                            onOpenChat = { uid -> sheetNav.navigate(Routes.chat(uid)) },
                            onOpenBoulders = { uid -> sheetNav.navigate(Routes.journalEntries(filter = "discipline:BOULDER", uid = uid)) },
                            onOpenRoutes = { uid -> sheetNav.navigate(Routes.journalEntries(filter = "discipline:ROUTE", uid = uid)) },
                            onOpenMaxGrade = { uid -> sheetNav.navigate(Routes.journalEntries(filter = "grade-max", uid = uid)) },
                            onOpenSchools = { uid -> sheetNav.navigate(Routes.journalSchools(uid)) },
                            onOpenSchoolEntries = { uid, schoolName -> sheetNav.navigate(Routes.journalSectors(schoolName, uid)) },
                            onOpenProjects = { uid -> sheetNav.navigate(Routes.projects(uid)) },
                            // Sección Publicaciones (feed) del perfil:
                            onOpenUserProfile = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) },
                            onOpenFeedSchool = { schoolId, lineId, lineName ->
                                sheetNav.navigate(Routes.schoolDetail(schoolId, via = lineName, viaId = lineId))
                            }
                        )
                    }
                    composable(
                        route = Routes.FOLLOW_LIST,
                        arguments = listOf(
                            navArgument("uid") { type = NavType.StringType },
                            navArgument("mode") { type = NavType.StringType }
                        )
                    ) {
                        FollowListScreen(
                            onBack = popSheetOrDismiss,
                            onUserClick = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) }
                        )
                    }
                    composable(Routes.FOLLOW_REQUESTS) {
                        FollowRequestsScreen(
                            onBack = popSheetOrDismiss,
                            onUserClick = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) }
                        )
                    }

                    composable(Routes.CHAT_LIST) {
                        ChatListScreen(
                            onBack = popSheetOrDismiss,
                            onOpenChat = { uid -> sheetNav.navigate(Routes.chat(uid)) },
                            onOpenGroup = { convId -> sheetNav.navigate(Routes.groupChat(convId)) },
                            onNewGroup = { sheetNav.navigate(Routes.NEW_GROUP) }
                        )
                    }
                    composable(
                        route = Routes.CHAT,
                        arguments = listOf(navArgument("uid") { type = NavType.StringType })
                    ) {
                        ChatScreen(
                            onBack = popSheetOrDismiss,
                            onOpenProfile = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) },
                            bottomInset = bottomInset
                        )
                    }
                    composable(Routes.NEW_GROUP) {
                        NewGroupScreen(
                            onBack = popSheetOrDismiss,
                            onCreated = { convId ->
                                sheetNav.popBackStack()
                                sheetNav.navigate(Routes.groupChat(convId))
                            }
                        )
                    }
                    composable(
                        route = Routes.GROUP_CHAT,
                        arguments = listOf(navArgument("convId") { type = NavType.StringType })
                    ) {
                        GroupChatScreen(
                            onBack = popSheetOrDismiss,
                            onOpenMeetup = { meetupId -> sheetNav.navigate(Routes.meetupDetail(meetupId)) },
                            bottomInset = bottomInset
                        )
                    }

                    composable(Routes.NOTIFICATIONS) {
                        NotificationsScreen(
                            onBack = popSheetOrDismiss,
                            onOpenUser = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) },
                            onOpenSchool = { id -> sheetNav.navigate(Routes.schoolDetail(id)) },
                            onOpenSubmissions = { sheetNav.navigate(Routes.MY_SUBMISSIONS) },
                            onOpenChat = { uid -> sheetNav.navigate(Routes.chat(uid)) },
                            onOpenFollowRequests = { sheetNav.navigate(Routes.FOLLOW_REQUESTS) },
                            onOpenFeedPost = { postId -> sheetNav.navigate(Routes.feedPost(postId)) }
                        )
                    }
                    // Detalle de un post del feed Comunidad (push/campanita).
                    composable(
                        route = Routes.FEED_POST,
                        arguments = listOf(navArgument("postId") { type = NavType.StringType })
                    ) {
                        com.meteomontana.android.ui.screens.community.FeedPostDetailScreen(
                            onBack = popSheetOrDismiss,
                            onOpenUser = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) },
                            onOpenSchool = { schoolId, lineId, lineName ->
                                sheetNav.navigate(Routes.schoolDetail(schoolId, via = lineName, viaId = lineId))
                            },
                            bottomInset = bottomInset
                        )
                    }
                    composable(Routes.SEARCH_USERS) {
                        SearchUsersScreen(
                            onBack = popSheetOrDismiss,
                            onUserClick = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) }
                        )
                    }
                    // ── Quedadas (sheets) ──
                    composable(
                        route = Routes.MEETUP_DETAIL,
                        arguments = listOf(navArgument("meetupId") { type = NavType.StringType })
                    ) { entry ->
                        val meetupId = entry.arguments?.getString("meetupId") ?: ""
                        MeetupDetailScreen(
                            meetupId = meetupId,
                            onBack = popSheetOrDismiss,
                            onOpenChat = { convId -> sheetNav.navigate(Routes.groupChat(convId)) },
                            onOpenSchool = { id -> sheetNav.navigate(Routes.schoolDetail(id)) },
                            onOpenProfile = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) }
                        )
                    }
                    composable(Routes.CREATE_MEETUP) {
                        CreateMeetupScreen(
                            onBack = popSheetOrDismiss,
                            onCreated = { id ->
                                sheetNav.popBackStack()
                                sheetNav.navigate(Routes.meetupDetail(id))
                            }
                        )
                    }
                    composable(Routes.MEETUP_ALERT) {
                        MeetupAlertScreen(onBack = popSheetOrDismiss)
                    }
                }
}
