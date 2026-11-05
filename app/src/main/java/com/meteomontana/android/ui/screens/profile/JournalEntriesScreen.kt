package com.meteomontana.android.ui.screens.profile

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonGradeRow
import androidx.compose.foundation.layout.height

import androidx.compose.ui.unit.sp

import kotlin.math.roundToInt

import androidx.compose.material.icons.filled.MoreHoriz

import androidx.compose.material.icons.outlined.CalendarMonth

import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.layout.offset

import androidx.compose.ui.draw.clipToBounds

import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.gestures.detectHorizontalDragGestures

import androidx.compose.material.icons.outlined.Search

import androidx.compose.foundation.layout.width
import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.util.AppText
import androidx.compose.runtime.setValue
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.usecase.journal.DeleteJournalEntryUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalStatsUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserJournalUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserStatsUseCase
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun JournalEntriesScreen(
    onBack: () -> Unit,
    onOpenSchool: (schoolId: String, via: String?, viaId: String?) -> Unit = { _, _, _ -> },
    viewModel: JournalEntriesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val styleError by viewModel.styleError.collectAsStateWithLifecycle()
    val estiloCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(styleError) {
        styleError?.let {
            android.widget.Toast.makeText(estiloCtx, estiloCtx.getString(R.string.journal_entries_screen_v3_no_se_pudo_cambiar_estilo, it), android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearStyleError()
        }
    }
    // G: filtro por grado, de VARIOS a la vez (vacío = todos). Un grado doble ("6b+/6c") cuenta para sus dos chips.
    var gradeFilters by androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(
            save = { it.toList() }, restore = { it.toSet() })
    ) { androidx.compose.runtime.mutableStateOf(emptySet<String>()) }
    // Filtro por ESTILO: "aVista" y/o "alFlash"; vacío = todos. Se pueden encender los dos para ver juntas
    // las de ambos estilos. Cliente, como el de grado — no necesita ruta nueva (Rodrigo, 2026-08-21).
    var styleFilters by androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(
            save = { it.toList() }, restore = { it.toSet() })
    ) { androidx.compose.runtime.mutableStateOf(emptySet<String>()) }
    // Buscar por nombre de vía, escuela, sector o nº de piedra (Álvaro, 2026-10-19).
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(viewModel.title, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        androidx.compose.material3.OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.journal_search_hint)) },
            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.Search, contentDescription = null) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            JournalEntriesUiState.Loading -> SkeletonList(count = 8) { SkeletonGradeRow() }
            is JournalEntriesUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is JournalEntriesUiState.Success -> {
                if (s.entries.isEmpty()) {
                    com.meteomontana.android.ui.components.EmptyState(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = stringResource(R.string.journal_entries_screen_v2_tu_diario_esta_vacio),
                        message = stringResource(R.string.journal_entries_screen_v3_marca_el_de_una_via)
                    )
                } else {
                    LazyColumn {
                        item {
                            com.meteomontana.android.ui.components.FirstTimeHint(
                                hintKey = "journal_tap_via",
                                text = stringResource(R.string.journal_entries_screen_v3_toca_una_via_para_ir)
                            )
                        }
                        // Si estamos viendo una escuela concreta, fila para abrir
                        // la escuela (sin piedra).
                        val headerSchoolId = s.entries.firstOrNull { !it.schoolId.isNullOrBlank() }?.schoolId
                        if (s.filter?.startsWith("school:") == true && headerSchoolId != null) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { onOpenSchool(headerSchoolId, null, null) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.common_view_school),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        // G: filtro por GRADO (chips con la paleta de topos).
                        val grades = s.entries
                            .flatMap { com.meteomontana.android.ui.components.gradeParts(it.grade) }
                            .distinct()
                            .sortedByDescending {
                                com.meteomontana.android.domain.usecase.journal.JournalStatsCalculator.gradeRank(it)
                            }
                        if (grades.size > 1) {
                            item(key = "grade-filter") {
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    items(grades.size) { gi ->
                                        val g = grades[gi]
                                        val gs = com.meteomontana.android.ui.theme.gradeChipStyle(g)
                                        val accent = if (gs.dark) MaterialTheme.colorScheme.onSurface else gs.stroke
                                        val active = g in gradeFilters
                                        Box(Modifier
                                            .background(
                                                if (active) accent else MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(6.dp))
                                            .border(1.dp, accent, RoundedCornerShape(6.dp))
                                            .clickable { gradeFilters = if (active) gradeFilters - g else gradeFilters + g }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)) {
                                            Text(g, style = MaterialTheme.typography.labelMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = if (active) androidx.compose.ui.graphics.Color.White
                                                        else accent)
                                        }
                                    }
                                }
                            }
                        }
                        // Filtro por ESTILO: solo si hay al menos una entrada marcada
                        // (si no, no tiene sentido mostrar chips que no filtran nada).
                        val anyStyled = s.entries.any { it.aVista || it.alFlash }
                        if (anyStyled) {
                            item(key = "style-filter") {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    StyleBadge(stringResource(R.string.journal_entries_screen_v4_a_vista), active = "aVista" in styleFilters) {
                                        styleFilters = if ("aVista" in styleFilters) styleFilters - "aVista" else styleFilters + "aVista"
                                    }
                                    StyleBadge(stringResource(R.string.journal_entries_screen_v4_al_flash), active = "alFlash" in styleFilters) {
                                        styleFilters = if ("alFlash" in styleFilters) styleFilters - "alFlash" else styleFilters + "alFlash"
                                    }
                                }
                            }
                        }
                        val visibleEntries = s.entries.filter {
                            com.meteomontana.android.ui.components.gradeMatches(it.grade, gradeFilters) &&
                                journalMatches(it, s.viaInfo[it.id], query)
                        }.let { list ->
                            if (styleFilters.isEmpty()) list
                            else list.filter { ("aVista" in styleFilters && it.aVista) || ("alFlash" in styleFilters && it.alFlash) }
                        }
                        // C3: agrupado por MES (cabecera "JULIO 2026 - N"), orden
                        // cronologico descendente por fecha de la sesion.
                        val byMonth = visibleEntries.sortedByDescending { it.date }
                            .groupBy { it.date.take(7) }
                        byMonth.forEach { (month, monthEntries) ->
                            item(key = "month-" + month) {
                                Text(
                                    formatMonthHeader(month) + " \u00b7 " + monthEntries.size,
                                    style = EyebrowTextStyle,
                                    color = com.meteomontana.android.ui.theme.Terra,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(monthEntries, key = { it.id }) { e ->
                                EntryRow(
                                    e, canDelete = s.isMine,
                                    info = s.viaInfo[e.id],
                                    onClick = { e.schoolId?.let { onOpenSchool(it, e.blockName, e.lineId) } },
                                    onDelete = { viewModel.delete(e.id) },
                                    onChangeDate = if (s.isMine) ({ newDate ->
                                        viewModel.changeDate(e.id, newDate)
                                    }) else null,
                                    onChangeStyle = if (s.isMine) ({ aVista, alFlash ->
                                        viewModel.changeStyle(e.id, aVista, alFlash)
                                    }) else null
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}

// internal (no private): reutilizado por JournalSectorsScreen.kt (mismo paquete)
// para las entradas sin sector, que se muestran directamente sin subcarpeta.
@androidx.compose.runtime.Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun EntryRow(
    e: JournalSession,
    canDelete: Boolean = true,
    info: com.meteomontana.android.domain.usecase.journal.ViaCatalogInfo? = null,
    onClick: () -> Unit = {},
    onDelete: () -> Unit,
    /** C3: cambiar la fecha de la entrada (null = no editable, diario ajeno). */
    onChangeDate: ((String) -> Unit)? = null,
    /** Cambiar el estilo (a vista / al flash), independientes entre sí.
     *  null = no editable (diario ajeno). */
    onChangeStyle: ((aVista: Boolean, alFlash: Boolean) -> Unit)? = null
) {
    var showDatePicker by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    // Borrar pide confirmación: un toque sin querer en el cubo no se lleva la entrada (Álvaro, 2026-10-19).
    var confirmDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.journal_delete_confirm_title, e.blockName)) },
            text = { Text(stringResource(R.string.journal_delete_confirm_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.journal_entries_screen_v2_borrar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
    if (showDatePicker && onChangeDate != null) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                java.time.LocalDate.parse(e.date.take(10)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= System.currentTimeMillis()
            })
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onChangeDate(java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) { androidx.compose.material3.DatePicker(state = pickerState) }
    }
    // Deslizar la fila a la izquierda descubre FECHA y BORRAR; el «⋯» hace lo mismo con nombre (Álvaro, 2026-10-19,
    // espejo de JournalRow de iOS). Solo cuenta el arrastre horizontal: el scroll de la lista no se toca.
    val canEdit = canDelete || onChangeDate != null
    val actionsWidth = (if (onChangeDate != null) 76.dp else 0.dp) + (if (canDelete) 76.dp else 0.dp)
    val actionsPx = with(androidx.compose.ui.platform.LocalDensity.current) { actionsWidth.toPx() }
    val swipe = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val closeSwipe: () -> Unit = { scope.launch { swipe.animateTo(0f) } }
    var menuOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Box(
        Modifier.fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clipToBounds()
    ) {
        if (canEdit && swipe.value < 0f) {
            Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
                if (onChangeDate != null) {
                    SwipeAction(stringResource(R.string.journal_swipe_date), Icons.Outlined.CalendarMonth,
                        com.meteomontana.android.ui.theme.Ink3) { closeSwipe(); showDatePicker = true }
                }
                if (canDelete) {
                    SwipeAction(stringResource(R.string.journal_entries_screen_v2_borrar), Icons.Outlined.Delete,
                        MaterialTheme.colorScheme.error) { closeSwipe(); confirmDelete = true }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(swipe.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .then(if (canEdit) Modifier.pointerInput(actionsPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch { swipe.animateTo(if (swipe.value < -actionsPx / 2) -actionsPx else 0f) }
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch { swipe.snapTo((swipe.value + dx).coerceIn(-actionsPx - 60f, 0f)) }
                        }
                    )
                } else Modifier)
                // Con la fila abierta, tocarla la cierra (en vez de abrir la piedra).
                .clickable(enabled = swipe.value < 0f || e.schoolId != null) {
                    if (swipe.value < 0f) closeSwipe() else onClick()
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Miniatura de la piedra con el trazo de la vía, como en el buscador (Álvaro, 2026-10-19).
            info?.photoUrl?.takeIf { it.isNotBlank() }?.let { photo ->
                com.meteomontana.android.ui.screens.schools.MiniTopoThumbnail(photo, info.linePath, info.grade ?: e.grade,
                    Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)))
            }
            // Grado ACTUAL del catálogo (refleja correcciones) o el guardado al marcar la vía: cuadrado de su color.
            val eGrade = info?.grade ?: e.grade
            val eSplit = com.meteomontana.android.ui.components.rememberLineSplit(e.lineId)
            if (!eGrade.isNullOrBlank() && eSplit != null) {
                // La altura parte la opinión: el grado va en dos colores.
                com.meteomontana.android.ui.screens.detail.GradePill(eGrade, lineId = e.lineId)
            } else if (!eGrade.isNullOrBlank()) {
                val gs = com.meteomontana.android.ui.theme.gradeChipStyle(eGrade)
                Box(
                    Modifier.size(width = 44.dp, height = 32.dp).background(gs.stroke)
                        .then(if (gs.dark) Modifier.border(1.dp, MaterialTheme.colorScheme.outline) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(eGrade.uppercase(), fontFamily = com.meteomontana.android.ui.theme.Mono,
                        fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1,
                        color = if (gs.dark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val deleted = info?.deleted == true
                Text(e.blockName,
                    fontFamily = com.meteomontana.android.ui.theme.Serif,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 16.sp,
                    color = if (deleted) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onBackground)
                if (deleted) {
                    Text(stringResource(R.string.journal_entries_screen_via_eliminada),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Estilo de ascensión: solo se ve si hay algo que enseñar (ajeno) o si es editable (propio).
                if (e.aVista || e.alFlash || onChangeStyle != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (e.aVista || onChangeStyle != null) {
                            StyleBadge(stringResource(R.string.journal_entries_screen_v4_a_vista), active = e.aVista,
                                onClick = onChangeStyle?.let { { it(!e.aVista, false) } })
                        }
                        if (e.alFlash || onChangeStyle != null) {
                            StyleBadge(stringResource(R.string.journal_entries_screen_v4_al_flash), active = e.alFlash,
                                onClick = onChangeStyle?.let { { it(false, !e.alFlash) } })
                        }
                    }
                }
                // Escuela · Piedra N · sector · fecha («12 oct»): la fecha ya no es un botón escondido.
                val subtitle = listOfNotNull(
                    e.schoolName?.takeIf { it.isNotBlank() },
                    info?.boulderNumber?.takeIf { it.isNotBlank() }?.let { "Piedra $it" },
                    info?.sector?.takeIf { it.isNotBlank() },
                    shortJournalDate(e.date)
                ).joinToString(" · ")
                Text(subtitle, fontFamily = com.meteomontana.android.ui.theme.Mono, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val eNotes = e.notes
                // Ocultamos la nota auto "Piedra: N" (obsoleta: el número se recicla).
                if (!eNotes.isNullOrBlank() && !eNotes.startsWith("Piedra: ")) {
                    Text(eNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (canEdit) {
                // «⋯»: todo lo que se puede hacer con la entrada, con nombre y un botón grande.
                Box {
                    Box(
                        Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(com.meteomontana.android.ui.components.cumbrePillBg())
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.journal_more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onChangeDate != null) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.journal_change_date)) },
                                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                                    tint = com.meteomontana.android.ui.theme.Terra) },
                                onClick = { menuOpen = false; showDatePicker = true })
                        }
                        if (canDelete) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.journal_delete_from_log), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDelete = true })
                        }
                    }
                }
            } else if (e.schoolId != null) {
                // Flecha que indica que la fila es pulsable → abre la piedra.
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.journal_entries_screen_v2_ver_piedra),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Botón que descubre el deslizar (FECHA gris / BORRAR rojo). */
@Composable
internal fun SwipeAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                        color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).fillMaxHeight().background(color).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

