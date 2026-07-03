package com.meteomontana.android.ui.screens.schools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.ui.components.SchoolListItem
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.TerraBg
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SchoolListScreen(
    onSchoolClick: (String) -> Unit,
    onProfileClick: () -> Unit = {},
    onSubmitSchool: () -> Unit = {},
    onSearchUsers: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onChats: () -> Unit = {},
    onDonate: () -> Unit = {},
    onCompare: (List<String>) -> Unit = {},
    viewModel: SchoolListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val unread by viewModel.unreadCount.collectAsState()
    val scores by viewModel.scores.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val compareSelection by viewModel.compareSelection.collectAsState()
    val selectedDays by viewModel.selectedDays.collectAsState()
    val rangeScores by viewModel.rangeScores.collectAsState()
    val chatUnread by viewModel.chatUnread.collectAsState()
    var mapExpanded by remember { mutableStateOf(false) }

    // Refresca el contador de no leídas al VOLVER a esta pantalla (p.ej. tras
    // ver y salir de la bandeja de notificaciones) → el badge se actualiza.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshUnread()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Pide permiso de ubicación al abrir la pantalla — al concederlo el VM
    // recarga ordenado por mejor score + filtra 50 km desde la posición real.
    // En la primera apertura el permiso se pide al FINAL del onboarding,
    // después de explicar para qué sirve.
    val context = androidx.compose.ui.platform.LocalContext.current
    var showOnboarding by remember {
        mutableStateOf(!com.meteomontana.android.ui.onboarding.isOnboardingDone(context))
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.onLocationGranted() }
    LaunchedEffect(Unit) {
        if (!showOnboarding) permLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    if (showOnboarding) {
        com.meteomontana.android.ui.onboarding.OnboardingOverlay(onFinish = {
            com.meteomontana.android.ui.onboarding.markOnboardingDone(context)
            showOnboarding = false
            permLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        })
        return
    }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // Fila de iconos top (no existe en la PWA pero hay que mantener
            // navegación a chats/notifs/perfil). Discreta para que el header
            // siguiente sea el foco visual.
            item {
                TopIconsRow(
                    unread = unread,
                    chatUnread = chatUnread,
                    onSearchUsers = onSearchUsers,
                    onChats = onChats,
                    onNotifications = onNotifications,
                    onProfileClick = onProfileClick
                )
            }

            // Header PWA: "Escuelas" · "193 escuelas" · [+ Enviar escuela]
            item {
                HeaderEscuelas(
                    count = (state as? SchoolListUiState.Success)?.schools?.size,
                    onSubmitSchool = onSubmitSchool
                )
            }

            // Buscador
            item {
                Box(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    OutlinedTextField(
                        value = filters.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.common_search) + "…") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }

            // Hint del mapa — justo antes del toggle "VER MAPA"
            item {
                com.meteomontana.android.ui.components.FirstTimeHint(
                    hintKey = "schools_map",
                    text = "Toca \"VER MAPA\" para ver todas las escuelas en el mapa, coloreadas por su índice del día."
                )
            }

            // Mapa global "VER MAPA" — colapsable con markers de las escuelas
            // visibles (mismas que la lista de abajo: usa los filtros del VM).
            item {
                val successState = state as? SchoolListUiState.Success
                SchoolsMapPanel(
                    schools = successState?.schools.orEmpty(),
                    scoresById = scores.mapValues { it.value.todayScore },
                    userLat = userLocation?.lat,
                    userLon = userLocation?.lon,
                    expanded = mapExpanded,
                    onToggle = { mapExpanded = !mapExpanded },
                    onSchoolDetail = onSchoolClick
                )
            }

            // Hint de filtros — justo antes de la barra de filtros
            item {
                com.meteomontana.android.ui.components.FirstTimeHint(
                    hintKey = "schools_filters",
                    text = "Usa los filtros de abajo para encontrar escuelas por distancia, tipo de roca o estilo (bloque/vía)."
                )
            }

            // Filtros
            item {
                SchoolFiltersBar(
                    filters = filters,
                    onDistance      = viewModel::setDistance,
                    onStyle         = viewModel::setStyle,
                    onRockToggle    = viewModel::toggleRock,
                    onOnlyFavorites = viewModel::setOnlyFavorites,
                    onOnlySavedOffline = viewModel::setOnlySavedOffline,
                    onSort          = viewModel::setSort,
                    onClearRocks    = viewModel::clearRocks
                )
            }

            // Selector de días: elige hasta 5 días concretos → la lista se
            // reordena por las mejores condiciones de ESE tramo (con lluvia).
            item {
                DaySelectorRow(
                    selectedDays = selectedDays,
                    onToggleDay = viewModel::toggleDay
                )
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp) }

            // Hint de comparar — justo antes de la lista de escuelas
            item {
                com.meteomontana.android.ui.components.FirstTimeHint(
                    hintKey = "schools_compare",
                    text = "Mantén pulsada una escuela para compararla con otras (hasta 3). También puedes tocar los días de arriba para ver un tramo de varios días.",
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }

            when (val s = state) {
                is SchoolListUiState.Loading -> items(6) { SkeletonRow() }
                is SchoolListUiState.Error   -> item { ErrorRow(s.message, onRetry = viewModel::refresh) }
                is SchoolListUiState.Success -> {
                    itemsIndexed(s.schools, key = { _, it -> it.id }) { index, school ->
                        val score = scores[school.id]
                        // animateItem(): cuando llegan los scores y la lista se
                        // re-ordena, las cards se deslizan a su sitio en vez de
                        // teletransportarse.
                        Column(modifier = Modifier.animateItem()) {
                            SchoolListItem(
                                rank = index + 1,
                                school = school,
                                todayScore = score?.todayScore,
                                hourlyScores = score?.hourlyScores,
                                distanceKm = viewModel.distanceTo(school.lat, school.lon),
                                dry = score?.dryRock,
                                rainMm = score?.rainMm,
                                rainProb = score?.rainProb,
                                range = if (selectedDays.isNotEmpty()) rangeScores[school.id] else null,
                                isFavorite = school.id in favoriteIds,
                                selectedForCompare = school.id in compareSelection,
                                onClick = {
                                    // En modo selección el tap también selecciona.
                                    if (compareSelection.isNotEmpty()) viewModel.toggleCompare(school.id)
                                    else onSchoolClick(school.id)
                                },
                                onLongClick = { viewModel.toggleCompare(school.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(school.id) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                        }
                    }
                    if (s.schools.isEmpty()) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(Spacing.xl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.schools_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(Spacing.md))
                                OutlinedCumbreButton(text = stringResource(R.string.schools_clear_filters), onClick = viewModel::clearFilters)
                            }
                        }
                    }
                }
            }
        }

        // Barra de comparación (aparece al seleccionar con long-press)
        if (compareSelection.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Spacing.md)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onBackground)
                    .padding(start = Spacing.xs, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::clearCompare) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.background
                    )
                }
                Text(
                    "${compareSelection.size} seleccionada${if (compareSelection.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.weight(1f)
                )
                // Botón Comparar grande (a partir de 2). Con 1, pista de qué falta.
                if (compareSelection.size >= 2) {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(Terra)
                            .clickable { onCompare(compareSelection.toList()); viewModel.clearCompare() }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.schools_compare) + " ▸",
                            style = MaterialTheme.typography.labelLarge,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                } else {
                    Text(
                        "Elige otra para comparar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = Spacing.sm)
                    )
                }
            }
        }
    }
}

