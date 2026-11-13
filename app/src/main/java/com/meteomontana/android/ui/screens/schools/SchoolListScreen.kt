package com.meteomontana.android.ui.screens.schools

import com.meteomontana.android.ui.components.dismissKeyboardOnScroll
import androidx.compose.ui.platform.testTag

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonSchoolRow
import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.ui.theme.terraFillColor

import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.ui.components.SchoolListItem
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.TerraBg
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import kotlinx.coroutines.flow.first

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SchoolListScreen(
    /**
     * Sube contador cada vez que se pulsa la pestaña ESTANDO ya en ella.
     *
     * Es el gesto de siempre en un movil: volver a tocar la pestaña activa
     * lleva arriba del todo. Con la lista a media altura y 191 escuelas, subir
     * a mano es un arrastre largo.
     */
    volverArribaSignal: Int = 0,
    onSchoolClick: (String) -> Unit,
    onProfileClick: () -> Unit = {},
    onSubmitSchool: () -> Unit = {},
    onSearchUsers: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onChats: () -> Unit = {},
    onDonate: () -> Unit = {},
    onCompare: (List<String>) -> Unit = {},
    /** Avisa a la pantalla principal de si se está enseñando la bienvenida (debe tapar la barra de pestañas y la burbuja). */
    onOnboardingActive: (Boolean) -> Unit = {},
    /** Resultado del buscador global de vías/bloques → abre la escuela en esa vía. */
    onViaHit: (schoolId: String, viaId: String?, viaName: String?) -> Unit = { s, _, _ -> onSchoolClick(s) },
    viewModel: SchoolListViewModel = hiltViewModel(),
    explore: ExploreLinesViewModel = hiltViewModel()
) {
    // "Enviar piedra": el selector de fotos está abierto.
    var eligiendoFoto by remember { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (eligiendoFoto) {
        SubmitBlockPhotoFlow(
            // El catálogo ENTERO, no lo que el filtro deja ver: la escuela de la
            // foto puede estar fuera del filtro activo.
            schools = viewModel.catalogoCompleto(),
            seedStore = viewModel.photoSeed,
            onOpenSchool = { id -> eligiendoFoto = false; onSchoolClick(id) },
            onDismiss = { eligiendoFoto = false }
        )
    }
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val unread by viewModel.unreadCount.collectAsStateWithLifecycle()
    val scores by viewModel.scores.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val compareSelection by viewModel.compareSelection.collectAsStateWithLifecycle()
    val selectedDays by viewModel.selectedDays.collectAsStateWithLifecycle()
    val rangeScores by viewModel.rangeScores.collectAsStateWithLifecycle()
    val chatUnread by viewModel.chatUnread.collectAsStateWithLifecycle()
    val viaHits by viewModel.viaHits.collectAsStateWithLifecycle()
    var mapExpanded by remember { mutableStateOf(false) }
    // Filtros y días PLEGADOS por defecto ("demasiadas cosas a la vista", 2026-10-14): se abren al tocar su chip.
    var filtersOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var daysOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    // ── Pestaña "Vías/Bloques" (docs/BLOCK_SEARCH_DESIGN.md §4.1b) ──
    val exploreTab by explore.tab.collectAsStateWithLifecycle()
    // La estrella FAVS se recuerda encendida entre aperturas.
    val compareCtx = androidx.compose.ui.platform.LocalContext.current
    var favoritesCompare by remember {
        androidx.compose.runtime.mutableStateOf(
            compareCtx.getSharedPreferences("schools", android.content.Context.MODE_PRIVATE).getBoolean("favoritesCompare", false))
    }
    val gradeMin by explore.gradeMin.collectAsStateWithLifecycle()
    val gradeMax by explore.gradeMax.collectAsStateWithLifecycle()
    val exploreOrientations by explore.orientations.collectAsStateWithLifecycle()
    val exploreSortBy by explore.sortBy.collectAsStateWithLifecycle()
    val exploreHeight by explore.height.collectAsStateWithLifecycle()
    val exploreHeightSource by explore.heightSource.collectAsStateWithLifecycle()
    val exploreGrouped by explore.grouped.collectAsStateWithLifecycle()
    val exploreHits by explore.hits.collectAsStateWithLifecycle()
    val exploreHitsSorted by explore.sortedHits.collectAsStateWithLifecycle()
    val exploreGroups by explore.groups.collectAsStateWithLifecycle()
    // Estilo del catálogo por escuela: para el icono vía/bloque de cabeceras y filas.
    val schoolStyleById = remember(exploreHits) {
        viewModel.catalogoCompleto().associate { it.id to it.style }
    }
    val exploreLoading by explore.loading.collectAsStateWithLifecycle()
    // Lo que «Vías/Bloques» toma de la lista: filtros comunes, ubicación y la nota de cada escuela (hoy o del tramo).
    LaunchedEffect(filters.style, filters.rockTypes, filters.maxDistanceKm, userLocation, scores, rangeScores, selectedDays) {
        explore.setContext(ExploreContext(
            style = filters.style, rockTypes = filters.rockTypes, maxDistanceKm = filters.maxDistanceKm,
            lat = userLocation?.lat ?: 40.4168, lon = userLocation?.lon ?: -3.7038,
            scoreBySchool = if (selectedDays.isNotEmpty()) rangeScores.mapValues { it.value.combinedScore ?: -1 }
                            else scores.mapValues { it.value.todayScore ?: -1 }
        ))
    }
    // Grupos de escuela DESPLEGADOS — empiezan todos plegados (Álvaro,
    // 2026-10-01: "que puedas verlos todos rápido"). No persiste.
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }

    // ¿Hay PERMISO de ubicación? Se sabe al instante. El aviso "Sin ubicación"
    // y el icono del mapa dependen de esto, no de `userLocation == null`: al
    // abrir la app la posición tarda un momento y el aviso salía y desaparecía
    // en cada arranque (Álvaro, 2026-10-02).
    val ctxPermiso = androidx.compose.ui.platform.LocalContext.current
    fun tienePermisoUbicacion() = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
    ).any {
        androidx.core.content.ContextCompat.checkSelfPermission(ctxPermiso, it) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    var permisoUbicacion by remember { mutableStateOf(tienePermisoUbicacion()) }

    // Refresca el contador de no leídas al VOLVER a esta pantalla (p.ej. tras
    // ver y salir de la bandeja de notificaciones) → el badge se actualiza.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUnread()
                permisoUbicacion = tienePermisoUbicacion()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Pide permiso de ubicación al abrir la pantalla — al concederlo el VM
    // recarga ordenado por mejor score + filtra 50 km desde la posición real.
    // En la primera apertura el permiso se pide al FINAL del onboarding,
    // después de explicar para qué sirve.
    // El detalle se abre como overlay DENTRO de esta misma pantalla, así que
    // el buscador conserva el foco y el teclado se queda encima de la ficha.
    // Todo lo que navegue desde aquí lo cierra primero.
    val closeKeyboard = com.meteomontana.android.ui.components.rememberKeyboardDismisser()
    val openSchool: (String) -> Unit = { id -> closeKeyboard(); onSchoolClick(id) }
    val openVia: (String, String?, String?) -> Unit = { schoolId, viaId, viaName ->
        closeKeyboard(); onViaHit(schoolId, viaId, viaName)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var showOnboarding by remember {
        mutableStateOf(!com.meteomontana.android.ui.onboarding.isOnboardingDone(context))
    }
    // FINE + COARSE: Android 12+ muestra el selector "precisa/aproximada".
    // Antes solo se pedía COARSE → el GPS nunca se activaba y el punto azul
    // caía a 500 m-1 km en el monte.
    val locationPerms = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    // Tras el diálogo: concedido → recarga con la posición; denegado y SIN
    // opción de volver a preguntar (denegado del todo) → Ajustes, que es el
    // único sitio donde se arregla. Es lo que hace iOS (Álvaro, 2026-10-01).
    var pidiendoUbicacionManual by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permisoUbicacion = tienePermisoUbicacion()
        if (grants.values.any { it }) viewModel.onLocationGranted()
        else if (pidiendoUbicacionManual) {
            val activity = context as? android.app.Activity
            val puedePreguntar = activity != null && locationPerms.any {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
            if (!puedePreguntar) {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null)
                    )
                )
            }
        }
        pidiendoUbicacionManual = false
    }
    val onRequestLocation: () -> Unit = {
        pidiendoUbicacionManual = true
        permLauncher.launch(locationPerms)
    }
    LaunchedEffect(Unit) {
        if (showOnboarding) return@LaunchedEffect
        // Espera a que el diálogo de notificaciones esté respondido (Android
        // solo muestra un permiso a la vez) y pide ubicación. Se pide aunque
        // ya haya APROXIMADA concedida: así el sistema ofrece una vez la
        // mejora a PRECISA a quien venía de versiones viejas.
        com.meteomontana.android.PermissionsGate.open.first { it }
        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!fineGranted) permLauncher.launch(locationPerms)
        else viewModel.onLocationGranted()
    }

    androidx.compose.runtime.LaunchedEffect(showOnboarding) { onOnboardingActive(showOnboarding) }
    if (showOnboarding) {
        com.meteomontana.android.ui.onboarding.OnboardingOverlay(
            onFinish = {
                com.meteomontana.android.ui.onboarding.markOnboardingDone(context)
                showOnboarding = false
                permLauncher.launch(locationPerms)
            }
        )
        return
    }

    // Tras el tutorial: si el perfil aún no tiene username, obligar a elegirlo
    // (solo sale si username == null en el servidor; reinstalar no lo re-muestra).
    com.meteomontana.android.ui.onboarding.UsernameGate()

    // Primera vez tras crear la cuenta: la guía de Escuelas sale sola, una vez (saltable) y solo si las guías están
    // encendidas. El resto de guías son opcionales (el "? AYUDA" de cada pantalla, que parpadea las primeras visitas).
    val guides = com.meteomontana.android.ui.guide.LocalGuideController.current
    val listLoaded = (state as? SchoolListUiState.Success)?.schools?.isNotEmpty() == true
    LaunchedEffect(listLoaded) {
        if (!listLoaded) return@LaunchedEffect
        val prefs = com.meteomontana.android.ui.guide.SharedGuidePrefs(context)
        if (!prefs.bool(com.meteomontana.android.ui.guide.GuideController.KEY_PENDING_SCHOOLS, false)) return@LaunchedEffect
        prefs.setBool(com.meteomontana.android.ui.guide.GuideController.KEY_PENDING_SCHOOLS, false)
        if (!guides.enabled) return@LaunchedEffect
        kotlinx.coroutines.delay(1200)
        guides.start("schools")
    }

    // M2: cámara del mapa de escuelas, recordada AQUÍ (fuera del LazyColumn) para
    // que sobreviva al reciclado del item del mapa al scrollear.
    // M2/M3: estado del mapa (cámara + ids encuadrados) recordado AQUÍ, fuera del
    // LazyColumn, para que sobreviva al reciclado del item del mapa al scrollear.
    val mapState = rememberSchoolsMapState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Fila de iconos top + header ("Escuelas" · [+ Aportar]) FUERA del
        // PullToRefreshBox a propósito: dentro del área de arrastre, el gesto
        // de refrescar competía por el toque con el botón APORTAR y a veces se
        // quedaba con él (toque "fantasma" — el clic se perdía en silencio,
        // solo colaba tocando la esquina superior derecha del botón, donde el
        // arbitraje de gestos de Compose apenas tiene margen para dudar).
        // Fijos aquí, nunca compiten con el pull-to-refresh de la lista.
        SchoolsHeader(
            unread = unread,
            chatUnread = chatUnread,
            onSearchUsers = onSearchUsers,
            onChats = onChats,
            onNotifications = onNotifications,
            onSubmitSchool = onSubmitSchool,
            onSubmitBlockPhoto = { eligiendoFoto = true }
        )

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize()
    ) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.runtime.LaunchedEffect(volverArribaSignal) {
            if (volverArribaSignal > 0) listState.animateScrollToItem(0)
        }
        LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().dismissKeyboardOnScroll(),
        // Hueco al final para que la ultima escuela no quede debajo de la capsula.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current)
    ) {

            // Buscador ÚNICO estilo Spotlight: escuelas Y vías/bloques a la vez.
            // El placeholder anuncia que busca ambas cosas, y al escribir salen
            // las dos secciones (aunque una esté vacía) → se aprende solo.
            item {
                Box(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    com.meteomontana.android.ui.components.CumbreSearchField(
                        value = filters.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = stringResource(R.string.school_list_screen_busca_escuelas_vias_y),
                        onSearch = { closeKeyboard() }
                    )
                }
            }

            // Resultados del buscador ÚNICO en DOS secciones (estilo Spotlight):
            // ESCUELAS (top 5 del catálogo, acceso directo) y VÍAS Y BLOQUES
            // (buscador global con mini-topo). Las cabeceras salen SIEMPRE al
            // escribir — así se aprende que el campo busca ambas cosas.
            if (filters.query.trim().length >= 2) {
                item {
                    val schoolMatches = (state as? SchoolListUiState.Success)
                        ?.schools.orEmpty().take(5)
                    SpotlightResults(schoolMatches, viaHits, openSchool, openVia)
                }
            }

            // El mapa, cuando está abierto, va ENCIMA del selector Escuelas / Vías-Bloques (como en iOS).
            if (mapExpanded) {
                item {
                    val successState = state as? SchoolListUiState.Success
                    SchoolsMapPanel(
                        schools = successState?.schools.orEmpty(),
                        scoresById = scores.mapValues { it.value.todayScore },
                        userLat = userLocation?.lat,
                        userLon = userLocation?.lon,
                        expanded = mapExpanded,
                        onSchoolDetail = openSchool,
                        mapState = mapState,
                        distanceKm = filters.maxDistanceKm,
                        onDistanceChange = viewModel::setDistance,
                        style = filters.style,
                        onStyleChange = viewModel::setStyle,
                        onRequestLocation = onRequestLocation,
                        locationPermission = permisoUbicacion
                    )
                }
            }

            item { Spacer(Modifier.height(Spacing.xs)) }
            item { ExploreTabSwitcher(exploreTab, explore::setTab) }
            item {
                Spacer(Modifier.height(Spacing.sm))
                MapToggleBar(
                    expanded = mapExpanded,
                    onToggle = { mapExpanded = !mapExpanded },
                    modifier = Modifier.guideTarget("schools.map")
                )
            }

            // Filtros PLEGADOS: una fila con lo aplicado y el chip de días; se despliegan al tocarla.
            item {
                val schoolCount = (state as? SchoolListUiState.Success)?.schools?.size
                val countText = schoolCount?.let { stringResource(R.string.schools_count, it) }
                val letters = remember { CalendarLabels.daysShortMonFirst().map { it.uppercase() } }
                val dayLabel = dayRangeLabel(
                    selected = selectedDays,
                    todayIso = java.time.LocalDate.now().dayOfWeek.value,
                    dayLetters = letters,
                    todayLabel = stringResource(R.string.w_today_caps),
                    nDaysLabel = { n -> context.getString(R.string.schools_n_days, n) }
                )
                Column(Modifier.padding(top = Spacing.sm)) {
                    FiltersToggleRow(
                        open = filtersOpen,
                        activeCount = activeFilterCount(filters, exploreTab, gradeMin, gradeMax, exploreOrientations, exploreSortBy) +
                            (if (exploreTab == ExploreTab.Blocks && exploreHeight != null) 1 else 0),
                        countText = countText,
                        dayLabel = dayLabel,
                        daysOpen = daysOpen,
                        onToggleOpen = { filtersOpen = !filtersOpen },
                        onToggleDays = { daysOpen = !daysOpen },
                        onClear = { viewModel.clearAllFilters(); explore.clearAll() },
                        favsOn = if (exploreTab == ExploreTab.Schools) favoritesCompare else null,
                        onToggleFavs = { favoritesCompare = !favoritesCompare; compareCtx.getSharedPreferences("schools", android.content.Context.MODE_PRIVATE).edit().putBoolean("favoritesCompare", favoritesCompare).apply() },
                        modifier = Modifier.guideTarget("schools.filters")
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    LocationWarning(hasLocation = permisoUbicacion, onRequestLocation = onRequestLocation)
                }
            }
            if (filtersOpen) {
                item {
                    SchoolFiltersBar(
                        filters = filters,
                        onDistance      = viewModel::setDistance,
                        onStyle         = viewModel::setStyle,
                        onRockToggle    = viewModel::toggleRock,
                        onOnlyFavorites = viewModel::setOnlyFavorites,
                        onOnlySavedOffline = viewModel::setOnlySavedOffline,
                        onSort          = viewModel::setSort,
                        onClearRocks    = viewModel::clearRocks,
                        exploreTab = exploreTab,
                        gradeMin = gradeMin,
                        gradeMax = gradeMax,
                        onGradeMin = explore::setGradeMin,
                        onGradeMax = explore::setGradeMax,
                        orientations = exploreOrientations,
                        onToggleOrientation = explore::toggleOrientation,
                        exploreSortBy = exploreSortBy,
                        onExploreSort = explore::setSortBy,
                        exploreHeight = exploreHeight,
                        onExploreHeight = explore::setHeight,
                        exploreHeightSource = exploreHeightSource,
                        onExploreHeightSource = explore::setHeightSource
                    )
                }
            }

            // Selector de días: elige hasta 5 días concretos → la lista se reordena por las mejores condiciones de
            // ESE tramo. Plegado tras el chip de días (HOY ⌄).
            if (daysOpen) {
                item {
                    DaySelectorRow(
                        selectedDays = selectedDays,
                        onToggleDay = viewModel::toggleDay
                    )
                }
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp) }

            // Comparar tus favoritas (estrella FAVS): anillo con la nota de hoy y una tira por día.
            if (favoritesCompare && exploreTab == ExploreTab.Schools) {
                item(key = "favorites-compare") {
                    FavoritesCompareCard(onOpen = openSchool, modifier = Modifier.padding(horizontal = 12.dp).padding(top = 8.dp))
                }
            }

            // Hint de comparar — justo antes de la lista de escuelas
            item {
                com.meteomontana.android.ui.components.FirstTimeHint(
                    hintKey = "schools_compare",
                    text = stringResource(R.string.school_list_screen_v3_manten_pulsada_una_escuela_para),
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }

            if (exploreTab == ExploreTab.Blocks) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (exploreLoading) stringResource(R.string.explore_searching)
                            else stringResource(R.string.explore_results_found, exploreHits.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = explore::toggleGrouped) {
                            Icon(
                                if (exploreGrouped) Icons.Outlined.ViewAgenda
                                else Icons.Outlined.GridView,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (exploreLoading && exploreHits.isEmpty()) {
                    item { SkeletonList(count = 4) { SkeletonSchoolRow() } }
                } else if (exploreHits.isEmpty()) {
                    item {
                        EmptyResults(onClear = explore::clearCriteria)
                    }
                } else if (exploreGrouped) {
                    exploreGroups.forEach { group ->
                        item(key = "group-${group.schoolId}") {
                            ExploreGroupHeader(
                                group = group,
                                kind = com.meteomontana.android.domain.util.SchoolKind.from(schoolStyleById[group.schoolId]),
                                score = if (selectedDays.isNotEmpty()) rangeScores[group.schoolId]?.combinedScore
                                        else scores[group.schoolId]?.todayScore,
                                dry = scores[group.schoolId]?.dryRock,
                                range = if (selectedDays.isNotEmpty()) rangeScores[group.schoolId] else null,
                                expanded = group.schoolId in expandedGroups,
                                onToggle = {
                                    expandedGroups = if (group.schoolId in expandedGroups)
                                        expandedGroups - group.schoolId else expandedGroups + group.schoolId
                                }
                            )
                        }
                        if (group.schoolId in expandedGroups) {
                            items(group.hits, key = { "line-${it.lineId}-${it.blockId}" }) { h ->
                                Column {
                                    ExploreLineRow(h, kind = exploreRowKind(h.discipline, filters.style.apiValue, schoolStyleById[h.schoolId]), showSchool = false, distanceKm = h.lat?.let { la -> h.lon?.let { lo -> viewModel.distanceTo(la, lo).toInt() } }, onClick = { openVia(h.schoolId, h.lineId, h.lineName ?: h.blockName) })
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                                }
                            }
                        }
                    }
                } else {
                    items(exploreHitsSorted, key = { "flat-${it.lineId}-${it.blockId}" }) { h ->
                        Column {
                            ExploreLineRow(h, kind = exploreRowKind(h.discipline, filters.style.apiValue, schoolStyleById[h.schoolId]), showSchool = true, distanceKm = h.lat?.let { la -> h.lon?.let { lo -> viewModel.distanceTo(la, lo).toInt() } }, onClick = { openVia(h.schoolId, h.lineId, h.lineName ?: h.blockName) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                        }
                    }
                }
            } else
            when (val s = state) {
                is SchoolListUiState.Loading -> item { SkeletonList(count = 6) { SkeletonSchoolRow() } }
                is SchoolListUiState.Error   -> item { ErrorRow(s.message, onRetry = viewModel::refresh) }
                is SchoolListUiState.Success -> {
                    itemsIndexed(s.schools, key = { _, it -> it.id }) { index, school ->
                        val score = scores[school.id]
                        // animateItem(): cuando llegan (solo deslizamiento, sin fundidos) los scores y la lista se
                        // re-ordena, las cards se deslizan a su sitio en vez de
                        // teletransportarse.
                        // Tarjetas separadas (propuesta A, Álvaro 2026-10-19): sin divisor, con hueco entre ellas.
                        Column(
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                                .padding(horizontal = 12.dp)
                                .padding(top = if (index == 0) 12.dp else 0.dp, bottom = 10.dp)
                                .guideTarget("schools.score", enabled = index == 0)
                                .testTag("school_card")
                        ) {
                            SchoolListItem(
                                school = school,
                                todayScore = score?.todayScore,
                                hourlyScores = score?.hourlyScores,
                                distanceKm = viewModel.distanceTo(school.lat, school.lon),
                                dry = score?.dryRock,
                                range = if (selectedDays.isNotEmpty()) rangeScores[school.id] else null,
                                isFavorite = school.id in favoriteIds,
                                selectedForCompare = school.id in compareSelection,
                                onClick = {
                                    // En modo selección el tap también selecciona.
                                    if (compareSelection.isNotEmpty()) viewModel.toggleCompare(school.id)
                                    else openSchool(school.id)
                                },
                                onLongClick = { viewModel.toggleCompare(school.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(school.id) }
                            )
                        }
                    }
                    if (s.schools.isEmpty()) {
                        item {
                            EmptyResults(onClear = viewModel::clearFilters)
                        }
                    }
                }
            }
        }

        // Barra de comparación (aparece al seleccionar con long-press)
        if (compareSelection.isNotEmpty()) {
            CompareBar(compareSelection, viewModel::clearCompare, onCompare, Modifier.align(Alignment.BottomCenter))
        }
    }
    }
}
