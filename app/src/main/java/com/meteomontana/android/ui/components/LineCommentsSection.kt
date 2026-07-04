package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.KtorBlockApi
import com.meteomontana.android.data.api.dto.CreateLineCommentRequest
import com.meteomontana.android.data.api.dto.LineCommentDto
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Comentarios de la comunidad (con votos ▲/▼) de una piedra o de una vía.
 * Un solo fetch por piedra: el VM trae TODOS los comentarios del bloque y
 * cada hilo (bloque / vía) filtra los suyos.
 */
@HiltViewModel
class LineCommentsViewModel @Inject constructor(
    private val blockApi: KtorBlockApi
) : ViewModel() {

    private val _comments = MutableStateFlow<List<LineCommentDto>>(emptyList())
    val comments: StateFlow<List<LineCommentDto>> = _comments

    private var loadedBlockId: String? = null

    fun load(blockId: String) {
        if (loadedBlockId == blockId) return
        loadedBlockId = blockId
        viewModelScope.launch {
            runCatching { blockApi.getComments(blockId) }
                .onSuccess { _comments.value = it }
        }
    }

    fun add(blockId: String, lineId: String?, text: String) {
        viewModelScope.launch {
            runCatching { blockApi.addComment(blockId, CreateLineCommentRequest(lineId, text)) }
                .onSuccess { created -> _comments.value = _comments.value + created }
        }
    }

    fun vote(commentId: String, value: Int) {
        viewModelScope.launch {
            runCatching { blockApi.voteComment(commentId, value) }.onSuccess { myVote ->
                _comments.value = _comments.value.map { c ->
                    if (c.id != commentId) c else {
                        val old = c.myVote
                        c.copy(
                            myVote = myVote,
                            upvotesCount = c.upvotesCount + (if (myVote == 1) 1 else 0) - (if (old == 1) 1 else 0),
                            downvotesCount = c.downvotesCount + (if (myVote == -1) 1 else 0) - (if (old == -1) 1 else 0)
                        )
                    }
                }
            }
        }
    }

    fun delete(commentId: String) {
        viewModelScope.launch {
            runCatching { blockApi.deleteComment(commentId) }
                .onSuccess { _comments.value = _comments.value.filter { it.id != commentId } }
        }
    }
}

/**
 * Hilo desplegable de comentarios. La CABECERA ENTERA es pulsable (lección del
 * boletín AEMET). lineId=null → comentarios de la piedra entera.
 */
@Composable
fun LineCommentsThread(
    blockId: String,
    lineId: String?,
    myUid: String?,
    viewModel: LineCommentsViewModel = hiltViewModel()
) {
    viewModel.load(blockId)
    val all by viewModel.comments.collectAsState()
    val mine = remember(all, blockId, lineId) {
        all.filter { it.blockId == blockId && it.lineId == lineId }
            .sortedWith(compareByDescending<LineCommentDto> { it.upvotesCount - it.downvotesCount }
                .thenByDescending { it.createdAt ?: "" })
    }
    var expanded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "💬 COMENTARIOS" + if (mine.isNotEmpty()) " · ${mine.size}" else "",
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(if (expanded) "▴" else "▾",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge)
        }

        if (expanded) {
            if (mine.isEmpty()) {
                Text("Sé el primero en comentar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            mine.forEach { c ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .padding(Spacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.author, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        if (myUid != null && myUid == c.uid) {
                            Text("BORRAR", style = EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable { viewModel.delete(c.id) }
                                    .padding(4.dp))
                        }
                    }
                    Text(c.text, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CommentVoteChip("▲", c.upvotesCount, c.myVote == 1) { viewModel.vote(c.id, 1) }
                        CommentVoteChip("▼", c.downvotesCount, c.myVote == -1) { viewModel.vote(c.id, -1) }
                    }
                }
            }
            // Añadir comentario
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un comentario…") },
                    maxLines = 3,
                    shape = MaterialTheme.shapes.small,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (draft.isBlank()) MaterialTheme.colorScheme.surfaceVariant else Terra)
                        .clickable(enabled = draft.isNotBlank()) {
                            viewModel.add(blockId, lineId, draft.trim())
                            draft = ""
                        }
                        .padding(horizontal = Spacing.md, vertical = Spacing.md)
                ) {
                    Text("ENVIAR", style = EyebrowTextStyle,
                        color = if (draft.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White)
                }
            }
        }
    }
}

@Composable
private fun CommentVoteChip(symbol: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (active) Terra else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (active) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, style = MaterialTheme.typography.labelMedium,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$count", style = MaterialTheme.typography.labelMedium,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
