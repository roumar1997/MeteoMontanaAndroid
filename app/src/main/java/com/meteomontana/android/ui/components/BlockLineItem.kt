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

/** Lo del diario en la ficha de piedra: vías hechas y proyectos (listas vivas) y qué hacer al marcar o desmarcar. */
class LineTicks(
    val ticked: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    val projects: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    val onTick: ((com.meteomontana.android.domain.model.BlockLine, Int, Boolean) -> Unit)?,
    val onProject: ((com.meteomontana.android.domain.model.BlockLine, Int, Boolean) -> Unit)?
)

/**
 * Una vía en la ficha de piedra: número y grado (votable), nombre y variante, salida, compartir, proyecto y hecha,
 * votos del grado, estrellas, descripción, enlaces de beta y comentarios. Sacado de BlockDetailDialog (2026-11-05).
 */
@Composable
internal fun BlockLineItem(
    line: com.meteomontana.android.domain.model.BlockLine,
    idx: Int,
    block: Block,
    isProposal: Boolean,
    /** Fuera del filtro de grado: se atenúa, no se oculta. */
    dimmed: Boolean,
    gradeBadge: GradeBadge?,
    ticks: LineTicks,
    onRateLine: ((lineId: String, stars: Int) -> Unit)?,
    onGradeVote: (com.meteomontana.android.domain.model.BlockLine) -> Unit,
    onShare: (com.meteomontana.android.domain.model.BlockLine) -> Unit
) {
        val lineGrade = line.grade
        val style = gradeChipStyle(lineGrade)
        // Filtro por grado (BLOCK_SEARCH_DESIGN.md §7): vías fuera de
        // la selección se atenúan, no se ocultan (contexto de la piedra).
        val gradeDimmed = dimmed
        Column(
            Modifier.graphicsLayer(alpha = if (gradeDimmed) 0.35f else 1f)
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(style.stroke)
                    .padding(2.dp)
                    .height(18.dp)
            ) {
                Text(
                    "${idx + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (style.dark) Color.Black else Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            if (lineGrade != null) {
                // C5: el grado es VOTABLE (chip discontinuo terra).
                if (!isProposal && line.id.isNotBlank()) {
                    // Pastilla con SU color y una flecha clara; si la altura parte la opinión, dos colores.
                    GradeChip(grade = lineGrade, split = gradeBadge?.split) { onGradeVote(line) }
                } else Text(
                    lineGrade,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (style.dark) MaterialTheme.colorScheme.onSurface else style.stroke
                )
            }
            // Nombre junto a la pastilla y, debajo, la variante en pequeño (como iOS): ocupa el
            // hueco que queda, así las acciones de la derecha nunca se salen ni se mueven.
            Column(Modifier.weight(1f)) {
                Text(
                    line.name.ifBlank { stringResource(R.string.school_map_v3_via_2, "${idx + 1}") },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                if (!line.variant.isNullOrBlank()) {
                    Text(
                        line.variant!!,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            if (line.startType != null) {
                Text(
                    line.startType!!,
                    fontFamily = com.meteomontana.android.ui.theme.Mono,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Compartir esta vía/bloque (WhatsApp etc.): enlace que
            // abre la app directamente en esta piedra con la línea.
            if (!isProposal) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.block_detail_dialog_v2_compartir),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onShare(line) }
                        .padding(5.dp)
                        .size(22.dp)
                )
            }
            // Proyecto: la estás probando, aún no te ha salido. Oculto
            // si ya está hecha (no tiene sentido marcarla como proyecto).
            if (ticks.onProject != null && !isProposal) {
                val done = ticks.ticked.contains(line.id)
                if (!done) {
                    // (El compartir ya empujó el grupo a la derecha.)
                    val isProject = ticks.projects.contains(line.id)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .then(
                                if (isProject) Modifier.background(terraFillColor(), CircleShape)
                                else Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                            )
                            .clickable {
                                if (isProject) ticks.projects.remove(line.id)
                                else ticks.projects.add(line.id)
                                ticks.onProject(line, idx, !isProject)
                            },
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            "P",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isProject) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
            // Tic: marca/desmarca la vía en tu diario (toggle).
            if (ticks.onTick != null && !isProposal) {
                val done = ticks.ticked.contains(line.id)
                // El botón de compartir ya empujó el grupo a la derecha.
                if (isProposal) Spacer(Modifier.weight(1f))
                Text(
                    if (done) "✓" else "○",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (done) Color(0xFF1FA84E)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (done) {
                                ticks.ticked.remove(line.id)
                            } else {
                                ticks.ticked.add(line.id)
                                // Al marcarla hecha desde aquí, si era un proyecto
                                // desaparece de la lista local (promoción; el
                                // ViewModel hace lo mismo en el servidor).
                                ticks.projects.remove(line.id)
                            }
                            ticks.onTick(line, idx, !done)
                        }
                        .padding(horizontal = 6.dp)
                )
            }
        }
        // Qué hace la pastilla del grado: cuántos votos hay y que se puede votar.
        if (!isProposal && line.id.isNotBlank() && !lineGrade.isNullOrEmpty()) {
            Text(
                gradeBadgeCaption(gradeBadge),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, bottom = 2.dp)
            )
        }
        // Estrellas: valoración media + votar (si está habilitado)
        if (onRateLine != null && !isProposal && block.type == "BLOCK") {
            LineStarsRow(
                lineId = line.id,
                avgStars = line.avgStars,
                myStars = line.myStars ?: 0,
                onRate = { stars -> onRateLine(line.id, stars) }
            )
        }
        // Descripción/beta de la vía (si la tiene).
        line.lineDescription?.takeIf { it.isNotBlank() }?.let { d ->
            Text(d, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, bottom = 2.dp))
        }
        // Enlaces de beta de ESTA vía (desplegable) — Álvaro, 2026-09-15.
        if (!isProposal) {
            Box(Modifier.padding(start = 28.dp)) {
                BetaLinksThread(
                    blockId = block.id,
                    lineId = line.id,
                    myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                )
            }
        }
        // Comentarios de ESTA vía (desplegable).
        if (!isProposal) {
            Box(Modifier.padding(start = 28.dp)) {
                LineCommentsThread(
                    blockId = block.id,
                    lineId = line.id,
                    myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                )
            }
        }
        }
}
