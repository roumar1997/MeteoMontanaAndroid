package com.meteomontana.android.ui.screens.detail


import com.meteomontana.android.util.AppText
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

// Formularios simples de "sitio con nombre y notas". Parking y sector eran DOS
// diálogos casi idénticos copiados (solo cambiaban los textos y si el nombre es
// obligatorio) — ahora comparten UNA implementación (DRY del reparto del
// antiguo ProposeContributionFlow.kt).

@Composable
internal fun ParkingFormDialog(
    lat: Double,
    lon: Double,
    onCancel: () -> Unit,
    onSubmit: suspend (name: String, notes: String) -> Boolean,
    onSaveOffline: ((name: String, notes: String) -> Unit)? = null
) = PlaceFormDialog(
    title = stringResource(R.string.place_form_dialog_v2_nuevo_parking),
    subtitle = stringResource(R.string.place_form_dialog_v2_anade_un_punto_de_aparcamiento),
    nameLabel = stringResource(R.string.place_form_dialog_v3_nombre_opcional),
    namePlaceholder = stringResource(R.string.place_form_dialog_v3_ej_parking_principal_area_forestal),
    nameRequired = false,
    notesPlaceholder = stringResource(R.string.place_form_dialog_v4_capacidad_restricciones_horario),
    notesFieldHeight = 100.dp,
    showPositionBadge = true,
    lat = lat, lon = lon,
    onCancel = onCancel, onSubmit = onSubmit, onSaveOffline = onSaveOffline
)

@Composable
internal fun SectorFormDialog(
    lat: Double,
    lon: Double,
    onCancel: () -> Unit,
    onSubmit: suspend (name: String, notes: String) -> Boolean,
    onSaveOffline: ((name: String, notes: String) -> Unit)? = null
) = PlaceFormDialog(
    title = stringResource(R.string.place_form_dialog_v2_nuevo_sector),
    subtitle = stringResource(R.string.place_form_dialog_v2_un_sector_agrupa_varias_piedras),
    nameLabel = "NOMBRE",
    namePlaceholder = stringResource(R.string.place_form_dialog_v3_ej_la_isla_vertedero_cuevas),
    nameRequired = true,
    notesPlaceholder = stringResource(R.string.place_form_dialog_v3_tipo_de_roca_orientacion_accesos),
    notesFieldHeight = 80.dp,
    showPositionBadge = false,
    lat = lat, lon = lon,
    onCancel = onCancel, onSubmit = onSubmit, onSaveOffline = onSaveOffline
)

@Composable
private fun PlaceFormDialog(
    title: String,
    subtitle: String,
    nameLabel: String,
    namePlaceholder: String,
    nameRequired: Boolean,
    notesPlaceholder: String,
    notesFieldHeight: androidx.compose.ui.unit.Dp,
    showPositionBadge: Boolean,
    lat: Double,
    lon: Double,
    onCancel: () -> Unit,
    onSubmit: suspend (name: String, notes: String) -> Boolean,
    onSaveOffline: ((name: String, notes: String) -> Unit)?
) {
    var name  by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    CumbreDialog(onDismiss = onCancel, scrollable = true, fullHeight = true) {
        Text(title,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.xs))
        Text(subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.lg))

        Text(nameLabel, style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(namePlaceholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Spacer(Modifier.height(Spacing.md))

        Text(stringResource(R.string.place_form_dialog_v2_coordenadas_lat_lon), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text("%.5f, %.5f".format(java.util.Locale.US, lat, lon),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
            color = Terra)
        if (showPositionBadge) {
            Text(stringResource(R.string.place_form_dialog_posicion_desde_el_mapa), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(Spacing.md))

        Text(stringResource(R.string.place_form_dialog_v2_notas_opcional), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().height(notesFieldHeight),
            placeholder = { Text(notesPlaceholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Spacer(Modifier.height(Spacing.lg))

        SubmitFooter(
            sending = sending, error = error,
            submitEnabled = !nameRequired || name.isNotBlank(),
            onCancel = onCancel,
            onSubmit = {
                sending = true; error = null
                scope.launch {
                    val ok = onSubmit(name, notes)
                    sending = false
                    if (!ok) error = AppText.get(R.string.place_form_dialog_v3_no_se_pudo_enviar_revisa)
                }
            },
            onSaveOffline = onSaveOffline?.let { save -> { save(name, notes) } }
        )
    }
}
