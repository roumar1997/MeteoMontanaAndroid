package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** Nombre de cada categoria de ascensiones (el de su pestaña). */
@Composable
internal fun ascentFilterLabel(f: AscentFilter): String = stringResource(
    when (f) {
        AscentFilter.ALL -> R.string.gym_asc_tab_all; AscentFilter.BLOCKS -> R.string.gym_asc_tab_blocks
        AscentFilter.ROUTES -> R.string.gym_asc_tab_routes; AscentFilter.BOARDS -> R.string.gym_asc_tab_boards
    }
)

/** Dialogo para ordenar las categorias de Ascensiones con flechas arriba/abajo; el orden vive en [AscentTabOrder]. */
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
                            .border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = terraFillColor(), modifier = Modifier.padding(end = 10.dp))
                        Text(ascentFilterLabel(f), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onChange(AscentTabOrder.move(order, i, -1)) }, enabled = i > 0) {
                            Icon(Icons.Outlined.ArrowUpward, null)
                        }
                        IconButton(onClick = { onChange(AscentTabOrder.move(order, i, 1)) }, enabled = i < order.lastIndex) {
                            Icon(Icons.Outlined.ArrowDownward, null)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.common_done)) } }
    )
}
