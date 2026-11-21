package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.meteomontana.android.domain.model.gym.GymOrder
import com.meteomontana.android.domain.model.gym.GymSummaryGym
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

/** Una fila numerada del dialogo con sus botones «Subir» y «Bajar» (grises al no poder mas). */
@Composable
private fun OrderRow(index: Int, label: String, last: Int, onMove: (Int) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}", fontWeight = FontWeight.Bold, color = terraFillColor(), modifier = Modifier.padding(end = 10.dp))
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        MoveButton(Icons.Outlined.ArrowUpward, stringResource(R.string.gym_asc_order_up), index > 0) { onMove(-1) }
        MoveButton(Icons.Outlined.ArrowDownward, stringResource(R.string.gym_asc_order_down), index < last) { onMove(1) }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

/**
 * Dialogo «Ordenar» con dos secciones y botones «Subir» / «Bajar»: las categorias de Ascensiones (orden en [AscentTabOrder])
 * y los rocodromos (orden en [GymOrder]; [gyms] ya viene en el orden que se ve ahora, y al mover se guardan todos).
 */
@Composable
internal fun AscentOrderDialog(
    order: List<AscentFilter>, gyms: List<GymSummaryGym>,
    onChange: (List<AscentFilter>) -> Unit, onChangeGyms: (List<Long>) -> Unit, onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.gym_asc_order_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.gym_order_cats))
                Text(stringResource(R.string.gym_asc_order_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                order.forEachIndexed { i, f -> OrderRow(i, ascentFilterLabel(f), order.lastIndex) { d -> onChange(AscentTabOrder.move(order, i, d)) } }
                if (gyms.size > 1) {
                    SectionLabel(stringResource(R.string.gym_order_gyms))
                    Text(stringResource(R.string.gym_order_gyms_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val ids = gyms.map { it.gymId }
                    gyms.forEachIndexed { i, g -> OrderRow(i, g.name ?: "-", gyms.lastIndex) { d -> onChangeGyms(GymOrder.move(ids, i, d)) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.common_done)) } }
    )
}
