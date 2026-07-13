package com.meteomontana.android.ui.screens.community

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.FeedComment
import com.meteomontana.android.domain.model.FeedPost
import com.meteomontana.android.domain.usecase.feed.AddFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.DeleteFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.DeleteFeedPostUseCase
import com.meteomontana.android.domain.usecase.feed.FeedScope
import com.meteomontana.android.domain.usecase.feed.GetFeedCommentsUseCase
import com.meteomontana.android.domain.usecase.feed.GetFeedPageUseCase
import com.meteomontana.android.domain.usecase.feed.LikeFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.LikeFeedPostUseCase
import com.meteomontana.android.domain.usecase.feed.UnlikeFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.UnlikeFeedPostUseCase
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Vistas del selector superior de la pestaña Comunidad.
 *  ALL se muestra como "Explorar" (el scope del backend sigue siendo "all"). */
enum class FeedTab { FOLLOWING, ALL, MINE, RANKING }

/** Filtro "Mostrar:" por tipo de post — SOLO en cliente: oculta sobre lo
 *  cargado; la paginación sigue trayendo todo. */
enum class FeedFilter { ALL, SENDS, NEW_BLOCKS }

private const val PAGE_SIZE = 20

data class FeedUiState(
    val tab: FeedTab = FeedTab.ALL,
    /** Filtro de tipo de post (cliente): Todo / Ascensos / Piedras nuevas. */
    val filter: FeedFilter = FeedFilter.ALL,
    val posts: List<FeedPost> = emptyList(),
    /** Primera carga del scope actual (spinner a pantalla). */
    val loading: Boolean = false,
    /** Pull-to-refresh en curso. */
    val refreshing: Boolean = false,
    /** Cargando la página siguiente (fila spinner al final). */
    val loadingMore: Boolean = false,
    /** Ya no quedan posts más antiguos. */
    val endReached: Boolean = false,
    /** El fallo de carga fue de conectividad → estado offline estándar. */
    val offline: Boolean = false,
    val error: String? = null
)

/** ¿La excepción es de conectividad (sin red / servidor inalcanzable)? */
internal fun Throwable.isConnectivityError(): Boolean =
    this is java.net.UnknownHostException ||
        this is java.net.ConnectException ||
        this is java.net.SocketTimeoutException

