@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.community

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.FeedComment
import com.meteomontana.android.domain.model.FeedPost
import com.meteomontana.android.domain.usecase.feed.AddFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.GetFeedCommentsUseCase
import com.meteomontana.android.domain.usecase.feed.GetFeedPostUseCase
import com.meteomontana.android.domain.usecase.feed.LikeFeedPostUseCase
import com.meteomontana.android.domain.usecase.feed.UnlikeFeedPostUseCase
import com.meteomontana.android.ui.components.ModerationViewModel
import com.meteomontana.android.ui.components.ReportDialog
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado del detalle de un post del feed (deep link de push/campanita). */
data class FeedPostDetailUiState(
    val loading: Boolean = true,
    val post: FeedPost? = null,
    val comments: List<FeedComment> = emptyList(),
    /** 404: el post ya no existe (borrado / no visible). */
    val notFound: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FeedPostDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getFeedPost: GetFeedPostUseCase,
    private val getComments: GetFeedCommentsUseCase,
    private val addCommentUseCase: AddFeedCommentUseCase,
    private val likePost: LikeFeedPostUseCase,
    private val unlikePost: UnlikeFeedPostUseCase
) : ViewModel() {

    // toLongOrNull: un id no numérico (p.ej. targetId inesperado desde admin)
    // cae al flujo 404 (toast "ya no existe") en vez de crashear.
    private val postId: Long? = savedStateHandle.get<String>("postId")?.toLongOrNull()

    private val _state = MutableStateFlow(FeedPostDetailUiState())
    val state: StateFlow<FeedPostDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        val id = postId
        if (id == null) {
            _state.value = _state.value.copy(loading = false, notFound = true)
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { getFeedPost(id) }
                .onSuccess { post ->
                    _state.value = _state.value.copy(loading = false, post = post)
                    runCatching { getComments(id) }.onSuccess { list ->
                        _state.value = _state.value.copy(comments = list)
                    }
                }
                .onFailure { t ->
                    val gone = (t as? ClientRequestException)?.response?.status?.value == 404
                    _state.value = _state.value.copy(
                        loading = false, notFound = gone,
                        error = if (gone) null else t.toUserMessage()
                    )
                }
        }
    }

    fun toggleLike() {
        val post = _state.value.post ?: return
        val liked = !post.likedByMe
        _state.value = _state.value.copy(
            post = post.copy(
                likedByMe = liked,
                likeCount = (post.likeCount + if (liked) 1 else -1).coerceAtLeast(0)
            )
        )
        viewModelScope.launch {
            runCatching { if (liked) likePost(post.id) else unlikePost(post.id) }
                .onSuccess { count ->
                    _state.value.post?.let {
                        _state.value = _state.value.copy(post = it.copy(likeCount = count))
                    }
                }
                .onFailure { _state.value = _state.value.copy(post = post) }
        }
    }

    suspend fun addComment(text: String): Boolean {
        val post = _state.value.post ?: return false
        return runCatching { addCommentUseCase(post.id, text) }
            .onSuccess { created ->
                _state.value = _state.value.copy(
                    comments = _state.value.comments + created,
                    post = _state.value.post?.copy(commentCount = post.commentCount + 1)
                )
            }.isSuccess
    }
}

/**
 * Detalle de un post del feed: tarjeta completa + comentarios inline + campo
 * de respuesta. Destino del push/campanita "feed_post". Si el post ya no
 * existe (404) → toast "La publicación ya no existe" y se cierra.
 */
@Composable
fun FeedPostDetailScreen(
    onBack: () -> Unit,
    onOpenUser: (uid: String) -> Unit,
    onOpenSchool: (schoolId: String, lineId: String?, lineName: String?) -> Unit,
    viewModel: FeedPostDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val moderation: ModerationViewModel = hiltViewModel()
    val hiddenIds by moderation.hiddenIds.collectAsState()
    var reportPost by remember { mutableStateOf(false) }
    var reportComment by remember { mutableStateOf<FeedComment?>(null) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    // 404 → toast + cerrar (no hay nada que enseñar).
    LaunchedEffect(state.notFound) {
        if (state.notFound) {
            Toast.makeText(ctx, ctx.getString(R.string.feed_post_gone), Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Cabecera: atrás + eyebrow.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                stringResource(R.string.feed_post_title).uppercase(),
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.common_retry), style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                            .clickable { viewModel.load() }.padding(8.dp)
                    )
                }
            }
            state.post != null -> {
                val post = state.post!!
                Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                    ) {
                        item {
                            FeedPostCard(
                                post = post,
                                onOpenSchool = onOpenSchool,
                                onOpenUser = onOpenUser,
                                onToggleLike = { viewModel.toggleLike() },
                                onOpenComments = {},
                                onDelete = {},
                                onReport = if (!post.mine) ({ reportPost = true }) else null
                            )
                        }
                        item {
                            Text(
                                stringResource(R.string.feed_comments_title).uppercase(),
                                style = EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }
                        val visible = state.comments.filter { it.id !in hiddenIds }
                        if (visible.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.feed_comments_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                        items(visible, key = { it.id }) { comment ->
                            FeedCommentRow(
                                comment = comment,
                                onOpenUser = onOpenUser,
                                onDelete = null,
                                onReport = if (!comment.mine) ({ reportComment = comment }) else null
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    // Campo de respuesta inline.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text(stringResource(R.string.feed_comment_hint)) },
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            shape = RoundedCornerShape(2.dp)
                        )
                        IconButton(
                            onClick = {
                                val t = text.trim()
                                if (t.isEmpty() || sending) return@IconButton
                                sending = true
                                scope.launch {
                                    if (viewModel.addComment(t)) text = ""
                                    sending = false
                                }
                            },
                            enabled = text.isNotBlank() && !sending
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = stringResource(R.string.common_send),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (reportPost) state.post?.let { post ->
        ReportDialog(
            title = stringResource(R.string.feed_report_post),
            authorLabel = post.author.displayName ?: post.author.username?.let { "@$it" },
            onReport = { reason, alsoBlock ->
                moderation.report(
                    "FEED_POST", post.id.toString(), reason,
                    alsoBlockUid = if (alsoBlock) post.author.uid else null
                )
                reportPost = false
            },
            onDismiss = { reportPost = false }
        )
    }

    reportComment?.let { c ->
        ReportDialog(
            title = stringResource(R.string.feed_report_comment),
            authorLabel = c.author?.displayName ?: c.author?.username?.let { "@$it" },
            onReport = { reason, alsoBlock ->
                moderation.report(
                    "FEED_COMMENT", c.id, reason,
                    alsoBlockUid = if (alsoBlock) (c.author?.uid ?: c.uid) else null
                )
                reportComment = null
            },
            onDismiss = { reportComment = null }
        )
    }
}
