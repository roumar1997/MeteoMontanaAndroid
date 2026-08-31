package com.meteomontana.android.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteomontana.android.domain.model.AdminSuggestionRow
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

/**
 * Pestaña "SUGERENCIAS" del panel admin: buzón del botón "?" de ayuda
 * (Álvaro, 2026-08-31: "poder responder o verlo más veces para poder
 * consultarlo"). Responder avisa al autor por push y marca atendida sola.
 */
@Composable
internal fun SugerenciasTab(
    rows: List<AdminSuggestionRow>?,
    onRespond: (id: String, resolved: Boolean?, reply: String?) -> Unit
) {
    if (rows == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Sin sugerencias todavía", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val sorted = remember(rows) { rows.sortedBy { it.resolved } }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(sorted, key = { it.id }) { row ->
            SuggestionCard(row, onRespond)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SuggestionCard(row: AdminSuggestionRow, onRespond: (String, Boolean?, String?) -> Unit) {
    var replyText by remember(row.id) { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                (row.displayName ?: row.email ?: row.uid) + " · " + row.platform +
                    (row.appVersion?.let { " $it" } ?: ""),
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.primary
            )
            if (row.resolved) {
                Text("ATENDIDA", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(row.message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground)
        row.createdAt?.let {
            Text(it.take(16), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        row.adminReply?.let { reply ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            ) {
                Text("TU RESPUESTA", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(reply, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value = replyText,
                onValueChange = { replyText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Responder…") },
                singleLine = true
            )
            Button(
                onClick = { onRespond(row.id, null, replyText); replyText = "" },
                enabled = replyText.isNotBlank()
            ) { Text("ENVIAR") }
        }
        if (!row.resolved) {
            TextButton(onClick = { onRespond(row.id, true, null) }) {
                Text("MARCAR ATENDIDA")
            }
        }
    }
}
