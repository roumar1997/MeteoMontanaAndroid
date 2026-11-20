package com.meteomontana.android.ui.screens.meetups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.MeetupGearItem
import com.meteomontana.android.domain.model.MeetupMember
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.terraFillColor

/** Clave de `gearJson` (la de siempre) de cada material de [MeetupGearItem]. */
private fun legacyKey(item: String): String = when (item) {
    MeetupGearItem.ROPE -> "cuerda"
    MeetupGearItem.GRIGRI -> "grigri"
    MeetupGearItem.QUICKDRAWS -> "cintas"
    else -> "crashpads"
}

private fun gearIcon(key: String): ImageVector = when (key) {
    "cuerda" -> Icons.Outlined.Link
    "grigri" -> Icons.Outlined.Security
    "cintas" -> Icons.Outlined.Straighten
    else -> Icons.Outlined.ViewAgenda
}

/** Material que se ofrece según el lugar y la modalidad (misma regla que al crear la quedada: `forPlace`). */
internal fun gearKeysForPlace(discipline: String?, gym: Boolean): List<String> =
    MeetupGearItem.forPlace(discipline, gym).map(::legacyKey)

private fun labelRes(key: String): Int = when (key) {
    "cuerda" -> R.string.gear_rope
    "grigri" -> R.string.gear_grigri
    "cintas" -> R.string.gear_quickdraws
    else -> R.string.gear_crashpad
}

/**
 * Hoja «¿Qué llevas tú?» (maqueta 5): una tarjeta grande por material con su interruptor (o contador si se cuenta) y,
 * debajo, cuántos del grupo lo llevan. Lo que lleva alguien sale resaltado; lo que falta, en terracota. Solo se ofrece
 * el material que tiene sentido para el lugar ([gearKeysForPlace]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditGearDialog(
    discipline: String?,
    currentGearJson: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    gym: Boolean = false,
    placeName: String? = null,
    members: List<MeetupMember> = emptyList(),
    myUid: String? = null
) {
    val keys = gearKeysForPlace(discipline, gym)
    val current = parseGear(currentGearJson)
    val mine = remember { mutableStateMapOf<String, Int>().apply { keys.forEach { put(it, current[it] ?: 0) } } }
    // Lo que llevan los DEMÁS: lo mío cambia con los interruptores.
    val others = members.filter { it.uid != myUid }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        shape = CumbreSheetShape
    ) {
        Column(
            Modifier.fillMaxWidth().cumbreSheetSurface(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.lg)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    placeName?.let { Text(it.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
                    Text(
                        stringResource(R.string.meetup_gear_sheet_title), fontFamily = Serif, fontWeight = FontWeight.Bold,
                        fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.common_close)) }
            }
            Column(Modifier.padding(top = Spacing.md).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (keys.isEmpty()) Text(
                    stringResource(R.string.meetup_gear_sheet_none), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md)
                )
                keys.forEach { key ->
                    val groupPeople = others.count { (parseGear(it.gearJson)[key] ?: 0) > 0 }
                    GearTile(
                        key = key, value = mine[key] ?: 0, groupPeople = groupPeople, groupSize = members.size.coerceAtLeast(1),
                        onChange = { mine[key] = it }
                    )
                }
            }
            if (keys.isNotEmpty()) Box(
                Modifier.fillMaxWidth().padding(top = Spacing.md).clip(CircleShape).background(terraFillColor())
                    .clickable { onSave(buildGearJson(mine)) }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.common_save).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun GearTile(key: String, value: Int, groupPeople: Int, groupSize: Int, onChange: (Int) -> Unit) {
    val on = value > 0
    val shape = RoundedCornerShape(18.dp)
    val carriers = groupPeople + if (on) 1 else 0
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape)
            .border(if (on) 2.dp else 1.dp, if (on) terraFillColor() else MaterialTheme.colorScheme.outline, shape)
            .clickable { if (isBooleanGearKey(key)) onChange(if (on) 0 else 1) else if (!on) onChange(1) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(gearIcon(key), null, tint = if (on) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(labelRes(key)), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                when {
                    carriers == 0 -> stringResource(R.string.meetup_gear_sheet_nobody)
                    carriers == 1 && on -> stringResource(R.string.meetup_gear_sheet_only_you)
                    else -> stringResource(R.string.meetup_gear_sheet_group_of, carriers, groupSize)
                },
                fontSize = 12.sp, color = if (carriers == 0) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isBooleanGearKey(key)) Switch(
            checked = on, onCheckedChange = { onChange(if (it) 1 else 0) },
            colors = SwitchDefaults.colors(checkedTrackColor = terraFillColor(), checkedThumbColor = Color.White)
        ) else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepperButton("−", if (value > 0) ({ onChange(value - 1) }) else null, primary = false, description = "−", size = 36)
            Text(value.toString(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            StepperButton("+", { onChange((value + 1).coerceAtMost(MeetupGearItem.MAX_QTY)) }, primary = true, description = "+", size = 36)
        }
    }
}
