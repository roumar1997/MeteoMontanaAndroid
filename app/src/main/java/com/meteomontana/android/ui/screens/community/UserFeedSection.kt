package com.meteomontana.android.ui.screens.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.meteomontana.android.domain.usecase.feed.DeleteFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.FeedScope
import com.meteomontana.android.domain.usecase.feed.GetFeedCommentsUseCase
import com.meteomontana.android.domain.usecase.feed.GetFeedPageUseCase
import com.meteomontana.android.domain.usecase.feed.LikeFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.LikeFeedPostUseCase
import com.meteomontana.android.domain.usecase.feed.UnlikeFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.UnlikeFeedPostUseCase
import com.meteomontana.android.ui.components.ModerationViewModel
import com.meteomontana.android.ui.components.ReportDialog
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE = 10

data class UserFeedUiState(
    val posts: List<FeedPost> = emptyList(),
    val loading: Boolean = true,
    val endReached: Boolean = false,
    val loadingMore: Boolean = false
)

/**
 * Posts de UN usuario para su perfil público (GET /api/feed?scope=user&uid=…).
 * El uid sale del SavedStateHandle del destino users/{uid}; si NO hay uid
 * (perfil PROPIO, pestaña/hoja Perfil) usa scope=mine. El backend devuelve
 * lista vacía si el perfil es privado y no le sigues (nunca 404).
 */
@HiltViewModel
class UserFeedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getFeedPage: GetFeedPageUseCase,
    private val likePost: LikeFeedPostUseCase,
    private val unlikePost: UnlikeFeedPostUseCase,
    private val getComments: GetFeedCommentsUseCase,
    private val addCommentUseCase: AddFeedCommentUseCase,
    private val deleteCommentUseCase: DeleteFeedCommentUseCase,
    private val likeCommentUseCase: LikeFeedCommentUseCase,
    private val unlikeCommentUseCase: UnlikeFeedCommentUseCase,
    private val deletePostUseCase: com.meteomontana.android.domain.usecase.feed.DeleteFeedPostUseCase
) : ViewModel() {

    // null = perfil propio → scope "mine" (sin uid).
    private val uid: String? = savedStateHandle.get<String>("uid")
    private val scope: String = if (uid != null) FeedScope.USER else FeedScope.MINE

    private val _state = MutableStateFlow(UserFeedUiState())
    val state: StateFlow<UserFeedUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            runCatching { getFeedPage(scope, before = null, limit = PAGE, uid = uid) }
                .onSuccess { page ->
                    _state.value = UserFeedUiState(
                        posts = page, loading = false, endReached = page.size < PAGE
                    )
                }
                .onFailure { _state.value = _state.value.copy(loading = false, endReached = true) }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        val lastId = s.posts.lastOrNull()?.id ?: return
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            runCatching { getFeedPage(scope, before = lastId, limit = PAGE, uid = uid) }
                .onSuccess { page ->
                    val known = _state.value.posts.map { it.id }.toSet()
                    _state.value = _state.value.copy(
                        posts = _state.value.posts + page.filter { it.id !in known },
                        loadingMore = false, endReached = page.size < PAGE
                    )
                }
                .onFailure { _state.value = _state.value.copy(loadingMore = false) }
        }
    }

    fun toggleLike(post: FeedPost) {
        val liked = !post.likedByMe
        updatePost(post.id) {
            it.copy(likedByMe = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0))
        }
        viewModelScope.launch {
            runCatching { if (liked) likePost(post.id) else unlikePost(post.id) }
                .onSuccess { count -> updatePost(post.id) { it.copy(likeCount = count) } }
                .onFailure {
                    updatePost(post.id) { p ->
                        p.copy(likedByMe = post.likedByMe, likeCount = post.likeCount)
                    }
                }
        }
    }

    /** Borra un post propio (optimista: lo quita de la lista y llama al server). */
    fun deletePost(post: FeedPost) {
        val previous = _state.value.posts
        _state.value = _state.value.copy(posts = previous.filter { it.id != post.id })
        viewModelScope.launch {
            runCatching { deletePostUseCase(post.id) }
                .onFailure { _state.value = _state.value.copy(posts = previous) }
        }
    }

    private fun updatePost(id: Long, transform: (FeedPost) -> FeedPost) {
        _state.value = _state.value.copy(
            posts = _state.value.posts.map { if (it.id == id) transform(it) else it }
        )
    }

    // Comentarios (para la hoja compartida).
    suspend fun loadComments(postId: Long): Result<List<FeedComment>> =
        runCatching { getComments(postId) }

    suspend fun addComment(postId: Long, text: String, parentId: String?): Result<FeedComment> =
        runCatching { addCommentUseCase(postId, text, parentId) }.onSuccess {
            updatePost(postId) { it.copy(commentCount = it.commentCount + 1) }
        }

    suspend fun deleteComment(postId: Long, commentId: String): Result<Unit> =
        runCatching { deleteCommentUseCase(commentId) }.onSuccess {
            updatePost(postId) { it.copy(commentCount = (it.commentCount - 1).coerceAtLeast(0)) }
        }

    /** Like/unlike de un comentario; devuelve el likeCount actualizado. */
    suspend fun toggleCommentLike(commentId: String, like: Boolean): Result<Long> =
        runCatching { if (like) likeCommentUseCase(commentId) else unlikeCommentUseCase(commentId) }
}

