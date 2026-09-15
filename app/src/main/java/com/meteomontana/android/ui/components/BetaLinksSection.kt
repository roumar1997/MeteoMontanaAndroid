package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.BetaLink
import com.meteomontana.android.domain.usecase.blocks.AddBetaLinkUseCase
import com.meteomontana.android.domain.usecase.blocks.DeleteBetaLinkUseCase
import com.meteomontana.android.domain.usecase.blocks.GetBetaLinksUseCase
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Enlaces de la comunidad a vídeos de beta (Instagram/YouTube) en piedras/
 * vías, con categoría opcional de altura — Álvaro, 2026-09-15: "que se pueda
 * poner igual que se vota... que se pueda tener varios, beta personas +1.70
 * y personas -1.70". Directo, sin revisión de admin — mismo patrón que
 * LineCommentsSection.kt: un fetch por piedra, cada hilo filtra los suyos.
 */
@HiltViewModel
class BetaLinksViewModel @Inject constructor(
    private val getBetaLinks: GetBetaLinksUseCase,
    private val addBetaLink: AddBetaLinkUseCase,
    private val deleteBetaLink: DeleteBetaLinkUseCase
) : ViewModel() {

    private val _links = MutableStateFlow<List<BetaLink>>(emptyList())
    val links: StateFlow<List<BetaLink>> = _links

    fun load(blockId: String) {
        viewModelScope.launch {
            runCatching { getBetaLinks(blockId, null) }
                .onSuccess { _links.value = it }
        }
    }

    fun add(blockId: String, lineId: String?, url: String, heightCategory: String?, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { addBetaLink(blockId, lineId, url, heightCategory) }
            result.onSuccess { created -> _links.value = _links.value + created }
            onDone(result.isSuccess)
        }
    }

    fun delete(linkId: String) {
        viewModelScope.launch {
            runCatching { deleteBetaLink(linkId) }
                .onSuccess { _links.value = _links.value.filter { it.id != linkId } }
        }
    }
}

/** Categoría de altura para etiquetar la beta — Álvaro, 2026-09-15. */
private enum class HeightFilter(val raw: String?, val label: String, val shortLabel: String) {
    ANY(null, "Cualquier altura", "Todas"),
    TALL("TALL", "Personas +1,70", "+1,70"),
    SHORT("SHORT", "Personas -1,70", "-1,70")
}

/**
 * Hilo desplegable de enlaces de beta: la CABECERA ENTERA es pulsable.
 * lineId=null → enlaces de la piedra entera. Añadir es un flujo de DOS
 * PASOS (elegir para quién → pegar el enlace), espejo de iOS.
 */
@Composable
fun BetaLinksThread(
    blockId: String,
    lineId: String?,
    myUid: String?,
    viewModel: BetaLinksViewModel = hiltViewModel()
) {
    LaunchedEffect(blockId) { viewModel.load(blockId) }
    val allLinks by viewModel.links.collectAsStateWithLifecycle()
    val mine = remember(allLinks, blockId, lineId) {
        allLinks.filter { it.blockId == blockId && it.lineId == lineId }
            .sortedByDescending { it.createdAt ?: "" }
    }

    var expanded by remember { mutableStateOf(false) }
    var viewFilter by remember { mutableStateOf(HeightFilter.ANY) }
    var choosingCategory by remember { mutableStateOf(false) }
    var pastingUrlFor by remember { mutableStateOf<HeightFilter?>(null) }
    var urlDraft by remember { mutableStateOf("") }
    var justAdded by remember { mutableStateOf(false) }

    val shown = remember(mine, viewFilter) {
        if (viewFilter == HeightFilter.ANY) mine else mine.filter { it.heightCategory == viewFilter.raw }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text(
                "BETA" + if (mine.isNotEmpty()) " · ${mine.size}" else "",
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(if (expanded) "▴" else "▾",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge)
        }

        if (expanded) {
            // VER: qué enlaces se muestran — todos, o solo los de una altura.
            if (mine.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeightFilter.entries.forEach { f ->
                        val active = viewFilter == f
                        Text(
                            f.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else null,
                            color = if (active) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (active) Modifier
                                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                )
                                .let { if (active) it.background(MaterialTheme.colorScheme.onSurface) else it }
                                .clickable { viewFilter = f }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                Text(
                    if (mine.isEmpty()) "Sé el primero en dejar un enlace de beta."
                    else "No hay enlaces de beta para ${viewFilter.label.lowercase()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            shown.forEachIndexed { idx, l ->
                if (idx > 0) androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 1.dp)
                BetaLinkRow(link = l, isMine = myUid != null && myUid == l.uid,
                    onDelete = { viewModel.delete(l.id) })
            }
            if (justAdded) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                        tint = com.meteomontana.android.ui.theme.Moss, modifier = Modifier.size(14.dp))
                    Text("Enlace añadido", style = MaterialTheme.typography.bodySmall,
                        color = com.meteomontana.android.ui.theme.Moss)
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .clickable { choosingCategory = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Terra, modifier = Modifier.size(14.dp))
                Text("Añadir enlace de beta", style = MaterialTheme.typography.bodySmall, color = Terra)
            }
        }
    }

    // Paso 1: ¿para quién es esta beta?
    if (choosingCategory) {
        AlertDialog(
            onDismissRequest = { choosingCategory = false },
            title = { Text("¿Para quién es esta beta?") },
            text = {
                Column {
                    HeightFilter.entries.forEach { f ->
                        Text(f.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    choosingCategory = false
                                    urlDraft = ""
                                    pastingUrlFor = f
                                }
                                .padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ choosingCategory = false }) { Text("Cancelar") } }
        )
    }

    // Paso 2: pegar el enlace.
    pastingUrlFor?.let { category ->
        AlertDialog(
            onDismissRequest = { pastingUrlFor = null },
            title = { Text("Enlace de beta") },
            text = {
                Column {
                    Text(
                        if (category == HeightFilter.ANY) "Se abrirá fuera de la app."
                        else "Para ${category.label.lowercase()}. Se abrirá fuera de la app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.sm)
                    )
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        placeholder = { Text("Enlace de Instagram/YouTube") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = urlDraft.trim()
                    if (u.isNotEmpty()) {
                        val target = pastingUrlFor
                        pastingUrlFor = null
                        viewModel.add(blockId, lineId, u, target?.raw) { ok ->
                            if (ok) justAdded = true
                        }
                    } else {
                        pastingUrlFor = null
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton({ pastingUrlFor = null }) { Text("Cancelar") } }
        )
    }

    LaunchedEffect(justAdded) {
        if (justAdded) {
            delay(2000)
            justAdded = false
        }
    }
}

@Composable
private fun BetaLinkRow(link: BetaLink, isMine: Boolean, onDelete: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val host = remember(link.url) { runCatching { java.net.URI(link.url).host?.lowercase() }.getOrNull() ?: "" }
    val label = when {
        host.contains("instagram.com") -> "Ver en Instagram"
        host.contains("youtube.com") || host.contains("youtu.be") -> "Ver en YouTube"
        else -> "Ver enlace"
    }
    val categoryLabel = when (link.heightCategory) {
        "TALL" -> "+1,70"
        "SHORT" -> "-1,70"
        else -> null
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link.url))
                    context.startActivity(intent)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            categoryLabel?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Terra,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, Terra, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Icon(Icons.Outlined.OpenInNew, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
        }
        if (isMine) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Borrar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(onClick = onDelete)
                    .padding(6.dp)
                    .size(16.dp))
        }
    }
}
