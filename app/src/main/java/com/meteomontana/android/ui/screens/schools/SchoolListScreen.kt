package com.meteomontana.android.ui.screens.schools

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun SchoolListScreen(
    onSchoolClick: (String) -> Unit,
    onProfileClick: () -> Unit = {},
    onSubmitSchool: () -> Unit = {},
    onSearchUsers: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onChats: () -> Unit = {},
    onDonate: () -> Unit = {},
    viewModel: SchoolListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val unread by viewModel.unreadCount.collectAsState()
    val scores by viewModel.scores.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    var mapExpanded by remember { mutableStateOf(false) }

    // Pide permiso de ubicación al abrir la pantalla — al concederlo el VM
    // recarga ordenado por mejor score + filtra 50 km desde la posición real.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.onLocationGranted() }
    LaunchedEffect(Unit) {
        permLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // Fila de iconos top (no existe en la PWA pero hay que mantener
            // navegación a chats/notifs/perfil). Discreta para que el header
            // siguiente sea el foco visual.
            item {
                TopIconsRow(
                    unread = unread,
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

            // Banner café: ¿Te ayuda la app? · Apóyanos
            item { CoffeeBanner(onDonate = onDonate) }

            // Buscador
            item {
                Box(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    OutlinedTextField(
                        value = filters.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar escuela…") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }

            // Mapa global "VER MAPA" — colapsable con markers de las escuelas
            // visibles (mismas que la lista de abajo: usa los filtros del VM).
            item {
                val successState = state as? SchoolListUiState.Success
                SchoolsMapPanel(
                    schools = successState?.schools.orEmpty(),
                    scoresById = scores.mapValues { it.value.todayScore },
                    userLat = null,   // TODO: pasar ubicación real cuando el VM la exponga.
                    userLon = null,
                    expanded = mapExpanded,
                    onToggle = { mapExpanded = !mapExpanded },
                    onSchoolDetail = onSchoolClick
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
                    onSort          = viewModel::setSort
                )
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp) }

            when (val s = state) {
                is SchoolListUiState.Loading -> item { LoaderRow() }
                is SchoolListUiState.Error   -> item { ErrorRow(s.message) }
                is SchoolListUiState.Success -> {
                    itemsIndexed(s.schools, key = { _, it -> it.id }) { index, school ->
                        val score = scores[school.id]
                        SchoolListItem(
                            rank = index + 1,
                            school = school,
                            todayScore = score?.todayScore,
                            hourlyScores = score?.hourlyScores,
                            distanceKm = viewModel.distanceTo(school.lat, school.lon),
                            dry = score?.dryRock,
                            rainMm = score?.rainMm,
                            rainProb = score?.rainProb,
                            isFavorite = school.id in favoriteIds,
                            onClick = { onSchoolClick(school.id) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    }
                    if (s.schools.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                                Text(
                                    "No hay escuelas con esos filtros",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
        IconButton(onClick = onSearchUsers) {
            Icon(Icons.Outlined.Search, contentDescription = "Buscar usuarios",
                tint = MaterialTheme.colorScheme.onBackground)
        }
        IconButton(onClick = onChats) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Chats",
                tint = MaterialTheme.colorScheme.onBackground)
        }
        IconButton(onClick = onNotifications) {
            if (unread > 0) {
                BadgedBox(badge = {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(if (unread > 9) "9+" else unread.toString(), color = Color.White)
                    }
                }) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notificaciones",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notificaciones",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        ThemeToggleButton()
        IconButton(onClick = onProfileClick) {
            Icon(Icons.Outlined.Person, contentDescription = "Perfil",
                tint = MaterialTheme.colorScheme.onBackground)
        }
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
            contentDescription = "Cambiar tema",
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
                "Escuelas",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (count != null) {
                Text(
                    "$count escuelas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedCumbreButton(text = "+ Enviar escuela", onClick = onSubmitSchool)
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
private fun OutlinedCumbreButton(text: String, onClick: () -> Unit) {
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
            color = if (text == "+ Enviar escuela") Terra else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun LoaderRow() {
    Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorRow(message: String) {
    Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        Text(
            "Error: $message",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}
