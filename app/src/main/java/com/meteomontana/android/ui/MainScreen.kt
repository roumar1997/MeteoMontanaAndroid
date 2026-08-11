@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import com.meteomontana.android.ui.components.CumbreCapsuleShape
import com.meteomontana.android.ui.components.LocalChromeTreatment
import com.meteomontana.android.ui.components.cumbreBackdrop
import com.meteomontana.android.ui.components.cumbreChromeSurface
import com.meteomontana.android.ui.theme.ChromeTreatment
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding

/** Ruta raíz (vacía) del NavHost interno del sheet: el sheet se abre vacío y se
 *  navega al destino real; al volver a ella se cierra la tarjeta. */
internal const val SHEET_ROOT = "sheet_root"

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
                // Push de propuesta nueva (a admins) → panel de admin (abre en PROPUESTAS).
                "admin_contributions" -> openFullScreen(Routes.ADMIN)
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

    // La cápsula de tabs se dibuja en el slot bottomBar, SIEMPRE encima del
    // overlay (perfil, chats, detalle…). En los chats hay un campo de texto
    // abajo y la cápsula lo tapaba (bug solo Android; en iOS el chat va en
    // .sheet y cubre la barra). Ocultamos la cápsula cuando el overlay muestra
    // un chat 1-a-1 o de grupo → el Scaffold deja de reservar su espacio y el
    // bottomInset del chat pasa a ~0 (campo pegado abajo, sobre el teclado).
    val sheetEntry by sheetNav.currentBackStackEntryAsState()
    val sheetShowsChat = sheetVisible &&
        sheetEntry?.destination?.route in setOf(Routes.CHAT, Routes.GROUP_CHAT)
    val showBottomBar = currentRoute == TABS_HOST && !sheetShowsChat

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                // Cápsula flotante (estilo iOS) en vez de la barra plana a todo el ancho.
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        // Márgenes laterales: la cápsula ocupa el ancho MENOS
                        // esto, igual que en iOS. Antes se encogía a lo que
                        // ocupaban los iconos.
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            // A todo el ancho disponible: es lo que permite
                            // repartir las cinco pestañas por igual con weight,
                            // sea cual sea el móvil.
                            .fillMaxWidth()
                            // Fondo + borde del armazón en UNA sola llamada: según
                            // el tratamiento activo será color liso, esmerilado o
                            // esmerilado con canto de luz. La barra no sabe cuál.
                            .cumbreChromeSurface(CumbreCapsuleShape)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        mainTabs.forEach { tab ->
                            val selected = selectedTab == tab.route
                            val tint = if (selected) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            // LOS CINCO RÓTULOS SIEMPRE, como en iOS: icono arriba
                            // y nombre debajo. Antes solo se etiquetaba el activo
                            // porque en horizontal no caben cinco nombres; en
                            // vertical sí, y así sabes a dónde vas antes de pulsar.
                            //
                            // ADAPTABLE: `weight(1f)` reparte el ancho a partes
                            // iguales sea cual sea la pantalla, y el rótulo se
                            // recorta con puntos suspensivos si el móvil es muy
                            // estrecho. Ni un ancho fijo, que es lo que descuadra
                            // al cambiar de dispositivo.
                            Column(
                                modifier = Modifier
                                    .weight(1f)
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
                                    .padding(horizontal = 2.dp, vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(tab.icon, contentDescription = tab.label,
                                    tint = tint, modifier = Modifier.size(20.dp))
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tint,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // En Radar el mapa ocupa TODA la pantalla y la cápsula de tabs flota
        // encima (el player del radar ya deja hueco).
        //
        // Y con la cápsula de cristal, IGUAL en todas partes: si el contenido se
        // parase justo encima de la barra, por detrás no habría nada que
        // difuminar y el efecto no se vería. El precio es que la última fila de
        // una lista queda debajo de la cápsula — por eso el modo SÓLIDO
        // conserva el reparto de siempre y sirve de referencia para comparar.
        // Cuánto reservó el Scaffold abajo: la cápsula de pestañas MÁS la barra
        // de navegación del móvil. Las dos cosas van juntas en el mismo número,
        // y ahí estuvo el lío del 2026-08-10: al quitarlo para que el contenido
        // pasara por detrás de la cápsula, se quitaba también el suelo de la
        // barra del sistema, y el campo de escribir de comentar/chats se metía
        // debajo de los botones del móvil.
        //
        // La regla ahora: se libera SOLO para el contenido de las pestañas —lo
        // único que tiene que correr por detrás de la cápsula para que haya algo
        // que difuminar— y se le DEVUELVE explícitamente a lo que se abre encima
        // (ver `reservaAbajo` más abajo, donde se envuelve el overlay).
        val reservaAbajo = padding.calculateBottomPadding()
        val armazonConMaterial = LocalChromeTreatment.current != ChromeTreatment.SOLIDO
        val layoutDir = androidx.compose.ui.platform.LocalLayoutDirection.current
        val effectivePadding = when {
            currentRoute == TABS_HOST && selectedTab == Tab.Weather.route && weatherShowsRadar ->
                androidx.compose.foundation.layout.PaddingValues(0.dp)
            armazonConMaterial -> androidx.compose.foundation.layout.PaddingValues(
                start = padding.calculateStartPadding(layoutDir),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDir),
                bottom = 0.dp
            )
            else -> padding
        }
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize()
                .padding(effectivePadding)
                // Esto es lo que la barra lee para difuminarlo. Sin material, no
                // hace nada ni cuesta nada.
                .cumbreBackdrop()
        ) {
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

                    // Publica el hueco que las listas de las pestañas tienen que
                    // dejar al final para que su última fila no quede debajo de
                    // la cápsula. Ver LocalTabBarInset.
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.meteomontana.android.ui.components.LocalTabBarInset provides
                            (if (armazonConMaterial) reservaAbajo else 0.dp)
                    ) {

                    // La pantalla de debajo retrocede mientras hay algo abierto
                    // encima. Con el MISMO muelle que el resto del movimiento
                    // (CumbreMotion): si esto fuese con otra curva, se vería que
                    // van por su cuenta. 0.92 es suficiente para leer la
                    // profundidad sin que parezca que la pantalla se cae.
                    val escalaFondo by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (sheetVisible) 0.92f else 1f,
                        animationSpec = com.meteomontana.android.ui.theme.CumbreMotion.opacidad,
                        label = "escalaFondo"
                    )
                    val radioFondo by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (sheetVisible) 18.dp else 0.dp,
                        // Duracion fija: un muelle sin tope no termina nunca (ver CumbreMotion).
                        animationSpec = androidx.compose.animation.core.tween(280),
                        label = "radioFondo"
                    )
                    Box(Modifier.fillMaxSize()) {
                        @androidx.compose.runtime.Composable
                        fun tabContainer(route: String, content: @androidx.compose.runtime.Composable () -> Unit) {
                            if (visited[route] == true) {
                                val sel = selectedTab == route
                                Box(
                                    Modifier.fillMaxSize()
                                        .zIndex(if (sel) 1f else 0f)
                                        .graphicsLayer {
                                            alpha = if (sel) 1f else 0f
                                            // Al abrir algo encima, esta pantalla
                                            // se va al fondo: se encoge y se
                                            // redondea. Es LO que hace que en un
                                            // iPhone se sienta que hay capas y no
                                            // pantallas sueltas apiladas.
                                            // SIN escalar. Encogia la pantalla
                                            // al 92% para dar profundidad, pero
                                            // Compose NO mueve las zonas de
                                            // toque con la escala: se DIBUJA
                                            // encogido y se TOCA donde estaba.
                                            // Cuanto mas abajo el boton, mas se
                                            // separaba lo que ves de donde hay
                                            // que pulsar — la barra "OCULTAR
                                            // MAPA" quedaba a mas de 100 px del
                                            // dedo y no habia forma de cerrar
                                            // el mapa (Rodrigo, 2026-08-11;
                                            // medido: la barra decia estar en
                                            // x=45, que es justo el margen que
                                            // deja el 92%).
                                            //
                                            // El redondeo si se queda: no toca
                                            // la geometria.
                                            shape = RoundedCornerShape(radioFondo)
                                            clip = radioFondo > 0.dp
                                        }
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
                        // Keep-alive: la pestaña no se recompone al volver a ella →
                        // el perfil quedaba RANCIO tras marcar vías (solo refrescaba
                        // al reabrir la app). Recarga al hacerse visible la pestaña
                        // Y al cerrarse el overlay (diario → escuela → desmarcar →
                        // volver: la pestaña nunca dejó de estar seleccionada).
                        visible = selectedTab == Tab.Profile.route && !sheetVisible,
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
                        onOpenMyPosts = { openSheet(Routes.myPosts()) },
                        onOpenStats = { openSheet(Routes.journalStats()) }
                    )
                }
                        tabContainer(Tab.Community.route) {
                    // Feed social Comunidad (SIGUIENDO | TODOS | RANKING).
                    com.meteomontana.android.ui.screens.community.FeedScreen(
                        onOpenSchool = { schoolId, lineId, lineName, blockId ->
                            openSheet(Routes.schoolDetail(schoolId, via = lineName, viaId = lineId, blockId = blockId))
                        },
                        onOpenUser = { uid -> openSheet(Routes.publicProfile(uid)) },
                        onSearchUsers = { openSheet(Routes.SEARCH_USERS) },
                        onOpenPost = { postId -> openSheet(Routes.feedPost(postId)) },
                        visible = selectedTab == Tab.Community.route && !sheetVisible
                    )
                }
                        tabContainer(Tab.Meetups.route) {
                    MeetupsScreen(
                        onMeetupClick = { id -> openSheet(Routes.meetupDetail(id)) },
                        onOpenChat = { convId -> openSheet(Routes.groupChat(convId)) },
                        onCreateMeetup = { openSheet(Routes.CREATE_MEETUP) },
                        onOpenAlert = { openSheet(Routes.MEETUP_ALERT) },
                        // Recarga el estado de la alerta al reaparecer la pestaña sin
                        // overlay (al cerrar la hoja de alerta) → icono siempre real.
                        visible = selectedTab == Tab.Meetups.route && !sheetVisible
                    )
                }
                        // ── Overlay estilo Apple, DENTRO del contenido ──
                        // Vive encima de las pestañas (zIndex 2) pero por DEBAJO de
                        // la cápsula de tabs (slot bottomBar del Scaffold, siempre
                        // encima del contenido) → la barra queda visible y pulsable.
                        // En función aparte para evitar la ambigüedad de overload de
                        // AnimatedVisibility con el ColumnScope que lo envuelve.
                        // EL SUELO DEL OVERLAY, devuelto a mano.
                        //
                        // El contenido de las pestañas corre por detrás de la
                        // cápsula a propósito (para que haya algo que
                        // difuminar), pero lo que se abre ENCIMA no: sus
                        // pantallas llevan campos de texto pegados abajo
                        // —comentar, chats, editar perfil— y sin esta reserva
                        // acaban debajo de los botones del sistema. Es
                        // exactamente el fallo que cazó Rodrigo comentando en
                        // el feed.
                        // La franja donde flota la cápsula, tapada mientras hay
                        // algo abierto encima.
                        //
                        // El contenido de las pestañas corre por detrás de la
                        // cápsula (es lo que le da algo que difuminar), pero con
                        // una publicación abierta esa franja enseñaba el mapa
                        // del feed asomando bajo el campo de comentar. Va por
                        // debajo del overlay (1.5) y por encima de las pestañas
                        // (1); no se dibuja cuando no hay nada abierto, así que
                        // el cristal se ve entero en el uso normal.
                        if (sheetVisible && reservaAbajo > 0.dp) {
                            Box(
                                Modifier.align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(reservaAbajo)
                                    .zIndex(1.5f)
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        }
                        //
                        // El zIndex NO es decorativo: el overlay se dibujaba en
                        // el nivel 2 y las pestañas en el 1. Al meterlo dentro
                        // de esta caja, el orden pasa a decidirlo la CAJA, y sin
                        // esto se quedaba en el nivel 0 → la pestaña se pintaba
                        // encima y se veía el feed y el radar a través de la
                        // publicación.
                        Box(
                            Modifier.fillMaxSize()
                                .zIndex(2f)
                                .padding(bottom = reservaAbajo)
                        ) {
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
                            // Lo que el Box de arriba ya le reservó: las pantallas
                            // con teclado se lo descuentan al imePadding para
                            // pegarse al teclado sin dejar un hueco muerto.
                            bottomInset = reservaAbajo
                        )
                        }
                    }
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
