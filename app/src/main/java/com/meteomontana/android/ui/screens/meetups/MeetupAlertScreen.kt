package com.meteomontana.android.ui.screens.meetups

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

@Composable
fun MeetupAlertScreen(
    onBack: () -> Unit = {},
    viewModel: MeetupsViewModel = hiltViewModel()
) {
    val alertState by viewModel.alertState.collectAsState()
    val schoolResults by viewModel.schoolResults.collectAsState()

    var enabled by remember(alertState) { mutableStateOf(alertState?.enabled == true) }
    var selectedDays by remember(alertState) {
        mutableStateOf(alertState?.daysCsv?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet())
    }
    var selectedPrivacy by remember { mutableStateOf<String?>(null) }
    var selectedDiscipline by remember { mutableStateOf<String?>(null) }
    var radiusKm by remember { mutableStateOf<Int?>(null) }
    var filterSchoolName by remember { mutableStateOf<String?>(null) }
    var filterSchoolId by remember { mutableStateOf<String?>(null) }
    var showSchoolPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadAlertState() }

    Column(Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver")
            }
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null,
                modifier = Modifier.size(20.dp).padding(end = 4.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("Alertas de quedadas", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Activar/desactivar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Activar alertas", style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium)
                    Text("Recibe una notificación cuando se cree una quedada que te interese",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            if (enabled) {
                HorizontalDivider()

                // Días concretos (próximos 10)
                SectionLabel("DÍAS")
                Text("Avísame si la quedada incluye alguno de estos días (vacío = cualquier día)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val next10 = remember { nextNDaysAlert(10) }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    next10.forEach { (iso, label) ->
                        AlertChip(label = label, selected = selectedDays.contains(iso),
                            onClick = {
                                selectedDays = if (selectedDays.contains(iso))
                                    selectedDays - iso else selectedDays + iso
                            })
                    }
                }

                // Escuela
                SectionLabel("ESCUELA")
                Text("Solo quedadas en esta escuela (vacío = cualquier escuela)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { showSchoolPicker = true }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text(
                        text = filterSchoolName ?: "Cualquier escuela",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (filterSchoolName != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (filterSchoolName != null) {
                        IconButton(onClick = { filterSchoolId = null; filterSchoolName = null },
                            modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Outlined.Close, null, Modifier.size(14.dp))
                        }
                    }
                }

                // Distancia
                SectionLabel("DISTANCIA")
                Text("Solo quedadas en escuelas cerca de ti",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    val distances = listOf(null to "Cualquiera", 25 to "< 25 km", 50 to "< 50 km",
                        100 to "< 100 km", 200 to "< 200 km")
                    distances.forEach { (km, label) ->
                        AlertChip(label = label, selected = radiusKm == km,
                            onClick = { radiusKm = km })
                    }
                }

                // Tipo de grupo
                SectionLabel("TIPO DE GRUPO")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    val privacies = listOf(null to "Cualquiera", "OPEN" to "Abierta",
                        "FOLLOWERS" to "Seguidos/Seguidores", "WOMEN" to "No mixto")
                    privacies.forEach { (key, label) ->
                        AlertChip(label = label, selected = selectedPrivacy == key,
                            onClick = { selectedPrivacy = key })
                    }
                }

                // Disciplina
                SectionLabel("DISCIPLINA")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    val disciplines = listOf(null to "Cualquiera", "BOULDER" to "Bloque",
                        "ROUTE" to "Vía", "BOTH" to "Ambas")
                    disciplines.forEach { (key, label) ->
                        AlertChip(label = label, selected = selectedDiscipline == key,
                            onClick = { selectedDiscipline = key })
                    }
                }
            }
        }

        // Botón guardar
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(Spacing.md)) {
            Button(
                onClick = {
                    val daysCsv = if (selectedDays.isEmpty()) null
                                  else selectedDays.sorted().joinToString(",")
                    viewModel.saveAlert(
                        enabled = enabled,
                        daysCsv = daysCsv,
                        schoolId = filterSchoolId,
                        privacy = selectedPrivacy,
                        discipline = selectedDiscipline,
                        radiusKm = radiusKm
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (enabled) "GUARDAR ALERTA" else "DESACTIVAR ALERTA")
            }
        }
    }

    if (showSchoolPicker) {
        AlertSchoolPickerDialog(
            results = schoolResults,
            onQueryChange = { viewModel.searchSchools(it) },
            onSelect = { school ->
                filterSchoolId = school.id
                filterSchoolName = school.name
                showSchoolPicker = false
                viewModel.clearSchoolSearch()
            },
            onDismiss = { showSchoolPicker = false; viewModel.clearSchoolSearch() }
        )
    }
}

private fun nextNDaysAlert(n: Int): List<Pair<String, String>> {
    val dayNames = listOf("dom","lun","mar","mié","jue","vie","sáb")
    val monthNames = listOf("ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic")
    val result = mutableListOf<Pair<String, String>>()
    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L
    for (i in 0 until n) {
        val ts = now + i * dayMs
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        val y = cal.get(java.util.Calendar.YEAR)
        val mo = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
        val iso = "%04d-%02d-%02d".format(y, mo, d)
        val label = "${dayNames[dow]} $d ${monthNames[mo - 1]}"
        result.add(iso to label)
    }
    return result
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AlertChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
             else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(12.dp), tint = fg)
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun AlertSchoolPickerDialog(
    results: List<School>,
    onQueryChange: (String) -> Unit,
    onSelect: (School) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar escuela", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; onQueryChange(it) },
                    placeholder = { Text("Ej. Zarzalejo, Pedriza…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp)
                )
                if (results.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(results) { school ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(school) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text(school.name, style = MaterialTheme.typography.bodyMedium)
                                school.location?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                } else if (query.length >= 2) {
                    Text("Sin resultados", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
