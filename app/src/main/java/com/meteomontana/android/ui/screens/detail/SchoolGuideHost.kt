package com.meteomontana.android.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.data.local.FeedSharePref
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.components.AscentSheet
import com.meteomontana.android.ui.components.CommunityVoteViewModel
import com.meteomontana.android.ui.screens.detail.guide.GuideActions
import com.meteomontana.android.ui.screens.detail.guide.GuideScroller
import com.meteomontana.android.util.AppText
import kotlinx.coroutines.launch

/**
 * La pestaña GUÍA dentro de la ficha: le da lo que vive FUERA de ella (la ficha de piedra del mapa, el diario y su
 * hoja de ascenso) — lo mismo que hace `SchoolDetailView` con `SchoolGuideView` en iOS.
 *
 * [goToMap] cambia a la pestaña MAPA; con `returnAfter` la ficha vuelve a la GUÍA al cerrar la piedra.
 */
@Composable
internal fun SchoolGuideHost(
    school: School,
    blocks: List<Block>,
    viewModel: SchoolDetailViewModel,
    blocksVm: SchoolBlocksViewModel,
    scroller: GuideScroller,
    goToMap: (returnAfter: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // El diario (hechas / proyecto) lo vigila la ficha: la guía solo lo traduce a "qué vías de esta escuela".
    val done by viewModel.doneViaKeys.collectAsStateWithLifecycle()
    val projects by viewModel.projectViaKeys.collectAsStateWithLifecycle()
    LaunchedEffect(done, projects) { blocksVm.setJournal(done, projects) }

    /** La vía de la guía tal como la necesita el diario: su piedra y su línea (con su posición). */
    fun resolve(h: LineSearchHit): Triple<Block, BlockLine, Int>? {
        val b = blocks.firstOrNull { it.id == h.blockId } ?: return null
        val i = b.lines.indexOfFirst { it.id == h.lineId }.takeIf { it >= 0 } ?: return null
        return Triple(b, b.lines[i], i)
    }

    var ticking by remember { mutableStateOf<LineSearchHit?>(null) }

    val actions = remember(blocks) {
        GuideActions(
            onOpen = { lineId, blockId ->
                if (lineId != null) viewModel.openVia(lineId, null) else viewModel.openBlock(blockId)
                goToMap(true)
            },
            onShowOnMap = { blockId ->
                viewModel.focusBlock(blockId)
                goToMap(false)
            },
            onTick = { h -> if (resolve(h) != null) ticking = h },
            onUnmark = { h ->
                resolve(h)?.let { (b, l, i) ->
                    scope.launch { viewModel.toggleLine(b, l, i, school.name, h.sectorName, markDone = false) }
                }
            },
            onToggleProject = { h, mark ->
                resolve(h)?.let { (b, l, i) ->
                    scope.launch { viewModel.toggleProject(b, l, i, school.name, h.sectorName, markProject = mark) }
                }
            }
        )
    }

    SchoolBlocksTab(blocksVm, school.name, actions, scroller)

    // Hoja de ascenso AQUÍ MISMO (la de la ficha de piedra): diario primero, luego Comunidad y el voto de grado.
    ticking?.let { h ->
        val (block, line, index) = resolve(h) ?: run { ticking = null; return }
        val wasProject = h.lineId in blocksVm.state.value.projectIds
        val votes: CommunityVoteViewModel = hiltViewModel()
        val myHeight by votes.myHeight.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { votes.loadMyHeight() }
        AscentSheet(
            lineName = line.name.ifBlank { stringResource(R.string.school_map_v3_via_2, index + 1) },
            setterGrade = line.grade,
            wasProject = wasProject,
            myHeight = myHeight,
            onSetHeight = { votes.setMyHeight(it) },
            initialShare = FeedSharePref.enabled(ctx),
            onSave = { choice ->
                ticking = null
                scope.launch {
                    val r = viewModel.toggleLine(block, line, index, school.name, h.sectorName, markDone = true,
                        sessionDate = choice.sessionDate, aVista = choice.aVista, alFlash = choice.alFlash)
                    if (r.getOrNull() != true) return@launch
                    if (choice.publish) {
                        viewModel.publishTickToFeed(
                            block, line, wasProject, choice.caption, photoUri = choice.photoUri?.toString(),
                            onPublishFailed = {
                                android.widget.Toast.makeText(ctx, AppText.get(R.string.school_map_v3_no_se_pudo_publicar_el),
                                    android.widget.Toast.LENGTH_LONG).show()
                            },
                            onPhotoUploadFailed = {
                                android.widget.Toast.makeText(ctx, R.string.feed_photo_upload_failed,
                                    android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    val grade = choice.gradeVote
                    if (grade != null && line.id.isNotEmpty()) votes.voteGrade(line.id, grade)
                }
            },
            onDismiss = { ticking = null }
        )
    }
}
