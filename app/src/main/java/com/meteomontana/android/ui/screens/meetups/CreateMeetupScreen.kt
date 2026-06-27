package com.meteomontana.android.ui.screens.meetups

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.CreateMeetupRequest
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Pantalla de creación de quedada. Campos: nombre, escuela, días, privacidad,
 *  disciplina y límite de participantes. */
@Composable
fun CreateMeetupScreen(
    onBack: () -> Unit = {},
    onCreated: (meetupId: String) -> Unit = {},
    viewModel: MeetupsViewModel = hiltViewModel()
) {
    val createError by viewModel.createError.collectAsState()
    val uploadingPhoto by viewModel.uploadingPhoto.collectAsState()

    var name by remember { mutableStateOf("") }
    var schoolId by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf("OPEN") }
    var discipline by remember { mutableStateOf<String?>(null) }
    var limitText by remember { mutableStateOf("") }
    val selectedDays = remember { mutableStateOf<Set<String>>(emptySet()) }
    var submitting by remember { mutableStateOf(false) }
    var photoUrl by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.uploadMeetupPhoto(bytes, mime) { url -> photoUrl = url }
        }
    }

    // Para el selector de escuela, reusamos búsqueda simple (cadena schoolId manual por ahora)
    // Se mejoraría con un picker de escuelas en próximas iteraciones.

    Column(Modifier.fillMaxSize()) {
        // ── Toolbar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Cancelar")
            }
            Text("Nueva quedada", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Foto del grupo
            FieldLabel("FOTO DEL GRUPO (opcional)")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { photoPicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Foto de la quedada",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AddAPhoto, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("AÑADIR FOTO", style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (uploadingPhoto) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }

            // Nombre
            FieldLabel("NOMBRE")
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                placeholder = { Text("Ej. Quedar en Pedriza") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(4.dp),
                colors = outlinedColors()
            )

            // Escuela (ID por ahora; en siguiente iteración picker de búsqueda)
            FieldLabel("ID DE ESCUELA")
            OutlinedTextField(
                value = schoolId, onValueChange = { schoolId = it },
                placeholder = { Text("ID de la escuela del backend") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(4.dp),
                colors = outlinedColors()
            )

            // Días (picker de próximos 14 días)
            FieldLabel("DÍAS (elige uno o varios)")
            DayPickerRow(
                selected = selectedDays.value,
                onToggle = { day ->
                    selectedDays.value = if (selectedDays.value.contains(day))
                        selectedDays.value - day else selectedDays.value + day
                }
            )

            // Privacidad
            FieldLabel("PRIVACIDAD")
            PrivacySelector(selected = privacy, onSelected = { privacy = it })

            // Disciplina
            FieldLabel("DISCIPLINA (opcional)")
            DisciplineSelector(selected = discipline, onSelected = { discipline = it })

            // Límite
            FieldLabel("LÍMITE DE PARTICIPANTES (opcional)")
            OutlinedTextField(
                value = limitText, onValueChange = { limitText = it.filter { c -> c.isDigit() } },
                placeholder = { Text("Sin límite") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(4.dp),
                colors = outlinedColors()
            )

            // Error
            createError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }

        // Botón crear
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().navigationBarsPadding()
            .background(MaterialTheme.colorScheme.surface).padding(Spacing.md)) {
            Button(
                onClick = {
                    if (name.isBlank() || schoolId.isBlank() || selectedDays.value.isEmpty()) return@Button
                    submitting = true
                    val req = CreateMeetupRequest(
                        schoolId = schoolId.trim(),
                        name = name.trim(),
                        discipline = discipline,
                        privacy = privacy,
                        memberLimit = limitText.toIntOrNull(),
                        photoUrl = photoUrl,
                        days = selectedDays.value.sorted()
                    )
                    viewModel.create(req) { meetup ->
                        submitting = false
                        onCreated(meetup.id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting && name.isNotBlank() && schoolId.isNotBlank() && selectedDays.value.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("CREAR QUEDADA")
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DayPickerRow(selected: Set<String>, onToggle: (String) -> Unit) {
    val days = nextNDays(14)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        days.forEach { (label, iso) ->
            val on = selected.contains(iso)
            val bg = if (on) MaterialTheme.colorScheme.primaryContainer
                     else MaterialTheme.colorScheme.surface
            val fg = if (on) MaterialTheme.colorScheme.onPrimaryContainer
                     else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .clickable { onToggle(iso) }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(label.first, style = MaterialTheme.typography.labelSmall, color = fg,
                    fontWeight = FontWeight.Bold)
                Text(label.second, style = MaterialTheme.typography.labelSmall, color = fg)
            }
        }
    }
}

private fun nextNDays(n: Int): List<Pair<Pair<String, String>, String>> {
    val result = mutableListOf<Pair<Pair<String, String>, String>>()
    val dayNames = listOf("DOM","LUN","MAR","MIÉ","JUE","VIE","SÁB")
    // Simple: usamos System.currentTimeMillis para calcular los próximos n días
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
        val label = dayNames[dow] to "%02d/%02d".format(d, mo)
        result.add(label to iso)
    }
    return result
}

@Composable
private fun PrivacySelector(selected: String, onSelected: (String) -> Unit) {
    val options = listOf(
        "OPEN" to "Abierta",
        "FOLLOWERS" to "Solo seguidores",
        "WOMEN" to "No mixto"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { (key, label) ->
            ToggleChip(label = label, selected = selected == key, onClick = { onSelected(key) })
        }
    }
}

@Composable
private fun DisciplineSelector(selected: String?, onSelected: (String?) -> Unit) {
    val options = listOf(
        null to "Cualquiera",
        "BOULDER" to "Bloque",
        "ROUTE" to "Vía",
        "BOTH" to "Ambas"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { (key, label) ->
            ToggleChip(label = label, selected = selected == key, onClick = { onSelected(key) })
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun outlinedColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)

