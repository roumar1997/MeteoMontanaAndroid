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
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.CreateJournalRequest
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

@HiltViewModel
class SchoolSearchViewModel @Inject constructor(
    private val api: SchoolApi
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<SchoolDto>>(emptyList())
    val results: StateFlow<List<SchoolDto>> = _results.asStateFlow()

    fun search(q: String) {
        _query.value = q
        viewModelScope.launch {
            delay(200)               // pequeño debounce
            if (_query.value != q) return@launch
            _results.value = runCatching {
                if (q.isBlank()) emptyList() else api.searchSchools(q)
            }.getOrDefault(emptyList())
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

    LaunchedEffect(schoolQuery, selectedSchool) {
        if (selectedSchool == null) searchVM.search(schoolQuery)
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

            Label("ESCUELA")
            OutlinedTextField(
                value = selectedSchool?.name ?: schoolQuery,
                onValueChange = { schoolQuery = it; selectedSchool = null },
                placeholder = { Text("Buscar escuela...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // Resultados debajo
            if (selectedSchool == null && results.isNotEmpty()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .background(MaterialTheme.colorScheme.surface)) {
                    Column {
                        results.take(5).forEach { sch ->
                            Text(
                                "${sch.name}${sch.region?.let { " · $it" } ?: ""}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSchool = sch
                                        schoolQuery = sch.name
                                    }
                                    .padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Label("FECHA")
            Text(today, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)

            Label("SECTOR (opcional)")
            OutlinedTextField(
                value = sector, onValueChange = { sector = it },
                placeholder = { Text("ej: Sector Bajo") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Label("BLOQUE / VÍA")
            OutlinedTextField(
                value = blockName, onValueChange = { blockName = it },
                placeholder = { Text("ej: El Pollito") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

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
