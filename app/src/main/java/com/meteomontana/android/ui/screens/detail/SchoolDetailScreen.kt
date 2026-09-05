@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch

@Composable
fun SchoolDetailScreen(
    onBack: () -> Unit,
    onOpenBlock: (String) -> Unit = {},
    onMyProposals: () -> Unit = {},
    onDayClick: (Int) -> Unit = {},
    onOpenChat: (uid: String, name: String) -> Unit = { _, _ -> },
    onOpenSchoolChat: (schoolName: String) -> Unit = {},
    viewModel: SchoolDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val success = state as? SchoolDetailUiState.Success
    var addBlockOpen by remember { mutableStateOf(false) }
    var processionaryOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Deslizar hacia abajo para pedir otra vez quién hay en "Estoy aquí" —
    // mejor a demanda que sondear sola cada X segundos (Álvaro, 2026-09-04).
    var isRefreshing by remember { mutableStateOf(false) }
    var presenceRefreshKey by remember { mutableStateOf(0) }
    val refreshScope = androidx.compose.runtime.rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    android.util.Log.i("CUMBRE-MAPA", "PANTALLA recibe " + e.type + " en " + e.changes.firstOrNull()?.position)
                }
            }
        }
        .background(MaterialTheme.colorScheme.background)) {
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
            } else null,
            processionaryAlertActive = success?.school?.processionaryAlertActive ?: false,
            onOpenProcessionary = if (success != null) { { processionaryOpen = true } } else null
        )
        // Fijo, fuera del scroll: si no se ve nada más que el título hasta que
        // bajas, nadie sabe que hay alguien ahí (Álvaro, 2026-09-03, paridad
        // con SchoolPresenceRow.swift).
        success?.let { s ->
            com.meteomontana.android.ui.components.SchoolPresenceRow(
                schoolId = s.school.id,
                schoolName = s.school.name,
                myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid,
                onOpenChat = onOpenChat,
                onOpenSchoolChat = { onOpenSchoolChat(s.school.name) },
                refreshKey = presenceRefreshKey
            )
        }
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.load()
                presenceRefreshKey++
                refreshScope.launch {
                    kotlinx.coroutines.delay(800)
                    isRefreshing = false
                }
            },
            modifier = Modifier.weight(1f)
        ) {
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
                    // Guardar offline: los datos se guardan sin preguntar (pesan
                    // poco), pero las FOTOS se consultan — son casi todo el peso y
                    // puede estar gastando datos. Sin ellas el topo no sirve en la
                    // roca, así que se dice qué se gana y cuánto cuesta.
                    Column {
                        FotosOfflineDialogs(
                            oferta = s.ofertaFotosOffline,
                            progreso = s.descargaFotos,
                            fallidas = s.fotosOfflineFallidas,
                            onDescargar = viewModel::descargarFotosOffline,
                            onRechazar = viewModel::rechazarFotosOffline,
                            onCerrarAviso = viewModel::limpiarAvisoFotos
                        )
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
                            mountainBulletin = s.mountainBulletin,
                            approaches = s.approaches,
                            isAdmin = s.isCurrentUserAdmin
                        )
                    }
                }
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

    if (processionaryOpen && success != null) {
        com.meteomontana.android.ui.components.ProcessionaryInfoSheet(
            hasKnownProcessionary = success.school.hasKnownProcessionary,
            alertActive = success.school.processionaryAlertActive,
            onConfirm = { viewModel.confirmProcessionary() },
            onRetract = { viewModel.retractProcessionary() },
            onDismiss = { processionaryOpen = false }
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
    onShare: (() -> Unit)? = null,
    processionaryAlertActive: Boolean = false,
    onOpenProcessionary: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // DOS PASTILLAS, como en iOS: el "atrás" en la suya y las acciones
        // agrupadas en otra. Sueltos sobre el fondo, como estaban, era una de
        // las cosas que más delataban que no eran la misma app.
        com.meteomontana.android.ui.components.CumbrePillGroup {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        // El nombre se queda con el hueco sobrante y se recorta si no cabe: en
        // una pantalla estrecha manda la pastilla de acciones, no el título.
        Text(title, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.xs).weight(1f))
        com.meteomontana.android.ui.components.CumbrePillGroup {
            com.meteomontana.android.ui.components.HelpButton(topicKey = "detail")
            if (onOpenProcessionary != null) {
                com.meteomontana.android.ui.components.ProcessionaryButton(
                    alertActive = processionaryAlertActive,
                    onClick = onOpenProcessionary
                )
            }
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
    mountainBulletin: com.meteomontana.android.domain.model.MountainBulletin? = null,
    approaches: List<com.meteomontana.android.domain.model.Approach> = emptyList(),
    isAdmin: Boolean = false
) {
    // Aproximaciones (parking → sector) — APPROACH_DESIGN.md, admin-gated.
    var followingApproach by remember { mutableStateOf<com.meteomontana.android.domain.model.Approach?>(null) }
    var recordingApproach by remember { mutableStateOf(false) }
    val approachScope = androidx.compose.runtime.rememberCoroutineScope()
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
        // movableContentOf, NO una lambda a secas.
        //
        // Esta seccion se pinta en DOS sitios: uno mientras carga el tiempo y
        // otro dentro del bloque del tiempo cuando llega. Con una lambda normal,
        // al llegar el tiempo se creaba una SEGUNDA instancia sin morir la
        // primera —comprobado por registro: "MAPA CREADO #1" y "#2" seguidos—,
        // y quedaban dos mapas vivos: veias uno y tocabas el otro. De ahi que
        // "OCULTAR MAPA" no hiciera nada (Rodrigo, 2026-08-11).
        //
        // movableContentOf mueve el MISMO contenido de un sitio a otro
        // conservando su identidad y su estado. Es justo para esto.
        //
        // OJO con las CAPTURAS: este `remember` no tiene claves a propósito
        // (recrearlo pierde la identidad del mapa, que es justo lo que
        // movableContentOf viene a evitar). Por eso el contenido NO puede leer
        // `blocks` directamente: se quedaría con la lista de la PRIMERA
        // composición —vacía, porque el servidor tarda ~0,4 s— y el mapa se
        // pintaba para siempre con un solo marcador, el de la escuela. Al salir
        // y volver a entrar se recomponía de cero y ya salía bien: ese era
        // exactamente el síntoma ("entro y no sale nada; salgo, entro y sí").
        // rememberUpdatedState mantiene la lectura viva sin recrear el bloque.
        // Cazado con registro el 2026-08-24 (Álvaro): "getBlocks(la-pedriza)
        // OK: 21 bloques" y el mapa repintando "marcadores=1" sin parar.
        val blocksState = androidx.compose.runtime.rememberUpdatedState(blocks)
        val approachesState = androidx.compose.runtime.rememberUpdatedState(approaches)
        val isAdminState = androidx.compose.runtime.rememberUpdatedState(isAdmin)
        val blocksSection = androidx.compose.runtime.remember { androidx.compose.runtime.movableContentOf {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                BlocksSection(
                    blocks = blocksState.value, onAddBlock = onAddBlock, onBlockClick = onBlockClick,
                    schoolLat = school.lat, schoolLon = school.lon,
                    schoolName = school.name, schoolStyle = school.style, schoolId = school.id,
                    viewModel = viewModel, onMyProposals = onMyProposals,
                    // APROXIMACIONES entre el MAPA y PARKINGS, que es donde las
                    // pone iOS (SchoolMapSection.swift:126-134). Aquí colgaban
                    // al final, detrás de sectores. Va por ranura porque el dato
                    // vive en esta pantalla pero su sitio está dentro del mapa.
                    contenidoTrasMapa = {
                        com.meteomontana.android.ui.components.ApproachesSection(
                            // Mismo motivo que blocksState: llegan del servidor
                            // DESPUÉS de la primera composición y se capturarían.
                            approaches = approachesState.value,
                            isAdmin = isAdminState.value,
                            onFollow = { followingApproach = it },
                            onRecord = { recordingApproach = true },
                            onDelete = { a -> approachScope.launch { viewModel.deleteApproach(a.id) } }
                        )
                    }
                )
            }
        } }
        if (forecast != null) {
            com.meteomontana.android.ui.components.ForecastBodyColumn(
                forecast = forecast,
                afterCurrentWeather = {
                    Column {
                        // Boletín de montaña AEMET: solo escuelas de un macizo
                        // ESPAÑOL. Fuera de España no existe boletín, y enseñar
                        // un hueco vacío es peor que no ofrecer la función.
                        mountainBulletin?.takeIf {
                            com.meteomontana.android.domain.util.SpainOnlyFeatures
                                .showsMountainBulletin(school?.country)
                        }?.let { b ->
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
        val s = viewModel.uiState.collectAsStateWithLifecycle().value as? SchoolDetailUiState.Success
        MonthlyStatsSection(stats = s?.monthlyStats, isLoading = s?.monthlyLoading == true)
        Spacer(Modifier.height(40.dp))
    }

    // Aproximaciones: pantallas completas (Dialog), fuera del scroll de arriba.
    followingApproach?.let { a ->
        com.meteomontana.android.ui.screens.approach.ApproachFollowScreen(
            approach = a,
            schoolName = school.name,
            isAdmin = isAdmin,
            onDismiss = { followingApproach = null },
            onDeleteApproach = { toDelete ->
                approachScope.launch { viewModel.deleteApproach(toDelete.id) }
            },
            onAddPin = { approachId, req ->
                approachScope.launch {
                    viewModel.addApproachPin(approachId, req)
                    // Refresca la aproximación abierta con la chincheta nueva.
                    followingApproach = (viewModel.uiState.value as? SchoolDetailUiState.Success)
                        ?.approaches?.firstOrNull { it.id == approachId }
                }
            }
        )
    }
    if (recordingApproach) {
        com.meteomontana.android.ui.screens.approach.ApproachRecordScreen(
            school = school,
            blocks = blocks,
            onDismiss = { recordingApproach = false },
            onSave = { req, pins -> viewModel.createApproach(req, pins) }
        )
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

private val CONTADOR_DETALLES = java.util.concurrent.atomic.AtomicInteger(0)
