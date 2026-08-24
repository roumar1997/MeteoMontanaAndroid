package com.meteomontana.android.ui.screens.profile

import com.meteomontana.android.ui.theme.inkButtonColor

import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.cumbreSheetSurface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import com.meteomontana.android.domain.usecase.schools.SearchSchoolsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private val GRADES = listOf("4", "5a", "5b", "5c", "6a", "6a+", "6b", "6b+", "6c", "6c+",
    "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+", "8b", "8b+", "8c", "8c+", "9a", "9a+")

/**
 * Sugerencias previas extraÃ­das del diario del usuario por escuela.
 */
data class SchoolHistory(
    val sectors: List<String>,    // sectores previamente usados en esa escuela
    val blocks: List<String>      // nombres de bloque previamente usados
)

/** Sugerencia de sector: puede ser uno catalogado (ZONE existente, blockId != null)
 *  o uno del historial del usuario sin id. */
data class SectorSuggestion(val name: String, val blockId: String?)

/** VÃ­a concreta sugerida al usuario, con su grado y tipo de inicio si los tiene. */
data class LineSuggestion(
    val blockName: String,
    val name: String,           // nombre de la vÃ­a (o "L1" si la vÃ­a no tiene nombre)
    val grade: String?,
    val startType: String?,
    val discipline: String = "BOULDER"  // BOULDER (bloque) / ROUTE (vÃ­a) â€” de la piedra
) {
    val displayLabel: String get() = buildString {
        append(name)
        val extras = listOfNotNull(grade, startType).joinToString(" Â· ")
        if (extras.isNotEmpty()) append(" Â· ").append(extras)
        append(" â€” ").append(blockName)
    }
}

