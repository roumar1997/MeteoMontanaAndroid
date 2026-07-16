package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.components.BlocksSection
import com.meteomontana.android.ui.components.MonthlyStatsSection
import com.meteomontana.android.ui.components.NotesSection
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.Spacing

@Composable
fun SchoolDetailScreen(
    onBack: () -> Unit,
    onOpenBlock: (String) -> Unit = {},
    onMyProposals: () -> Unit = {},
    onDayClick: (Int) -> Unit = {},
    viewModel: SchoolDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val success = state as? SchoolDetailUiState.Success
    var addBlockOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            title = success?.school?.name ?: "",
            isFavorite = success?.isFavorite ?: false,
            showFavorite = success != null,
            onBack = onBack,
            onToggleFavorite = viewModel::toggleFavorite,
            isSavedOffline = success?.isSavedOffline ?: false,
            showSaveOffline = success != null,
            onToggleSaveOffline = viewModel::toggleSaveOffline,
            onDirections = if (success != null) {
                {
                    val s = success.school
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                    "https://www.google.com/maps/dir/?api=1&destination=${s.lat},${s.lon}"
                                )
                            )
                        )
                    }
                }
            } else null,
            onShare = if (success != null) {
                {
                    // Con forecast → card de imagen (más viral en WhatsApp);
                    // sin él, el texto plano de siempre.
                    if (success.forecast != null) {
                        runCatching {
                            com.meteomontana.android.ui.share.shareSchoolAsImage(
                                context, success.school, success.forecast
                            )
                        }.onFailure { shareSchool(context, success.school, success.forecast) }
                    } else {
                        shareSchool(context, success.school, success.forecast)
                    }
                }
            } else null
        )
        when (val s = state) {
            is SchoolDetailUiState.Loading -> Center { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            is SchoolDetailUiState.Error -> Center {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(Spacing.md))
                    androidx.compose.material3.OutlinedButton(onClick = viewModel::load) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
            is SchoolDetailUiState.Success -> {
                if (s.offlineSnapshotAt != null) {
                    OfflineBanner(timestamp = s.offlineSnapshotAt)
                }
                if (s.forecastCachedAt != null) {
                    StaleForecastBanner(timestamp = s.forecastCachedAt, onRetry = viewModel::load)
                }
                Content(
                    school = s.school,
                    forecast = s.forecast,
                    forecastError = s.forecastError,
                    notes = s.notes,
                    blocks = s.blocks,
                    onPublishNote = viewModel::publishNote,
                    onAddBlock = { addBlockOpen = true },
                    onBlockClick = onOpenBlock,
                    viewModel = viewModel,
                    onMyProposals = onMyProposals,
                    onDayClick = onDayClick,
                    mountainBulletin = s.mountainBulletin
                )
            }
        }
    }

    if (addBlockOpen && success != null) {
        AddBlockToSchoolSheet(
            schoolLat = success.school.lat,
            schoolLon = success.school.lon,
            onDismiss = { addBlockOpen = false },
            onSave = { req ->
                viewModel.addBlock(req)
                addBlockOpen = false
            }
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    isFavorite: Boolean,
    showFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    isSavedOffline: Boolean = false,
    showSaveOffline: Boolean = false,
    onToggleSaveOffline: () -> Unit = {},
    onDirections: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(title, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.xs).weight(1f))
        com.meteomontana.android.ui.components.HelpButton(topicKey = "detail")
        if (onDirections != null) {
            IconButton(onClick = onDirections, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.Place, contentDescription = stringResource(R.string.common_directions),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        if (onShare != null) {
            IconButton(onClick = onShare, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.common_share),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        if (showSaveOffline) {
            IconButton(onClick = onToggleSaveOffline, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (isSavedOffline) Icons.Filled.DownloadDone else Icons.Outlined.FileDownload,
                    contentDescription = if (isSavedOffline) stringResource(R.string.detail_saved_offline) else stringResource(R.string.detail_save_offline),
                    tint = if (isSavedOffline) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onBackground
                )
            }
        }
        if (showFavorite) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
}

@Composable
private fun Content(
    school: School,
    forecast: Forecast?,
    forecastError: String?,
    notes: List<Note>,
    blocks: List<Block>,
    onPublishNote: (String, com.meteomontana.android.domain.model.FileRef?) -> Unit,
    onAddBlock: () -> Unit,
    onBlockClick: (String) -> Unit,
    viewModel: SchoolDetailViewModel,
    onMyProposals: () -> Unit,
    onDayClick: (Int) -> Unit = {},
    mountainBulletin: com.meteomontana.android.data.api.MountainBulletinDto? = null
) {
    // Columna NO perezosa (paridad con el ScrollView de iOS): toda la pantalla
    // se compone al entrar → los deep-links a piedras/vías (feed, diario,
    // buscador, enlaces) abren la ficha en cuanto cargan los bloques, sin
    // esperar a que el usuario scrollee. Antes era un LazyColumn y la sección
    // del mapa (la que abre la ficha) no existía hasta entrar en pantalla —
    // hicieron falta parches (scroll programático + velo) que esto elimina.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        com.meteomontana.android.ui.components.FirstTimeHint(
            hintKey = "detail_offline",
            text = "Toca ↓ (arriba) para guardar esta escuela y verla sin conexión, incluyendo el mapa y las piedras."
        )
        com.meteomontana.android.ui.components.FirstTimeHint(
            hintKey = "detail_propose",
            text = "Despliega el mapa de abajo y usa + PROPONER para añadir piedras, parkings o sectores que falten. Un admin lo revisa."
        )
        com.meteomontana.android.ui.components.FirstTimeHint(
            hintKey = "detail_tick",
            text = "Toca una piedra en el mapa para ver sus vías. El círculo ○ marca una vía como hecha y la guarda en tu diario."
        )
        // Sección de piedras/mapa: UNA sola definición (lambda) — se pinta en
        // su sitio de siempre cuando hay forecast, y SIN esperar al forecast
        // mientras carga o si falla (antes las piedras no existían hasta que
        // el tiempo llegaba → los deep-links a piedras esperaban de más).
        val blocksSection: @Composable () -> Unit = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                BlocksSection(
                    blocks = blocks, onAddBlock = onAddBlock, onBlockClick = onBlockClick,
                    schoolLat = school.lat, schoolLon = school.lon,
                    schoolName = school.name, schoolId = school.id,
                    viewModel = viewModel, onMyProposals = onMyProposals
                )
            }
        }
        if (forecast != null) {
            com.meteomontana.android.ui.components.ForecastBodyColumn(
                forecast = forecast,
                afterCurrentWeather = {
                    Column {
                        // Boletín de montaña AEMET (solo escuelas en un macizo).
                        mountainBulletin?.let { b ->
                            com.meteomontana.android.ui.components.MountainBulletinSection(b)
                        }
                        blocksSection()
                    }
                },
                onDayClick = onDayClick
            )
        } else {
            if (forecastError != null) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .padding(Spacing.lg)
                ) {
                    Column {
                        Text("Tiempo no disponible",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(Spacing.xs))
                        Text(forecastError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Forecast aún cargando: hueco discreto en su zona.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.padding(start = Spacing.sm))
                    Text("Cargando el tiempo…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Piedras y mapa disponibles YA, con el tiempo cargando o caído.
            blocksSection()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        NotesSection(notes = notes, onPublish = onPublishNote,
            onVote = { n, v -> viewModel.voteNote(n, v) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        // (Guardar offline movido al toolbar superior, como en iOS.)
        val s = viewModel.uiState.collectAsState().value as? SchoolDetailUiState.Success
        MonthlyStatsSection(stats = s?.monthlyStats, isLoading = s?.monthlyLoading == true)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun OfflineBanner(timestamp: Long) {
    val label = remember(timestamp) { formatOfflineTimestamp(timestamp) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("● SIN CONEXIÓN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.padding(start = Spacing.sm))
        Text("Datos del $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Aviso ámbar: el forecast viene de la caché local porque la red falló.
 * Muestra la antigüedad de los datos y permite reintentar.
 */
@Composable
private fun StaleForecastBanner(timestamp: Long, onRetry: () -> Unit) {
    val ageMin = ((System.currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0)
    val ageLabel = when {
        ageMin < 60        -> "hace $ageMin min"
        ageMin < 60 * 24   -> "hace ${ageMin / 60} h"
        else               -> "hace ${ageMin / (60 * 24)} días"
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⚠ PREVISIÓN DE $ageLabel".uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f))
        Text(stringResource(R.string.common_retry),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onRetry))
    }
}

private fun formatOfflineTimestamp(ms: Long): String {
    val date = java.util.Date(ms)
    val fmt = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("es"))
    return fmt.format(date)
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private fun shareSchool(
    context: android.content.Context,
    school: School,
    forecast: Forecast?
) {
    // Formato WhatsApp (los *asteriscos* son negrita allí) + nuestro enlace
    // inteligente: abre la app si la tienes, o la página con las stores si no.
    val base = com.meteomontana.android.BuildConfig.API_BASE_URL.removeSuffix("api/")
    val sb = StringBuilder()
    sb.append("🧗 *").append(school.name).append("*")
    school.region?.let { sb.append(" · ").append(it) }
    sb.append("\n")
    if (forecast != null) {
        val c = forecast.current
        sb.append("📊 Índice *").append(c.score).append("/100* (").append(c.scoreLabel).append(")\n")
        forecast.bestWindow?.let {
            sb.append("🕐 Óptimo *").append(it.start).append("–").append(it.end).append("*\n")
        }
        sb.append(if (c.dryRock) "🪨 Roca seca" else "💧 Roca mojada")
        sb.append(" · ").append(c.temperature.toInt()).append("° · viento ")
            .append(c.windSpeed.toInt()).append(" km/h\n")
    }
    sb.append("\n👉 Ábrela en Cumbre:\n").append(base).append("s/e/").append(school.id)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
        putExtra(android.content.Intent.EXTRA_SUBJECT, school.name)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Compartir escuela"))
}
