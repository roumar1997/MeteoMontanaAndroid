package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.AscentFilter
import com.meteomontana.android.domain.model.gym.AscentTabOrder
import com.meteomontana.android.ui.theme.terraFillColor

/** Botón con texto claro («Subir» / «Bajar») para mover una categoría; gris si no se puede mover más. */
@Composable
private fun MoveButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = if (enabled) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
        Text(label, color = c, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Nombre de cada categoria de ascensiones (el de su pestaña). */
@Composable
internal fun ascentFilterLabel(f: AscentFilter): String = stringResource(
    when (f) {
        AscentFilter.ALL -> R.string.gym_asc_tab_all; AscentFilter.BLOCKS -> R.string.gym_asc_tab_blocks
        AscentFilter.ROUTES -> R.string.gym_asc_tab_routes; AscentFilter.BOARDS -> R.string.gym_asc_tab_boards
    }
)

/** Pastilla «Ordenar» (icono + texto) de la cabecera de Ascensiones: abre [AscentOrderDialog]. */
@Composable
internal fun OrderPill(onClick: () -> Unit) {
    val c = terraFillColor()
    Row(
        Modifier.clip(CircleShape).border(1.dp, c, CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Outlined.SwapVert, null, tint = c, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.gym_asc_order_btn), color = c, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
    }
}

/** Dialogo para ordenar las categorias de Ascensiones con botones «Subir» y «Bajar»; el orden vive en [AscentTabOrder]. */
@Composable
internal fun AscentOrderDialog(order: List<AscentFilter>, onChange: (List<AscentFilter>) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.gym_asc_order_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.gym_asc_order_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                order.forEachIndexed { i, f ->
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = terraFillColor(), modifier = Modifier.padding(end = 10.dp))
                        Text(ascentFilterLabel(f), fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                        MoveButton(Icons.Outlined.ArrowUpward, stringResource(R.string.gym_asc_order_up), i > 0) { onChange(AscentTabOrder.move(order, i, -1)) }
                        MoveButton(Icons.Outlined.ArrowDownward, stringResource(R.string.gym_asc_order_down), i < order.lastIndex) { onChange(AscentTabOrder.move(order, i, 1)) }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.common_done)) } }
    )
}
