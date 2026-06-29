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

                // Días concretos (próximos 14, mismo rango que crear)
                SectionLabel("DÍAS")
                Text("Avísame si la quedada incluye alguno de estos días (vacío = cualquier día)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val nextDays = remember { nextNDaysAlert(14) }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    nextDays.forEach { (iso, label) ->
                        AlertChip(label = label, selected = selectedDays.contains(iso),
                            onClick = {
                                selectedDays = if (selectedDays.contains(iso))
                                    selectedDays - iso else selectedDays + iso
                            })
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
                    viewModel.toggleAlert(enabled, daysCsv)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (enabled) "GUARDAR ALERTA" else "DESACTIVAR ALERTA")
            }
        }
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
