@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.components

import com.meteomontana.android.ui.theme.terraFillColor

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.gradeStyle
import kotlinx.coroutines.launch

/**
 * Dialog con detalles de un bloque/parking/zona seleccionado en el mapa:
 * foto con líneas (solo BLOCK con foto), lista de vías, descripción, coords
 * y botón "CÓMO LLEGAR" (abre Google Maps).
 *
 * Compartido entre el mapa del admin (`FullScreenMapDialog`) y el mapa del
 * usuario en `SchoolMap`. Mismo render para ambos.
 *
 * @param isProposal si es la propuesta pendiente (cambia el badge y color).
 */
@Composable
fun BlockDetailDialog(
    block: Block,
    /** Nombre de la escuela (para el texto de compartir). */
    schoolName: String = "",
    /** Vía objetivo (deep-link del diario): su cara/foto se muestra la primera. */
    highlightVia: String? = null,
    isProposal: Boolean = false,
    onAddLines: (() -> Unit)? = null,
    onEditLine: ((com.meteomontana.android.domain.model.BlockLine) -> Unit)? = null,
    /** Marca/desmarca una vía en el diario. El 3er parámetro es el estado
     *  DESEADO (true = marcar hecha) — lo que el usuario ve al pulsar, para
     *  que una carga tardía del diario no invierta la acción. null = sin tic. */
    onTickLine: ((com.meteomontana.android.domain.model.BlockLine, Int, Boolean) -> Unit)? = null,
    /** Ids de vías ya hechas (del diario) para mostrarlas marcadas ✓. */
    initiallyTicked: Set<String> = emptySet(),
    /** Marca/desmarca una vía como PROYECTO (3er parámetro = estado deseado). */
    onToggleProject: ((com.meteomontana.android.domain.model.BlockLine, Int, Boolean) -> Unit)? = null,
    /** Ids de vías ya marcadas como proyecto, para mostrarlas al abrir. */
    initiallyProjects: Set<String> = emptySet(),
    /** Valorar una vía (1-5 estrellas). null = no mostrar estrellas. */
    onRateLine: ((lineId: String, stars: Int) -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    /** Sectores (ZONE) disponibles para "ASIGNAR SECTOR". null = no mostrar el botón. */
    availableSectors: List<Block>? = null,
    onAssignSector: ((sectorBlockId: String) -> Unit)? = null,
    /** Ids de vías que caen en el filtro de grado (BLOCK_SEARCH_DESIGN.md §7).
     *  null = sin filtro, nada se atenúa. */
    gradeMatchingLineIds: Set<String>? = null,
    onDismiss: () -> Unit
) {
    var showLinePicker by remember { mutableStateOf(false) }
    val tickedLines = remember { mutableStateListOf<String>().apply { addAll(initiallyTicked) } }   // vías hechas
    val projectLines = remember { mutableStateListOf<String>().apply { addAll(initiallyProjects) } } // vías proyecto
    // Si la ficha se abrió ANTES de que cargara el diario (la vista instantánea
    // la abre muy rápido → salía desmarcada, #14/#15), FUSIONA las marcas que
    // van llegando. Solo AÑADE por lineId (nunca quita: quitar es acción del
    // usuario). Es seguro con homónimas porque las claves ya son lineIds, no
    // nombres — el motivo por el que antes NO se sincronizaba desapareció al
    // migrar el diario a lineId.
    androidx.compose.runtime.LaunchedEffect(initiallyTicked) {
        initiallyTicked.forEach { if (!tickedLines.contains(it)) tickedLines.add(it) }
    }
    androidx.compose.runtime.LaunchedEffect(initiallyProjects) {
        initiallyProjects.forEach { if (!projectLines.contains(it)) projectLines.add(it) }
    }
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    // Votacion comunitaria (C2/C5): orientacion + sol/sombra + grado por consenso.
    // Solo en piedras/sectores reales (no en propuestas pendientes).
    val communityVm: CommunityVoteViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val orientationSummaries by communityVm.orientation.collectAsStateWithLifecycle()
    val sunByPhoto by communityVm.sun.collectAsStateWithLifecycle()
    val gradeSummary by communityVm.grade.collectAsStateWithLifecycle()
    val communityError by communityVm.error.collectAsStateWithLifecycle()
    var orientationTarget by remember { mutableStateOf<Int?>(null) }
    var orientationOpen by remember { mutableStateOf(false) }
    var gradeVoteLine by remember { mutableStateOf<com.meteomontana.android.domain.model.BlockLine?>(null) }
    androidx.compose.runtime.LaunchedEffect(block.id, isProposal) {
        if (!isProposal) {
            communityVm.clearForBlock()
            communityVm.loadOrientation(block.id)
            communityVm.loadSun(block.id, null)
        }
    }
    androidx.compose.runtime.LaunchedEffect(communityError) {
        communityError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            communityVm.consumeError()
        }
    }
    fun orientationOf(photoIndex: Int?) =
        orientationSummaries.firstOrNull { it.photoIndex == photoIndex }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSectorPicker by remember { mutableStateOf(false) }
    // "OPCIONES" plegado: la ficha ya tiene muchos botones; solo CÓMO LLEGAR
    // queda a la vista y el resto (editar vías, sector, eliminar) va dentro.
    var optionsOpen by remember { mutableStateOf(false) }

    // El scroll del contenido, en una variable: hace falta para decidir cuándo
    // se puede cerrar la hoja arrastrando (ver confirmValueChange).
    val contenidoScroll = rememberScrollState()
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        // ARRASTRAR PARA CERRAR, solo desde arriba.
        //
        // La ficha es larga y su gesto de cierre competía con el scroll: al
        // deslizar para leer, muchas veces se cerraba la hoja en vez de mover
        // el contenido (reportado por Rodrigo). Ahora solo se deja cerrar
        // cuando el contenido ya está arriba del todo — que es exactamente
        // como se comporta iOS: si estás leyendo por la mitad, el dedo mueve
        // la ficha, no la cierra. El ✕ y el botón atrás siguen cerrando
        // siempre, así que no se pierde ninguna salida.
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { valor ->
                valor != androidx.compose.material3.SheetValue.Hidden ||
                    contenidoScroll.value == 0
            }
        ),
        // El fondo lo pone cumbreSheetSurface (borde + canto de luz); si lo
        // pintase el sheet, los taparía.
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        shape = CumbreSheetShape,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        // La cabecera va FUERA del área que scrollea: en iOS el "Cerrar" se
        // queda fijo arriba por mucho que bajes, para que puedas salir en
        // cualquier momento. Aquí se iba con el contenido y había que subir
        // del todo para encontrarlo.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)   // tarjeta a pantalla (casi) completa, como el resto de sheets
                .cumbreSheetSurface()
                // Sin esto el teclado tapa el campo/botón de comentar una vía.
                .imePadding()
        ) {
            val (badgeColor, badgeLabel) = when {
                isProposal              -> Color(0xFFF59E0B) to "PROPUESTA"
                block.type == "PARKING" -> Color(0xFF1D6DD6) to "PARKING"
                block.type == "ZONE"    -> Color(0xFF1FA84E) to "ZONA"
                else                    -> Terra to "PIEDRA"
            }
            CumbreSheetHeader(titulo = block.name, onClose = onDismiss)

            // Caras de la piedra, en el MISMO orden en que se pintan abajo. Se
            // calcula aquí (y no dentro del scroll) porque las pestañas de
            // salto necesitan conocerlas antes de que se dibuje el contenido.
            val carasOrdenadas = remember(block, highlightVia) {
                block.facesOrDerived().let { fs ->
                    val viaName = highlightVia?.trim()
                    if (viaName.isNullOrBlank()) fs
                    else {
                        val hit = fs.indexOfFirst { f ->
                            f.lines.any { it.name.trim().equals(viaName, ignoreCase = true) }
                        }
                        if (hit > 0) listOf(fs[hit]) + fs.filterIndexed { i, _ -> i != hit } else fs
                    }
                }
            }
            // Dónde empieza cada cara dentro del scroll, para poder saltar.
            val posicionDeCara = remember(block) { mutableStateMapOf<Int, Int>() }
            // Cara visible ahora: la última cuyo inicio ya ha pasado por arriba.
            val caraActual = posicionDeCara.entries
                .filter { it.value <= contenidoScroll.value + 1 }
                .maxByOrNull { it.value }?.key ?: 0

            // Saltar de una cara a otra SIN scrollear. El scroll sigue igual:
            // esto es un atajo, no un sustituto (petición de Rodrigo,
            // 2026-08-16). Solo aparece si de verdad hay varias fotos.
            if (carasOrdenadas.count { !it.photoPath.isNullOrBlank() } > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    carasOrdenadas.forEachIndexed { idx, cara ->
                        if (!cara.photoPath.isNullOrBlank()) {
                            MochilaCard(label = "FOTO ${idx + 1}", selected = idx == caraActual) {
                                posicionDeCara[idx]?.let { y ->
                                    shareScope.launch { contenidoScroll.animateScrollTo(y) }
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(contenidoScroll)
                    .padding(horizontal = Spacing.md)
                    // Holgura abajo para que los últimos botones (p.ej. OPCIONES
                    // desplegado) queden por ENCIMA de la cápsula flotante de
                    // pestañas, que ahora está siempre visible y se dibuja sobre
                    // el contenido.
                    .padding(bottom = 100.dp)
            ) {
            Spacer(Modifier.height(Spacing.sm))
            Text(badgeLabel, style = EyebrowTextStyle, color = badgeColor)
            Spacer(Modifier.height(2.dp))
            Text(
                block.name,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onSurface
            )

            // C2: orientacion votable de la piedra/sector entero + tira de sol.
            if (!isProposal) {
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    VotableChip(
                        text = orientationOf(null)?.consensus?.let { c -> "PARED " + c } ?: "ORIENTACION",
                    ) { orientationTarget = null; orientationOpen = true }
                    val votesTotal = orientationOf(null)?.votes?.values?.sum() ?: 0
                    if (votesTotal > 0) Text(
                        votesTotal.toString() + " votos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                sunByPhoto[null]?.takeIf { sh -> sh.hours.isNotEmpty() }?.let { sun ->
                    Spacer(Modifier.height(Spacing.sm))
                    SunStrip(sun)
                }
            }

            // Sector actual de la piedra (si lo tiene)
            if (block.type == "BLOCK" && block.sectorBlockId != null) {
                Spacer(Modifier.height(Spacing.sm))
                val sectorName = availableSectors
                    ?.firstOrNull { it.id == block.sectorBlockId }?.name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF1FA84E))
                            .padding(horizontal = Spacing.sm, vertical = 2.dp)
                    ) {
                        // El sector, TODO dentro de la misma pastilla verde:
                        // "SECTOR · ALUNECER", como en iOS. Antes la insignia
                        // llevaba solo la palabra y el nombre iba suelto al
                        // lado, y se leían como dos cosas distintas.
                        Text(
                            "SECTOR · " + (sectorName ?: "SIN NOMBRE").uppercase(),
                            style = EyebrowTextStyle, color = Color.White
                        )
                    }
                }
            }

            // CARAS: una piedra grande se enseña con varias fotos. Cada cara es
            // una foto con sus vías dibujadas y, debajo, su lista de vías
            // marcables. Se hace scroll de cara en cara. Una piedra de una sola
            // foto tiene una única cara (idéntico a antes).
            if (block.type == "BLOCK") {
                if (onTickLine != null && !isProposal && block.lines.isNotEmpty()) {
                    com.meteomontana.android.ui.components.FirstTimeHint(
                        hintKey = "via_tick",
                        text = "Toca el círculo de una vía para apuntarla como hecha en tu diario."
                    )
                }
                if (onToggleProject != null && !isProposal && block.lines.isNotEmpty()) {
                    com.meteomontana.android.ui.components.FirstTimeHint(
                        hintKey = "via_project",
                        text = "Toca la P de una vía para marcarla como PROYECTO (la estás probando, aún no te ha salido)."
                    )
                }
                // Si venimos de pulsar una vía (deep-link del diario), su cara va
                // primero. El orden se calculó arriba, junto a las pestañas de
                // salto, para que las dos cosas usen exactamente el mismo.
                val orderedFaces = carasOrdenadas
                orderedFaces.forEachIndexed { faceIdx, face ->
                    val facePhoto = face.photoPath
                    if (!facePhoto.isNullOrBlank()) {
                        // Se anota dónde empieza esta cara para que su pestaña
                        // sepa a qué altura saltar.
                        Spacer(
                            Modifier
                                .height(Spacing.sm)
                                .onGloballyPositioned {
                                    posicionDeCara[faceIdx] = it.positionInParent().y.toInt()
                                }
                        )
                        if (orderedFaces.size > 1) {
                            val originalIdx = block.facesOrDerived().indexOf(face).takeIf { i -> i >= 0 } ?: faceIdx
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text(
                                    "FOTO ${faceIdx + 1}",
                                    style = EyebrowTextStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // C2: cada cara de un muro vota su propia orientacion.
                                if (!isProposal) VotableChip(
                                    text = orientationOf(originalIdx)?.consensus?.let { c -> "PARED " + c } ?: "ORIENTAR ESTA CARA",
                                ) {
                                    orientationTarget = originalIdx
                                    orientationOpen = true
                                    communityVm.loadSun(block.id, originalIdx)
                                }
                            }
                            Spacer(Modifier.height(Spacing.xs))
                        }
                        // Visor con zoom y foco: en piedras con muchas vias
                        // (el muro de Teverga tiene 13) es la unica forma de
                        // distinguirlas a tamano de movil.
                        TopoPhotoViewer(
                            photoUrl = facePhoto,
                            lines = face.lines.toTopoLines()
                        )
                    }
                    if (face.lines.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "${stringResource(R.string.block_routes)} (${face.lines.size})",
                            style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        face.lines.forEachIndexed { idx, line ->
                            val lineGrade = line.grade
                            val style = gradeStyle(lineGrade)
                            // Filtro por grado (BLOCK_SEARCH_DESIGN.md §7): vías fuera de
                            // la selección se atenúan, no se ocultan (contexto de la piedra).
                            val gradeDimmed = gradeMatchingLineIds != null && line.id !in gradeMatchingLineIds
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
                                        VotableChip(text = lineGrade) {
                                            gradeVoteLine = line
                                            communityVm.loadGrade(line.id)
                                        }
                                    } else Text(
                                        lineGrade,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (style.dark) MaterialTheme.colorScheme.onSurface else style.stroke
                                    )
                                }
                                if (line.startType != null) {
                                    Text(
                                        "· ${line.startType}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // El NOMBRE ya no vive en esta fila: va debajo, a todo
                                // ancho (ver más abajo). Aquí solo quedan los chips y las
                                // acciones, que así nunca se mueven ni se salen.
                                Spacer(Modifier.weight(1f))
                                // Compartir esta vía/bloque (WhatsApp etc.): enlace que
                                // abre la app directamente en esta piedra con la línea.
                                if (!isProposal) {
                                    androidx.compose.material3.Icon(
                                        Icons.Outlined.Share,
                                        contentDescription = "Compartir",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                val sectorName = availableSectors
                                                    ?.firstOrNull { it.id == block.sectorBlockId }?.name
                                                shareScope.launch {
                                                // N10: los datos comunitarios se consultan AL
                                                // compartir (best-effort, ~100ms) — el preload
                                                // podia no haber llegado.
                                                val badge = orientationOf(null)?.consensus
                                                    ?: communityVm.fetchOrientationConsensus(block.id)
                                                val setterRef = communityVm.fetchSetterGradeRef(line.id)
                                                shareVia(
                                                    shareScope, context, block, line, schoolName,
                                                    tickedLines.toSet(), projectLines.toSet(), sectorName,
                                                    orientationBadge = badge,
                                                    setterGradeRef = setterRef
                                                )
                                            }
                                            }
                                            .padding(5.dp)
                                            .size(22.dp)
                                    )
                                }
                                // Proyecto: la estás probando, aún no te ha salido. Oculto
                                // si ya está hecha (no tiene sentido marcarla como proyecto).
                                if (onToggleProject != null && !isProposal) {
                                    val done = tickedLines.contains(line.id)
                                    if (!done) {
                                        // (El compartir ya empujó el grupo a la derecha.)
                                        val isProject = projectLines.contains(line.id)
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .then(
                                                    if (isProject) Modifier.background(terraFillColor(), CircleShape)
                                                    else Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                                                )
                                                .clickable {
                                                    if (isProject) projectLines.remove(line.id)
                                                    else projectLines.add(line.id)
                                                    onToggleProject(line, idx, !isProject)
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
                                if (onTickLine != null && !isProposal) {
                                    val done = tickedLines.contains(line.id)
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
                                                    tickedLines.remove(line.id)
                                                } else {
                                                    tickedLines.add(line.id)
                                                    // Al marcarla hecha desde aquí, si era un proyecto
                                                    // desaparece de la lista local (promoción; el
                                                    // ViewModel hace lo mismo en el servidor).
                                                    projectLines.remove(line.id)
                                                }
                                                onTickLine(line, idx, !done)
                                            }
                                            .padding(horizontal = 6.dp)
                                    )
                                }
                            }
                            // NOMBRE (+ variante) a todo ancho, alineado con la
                            // descripción y los comentarios: se lee entero por largo que
                            // sea y las acciones de arriba quedan siempre accesibles.
                            if (line.name.isNotBlank()) {
                                Text(
                                    line.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
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
                    }
                }
            }

            // Descripción
            block.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }

            // (Los comentarios viven en cada vía, no en la piedra entera —
            // decisión de Rodrigo 2026-07-04: evitaba el doble "COMENTARIOS".)

            // Coordenadas
            Spacer(Modifier.height(Spacing.sm))
            Text(
                // Locale.US y 5 decimales, como iOS. Con el idioma del móvil en
                // español salía "40,538180" con COMA, y unas coordenadas con
                // coma no se pueden pegar en Google Maps — que es exactamente
                // para lo que están ahí. Cinco decimales bastan: es ~1 metro.
                "%.5f, %.5f".format(java.util.Locale.US, block.lat, block.lon),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.md))

            // Botón CÓMO LLEGAR (Google Maps) — disponible para cualquier tipo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .background(terraFillColor())
                    .clickable {
                        val uri = Uri.parse(
                            "https://www.google.com/maps/dir/?api=1&destination=${block.lat},${block.lon}"
                        )
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text("→ ${stringResource(R.string.common_directions)}", style = EyebrowTextStyle, color = Color.White)
            }

            BlockOptionsSection(
                block = block, isProposal = isProposal,
                optionsOpen = optionsOpen, onToggleOptions = { optionsOpen = !optionsOpen },
                onAddLines = onAddLines, availableSectors = availableSectors,
                onOpenSectorPicker = if (onAssignSector != null) ({ showSectorPicker = true }) else null,
                onEdit = onEdit,
                onRequestDelete = if (onDelete != null) ({ showDeleteConfirm = true }) else null,
                onShareBlock = {
                    // N10: comparte la piedra entera usando su primera via como ancla
                    // (la tarjeta ya lista TODAS las vias de la cara).
                    block.lines.firstOrNull()?.let { first ->
                        val blockSector = availableSectors
                            ?.firstOrNull { z -> z.id == block.sectorBlockId }?.name
                        shareScope.launch {
                            val badge = orientationOf(null)?.consensus
                                ?: communityVm.fetchOrientationConsensus(block.id)
                            shareVia(shareScope, context, block, first, schoolName,
                                tickedLines.toSet(), projectLines.toSet(), blockSector,
                                orientationBadge = badge)
                        }
                    }
                },
            )
            }
        }
    }

    // Selector de via a corregir
    // ── C2: diálogo de votar orientación ──────────────────────────────────
    if (orientationOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { orientationOpen = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { orientationOpen = false }) {
                    Text("CERRAR", style = EyebrowTextStyle, color = Terra)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                OrientationVoteContent(
                    summary = orientationOf(orientationTarget)
                ) { aspect ->
                    communityVm.voteOrientation(block.id, orientationTarget, aspect)
                }
            }
        )
    }

    // ── C5: diálogo de votar grado ────────────────────────────────────────
    gradeVoteLine?.let { gl ->
        val canVote = tickedLines.contains(gl.id) || projectLines.contains(gl.id)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { gradeVoteLine = null },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { gradeVoteLine = null }) {
                    Text("CERRAR", style = EyebrowTextStyle, color = Terra)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                GradeVoteContent(summary = gradeSummary, canVote = canVote) { g ->
                    communityVm.voteGrade(gl.id, g)
                }
            }
        )
    }

    if (showLinePicker && onEditLine != null) {
        BlockLinePickerDialog(block,
            onPick = { showLinePicker = false; onEditLine(it) },
            onDismiss = { showLinePicker = false })
    }

    // Dialog de confirmacion de borrado
    if (showDeleteConfirm && onDelete != null) {
        BlockDeleteConfirmDialog(block,
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false })
    }

    // Picker de sector para "ASIGNAR SECTOR"
    if (showSectorPicker && onAssignSector != null && !availableSectors.isNullOrEmpty()) {
        BlockSectorPickerDialog(block, availableSectors,
            onPick = { showSectorPicker = false; onAssignSector(it) },
            onDismiss = { showSectorPicker = false })
    }
}
