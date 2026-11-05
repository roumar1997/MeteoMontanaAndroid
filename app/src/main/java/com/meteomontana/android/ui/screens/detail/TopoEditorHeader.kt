package com.meteomontana.android.ui.screens.detail

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.renderTopo
import com.meteomontana.android.ui.components.drawOp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.gradeStyle
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/** Arriba del editor de líneas: título con cerrar y la fila de vías para elegir cuál se dibuja. */
@Composable
internal fun TopoEditorHeader(bloques: List<BoulderBloqueForm>, selectedIdx: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    Column {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.contribution_topo_dialog_v2_dibujar_lineas),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif),
                color = Terra,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            ) {
                Text("✕", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // ── Selector de bloque ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                stringResource(R.string.contribution_topo_dialog_v2_dibujando_para),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                contentPadding = PaddingValues(end = Spacing.sm)
            ) {
                itemsIndexed(bloques) { idx, b ->
                    val sel = idx == selectedIdx
                    val gStyle = gradeStyle(b.grade)
                    val bgColor = if (sel) gStyle.stroke else MaterialTheme.colorScheme.surface
                    val textColor = when {
                        !sel -> MaterialTheme.colorScheme.onSurface
                        gStyle.dark -> Color.Black
                        else -> Color.White
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(bgColor)
                            .border(1.dp, gStyle.stroke, RoundedCornerShape(2.dp))
                            .clickable { onSelect(idx) }
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    ) {
                        Text(
                            com.meteomontana.android.domain.util.TopoChipLabel.of(
                                idx, b.name, b.variant, b.grade),
                            maxLines = 1,
                            style = EyebrowTextStyle,
                            color = textColor
                        )
                    }
                }
                // Botón + NUEVO para añadir otro bloque en el futuro (deshabilitado aquí)
                item {
                    Spacer(Modifier.width(Spacing.xs))
                }
            }
        }

    }
}
