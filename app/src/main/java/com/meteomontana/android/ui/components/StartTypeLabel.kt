package com.meteomontana.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/**
 * Mapeo ÚNICO tipo de inicio → etiqueta traducida (SIT/STAND/JUMP/TRAV).
 * Lo usan el editor de topos (chips INICIO), las tarjetas del feed y la
 * imagen de compartir — no duplicar este mapeo en otro sitio.
 */
val START_TYPE_LABELS = listOf(
    "SIT" to R.string.topo_editor_start_sit,
    "STAND" to R.string.topo_editor_start_stand,
    "JUMP" to R.string.topo_editor_start_jump,
    "TRAV" to R.string.topo_editor_start_trav
)

/** Recurso de la etiqueta del tipo de inicio, o null si no se reconoce. */
fun startTypeLabelRes(startType: String?): Int? =
    START_TYPE_LABELS.firstOrNull { it.first.equals(startType, ignoreCase = true) }?.second

/** Etiqueta traducida del tipo de inicio ("Sentado", "Pie"…), o null. */
@Composable
fun startTypeLabel(startType: String?): String? =
    startTypeLabelRes(startType)?.let { stringResource(it) }