@HiltViewModel
class FeedViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val getFeedPage: GetFeedPageUseCase,
    private val likePost: LikeFeedPostUseCase,
    private val unlikePost: UnlikeFeedPostUseCase,
    private val deletePostUseCase: DeleteFeedPostUseCase,
    private val getComments: GetFeedCommentsUseCase,
    private val addCommentUseCase: AddFeedCommentUseCase,
    private val deleteCommentUseCase: DeleteFeedCommentUseCase,
    private val likeCommentUseCase: LikeFeedCommentUseCase,
    private val unlikeCommentUseCase: UnlikeFeedCommentUseCase
) : ViewModel() {

    // Persistimos la última pestaña elegida (paridad con el diseño: DataStore/
    // UserDefaults; SharedPreferences basta para un enum).
    private val prefs = context.getSharedPreferences("feed", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        FeedUiState(
            // Explorar (ALL) por defecto; se persiste la última vista y filtro.
            tab = runCatching { FeedTab.valueOf(prefs.getString("tab", null) ?: "") }
                .getOrDefault(FeedTab.ALL),
            filter = runCatching { FeedFilter.valueOf(prefs.getString("filter", null) ?: "") }
                .getOrDefault(FeedFilter.ALL)
        )
    )
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    init { if (_state.value.tab != FeedTab.RANKING) load(initial = true) }

    private fun scopeParam(tab: FeedTab): String = when (tab) {
        FeedTab.ALL -> FeedScope.ALL
        FeedTab.MINE -> FeedScope.MINE
        else -> FeedScope.FOLLOWING
    }

    /** Cambia el filtro "Mostrar:" (persistido; solo oculta en cliente). */
    fun selectFilter(filter: FeedFilter) {
        if (filter == _state.value.filter) return
        prefs.edit().putString("filter", filter.name).apply()
        _state.value = _state.value.copy(filter = filter)
    }

    fun selectTab(tab: FeedTab) {
        if (tab == _state.value.tab) return
        prefs.edit().putString("tab", tab.name).apply()
        _state.value = _state.value.copy(tab = tab)
        // RANKING pinta CommunityScreen (su propio VM); el feed no recarga.
        if (tab != FeedTab.RANKING) load(initial = _state.value.posts.isEmpty())
    }

    /** Recarga silenciosa (ON_RESUME / al volver a la pestaña): trae la primera
     *  página y sustituye la lista, sin spinner si ya había contenido. */
    fun refreshSilent() {
        if (_state.value.tab == FeedTab.RANKING) return
        load(initial = _state.value.posts.isEmpty())
    }

    fun refresh() {
        if (_state.value.tab == FeedTab.RANKING) return
        _state.value = _state.value.copy(refreshing = true)
        load(initial = false)
    }

    private fun load(initial: Boolean) {
        val tab = _state.value.tab
        if (initial) _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { getFeedPage(scopeParam(tab), before = null, limit = PAGE_SIZE) }
                .onSuccess { page ->
                    // Si el usuario cambió de pestaña mientras cargaba, descartar.
                    if (_state.value.tab != tab) return@launch
                    _state.value = _state.value.copy(
                        posts = page, loading = false, refreshing = false,
                        endReached = page.size < PAGE_SIZE, offline = false, error = null
                    )
                }
                .onFailure { t ->
                    if (_state.value.tab != tab) return@launch
                    val noContent = _state.value.posts.isEmpty()
                    _state.value = _state.value.copy(
                        loading = false, refreshing = false,
                        // Sin red → estado offline estándar en vez de error genérico.
                        offline = noContent && t.isConnectivityError(),
                        // Con contenido previo, el error no borra la lista.
                        error = if (noContent && !t.isConnectivityError()) t.toUserMessage() else null
                    )
                }
        }
    }

    /** Página siguiente (cursor = id del último post visible). */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached || s.tab == FeedTab.RANKING) return
        val lastId = s.posts.lastOrNull()?.id ?: return
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            runCatching { getFeedPage(scopeParam(s.tab), before = lastId, limit = PAGE_SIZE) }
                .onSuccess { page ->
                    val cur = _state.value
                    val known = cur.posts.map { it.id }.toSet()
                    _state.value = cur.copy(
                        posts = cur.posts + page.filter { it.id !in known },
                        loadingMore = false,
                        endReached = page.size < PAGE_SIZE
                    )
                }
                .onFailure { _state.value = _state.value.copy(loadingMore = false) }
        }
    }

    /** Toggle like con actualización optimista; el server devuelve el contador real. */
    fun toggleLike(post: FeedPost) {
        val liked = !post.likedByMe
        updatePost(post.id) {
            it.copy(likedByMe = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0))
        }
        viewModelScope.launch {
            runCatching { if (liked) likePost(post.id) else unlikePost(post.id) }
                .onSuccess { count -> updatePost(post.id) { it.copy(likeCount = count) } }
                .onFailure {
                    // Revertir el optimismo si falló.
                    updatePost(post.id) { p ->
                        p.copy(likedByMe = post.likedByMe, likeCount = post.likeCount)
                    }
                }
        }
    }

    fun deletePost(post: FeedPost) {
        viewModelScope.launch {
            runCatching { deletePostUseCase(post.id) }.onSuccess {
                _state.value = _state.value.copy(posts = _state.value.posts.filter { it.id != post.id })
            }
        }
    }

    private fun updatePost(id: Long, transform: (FeedPost) -> FeedPost) {
        _state.value = _state.value.copy(
            posts = _state.value.posts.map { if (it.id == id) transform(it) else it }
        )
    }

    // ── Comentarios (hoja) ───────────────────────────────────────────────────

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
