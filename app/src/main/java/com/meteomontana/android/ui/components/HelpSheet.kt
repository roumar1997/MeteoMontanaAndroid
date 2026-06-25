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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteomontana.android.help.HelpCatalog
import com.meteomontana.android.help.HelpTopic
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
            Spacer(Modifier.padding(bottom = Spacing.lg))
        }
    }
}