/**
 * Selector de días: los próximos 7 días (hoy incluido) como chips "LUN 17".
 * Toca para elegir hasta 5; con ≥1 elegido la lista pasa a modo tramo y se
 * ordena por las mejores condiciones de esos días. Un chip extra "Hoy" (ninguno
 * elegido) representa el modo de siempre.
 */
@Composable
private fun DaySelectorRow(
    selectedDays: Set<Int>,           // ISO 1-7
    onToggleDay: (Int) -> Unit
) {
    val today = remember { java.time.LocalDate.now() }
    val days = remember(today) { (0..6).map { today.plusDays(it.toLong()) } }
    val dayLetters = arrayOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")  // ISO 1=lunes

    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Text(
            text = if (selectedDays.isEmpty()) "DÍAS · elige hasta 5 para comparar el tramo"
                   else "DÍAS · ${selectedDays.size} elegido${if (selectedDays.size > 1) "s" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            days.forEach { d ->
                val iso = d.dayOfWeek.value
                val selected = iso in selectedDays
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.small
                        )
                        .clickable { onToggleDay(iso) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayLetters[iso - 1],
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Sub-componentes del header                                                 */
/* ─────────────────────────────────────────────────────────────────────────── */

@Composable
private fun TopIconsRow(
    unread: Long,
    chatUnread: Long = 0,
    onSearchUsers: () -> Unit,
    onChats: () -> Unit,
    onNotifications: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.meteomontana.android.ui.components.HelpButton(topicKey = "schools")
        IconButton(onClick = onSearchUsers) {
            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search_users_title),
                tint = MaterialTheme.colorScheme.onBackground)
        }
        IconButton(onClick = onChats) {
            if (chatUnread > 0) {
                BadgedBox(badge = {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(if (chatUnread > 9) "9+" else chatUnread.toString(), color = Color.White)
                    }
                }) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = stringResource(R.string.chat_title),
                        tint = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = stringResource(R.string.chat_title),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        IconButton(onClick = onNotifications) {
            if (unread > 0) {
                BadgedBox(badge = {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(if (unread > 9) "9+" else unread.toString(), color = Color.White)
                    }
                }) {
                    Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.notifications_title),
                        tint = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.notifications_title),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        ThemeToggleButton()
        // El perfil ya no va aquí: tiene su propia pestaña inferior.
    }
}

