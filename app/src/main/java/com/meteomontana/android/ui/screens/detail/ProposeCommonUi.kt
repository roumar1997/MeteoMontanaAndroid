@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra

// Primitivas COMPARTIDAS del flujo de proponer (reparto del antiguo
// ProposeContributionFlow.kt de 1.595 líneas): footer de envío, selectores
// segmentados y el contenedor de diálogo estilo Cumbre.

// ─── Footer de envío compartido (CANCELAR + ENVIAR con error/reintento) ─────
//
// El envío puede fallar (sin cobertura en la escuela = caso habitual): al
// fallar se resetea el spinner, se muestra el error y el botón vuelve a
// ENVIAR — los datos del formulario NUNCA se pierden. Si [onSaveOffline] no
// es null, además se ofrece guardar la propuesta para enviarla al recuperar
// cobertura (cola offline).
@Composable
internal fun SubmitFooter(
    sending: Boolean,
    error: String?,
    submitEnabled: Boolean = true,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    onSaveOffline: (() -> Unit)? = null
) {
    if (error != null) {
        Text(error, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        if (onSaveOffline != null) {
            Spacer(Modifier.height(Spacing.xs))
            Box(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable(enabled = !sending, onClick = onSaveOffline)
                .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center) {
                Text("GUARDAR Y ENVIAR CON COBERTURA", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
    }
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .clickable(enabled = !sending, onClick = onCancel)
            .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.common_cancel), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Box(modifier = Modifier.weight(1.5f).clip(MaterialTheme.shapes.small)
            .background(if (submitEnabled) Terra else MaterialTheme.colorScheme.outline)
            .clickable(enabled = !sending && submitEnabled, onClick = onSubmit)
            .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center) {
            if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp),
                color = Color.White, strokeWidth = 2.dp)
            else Text(
                if (error != null) "REINTENTAR" else stringResource(R.string.propose_submit),
                style = EyebrowTextStyle, color = Color.White
            )
        }
    }
}

// ─── Selectores segmentados ───────────────────────────────────────────────────

/** Selector de modalidad de la piedra: BLOQUE (BOULDER) o VÍA (ROUTE).
 *  Reutilizado al proponer/crear piedra y al editarla (admin). */
@Composable
fun DisciplineSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    SegmentedSelector(
        options = listOf(
            "BOULDER" to stringResource(R.string.propose_discipline_boulder),
            "ROUTE" to stringResource(R.string.propose_discipline_route)
        ),
        selected = selected,
        onSelect = onSelect
    )
}

/** Selector de geometría de la piedra: PUNTO (marcador) o MURO (polilínea). */
@Composable
fun GeometrySelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    SegmentedSelector(
        options = listOf(
            "POINT" to stringResource(R.string.propose_geometry_point),
            "LINE" to stringResource(R.string.propose_geometry_wall)
        ),
        selected = selected,
        onSelect = onSelect
    )
}

/** Sentido de numeración de las vías del muro. */
@Composable
fun DirectionSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    SegmentedSelector(
        options = listOf(
            "LTR" to stringResource(R.string.propose_direction_ltr),
            "RTL" to stringResource(R.string.propose_direction_rtl)
        ),
        selected = selected,
        onSelect = onSelect
    )
}

/** Botonera segmentada genérica (mismo estilo en todos los selectores). */
@Composable
internal fun SegmentedSelector(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        options.forEach { (value, label) ->
            val sel = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .then(if (sel) Modifier.background(Terra) else Modifier)
                    .border(
                        1.dp,
                        if (sel) Terra else MaterialTheme.colorScheme.outline,
                        MaterialTheme.shapes.small
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = EyebrowTextStyle,
                    color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ─── Contenedor de diálogo estilo Cumbre ─────────────────────────────────────

@Composable
internal fun CumbreDialog(
    onDismiss: () -> Unit,
    scrollable: Boolean = false,
    /** Tarjeta a pantalla (casi) completa (formularios), como el resto de sheets. */
    fullHeight: Boolean = false,
    content: @Composable () -> Unit
) {
    // Bottom-sheet flotante (sube desde abajo, esquinas superiores redondeadas,
    // scrim) — paridad con los .sheet de iOS, en vez de un diálogo centrado.
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        val colMod = Modifier
            .then(if (fullHeight) Modifier.fillMaxWidth()
                .fillMaxHeight(0.94f) else Modifier)
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.lg)
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        Column(modifier = colMod) { content() }
    }
}

@Composable
internal fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor   = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedIndicatorColor   = Terra,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
)
