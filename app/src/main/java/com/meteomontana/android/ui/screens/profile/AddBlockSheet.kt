package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.BlockApi
import com.meteomontana.android.data.api.JournalApi
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.data.api.dto.SchoolDto
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
 * Sugerencias previas extraídas del diario del usuario por escuela.
 */
data class SchoolHistory(
    val sectors: List<String>,    // sectores previamente usados en esa escuela
    val blocks: List<String>      // nombres de bloque previamente usados
)

@HiltViewModel
class SchoolSearchViewModel @Inject constructor(
    private val api: SchoolApi,
    private val journalApi: JournalApi,
    private val blockApi: BlockApi
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<SchoolDto>>(emptyList())
    val results: StateFlow<List<SchoolDto>> = _results.asStateFlow()

    /** Historial del usuario en la escuela actualmente seleccionada (sectores y bloques previos). */
    private val _history = MutableStateFlow(SchoolHistory(emptyList(), emptyList()))
    val history: StateFlow<SchoolHistory> = _history.asStateFlow()

    /** Bloques reales registrados en la escuela seleccionada (BLOCK type). */
    private val _schoolBlocks = MutableStateFlow<List<Block>>(emptyList())
    val schoolBlocks: StateFlow<List<Block>> = _schoolBlocks.asStateFlow()

    private var allJournal: List<JournalSession> = emptyList()

    init {
        viewModelScope.launch {
            allJournal = runCatching { journalApi.getMyJournal().map { it.toDomain() } }.getOrDefault(emptyList())
        }
    }

    fun search(q: String) {
        _query.value = q
        viewModelScope.launch {
            delay(200)
            if (_query.value != q) return@launch
            _results.value = runCatching {
                if (q.isBlank()) emptyList() else api.searchSchools(q)
            }.getOrDefault(emptyList())
        }
    }

    fun onSchoolSelected(school: SchoolDto?) {
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
            _schoolBlocks.value = runCatching { blockApi.getBlocks(school.id).map { it.toDomain() } }.getOrDefault(emptyList())
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

    var selectedSchool by remember { mutableStateOf<SchoolDto?>(null) }
    var schoolQuery by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("") }
    var blockName by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var gradeMenuExpanded by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now().toString() }
    val results by searchVM.results.collectAsState()
    val history by searchVM.history.collectAsState()
    val schoolBlocks by searchVM.schoolBlocks.collectAsState()

    LaunchedEffect(schoolQuery, selectedSchool) {
        if (selectedSchool == null) searchVM.search(schoolQuery)
    }
    LaunchedEffect(selectedSchool) { searchVM.onSchoolSelected(selectedSchool) }

    // Sugerencias filtradas por lo que escribe el usuario
    val sectorSuggestions = remember(sector, history) {
        if (sector.isBlank()) history.sectors.take(5)
        else history.sectors.filter { it.contains(sector, ignoreCase = true) && it != sector }.take(5)
    }
    val blockSuggestions = remember(blockName, history, schoolBlocks) {
        val previous = history.blocks
        val fromSchool = schoolBlocks.filter { it.type == "BLOCK" }.map { it.name }
        val combined = (previous + fromSchool).distinct()
        if (blockName.isBlank()) combined.take(5)
        else combined.filter { it.contains(blockName, ignoreCase = true) && it != blockName }.take(5)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Añadir bloque", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)

            // ─── ESCUELA con autocomplete ───
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
                            text = "${sch.name}${sch.region?.let { " · $it" } ?: ""}",
                            onClick = { selectedSchool = sch; schoolQuery = sch.name }
                        )
                    }
                }
            }

            Label("FECHA")
            Text(today, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)

            // ─── SECTOR con autocomplete (sectores previos del usuario) ───
            Label("SECTOR (opcional)")
            OutlinedTextField(
                value = sector, onValueChange = { sector = it },
                placeholder = { Text("ej: Sector Bajo") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (sectorSuggestions.isNotEmpty()) {
                SuggestionsBox {
                    sectorSuggestions.forEach { s ->
                        SuggestionRow(text = s, onClick = { sector = s })
                    }
                }
            }

            // ─── BLOQUE con autocomplete (bloques previos + bloques de la escuela) ───
            Label("BLOQUE / VÍA")
            OutlinedTextField(
                value = blockName, onValueChange = { blockName = it },
                placeholder = { Text("ej: El Pollito") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (blockSuggestions.isNotEmpty()) {
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
                    value = grade ?: "—",
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
                placeholder = { Text("¿Qué tal fue?") },
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
                        date = today
                    ))
                },
                enabled = blockName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1A), contentColor = Color.White
                ),
                shape = MaterialTheme.shapes.small
            ) { Text("GUARDAR") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