/** Sol / Luna que alterna el tema. Lee el ThemeManager vía hiltViewModel. */
@Composable
private fun ThemeToggleButton() {
    val vm: ThemeToggleViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val mode by vm.mode.collectAsState()
    val isDark = when (mode) {
        com.meteomontana.android.ui.theme.ThemeMode.DARK -> true
        com.meteomontana.android.ui.theme.ThemeMode.LIGHT -> false
        com.meteomontana.android.ui.theme.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    IconButton(onClick = vm::toggle) {
        Icon(
            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class ThemeToggleViewModel @javax.inject.Inject constructor(
    private val themeManager: com.meteomontana.android.ui.theme.ThemeManager
) : androidx.lifecycle.ViewModel() {
    val mode = themeManager.mode
    fun toggle() = themeManager.toggle()
}

/** Header como en la PWA: título grande, count debajo, botón outlined a la derecha. */
@Composable
private fun HeaderEscuelas(
    count: Int?,
    onSubmitSchool: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.schools_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (count != null) {
                Text(
                    stringResource(R.string.schools_count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedCumbreButton(text = stringResource(R.string.schools_submit), onClick = onSubmitSchool, textColor = Terra)
    }
}

/** Banner café. Adaptativo al tema (usa surface/onSurface). */
@Composable
private fun CoffeeBanner(onDonate: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "☕",
            modifier = Modifier.padding(end = Spacing.sm),
            style = MaterialTheme.typography.headlineMedium
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "¿Te ayuda la app?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Mantenida con amor por la comunidad escaladora",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
        OutlinedCumbreButton(text = "Apóyanos", onClick = { showDialog = true; onDonate() })
    }
    if (showDialog) DonateDialog(onDismiss = { showDialog = false })
}

/** Dialog que explica y abre Ko-fi en el navegador. */
@Composable
private fun DonateDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(Spacing.lg)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("✕",
                    modifier = Modifier.clickable(onClick = onDismiss),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text("☕",
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.displayMedium)
            Text("¿Te es útil la app?",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.md))
            Text(
                "ClimbingTeams es una app gratuita hecha con amor para la " +
                "comunidad escaladora. Si te ayuda a elegir el mejor día en la roca, " +
                "considera invitarme a un café para seguir mejorándola.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.md))
            Column(modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                .padding(Spacing.md)) {
                listOf(
                    "Condiciones de escalada en tiempo real",
                    "Escuelas cercanas con previsión",
                    "Previsión de 7 días",
                    "Mejor día y análisis de secado"
                ).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Box(modifier = Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                .clickable {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://ko-fi.com/climbingteams"))
                    context.startActivity(intent)
                    onDismiss()
                }
                .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text("☕ INVÍTAME A UN CAFÉ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text("Cada café nos acerca a nuevas funciones. ¡Gracias de corazón!",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Botón outlined estilo PWA: borde `ink`, texto `ink`, fondo transparente,
 * radius muy bajo. Material3 OutlinedButton tiene esquinas redondeadas y
 * padding excesivos, así que lo construimos como Box clickable.
 */
@Composable
private fun OutlinedCumbreButton(text: String, onClick: () -> Unit, textColor: Color? = null) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.onBackground, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor ?: MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Silueta gris de una fila de escuela mientras carga el catálogo. Se percibe
 * más rápido que un spinner porque ya enseña la estructura de la pantalla.
 */
@Composable
private fun SkeletonRow() {
    val tone = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(tone))
            Spacer(Modifier.size(Spacing.md))
            Column {
                Box(Modifier.height(16.dp).fillMaxWidth(0.55f)
                    .clip(MaterialTheme.shapes.small).background(tone))
                Spacer(Modifier.height(Spacing.xs))
                Box(Modifier.height(12.dp).fillMaxWidth(0.35f)
                    .clip(MaterialTheme.shapes.small).background(tone))
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Box(Modifier.height(12.dp).fillMaxWidth()
            .clip(MaterialTheme.shapes.small).background(tone))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
}

@Composable
private fun ErrorRow(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Error: $message",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(Spacing.md))
        OutlinedCumbreButton(text = stringResource(R.string.common_retry), onClick = onRetry)
    }
}
