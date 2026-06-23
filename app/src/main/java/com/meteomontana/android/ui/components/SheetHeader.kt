package com.meteomontana.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Cabecera estándar de los sheets (paridad con los .sheet de iOS): título o
 * contenido centrado, acciones opcionales + botón "Cerrar" a la derecha, y una
 * línea divisoria debajo. La usa Cuenta (Perfil) y la replican el resto de
 * pantallas que se presentan como bottom-sheet (Notificaciones, Buscar, Chats,
 * conversación de chat) para que todas se abran y se vean igual.
 *
 * Junto con un contenedor `Modifier.fillMaxWidth().fillMaxHeight(0.94f)` produce
 * el look de "tarjeta que sube y deja asomar la pantalla anterior".
 */
@Composable
fun SheetHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    center: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        center()
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()
            TextButton(onClick = onClose) {
                Text("Cerrar", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
}

/** Variante con título de texto centrado (el caso más común). */
@Composable
fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    SheetHeader(onClose = onClose, modifier = modifier, actions = actions) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center))
    }
}