/**
 * Sección "Publicaciones" del perfil público: lista los posts del usuario
 * reutilizando FeedPostCard (likes/comentarios funcionales). Column normal
 * (el perfil ya hace scroll). Si el usuario no tiene posts (o es privado y
 * el backend devuelve vacío) se muestra un texto discreto.
 */
@Composable
fun UserFeedSection(
    onOpenUser: (uid: String) -> Unit,
    onOpenSchool: (schoolId: String, lineId: String?, lineName: String?, blockId: String?) -> Unit,
    /** Título de la sección (perfil propio usa "Mis publicaciones"). */
    titleRes: Int = R.string.feed_posts_section,
    /** true en el perfil PROPIO: recarga en ON_RESUME (post nuevo tras marcar
     *  una vía) y permite borrar los posts propios. */
    ownProfile: Boolean = false,
    /** false cuando la sección vive en una pantalla dedicada con su propio título. */
    showTitle: Boolean = true,
    viewModel: UserFeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val moderation: ModerationViewModel = hiltViewModel()
    val hiddenIds by moderation.hiddenIds.collectAsState()
    var commentsPost by remember { mutableStateOf<FeedPost?>(null) }
    var reportPost by remember { mutableStateOf<FeedPost?>(null) }
    var deleteCandidate by remember { mutableStateOf<FeedPost?>(null) }

    // Perfil propio: refresca al volver a primer plano (patrón ON_RESUME).
    if (ownProfile) {
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.load()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    if (state.loading) return
    Column(Modifier.fillMaxWidth()) {
        if (showTitle) Text(
            stringResource(titleRes).uppercase(),
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        // "Mis publicaciones" = solo lo que el usuario ELIGE publicar (TICK /
        // PROJECT_DONE). Las de piedra/vía nueva (NEW_BLOCK/NEW_LINE) son
        // automáticas al aprobar una propuesta → no van aquí ni en el perfil
        // público. (La pestaña "Mías" del feed sí las muestra, no se toca.)
        val visible = state.posts
            .filter { it.kind != "NEW_BLOCK" && it.kind != "NEW_LINE" }
            .filter { "FEED_POST:${it.id}" !in hiddenIds }
        if (visible.isEmpty()) {
            Text(
                stringResource(R.string.feed_posts_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                visible.forEach { post ->
                    FeedPostCard(
                        post = post,
                        onOpenSchool = onOpenSchool,
                        onOpenUser = onOpenUser,
                        onToggleLike = { viewModel.toggleLike(post) },
                        onOpenComments = { commentsPost = post },
                        onDelete = { if (post.mine) deleteCandidate = post },
                        onReport = if (!post.mine) ({ reportPost = post }) else null
                    )
                }
                if (!state.endReached) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .clickable(enabled = !state.loadingMore) { viewModel.loadMore() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.feed_load_more),
                            style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    commentsPost?.let { post ->
        FeedCommentsSheet(
            post = post,
            loadComments = viewModel::loadComments,
            addComment = viewModel::addComment,
            deleteComment = viewModel::deleteComment,
            toggleCommentLike = viewModel::toggleCommentLike,
            onOpenUser = onOpenUser,
            onDismiss = { commentsPost = null }
        )
    }

    deleteCandidate?.let { post ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.feed_delete_post)) },
            text = { Text(stringResource(R.string.feed_delete_post_confirm)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.deletePost(post)
                    deleteCandidate = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    reportPost?.let { post ->
        ReportDialog(
            title = stringResource(R.string.feed_report_post),
            authorLabel = post.author.displayName ?: post.author.username?.let { "@$it" },
            onReport = { reason, alsoBlock ->
                moderation.report(
                    "FEED_POST", post.id.toString(), reason,
                    alsoBlockUid = if (alsoBlock) post.author.uid else null
                )
                reportPost = null
            },
            onDismiss = { reportPost = null }
        )
    }
}
