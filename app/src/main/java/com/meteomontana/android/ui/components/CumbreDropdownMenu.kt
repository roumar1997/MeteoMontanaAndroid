package com.meteomontana.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/** Forma de todos los menús desplegables: esquinas de 10 dp, como las tarjetas de la app. */
internal val CumbreMenuShape = RoundedCornerShape(10.dp)

/**
 * Menú desplegable de la app («⋯», filtros, selectores): papel de Cumbre (`surface`) con borde de 1 dp (`outline`) y
 * esquinas de 10 dp, SIN el tinte lila de Material (contenedor por defecto = `surfaceContainer`, que en el tema
 * sale lila) y sin elevación tonal. Usar SIEMPRE este en vez de `DropdownMenu` a secas: así los menús salen todos
 * iguales (Álvaro, 2026-11-15). Las acciones destructivas pintan su texto/icono con `colorScheme.error`.
 */
@Composable
fun CumbreDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        properties = properties,
        shape = CumbreMenuShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        content = content
    )
}
