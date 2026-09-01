package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteomontana.android.help.HelpCatalog
import com.meteomontana.android.help.HelpTopic
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/**
 * Botón "?" reutilizable que abre la hoja de ayuda contextual de una pantalla.
 * [topicKey] = clave del [HelpCatalog] (p.ej. "schools", "detail", "profile"…).
 */
@Composable
fun HelpButton(topicKey: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = "Ayuda",
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
    if (open) HelpSheet(topicKey = topicKey, onDismiss = { open = false })
}

private fun helpIcon(name: String): ImageVector = when (name) {
    "filter" -> Icons.Outlined.FilterList
    "calendar" -> Icons.Outlined.CalendarMonth
    "star" -> Icons.Outlined.Star
    "compare" -> Icons.Outlined.CompareArrows
    "map" -> Icons.Outlined.Map
    "plus" -> Icons.Outlined.AddCircleOutline
    "clock" -> Icons.Outlined.Schedule
    "download" -> Icons.Outlined.Download
    "tick" -> Icons.Outlined.CheckCircle
    "edit" -> Icons.Outlined.Edit
    "wall" -> Icons.Outlined.ViewColumn
    "book" -> Icons.AutoMirrored.Outlined.MenuBook
    "person" -> Icons.Outlined.People
    "bell" -> Icons.Outlined.Notifications
    "chat" -> Icons.Outlined.ChatBubbleOutline
    "reply" -> Icons.AutoMirrored.Outlined.Reply
    "wifioff" -> Icons.Outlined.CloudOff
    else -> Icons.Outlined.Info
}

/** Hoja de ayuda: cabecera, intro destacada y filas icono + título + descripción. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(topicKey: String, onDismiss: () -> Unit) {
    val topic: HelpTopic = HelpCatalog.byKey(topicKey) ?: return
    // ARRASTRAR PARA CERRAR, solo desde arriba (mismo arreglo que
    // BlockDetailDialog.kt/AddLinesFlow.kt): hojas largas competían el gesto
    // de cierre con el scroll.
    val contenidoScroll = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { valor ->
            valor != SheetValue.Hidden || contenidoScroll.value == 0
        }
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        shape = CumbreSheetShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // El fondo ANTES del scroll: puesto después se desplazaría con
                // el contenido en vez de quedarse quieto detrás.
                .cumbreSheetSurface()
                .verticalScroll(contenidoScroll)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("AYUDA", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
                Text(
                    topic.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            // Intro en caja tintada.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(Spacing.md)
            ) {
                Text(
                    topic.intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            // Botón "Sugerir algo / reportar un fallo" — buzón directo al
            // admin (Álvaro, 2026-08-31: "quiero un botón para añadir fallos
            // o mejoras que se le ocurran a la gente"). Arriba del todo:
            // Álvaro pidió que no quedara perdido al final de la hoja.
            var mostrandoSugerencia by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable { mostrandoSugerencia = true }
                    .padding(Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Outlined.Feedback,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Sugerir algo / reportar un fallo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (mostrandoSugerencia) {
                SuggestionDialog(onDismiss = { mostrandoSugerencia = false })
            }
            // Filas: icono en círculo + título + descripción.
            topic.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            helpIcon(item.icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            item.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // Botón "Volver a ver las pistas" — accesible desde cualquier hoja de ayuda
            val ctx = LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable {
                        resetAllHints(ctx)
                        android.widget.Toast.makeText(
                            ctx,
                            "Pistas reactivadas — vuelve a cada pantalla para verlas",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    .padding(Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.profile_show_hints),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.padding(bottom = Spacing.lg))
        }
    }
}

/** Diálogo simple de texto libre → POST /api/suggestions (sin cola de revisión). */
@Composable
private fun SuggestionDialog(onDismiss: () -> Unit) {
    val vm: SuggestionViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    var texto by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state == SuggestionSendState.SENT) {
            kotlinx.coroutines.delay(1200)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (state != SuggestionSendState.SENDING) onDismiss() },
        title = { Text("Sugerir algo o reportar un fallo") },
        text = {
            when (state) {
                SuggestionSendState.SENT -> Text("¡Gracias! Lo hemos recibido.")
                else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "Cuéntanos qué te gustaría que hiciera la app o qué no funciona bien.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("Escribe aquí…") },
                        enabled = state != SuggestionSendState.SENDING
                    )
                    if (state == SuggestionSendState.ERROR) {
                        Text(
                            "No se pudo enviar. Inténtalo otra vez.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state != SuggestionSendState.SENT) {
                TextButton(
                    onClick = { vm.send(texto) },
                    enabled = texto.isNotBlank() && state != SuggestionSendState.SENDING
                ) {
                    if (state == SuggestionSendState.SENDING) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("ENVIAR")
                    }
                }
            }
        },
        dismissButton = {
            if (state != SuggestionSendState.SENDING && state != SuggestionSendState.SENT) {
                TextButton(onClick = onDismiss) { Text("CANCELAR") }
            }
        }
    )
}
