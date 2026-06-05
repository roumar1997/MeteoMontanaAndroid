package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Moss
import com.meteomontana.android.ui.theme.OkDark
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import kotlinx.coroutines.launch

// ─── Estados internos del flujo ─────────────────────────────────────────────

private sealed interface ProposeStep {
    data object TypePicker   : ProposeStep   // elegir tipo
    data object WaitingMapTap: ProposeStep   // "pulsa en el mapa"
    data class  Form(val lat: Double, val lon: Double) : ProposeStep  // formulario
    data object Success      : ProposeStep   // propuesta enviada
}

// ─── Punto de entrada ────────────────────────────────────────────────────────

/**
 * Flujo completo de "Proponer mejora":
 *
 *   TypePicker → (PARKING) → WaitingMapTap → Form → Success
 *
 * La pantalla padre (SchoolMap) debe:
 *   - Mostrar el banner "PULSA EN EL MAPA" cuando `waitingForTap == true`
 *   - Llamar a `onMapTap(lat, lon)` cuando el usuario toque el mapa en ese estado
 *
 * @param waitingForTap  true mientras el flujo espera un tap en el mapa
 * @param onMapTap       el mapa llama a esto con las coords cuando el usuario toca
 * @param onDismiss      cierra todo el flujo
 * @param onMyProposals  navega a "Mis propuestas"
 * @param viewModel      para enviar la propuesta al back
 */
@Composable
fun ProposeContributionFlow(
    schoolName: String,
    waitingForTap: Boolean,
    onStartWaitingTap: () -> Unit,
    onMapTap: ((Double, Double) -> Unit) -> Unit,   // registra el callback en el padre
    onDismiss: () -> Unit,
    onMyProposals: () -> Unit,
    viewModel: SchoolDetailViewModel
) {
    var step by remember { mutableStateOf<ProposeStep>(ProposeStep.TypePicker) }
    val scope = rememberCoroutineScope()

    // Registra el callback de tap en el mapa una sola vez
    onMapTap { lat, lon ->
        if (step is ProposeStep.WaitingMapTap) {
            step = ProposeStep.Form(lat, lon)
        }
    }

    when (val s = step) {
        is ProposeStep.TypePicker -> TypePickerDialog(
            onParking = {
                step = ProposeStep.WaitingMapTap
                onStartWaitingTap()
            },
            onDismiss = onDismiss
        )

        is ProposeStep.WaitingMapTap -> {
            // El banner lo pinta SchoolMap. Aquí no hay Dialog.
            // Si el usuario cancela el banner:
            BannerCancelOverlay(onCancel = onDismiss)
        }

        is ProposeStep.Form -> ParkingFormDialog(
            lat = s.lat,
            lon = s.lon,
            onCancel = onDismiss,
            onSubmit = { name, notes ->
                scope.launch {
                    val req = ContributionRequest(
                        type = "PARKING",
                        name = name.takeIf { it.isNotBlank() },
                        lat = s.lat,
                        lon = s.lon,
                        notes = notes.takeIf { it.isNotBlank() },
                        description = null,
                        proposedLat = null,
                        proposedLon = null,
                        correctionReason = null,
                        targetBlockId = null
                    )
                    val result = viewModel.submitContribution(req)
                    if (result.isSuccess) step = ProposeStep.Success
                }
            }
        )

        is ProposeStep.Success -> SuccessDialog(
            onClose = onDismiss,
            onMyProposals = { onDismiss(); onMyProposals() }
        )
    }
}

// ─── TypePickerDialog ────────────────────────────────────────────────────────