/** «2026-10-12» → «12 oct» (en el idioma del móvil, sin el punto de la abreviatura). */
internal fun shortJournalDate(iso: String): String = runCatching {
    java.time.LocalDate.parse(iso.take(10))
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))
        .replace(".", "")
}.getOrDefault(iso.take(10))

/** Chip pequeño de estilo (A VISTA / AL FLASH) — pulsable si onClick != null,
 *  para poder marcarlo/desmarcarlo sin abrir un diálogo aparte. */
@Composable
internal fun StyleBadge(label: String, active: Boolean, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .clip(com.meteomontana.android.ui.theme.CumbrePillShape)
            .background(if (active) com.meteomontana.android.ui.theme.Terra
                        else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (active) com.meteomontana.android.ui.theme.Terra
                          else MaterialTheme.colorScheme.outlineVariant,
                com.meteomontana.android.ui.theme.CumbrePillShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = if (active) androidx.compose.ui.graphics.Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun formatMonthHeader(yyyyMm: String): String = runCatching {
    CalendarLabels.monthsLong()[yyyyMm.substringAfter('-').toInt() - 1].uppercase() + " " + yyyyMm.take(4)
}.getOrDefault(yyyyMm)

/** ¿Casa con lo buscado? Nombre de la vía, escuela, sector o nº de piedra; sin tildes ni mayúsculas. Vacío = todas. */
internal fun journalMatches(e: JournalSession, info: com.meteomontana.android.domain.usecase.journal.ViaCatalogInfo?, query: String): Boolean {
    fun fold(t: String) = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase()
    val q = fold(query.trim())
    if (q.isEmpty()) return true
    return fold(listOf(e.blockName, e.schoolName.orEmpty(), info?.sector ?: e.sector.orEmpty(), info?.boulderNumber.orEmpty())
        .joinToString(" ")).contains(q)
}
