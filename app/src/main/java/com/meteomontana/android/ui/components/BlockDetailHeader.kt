package com.meteomontana.android.ui.components

import com.meteomontana.android.util.CatalogLabels
import com.meteomontana.android.ui.theme.terraFillColor
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.gradeChipStyle
import com.meteomontana.android.ui.theme.gradeStyle
import kotlinx.coroutines.launch

/** Barra fija de la ficha de piedra: cerrar, cómo llegar, compartir y ⚙ opciones (añadir/editar vías, sector, editar, borrar). */
@Composable
internal fun BlockDetailHeader(
    block: Block,
    isProposal: Boolean,
    availableSectors: List<Block>?,
    onDismiss: () -> Unit,
    onShareBlock: () -> Unit,
    onAddLines: (() -> Unit)?,
    onAssignSector: ((sectorBlockId: String) -> Unit)?,
    onPickSector: () -> Unit
) {
    val context = LocalContext.current
    var optionsOpen by remember { mutableStateOf(false) }
    val hayOpciones = !isProposal && (
        (onAddLines != null && block.type == "BLOCK") ||
        (onAssignSector != null && block.type == "BLOCK" && !availableSectors.isNullOrEmpty()))
    val puedeCompartir = block.type == "BLOCK" && !isProposal && block.lines.isNotEmpty()
    CumbreSheetHeader(
        titulo = block.name,
        onClose = onDismiss,
        accion = {
            androidx.compose.material3.IconButton(onClick = {
                val uri = Uri.parse(
                    "https://www.google.com/maps/dir/?api=1&destination=${block.lat},${block.lon}"
                )
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.Directions,
                    contentDescription = stringResource(R.string.common_directions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (puedeCompartir) {
                androidx.compose.material3.IconButton(onClick = { onShareBlock() }) {
                    androidx.compose.material3.Icon(
                        Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.common_share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (hayOpciones) {
                Box {
                    androidx.compose.material3.IconButton(onClick = { optionsOpen = true }) {
                        androidx.compose.material3.Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.block_detail_dialog_v2_opciones),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = optionsOpen,
                        onDismissRequest = { optionsOpen = false },
                        // Con el contenedor por defecto el menú salía
                        // BLANCO, ajeno al resto de la app (Álvaro,
                        // 2026-08-24). Papel de Cumbre + borde, como
                        // cualquier otra superficie.
                        containerColor = MaterialTheme.colorScheme.surface,
                        shape = androidx.compose.material3.MaterialTheme.shapes.small,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        if (onAddLines != null && block.type == "BLOCK") {
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(if (block.lines.isEmpty()) stringResource(R.string.block_add_routes)
                                         else stringResource(R.string.block_edit_routes))
                                },
                                onClick = { optionsOpen = false; onAddLines() }
                            )
                        }
                        if (onAssignSector != null && block.type == "BLOCK" && !availableSectors.isNullOrEmpty()) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(if (block.sectorBlockId == null) stringResource(R.string.propose_assign_sector)
                                         else stringResource(R.string.propose_change_sector))
                                },
                                onClick = { optionsOpen = false; onPickSector() }
                            )
                        }
                    }
                }
            }
        }
    )
}
