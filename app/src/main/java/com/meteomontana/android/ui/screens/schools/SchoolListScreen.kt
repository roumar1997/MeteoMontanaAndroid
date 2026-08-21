package com.meteomontana.android.ui.screens.schools

import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    /** Resultado del buscador global de vías/bloques → abre la escuela en esa vía. */
    onViaHit: (schoolId: String, viaId: String?, viaName: String?) -> Unit = { s, _, _ -> onSchoolClick(s) },
    viewModel: SchoolListViewModel = hiltViewModel()
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
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> if (grants.values.any { it }) viewModel.onLocationGranted() }
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

    if (showOnboarding) {
        com.meteomontana.android.ui.onboarding.OnboardingOverlay(onFinish = {
            com.meteomontana.android.ui.onboarding.markOnboardingDone(context)
            showOnboarding = false
            permLauncher.launch(locationPerms)
        })
        return
    }

    // Tras el tutorial: si el perfil aún no tiene username, obligar a elegirlo
    // (solo sale si username == null en el servidor; reinstalar no lo re-muestra).
    com.meteomontana.android.ui.onboarding.UsernameGate()

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
        TopIconsRow(
            unread = unread,
            chatUnread = chatUnread,
            onSearchUsers = onSearchUsers,
            onChats = onChats,
            onNotifications = onNotifications,
            onProfileClick = onProfileClick
        )
        HeaderEscuelas(
            count = (state as? SchoolListUiState.Success)?.schools?.size,
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
        modifier = Modifier.fillMaxSize(),
        // Hueco al final para que la ultima escuela no quede debajo de la capsula.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current)
    ) {

            // Buscador ÚNICO estilo Spotlight: escuelas Y vías/bloques a la vez.
            // El placeholder anuncia que busca ambas cosas, y al escribir salen
            // las dos secciones (aunque una esté vacía) → se aprende solo.
            item {
                Box(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    OutlinedTextField(
                        value = filters.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Busca escuelas, vías y bloques…") },
                        // Lupa dentro del campo, como en iOS: sin ella el
                        // buscador parece una caja de texto cualquiera.
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            // P8: X para limpiar de un toque (paridad iOS).
                            if (filters.query.isNotEmpty()) {
                                androidx.compose.material3.Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                    modifier = Modifier.clickable { viewModel.setQuery("") }
                                )
                            }
                        },
                        singleLine = true,
                        // La tecla de buscar del propio teclado lo cierra: la
                        // lista ya filtra al escribir, no hay nada que enviar.
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { closeKeyboard() }
                        ),
                        shape = com.meteomontana.android.ui.theme.CumbrePillShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
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
                    Column(Modifier.padding(horizontal = Spacing.lg)) {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface,
                                    MaterialTheme.shapes.small)
                                .border(1.dp, MaterialTheme.colorScheme.outline,
                                    MaterialTheme.shapes.small)
                        ) {
                            Text("ESCUELAS",
                                style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            if (schoolMatches.isEmpty()) {
                                Text("Sin resultados",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                        .padding(bottom = 8.dp))
                            } else {
                                schoolMatches.forEach { s ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable { openSchool(s.id) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(s.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1)
                                            s.region?.takeIf { it.isNotBlank() }?.let {
                                                Text(it,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1)
                                            }
                                        }
                                        Text("▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Text("VÍAS Y BLOQUES",
                                style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            if (viaHits.isEmpty()) {
                                Text("Sin resultados",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                        .padding(bottom = 8.dp))
                            }
                            viaHits.forEach { h ->
                                Column(
                                    Modifier.fillMaxWidth()
                                        .clickable { openVia(h.schoolId, h.lineId, h.lineName ?: h.blockName) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                (h.lineName ?: h.blockName) +
                                                    (h.grade?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1)
                                            Text(
                                                listOf(h.blockName.takeIf { h.lineName != null },
                                                       h.sectorName, h.schoolName)
                                                    .filterNotNull().filter { it.isNotBlank() }
                                                    .joinToString(" · "),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1)
                                        }
                                        Text("▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    // Mini-topo: la foto de la cara con la línea dibujada
                                    // (solo si el backend mandó foto; la piedra sale sin trazo).
                                    h.photoPath?.takeIf { it.isNotBlank() }?.let { photo ->
                                        // P8: dedup de puntos consecutivos casi identicos — en
                                        // trazos a mano antiguos los duplicados fusionaban los
                                        // guiones y la linea salia CONTINUA solo aqui.
                                        val rawStroke = com.meteomontana.android.ui.screens.topo
                                            .parseLineStroke(h.linePath)
                                        val stroke = rawStroke.copy(points = rawStroke.points
                                            .filterIndexed { i, pt ->
                                                i == 0 || kotlin.math.abs(pt.x - rawStroke.points[i - 1].x) +
                                                    kotlin.math.abs(pt.y - rawStroke.points[i - 1].y) > 0.004f
                                            })
                                        val topoLines = if (stroke.points.size >= 2) listOf(
                                            com.meteomontana.android.ui.components.TopoLine(
                                                name = h.lineName, grade = h.grade,
                                                startType = h.startType, points = stroke.points)
                                        ) else emptyList()
                                        com.meteomontana.android.ui.components.TopoPhotoCanvas(
                                            photoUrl = photo,
                                            lines = topoLines,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                    onSchoolDetail = openSchool,
                    mapState = mapState
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
                                    else openSchool(school.id)
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
                    // POR ENCIMA de la cápsula de pestañas. Es una barra
                    // flotante anclada abajo, así que el contentPadding de la
                    // lista no la alcanza: se le da su propio hueco o el botón
                    // COMPARAR queda debajo de las pestañas y no se puede
                    // pulsar (lo cazó Rodrigo intentando comparar dos escuelas).
                    .padding(bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current)
                    .padding(Spacing.md)
                    .clip(MaterialTheme.shapes.small)
                    // Fondo FIJO oscuro: onBackground se invierte en modo
                    // oscuro y la barra salía blanca y deslumbrante.
                    .background(androidx.compose.ui.graphics.Color(0xFF17170F))
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .padding(start = Spacing.xs, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::clearCompare) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
                Text(
                    "${compareSelection.size} seleccionada${if (compareSelection.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.White,
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
                    // Radio intermedio, NO píldora: es una celda casi cuadrada
                    // (día + número apilados), y una píldora completa ahí se ve
                    // como un óvalo forzado — mismo motivo que las stat cards.
                    modifier = Modifier
                        .clip(com.meteomontana.android.ui.theme.CumbreStatCardShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            com.meteomontana.android.ui.theme.CumbreStatCardShape
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
        // Los cinco iconos dentro de una pastilla, como en iOS. Sueltos sobre
        // el fondo ocupaban toda la parte de arriba y era lo que hacía que la
        // cabecera no se pareciera en nada a la del iPhone.
        com.meteomontana.android.ui.components.CumbrePillGroup {
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
        // Tema ANTES que la campana: es el orden de iOS (?, buscar, chats,
        // luna, campana). Estaban intercambiados y, al comparar las dos
        // pantallas, la cabecera era lo primero que delataba que no eran la
        // misma app.
        ThemeToggleButton()
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
        }
        // El perfil ya no va aquí: tiene su propia pestaña inferior.
    }
}

/** Sol / Luna que alterna el tema. Lee el ThemeManager vía hiltViewModel. */
@Composable
private fun ThemeToggleButton() {
    val vm: ThemeToggleViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val isDark = when (mode) {
        com.meteomontana.android.ui.theme.ThemeMode.DARK -> true
        com.meteomontana.android.ui.theme.ThemeMode.LIGHT -> false
        com.meteomontana.android.ui.theme.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    IconButton(onClick = vm::toggle) {
        Icon(
            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = stringResource(R.string.a11y_toggle_theme),
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
    onSubmitSchool: () -> Unit,
    /** Elegir una foto y proponer la piedra en la escuela donde se hizo. */
    onSubmitBlockPhoto: () -> Unit
) {
    var aportando by remember { mutableStateOf(false) }
    if (aportando) {
        AportarSheet(
            onDismiss = { aportando = false },
            onPiedra = { aportando = false; onSubmitBlockPhoto() },
            onEscuela = { aportando = false; onSubmitSchool() }
        )
    }
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
        // UN solo boton: corto, entra en cualquier pantalla y con el texto
        // grande de accesibilidad. Las dos formas de aportar viven en la hoja,
        // donde cada una cabe con su explicacion.
        OutlinedCumbreButton(text = stringResource(R.string.schools_contribute),
            onClick = { aportando = true }, textColor = Terra)
    }
}

/** Las dos formas de aportar al catalogo, cada una con su porque. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AportarSheet(
    onDismiss: () -> Unit,
    onPiedra: () -> Unit,
    onEscuela: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        shape = com.meteomontana.android.ui.components.CumbreSheetShape
    ) {
        Column(modifier = Modifier.fillMaxWidth()
            .cumbreSheetSurface()
            .padding(Spacing.lg)) {
            Text(
                stringResource(R.string.contribute_title),
                style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = Terra
            )
            Spacer(Modifier.height(Spacing.md))
            AportarOpcion(
                titulo = stringResource(R.string.contribute_block),
                detalle = stringResource(R.string.contribute_block_hint),
                onClick = onPiedra
            )
            Spacer(Modifier.height(Spacing.sm))
            AportarOpcion(
                titulo = stringResource(R.string.contribute_school),
                detalle = stringResource(R.string.contribute_school_hint),
                onClick = onEscuela
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun AportarOpcion(titulo: String, detalle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.onBackground, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(Spacing.md)
    ) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Text(detalle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun OutlinedCumbreButton(
    text: String,
    onClick: () -> Unit,
    textColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(com.meteomontana.android.ui.theme.CumbrePillShape)
            .border(1.dp, MaterialTheme.colorScheme.onBackground, com.meteomontana.android.ui.theme.CumbrePillShape)
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
