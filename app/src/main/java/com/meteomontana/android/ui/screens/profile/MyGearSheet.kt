package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.components.CumbreSheetHeader
import com.meteomontana.android.ui.screens.meetups.buildGearJson
import com.meteomontana.android.ui.screens.meetups.gearItemsForDiscipline
import com.meteomontana.android.ui.screens.meetups.isBooleanGearKey
import com.meteomontana.android.ui.screens.meetups.parseGear
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

/**
 * "Mi material" como hoja propia, colgada del icono de mochila del perfil.
 * Espejo EXACTO de `MyGearSheet.swift`.
 *
 * Antes vivía enterrado en Editar perfil, entre el nombre y el género. Aquí se
 * llega en un toque y solo hay material, que es lo que se cambia a menudo:
 * llevas dos crashpads un finde y tres el siguiente.
 *
 * Reutiliza los helpers de las quedadas (`parseGear`, `buildGearJson`,
 * `gearItemsForDiscipline`, `isBooleanGearKey`) — el formato es el mismo, y de
 * ahí sale el reparto de material al unirte a una quedada.
 */
@Composable
fun MyGearSheet(
    gearJson: String?,
    onClose: () -> Unit,
    onSave: (String) -> Unit
) {
    // Un mapa observable: tocar +/- tiene que repintar la fila al momento.
    val gear = remember(gearJson) {
        mutableStateMapOf<String, Int>().apply {
            val actual = parseGear(gearJson)
            gearItemsForDiscipline(null).forEach { (key, _) -> put(key, actual[key] ?: 0) }
        }
    }
    var guardando by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        CumbreSheetHeader(
            titulo = "MI MATERIAL",
            onClose = onClose,
            accion = {
                TextButton(
                    onClick = { guardando = true; onSave(buildGearJson(gear)) },
                    enabled = !guardando
                ) {
                    Text(
                        if (guardando) "GUARDANDO…" else "GUARDAR",
                        style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(bottom = 100.dp)
        ) {
            Text(
                "Lo que sueles llevar. Se usa para repartir el material en las quedadas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))

            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Spacing.sm)
            ) {
                gearItemsForDiscipline(null).forEachIndexed { i, (key, label) ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (isBooleanGearKey(key)) {
                            Switch(
                                checked = (gear[key] ?: 0) > 0,
                                onCheckedChange = { gear[key] = if (it) 1 else 0 }
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                IconButton(
                                    onClick = { (gear[key] ?: 0).let { if (it > 0) gear[key] = it - 1 } },
                                    enabled = (gear[key] ?: 0) > 0
                                ) {
                                    Icon(
                                        Icons.Outlined.RemoveCircleOutline,
                                        contentDescription = "Quitar un $label"
                                    )
                                }
                                Text(
                                    "${gear[key] ?: 0}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )
                                IconButton(onClick = { gear[key] = (gear[key] ?: 0) + 1 }) {
                                    Icon(
                                        Icons.Outlined.AddCircleOutline,
                                        contentDescription = "Añadir un $label",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}
