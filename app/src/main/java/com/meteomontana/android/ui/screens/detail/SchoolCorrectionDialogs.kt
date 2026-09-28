package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.util.AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

// Corregir NOMBRE / ESTILO de la escuela — tipos SCHOOL_NAME_CORRECTION y
// SCHOOL_STYLE_CORRECTION, sin tocar el mapa (a diferencia de CORRECTION, que
// mueve posiciones). Espejo de SchoolNameCorrectionSheet / SchoolStyleCorrectionSheet
// en ContributionSheets.swift (iOS).

@Composable
internal fun SchoolNameCorrectionDialog(
    currentName: String,
    onCancel: () -> Unit,
    onSubmit: suspend (newName: String) -> Boolean
) {
    var name by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    CumbreDialog(onDismiss = onCancel, scrollable = true, fullHeight = true) {
        Text(stringResource(R.string.school_correction_dialogs_v2_corregir_nombre),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.lg))

        Text(stringResource(R.string.school_correction_dialogs_v2_nombre_actual), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text(currentName, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.md))

        Text(stringResource(R.string.school_correction_dialogs_v2_nombre_propuesto), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.school_correction_dialogs_v2_nombre_correcto), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Spacer(Modifier.height(Spacing.lg))

        SubmitFooter(
            sending = sending, error = error,
            submitEnabled = name.isNotBlank(),
            onCancel = onCancel,
            onSubmit = {
                sending = true; error = null
                scope.launch {
                    val ok = onSubmit(name.trim())
                    sending = false
                    if (!ok) error = AppText.get(R.string.place_form_dialog_v3_no_se_pudo_enviar_revisa)
                }
            }
        )
    }
}

@Composable
internal fun SchoolStyleCorrectionDialog(
    currentStyle: String?,
    onCancel: () -> Unit,
    onSubmit: suspend (newStyle: String) -> Boolean
) {
    // Preselecciona lo que la escuela YA tiene: si el admin no toca nada, la
    // propuesta no cambia el estilo. Fix pedido por Rodrigo (misma sesión que
    // el equivalente iOS): antes arrancaba vacío y era fácil borrar por
    // descuido el estilo que ya estaba bien al añadir el que faltaba.
    val currentSet = remember(currentStyle) {
        (currentStyle ?: "").split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }
    var selected by remember(currentStyle) { mutableStateOf(currentSet) }
    val options = remember(currentSet) { (currentSet + setOf(AppText.get(R.string.school_map_v3_via), "Bloque")).sorted() }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    CumbreDialog(onDismiss = onCancel, scrollable = true, fullHeight = true) {
        Text(stringResource(R.string.school_correction_dialogs_v2_corregir_estilo),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.lg))

        Text(stringResource(R.string.school_correction_dialogs_v2_estilo_actual), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            if (!currentStyle.isNullOrBlank()) currentStyle else stringResource(R.string.school_correction_dialogs_v3_sin_especificar),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))

        Text(stringResource(R.string.school_correction_dialogs_v2_estilo_propuesto), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(options) { opt ->
                CumbreChip(
                    label = opt,
                    selected = opt in selected,
                    onClick = {
                        selected = if (opt in selected) selected - opt else selected + opt
                    }
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))

        SubmitFooter(
            sending = sending, error = error,
            submitEnabled = selected.isNotEmpty(),
            onCancel = onCancel,
            onSubmit = {
                sending = true; error = null
                scope.launch {
                    val ok = onSubmit(selected.sorted().joinToString(","))
                    sending = false
                    if (!ok) error = AppText.get(R.string.place_form_dialog_v3_no_se_pudo_enviar_revisa)
                }
            }
        )
    }
}
