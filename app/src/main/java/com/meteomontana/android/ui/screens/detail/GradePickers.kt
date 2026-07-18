@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.gradeStyle

/**
 * Selector de grado como GRID DE CHIPS de un toque — sustituye al desplegable
 * de 24 opciones (abrir + scroll + tap fino era la interacción más torpe del
 * flujo, y es la más repetida: una vez por vía). Cada chip va coloreado con
 * el MISMO código de color por dificultad que pintan los topos (gradeStyle),
 * así el usuario ve la escala completa de un vistazo. Tocar el chip
 * seleccionado lo deselecciona (el grado es opcional).
 */
@Composable
fun GradeChipsGrid(
    selected: String?,
    onSelect: (String?) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        BOULDER_GRADES.forEach { grade ->
            val gs = gradeStyle(grade)
            val sel = grade == selected
            val bg = if (sel) gs.stroke else gs.stroke.copy(alpha = 0.16f)
            val fg = when {
                !sel -> MaterialTheme.colorScheme.onSurface
                gs.dark -> Color.Black
                else -> Color.White
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .then(
                        if (sel) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(9.dp))
                        else Modifier.border(1.dp, gs.stroke.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                    )
                    .clickable { onSelect(if (sel) null else grade) }
                    .defaultMinSize(minWidth = 40.dp)
                    .padding(horizontal = Spacing.xs, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(grade, style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}

/** Códigos de tipo de inicio (los que viajan al backend) → etiqueta legible.
 *  Antes se mostraban las siglas a pelo (PIE/SIT/LANCE/TRAV) sin explicación. */
val START_TYPE_LABELS = listOf(
    "PIE" to "De pie",
    "SIT" to "Sentado",
    "SEMI" to "Semi-sit",
    "LANCE" to "Lance",
    "TRAV" to "Travesía"
)

/** Chips de tipo de inicio con nombre completo. Tocar el seleccionado lo quita. */
@Composable
fun StartTypeChips(
    selected: String?,
    onSelect: (String?) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        START_TYPE_LABELS.forEach { (code, label) ->
            val sel = selected == code
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (sel) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
                    .clickable { onSelect(if (sel) null else code) }
                    .padding(horizontal = Spacing.sm, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = if (sel) MaterialTheme.colorScheme.background
                            else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
