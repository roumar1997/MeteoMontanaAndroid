@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.ui.components.SkeletonSchoolDetail
import com.meteomontana.android.util.AppText
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.components.BlocksSection
import com.meteomontana.android.ui.components.MonthlyStatsSection
import com.meteomontana.android.ui.components.NotesSection
import com.meteomontana.android.ui.guide.guideTarget
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.Spacing
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow

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

    com.meteomontana.android.ui.guide.GuideHost("detail") {
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
        if (success != null) {
            // Cabecera en UNA fila: [volver] … [chat][Aquí] [cápsula de iconos]. Fija, fuera del scroll (Álvaro,
            // 2026-09-03: si no, nadie sabe que hay alguien ahí).
            val s = success
            com.meteomontana.android.ui.components.SchoolPresenceRow(
                schoolId = s.school.id,
                schoolName = s.school.name,
                myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid,
                onOpenChat = onOpenChat,
                onOpenSchoolChat = { onOpenSchoolChat(s.school.name) },
                refreshKey = presenceRefreshKey,
                leading = { item -> BackPill(onBack, item) },
                tileCount = 8,
                trailing = { item ->
                    HeaderActions(
                        item = item,
                        isFavorite = s.isFavorite,
                        onToggleFavorite = viewModel::toggleFavorite,
                        isSavedOffline = s.isSavedOffline,
                        savingProgress = s.descargaFotos,
                        onToggleSaveOffline = viewModel::toggleSaveOffline,
                        onDirections = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(
                                            "https://www.google.com/maps/dir/?api=1&destination=${s.school.lat},${s.school.lon}"
                                        )
                                    )
                                )
                            }
                        },
                        onShare = {
                            // Con forecast → card de imagen (más viral en WhatsApp); sin él, el texto plano de siempre.
                            if (s.forecast != null) {
                                runCatching {
                                    com.meteomontana.android.ui.share.shareSchoolAsImage(context, s.school, s.forecast)
                                }.onFailure { shareSchool(context, s.school, s.forecast) }
                            } else {
                                shareSchool(context, s.school, s.forecast)
                            }
                        },
                        processionaryAlertActive = s.school.processionaryAlertActive,
                        onOpenProcessionary = { processionaryOpen = true }
                    )
                }
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm)) { BackPill(onBack, Modifier.width(44.dp)) }
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
                is SchoolDetailUiState.Loading -> SkeletonSchoolDetail()
                is SchoolDetailUiState.Error -> Center {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.school_detail_screen_v2_error_1_s, s.message), color = MaterialTheme.colorScheme.error)
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
                            approaches = s.approaches
                        )
                    }
                }
            }
        }
    }
    }   // GuideHost

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
            activeNowSet = success.school.processionaryActiveNowSet,
            onConfirm = { viewModel.confirmProcessionary() },
            onRetract = { viewModel.retractProcessionary() },
            onActiveNow = { viewModel.reportProcessionaryActiveNow() },
            onClearActiveNow = { viewModel.clearProcessionaryActiveNow() },
            onDismiss = { processionaryOpen = false }
        )
    }
}