@HiltViewModel
class SchoolSearchViewModel @Inject constructor(
    private val searchSchools: SearchSchoolsUseCase,
    private val getMyJournal: GetMyJournalUseCase,
    private val getBlocks: GetBlocksUseCase
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<School>>(emptyList())
    val results: StateFlow<List<School>> = _results.asStateFlow()

    /** Historial del usuario en la escuela actualmente seleccionada (sectores y bloques previos). */
    private val _history = MutableStateFlow(SchoolHistory(emptyList(), emptyList()))
    val history: StateFlow<SchoolHistory> = _history.asStateFlow()

    /** Bloques reales registrados en la escuela seleccionada (BLOCK type). */
    private val _schoolBlocks = MutableStateFlow<List<Block>>(emptyList())
    val schoolBlocks: StateFlow<List<Block>> = _schoolBlocks.asStateFlow()

    private var allJournal: List<JournalSession> = emptyList()

    init {
        viewModelScope.launch {
            allJournal = runCatching { getMyJournal() }.getOrDefault(emptyList())
        }
    }

    fun search(q: String) {
        _query.value = q
        viewModelScope.launch {
            delay(200)
            if (_query.value != q) return@launch
            _results.value = runCatching {
                if (q.isBlank()) emptyList() else searchSchools(q)
            }.getOrDefault(emptyList())
        }
    }

    fun onSchoolSelected(school: School?) {
        if (school == null) {
            _history.value = SchoolHistory(emptyList(), emptyList())
            _schoolBlocks.value = emptyList()
            return
        }
        // De mi diario: sectores y bloques previos en esta escuela
        val mine = allJournal.filter {
            it.schoolId == school.id ||
                    it.schoolName?.equals(school.name, ignoreCase = true) == true
        }
        _history.value = SchoolHistory(
            sectors = mine.mapNotNull { it.sector }.distinct(),
            blocks  = mine.map { it.blockName }.distinct()
        )
        // De los bloques registrados en la escuela
        viewModelScope.launch {
            _schoolBlocks.value = runCatching { getBlocks(school.id) }.getOrDefault(emptyList())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlockSheet(
    onDismiss: () -> Unit,
    onSave: (CreateJournalRequest) -> Unit,
    searchVM: SchoolSearchViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedSchool by remember { mutableStateOf<School?>(null) }
    var schoolQuery by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("") }
    var blockName by remember { mutableStateOf("") }
    // Modalidad: BOULDER (bloque) o ROUTE (vÃ­a). Antes se omitÃ­a â†’ toda entrada
    // manual caÃ­a en "Bloques" y nunca en "VÃ­as" (el diario separa por discipline).
    var discipline by remember { mutableStateOf("BOULDER") }
    var grade by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var gradeMenuExpanded by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now().toString() }
    val results by searchVM.results.collectAsStateWithLifecycle()
    val history by searchVM.history.collectAsStateWithLifecycle()
    val schoolBlocks by searchVM.schoolBlocks.collectAsStateWithLifecycle()

    LaunchedEffect(schoolQuery, selectedSchool) {
        if (selectedSchool == null) searchVM.search(schoolQuery)
    }
    LaunchedEffect(selectedSchool) { searchVM.onSchoolSelected(selectedSchool) }

    // Sugerencias de SECTOR: historial del usuario + sectores reales (ZONE)
    // catalogados en la escuela. Cuando el usuario pulsa uno catalogado guardamos
    // su id para poder filtrar las vÃ­as por sector.
    var selectedSectorBlockId by remember { mutableStateOf<String?>(null) }
    // Se calculan en CADA recomposiciÃ³n (no en un `remember` cacheado) para que,
    // en cuanto lleguen por red los bloques/sectores de la escuela, el recuadro
    // de sugerencias aparezca solo â€” igual que iOS, que recalcula inline. Con un
    // `remember` el recuadro no se refrescaba hasta tocar el campo.
    val sectorSuggestions = run {
        val real = schoolBlocks.filter { it.type == "ZONE" }
            .map { SectorSuggestion(it.name, it.id) }
        val historical = history.sectors.map { SectorSuggestion(it, null) }
        val combined = (real + historical).distinctBy { it.name }
        val filtered = if (sector.isBlank()) combined
        else combined.filter { it.name.contains(sector, ignoreCase = true) && it.name != sector }
        filtered.take(6)
    }

    // VÃ­as reales (con grado + tipo). Si hay sector seleccionado, filtramos a las
    // vÃ­as de las piedras de ese sector.
    val lineSuggestions = run {
        val blocksScope = schoolBlocks.filter { it.type == "BLOCK" }
            .let { all ->
                if (selectedSectorBlockId != null)
                    all.filter { it.sectorBlockId == selectedSectorBlockId }
                else all
            }
        val all = blocksScope.flatMap { b ->
            b.lines.map { l ->
                LineSuggestion(
                    blockName = b.name,
                    name = l.name.ifBlank { "L${l.sortOrder + 1}" },
                    grade = l.grade,
                    startType = l.startType,
                    discipline = b.discipline
                )
            }
        }
        if (blockName.isBlank()) all.take(6)
        else all.filter {
            it.name.contains(blockName, ignoreCase = true) ||
                it.blockName.contains(blockName, ignoreCase = true)
        }.take(6)
    }
    // Fallback: si la escuela aÃºn no tiene vÃ­as catalogadas, sugerimos bloques.
    val blockSuggestions = run {
        if (lineSuggestions.isNotEmpty()) emptyList()
        else {
            val previous = history.blocks
            val fromSchool = schoolBlocks.filter { it.type == "BLOCK" }.map { it.name }
            val combined = (previous + fromSchool).distinct()
            if (blockName.isBlank()) combined.take(5)
            else combined.filter { it.contains(blockName, ignoreCase = true) && it != blockName }.take(5)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        shape = com.meteomontana.android.ui.components.CumbreSheetShape
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .cumbreSheetSurface(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AÃ±adir bloque", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)

            // â”€â”€â”€ ESCUELA con autocomplete â”€â”€â”€
            val closeKeyboard = com.meteomontana.android.ui.components.rememberKeyboardDismisser()
            Label("ESCUELA")
            OutlinedTextField(
                value = selectedSchool?.name ?: schoolQuery,
                onValueChange = { schoolQuery = it; selectedSchool = null },
                placeholder = { Text("Buscar escuela...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (selectedSchool == null && results.isNotEmpty()) {
                SuggestionsBox {
                    results.take(5).forEach { sch ->
                        SuggestionRow(
                            text = "${sch.name}${sch.region?.let { " Â· $it" } ?: ""}",
                            onClick = {
                                closeKeyboard(); selectedSchool = sch; schoolQuery = sch.name
                            }
                        )
                    }
                }
            }

            Label("FECHA")
            Text(today, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)

            // â”€â”€â”€ SECTOR con autocomplete (sectores previos del usuario) â”€â”€â”€
            Label("SECTOR (opcional)")
            OutlinedTextField(
                value = sector,
                onValueChange = { sector = it; selectedSectorBlockId = null },
                placeholder = { Text("ej: Sector Bajo") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (sectorSuggestions.isNotEmpty()) {
                SuggestionsBox {
                    sectorSuggestions.forEach { sug ->
                        val label = if (sug.blockId != null) "${sug.name} Â· catalogado" else sug.name
                        SuggestionRow(
                            text = label,
                            onClick = {
                                sector = sug.name
                                selectedSectorBlockId = sug.blockId
                            }
                        )
                    }
                }
            }

            // â”€â”€â”€ MODALIDAD: bloque o vÃ­a (decide en quÃ© lista del diario cae) â”€â”€â”€
            Label("MODALIDAD")
            ModalityToggle(selected = discipline, onSelect = { discipline = it })

            // â”€â”€â”€ NOMBRE con autocomplete (bloques/vÃ­as previos + de la escuela) â”€â”€â”€
            Label(if (discipline == "ROUTE") "VÃA" else "BLOQUE")
            OutlinedTextField(
                value = blockName, onValueChange = { blockName = it },
                placeholder = { Text("ej: El Pollito") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (lineSuggestions.isNotEmpty()) {
                SuggestionsBox {
                    lineSuggestions.forEach { l ->
                        SuggestionRow(
                            text = l.displayLabel,
                            onClick = {
                                blockName = l.name
                                if (!l.grade.isNullOrBlank()) grade = l.grade
                                // Al elegir una vÃ­a catalogada, hereda su modalidad.
                                discipline = l.discipline
                            }
                        )
                    }
                }
            } else if (blockSuggestions.isNotEmpty()) {
                SuggestionsBox {
                    blockSuggestions.forEach { b ->
                        SuggestionRow(text = b, onClick = { blockName = b })
                    }
                }
            }

            Label("GRADO")
            ExposedDropdownMenuBox(
                expanded = gradeMenuExpanded,
                onExpandedChange = { gradeMenuExpanded = !gradeMenuExpanded }
            ) {
                OutlinedTextField(
                    value = grade ?: "â€”",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                DropdownMenu(
                    expanded = gradeMenuExpanded,
                    onDismissRequest = { gradeMenuExpanded = false }
                ) {
                    GRADES.forEach { g ->
                        DropdownMenuItem(text = { Text(g) },
                            onClick = { grade = g; gradeMenuExpanded = false })
                    }
                }
            }

            Label("NOTAS (opcional)")
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                placeholder = { Text("Â¿QuÃ© tal fue?") },
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    onSave(CreateJournalRequest(
                        schoolId = selectedSchool?.id,
                        schoolName = selectedSchool?.name ?: schoolQuery.takeIf { it.isNotBlank() },
                        sector = sector.takeIf { it.isNotBlank() },
                        blockName = blockName,
                        grade = grade,
                        notes = notes.takeIf { it.isNotBlank() },
                        date = today,
                        discipline = discipline
                    ))
                },
                enabled = blockName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = inkButtonColor(), contentColor = Color.White
                ),
                shape = MaterialTheme.shapes.small
            ) { Text("GUARDAR") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Selector Bloque / VÃ­a. Decide el campo `discipline` de la entrada del diario,
 *  que es lo que separa las listas "Mis bloques" y "Mis vÃ­as" del perfil. */
@Composable
private fun ModalityToggle(selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModalityOption("BLOQUE", selected == "BOULDER", Modifier.weight(1f)) { onSelect("BOULDER") }
        ModalityOption("VÃA", selected == "ROUTE", Modifier.weight(1f)) { onSelect("ROUTE") }
    }
}

@Composable
private fun ModalityOption(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (active) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .background(bg, MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun SuggestionsBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 160.dp)
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
    ) { Column { content() } }
}

@Composable
private fun SuggestionRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}