@Composable
private fun TypePickerDialog(
    onParking: () -> Unit,
    onDismiss: () -> Unit
) {
    CumbreDialog(onDismiss = onDismiss) {
        Text(
            "Proponer una mejora",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "El admin revisará tu propuesta antes de publicarla.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.lg))

        TypeOption(
            icon = "+",
            label = "AÑADIR PIEDRA",
            description = "Una roca con uno o más bloques/vías",
            enabled = false,   // próximamente
            onClick = {}
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        TypeOption(
            icon = "+",
            label = "AÑADIR SECTOR",
            description = "Sector dentro de la escuela (ej: La Isla, Vertedero…)",
            enabled = false,
            onClick = {}
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        TypeOption(
            icon = "■",
            label = "AÑADIR PARKING",
            description = "Punto de aparcamiento para acceder a la escuela",
            enabled = true,
            onClick = onParking
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        TypeOption(
            icon = "↔",
            label = "CORREGIR POSICIÓN",
            description = "Sugerir nueva ubicación de la escuela",
            enabled = false,
            onClick = {}
        )

        Spacer(Modifier.height(Spacing.lg))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable(onClick = onDismiss)
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "CERRAR",
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TypeOption(
    icon: String,
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(icon, color = Terra.copy(alpha = alpha), style = MaterialTheme.typography.titleMedium)
        Column {
            Text(
                label,
                style = EyebrowTextStyle,
                color = Terra.copy(alpha = alpha)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}

// ─── Banner cancelar (cuando WaitingMapTap sin dialog) ──────────────────────

@Composable
private fun BannerCancelOverlay(onCancel: () -> Unit) {
    // El banner visual lo pinta SchoolMap. Esto solo expone un composable
    // transparente para capturar el cancel si el usuario pulsa fuera.
    // En la práctica SchoolMap pintará el botón ✕ que llama a onDismiss.
}

// ─── ParkingFormDialog ───────────────────────────────────────────────────────

@Composable
private fun ParkingFormDialog(
    lat: Double,
    lon: Double,
    onCancel: () -> Unit,
    onSubmit: (name: String, notes: String) -> Unit
) {
    var name  by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    CumbreDialog(onDismiss = onCancel) {
        Text(
            "Nuevo parking",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Pulsa en el mapa para fijar la posición",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.lg))

        // Campo nombre
        Text("NOMBRE (OPCIONAL)", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ej: Parking principal, Área forestal…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Spacer(Modifier.height(Spacing.md))

        // Coordenadas (solo lectura)
        Text("COORDENADAS (LAT, LON)", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "%.6f, %.6f".format(lat, lon),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = com.meteomontana.android.ui.theme.Mono),
            color = Terra
        )
        Text(
            "✓ POSICIÓN DESDE EL MAPA",
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(Spacing.md))

        // Notas
        Text("NOTAS (OPCIONAL)", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            placeholder = { Text("Capacidad, restricciones, horario…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Spacer(Modifier.height(Spacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // CANCELAR
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable(enabled = !sending, onClick = onCancel)
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text("CANCELAR", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            // ENVIAR PROPUESTA
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .clip(MaterialTheme.shapes.small)
                    .background(Terra)
                    .clickable(enabled = !sending) {
                        sending = true
                        onSubmit(name, notes)
                    }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("ENVIAR PROPUESTA", style = EyebrowTextStyle, color = Color.White)
                }
            }
        }
    }
}

// ─── SuccessDialog ───────────────────────────────────────────────────────────

@Composable
private fun SuccessDialog(
    onClose: () -> Unit,
    onMyProposals: () -> Unit
) {
    CumbreDialog(onDismiss = onClose) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Círculo verde con ✓
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Moss),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                "PROPUESTA ENVIADA",
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Un admin la revisará en ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "24-48h.",
                style = MaterialTheme.typography.bodyMedium,
                color = Terra
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Te avisaremos por email y notificación\npush cuando haya respuesta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable(onClick = onClose)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CERRAR", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.onBackground)
                        .clickable(onClick = onMyProposals)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("VER MIS PROPUESTAS", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
}

// ─── Primitivas compartidas ───────────────────────────────────────────────────

@Composable
private fun CumbreDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(Spacing.lg)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor   = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedIndicatorColor   = Terra,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
)