@Composable
internal fun Content(
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
    approaches: List<com.meteomontana.android.domain.model.Approach> = emptyList()
) {
    // Aproximaciones (parking → sector) — docs/APPROACH_DESIGN.md (solo lectura).
    var followingApproach by remember { mutableStateOf<com.meteomontana.android.domain.model.Approach?>(null) }
    // Columna NO perezosa (paridad con el ScrollView de iOS): toda la pantalla
    // se compone al entrar → los deep-links a piedras/vías (feed, diario,
    // buscador, enlaces) abren la ficha en cuanto cargan los bloques, sin
    // esperar a que el usuario scrollee. Antes era un LazyColumn y la sección
    // del mapa (la que abre la ficha) no existía hasta entrar en pantalla —
    // hicieron falta parches (scroll programático + velo) que esto elimina.
    // Restricciones en vigor ("!" en sectores y piedras): se dan aquí una vez y las leen los hijos.
    val restrictionsVm: SchoolRestrictionsViewModel = hiltViewModel()
    LaunchedEffect(school.id) { restrictionsVm.load(school.id) }
    val restrictions by restrictionsVm.state.collectAsStateWithLifecycle()
    val restrictionsUi = remember(restrictions, blocks) {
        restrictions?.takeUnless { it.isEmpty }?.let { com.meteomontana.android.ui.components.SchoolRestrictionsUi(it, blocks) }
    }
    var showRestrictions by remember { mutableStateOf(false) }
    androidx.compose.runtime.CompositionLocalProvider(
        com.meteomontana.android.ui.components.LocalSchoolRestrictions provides restrictionsUi
    ) {
    // El scroll de la ficha, a mano: la GUÍA baja hasta un sector o una piedra (índice, marcapáginas).
    val scroll = androidx.compose.foundation.rememberScrollState()
    // Pestaña activa: la barra de pestañas vive en la cabecera fija, fuera del scroll.
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SchoolTab.MAP) }
    var viewportTop by remember { mutableStateOf(0f) }
    val guideScope = androidx.compose.runtime.rememberCoroutineScope()
    val topMarginPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
    val guideScroller = remember(scroll) {
        com.meteomontana.android.ui.screens.detail.guide.GuideScroller { y ->
            guideScope.launch { scroll.animateScrollTo((scroll.value + (y - viewportTop - topMarginPx)).toInt().coerceAtLeast(0)) }
        }
    }
    // Estado que comparten la cabecera fija y el contenido que se desliza.
    var showHelp by remember { mutableStateOf(false) }
    val guides = com.meteomontana.android.ui.guide.LocalGuideController.current
    val photoFinder: com.meteomontana.android.ui.assistant.photo.SchoolPhotoFinderViewModel = hiltViewModel()
    Column(Modifier.fillMaxSize()) {
    // ── CABECERA FIJA (fuera del scroll): nombre + «? AYUDA» + cámara, y las pestañas MAPA · GUÍA · TIEMPO · NOTAS.
    // Va ANTES del scroll, no pegada encima: así no se mueve nunca (ni en el mapa, ni en la guía, ni en tiempo/notas).
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            school.name,
            modifier = Modifier.weight(1f).padding(end = 10.dp),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = com.meteomontana.android.ui.theme.Serif,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 26.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        // "! 2 CERRADOS" junto al nombre: solo DENTRO de la escuela (nunca en la lista), toca y ves el detalle.
        restrictionsUi?.let {
            com.meteomontana.android.ui.components.RestrictionsPill(it.restrictions) { showRestrictions = true }
            Spacer(Modifier.size(8.dp))
        }
        // UN solo botón de ayuda que reconoce pantalla y pestaña: la 1ª vez cuenta la pantalla y encadena la de
        // la pestaña; luego solo la de la pestaña. Sin guía, la ayuda de texto de siempre.
        com.meteomontana.android.ui.guide.GuidePill("detail") {
            if (!guides.startScreenAndTab("detail", tab.guideKey)) {
                guides.markUsed("detail")
                showHelp = true
            }
        }
        // «¿Qué piedra es esta?»: una foto y dice cuál de las de ESTA escuela es (todo en el móvil).
        Spacer(Modifier.size(6.dp))
        com.meteomontana.android.ui.assistant.photo.SchoolPhotoButton(school, photoFinder, Modifier.guideTarget("detail.photo"))
    }
    if (showHelp) com.meteomontana.android.ui.components.HelpSheet(topicKey = "detail", onDismiss = { showHelp = false })
    com.meteomontana.android.ui.assistant.photo.SchoolPhotoSheet(photoFinder, school.name) { blockId ->
        photoFinder.cancel()
        onBlockClick(blockId)
    }
    Spacer(Modifier.height(6.dp))
    SchoolTabBar(
        selected = tab,
        onSelect = { tab = it },
        blocksKind = blocksTabKind(school.style),
        modifier = Modifier.guideTarget("detail.tabs")
    )
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .onGloballyPositioned { viewportTop = it.positionInWindow().y }
            .verticalScroll(scroll)
    ) {
        com.meteomontana.android.ui.components.FirstTimeHint(
            hintKey = "detail_offline",
            text = stringResource(R.string.school_detail_screen_v3_toca_arriba_para_guardar_esta)
        )
        com.meteomontana.android.ui.components.FirstTimeHint(
            hintKey = "detail_propose",
            text = stringResource(R.string.school_detail_screen_v3_despliega_el_mapa_de_abajo)
        )
        com.meteomontana.android.ui.components.FirstTimeHint(
            hintKey = "detail_tick",
            text = stringResource(R.string.school_detail_screen_v3_toca_una_piedra_en_el)
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
                    approachTo = { id -> approachesState.value.firstOrNull { it.toBlockId == id && it.isVerified } ?: approachesState.value.firstOrNull { it.toBlockId == id } },
                    onFollowApproach = { followingApproach = it },
                    contenidoTrasMapa = {
                        com.meteomontana.android.ui.components.ApproachesSection(
                            // Mismo motivo que blocksState: llegan del servidor
                            // DESPUÉS de la primera composición y se capturarían.
                            approaches = approachesState.value,
                            onFollow = { followingApproach = it }
                        )
                    }
                )
            }
        } }
        val blocksVm: SchoolBlocksViewModel = hiltViewModel()
        // Piedra abierta desde la GUÍA: al cerrar su ficha se vuelve a la GUÍA (y el marcapáginas baja hasta ella).
        var returnToGuide by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        var sheetSeen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        val sheetOpen by viewModel.blockSheetOpen.collectAsStateWithLifecycle()
        LaunchedEffect(sheetOpen) {
            if (!returnToGuide) return@LaunchedEffect
            if (sheetOpen) sheetSeen = true
            else if (sheetSeen) { returnToGuide = false; sheetSeen = false; tab = SchoolTab.BLOCKS }
        }
        // Salir del MAPA a otra pestaña a mano cancela la vuelta automática.
        LaunchedEffect(tab) { if (tab != SchoolTab.MAP) returnToGuide = false }

        if (forecast != null) {
            HoyCard(forecast)
        } else if (forecastError != null) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .padding(Spacing.lg)
            ) {
                Column {
                    Text(stringResource(R.string.school_detail_screen_v2_tiempo_no_disponible),
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
                Text(stringResource(R.string.school_detail_screen_v2_cargando_el_tiempo),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))

        when (tab) {
            // Piedras y mapa disponibles YA, con el tiempo cargando o caído.
            SchoolTab.MAP -> blocksSection()
            SchoolTab.BLOCKS -> SchoolGuideHost(
                school = school, blocks = blocks, viewModel = viewModel, blocksVm = blocksVm,
                scroller = guideScroller,
                goToMap = { returnAfter -> returnToGuide = returnAfter; sheetSeen = false; tab = SchoolTab.MAP }
            )
            SchoolTab.WEATHER -> if (forecast != null) {
                SchoolWeatherTab(
                    forecast = forecast,
                    // Boletín de montaña AEMET QUITADO (Álvaro, 2026-11-05): unas veces llega y otras no. Para volver a ponerlo,
                    // pasar aquí `bulletin = mountainBulletin?.let { b -> { MountainBulletinSection(b) } }` (solo España).
                    bulletin = null,
                    onDayClick = onDayClick
                )
            }
            SchoolTab.NOTES -> {
                NotesSection(notes = notes, onPublish = onPublishNote,
                    onVote = { n, v -> viewModel.voteNote(n, v) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                // (Guardar offline movido al toolbar superior, como en iOS.)
                val s = viewModel.uiState.collectAsStateWithLifecycle().value as? SchoolDetailUiState.Success
                MonthlyStatsSection(stats = s?.monthlyStats, isLoading = s?.monthlyLoading == true)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
    }
    }
    if (showRestrictions && restrictionsUi != null) {
        com.meteomontana.android.ui.components.RestrictionsSheet(
            restrictionsUi.restrictions.active, blocks, onDismiss = { showRestrictions = false })
    }

    // Aproximaciones: pantallas completas (Dialog), fuera del scroll de arriba.
    followingApproach?.let { a ->
        com.meteomontana.android.ui.screens.approach.ApproachFollowScreen(
            approach = a,
            schoolName = school.name,
            onDismiss = { followingApproach = null }
        )
    }
}

