package com.meteomontana.android.community

import android.content.Context
import android.content.SharedPreferences
import com.meteomontana.android.domain.model.FeedAuthor
import com.meteomontana.android.domain.model.FeedComment
import com.meteomontana.android.domain.model.FeedPost
import com.meteomontana.android.domain.usecase.feed.AddFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.DeleteFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.DeleteFeedPostUseCase
import com.meteomontana.android.domain.usecase.feed.GetFeedCommentsUseCase
import com.meteomontana.android.domain.usecase.feed.GetFeedPageUseCase
import com.meteomontana.android.domain.usecase.feed.LikeFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.LikeFeedPostUseCase
import com.meteomontana.android.domain.usecase.feed.UnlikeFeedCommentUseCase
import com.meteomontana.android.domain.usecase.feed.UnlikeFeedPostUseCase
import com.meteomontana.android.ui.screens.community.FeedTab
import com.meteomontana.android.ui.screens.community.FeedViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * FeedViewModel: la pantalla más viva de la app (paginación por cursor, likes
 * optimistas con reversión, pestañas persistidas). Reglas que protege:
 *  - el like optimista se REVIERTE si el server falla (si no, likes fantasma)
 *  - loadMore deduplica ids (el cursor puede solapar página)
 *  - un fallo de red con contenido previo NO borra la lista
 *  - fallo de conectividad sin contenido → estado offline, no error genérico
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var getFeedPage: GetFeedPageUseCase
    private lateinit var likePost: LikeFeedPostUseCase
    private lateinit var unlikePost: UnlikeFeedPostUseCase
    private lateinit var deletePost: DeleteFeedPostUseCase
    private lateinit var getComments: GetFeedCommentsUseCase
    private lateinit var addComment: AddFeedCommentUseCase
    private lateinit var deleteComment: DeleteFeedCommentUseCase
    private lateinit var likeComment: LikeFeedCommentUseCase
    private lateinit var unlikeComment: UnlikeFeedCommentUseCase

    private val ana = FeedAuthor("uid1", "ana", "Ana", null)

    private fun post(id: Long, likes: Long = 0, likedByMe: Boolean = false) = FeedPost(
        id = id, kind = "TICK", createdAt = "2026-07-19T10:00:00", author = ana,
        schoolId = "esc", schoolName = "Zarzalejo", blockId = "b1", blockName = "15",
        lineId = "l$id", lineName = "Vía $id", grade = "6b", discipline = "BOULDER",
        rockType = "Granito", photoPath = null, linePath = null,
        likeCount = likes, likedByMe = likedByMe, commentCount = 0, mine = false
    )

    /** Página completa (PAGE_SIZE=20) → endReached=false. */
    private fun fullPage(from: Long) = (from until from + 20).map { post(it) }

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        prefs = mockk(relaxed = true) {
            every { getString(any(), any()) } returns null
        }
        context = mockk { every { getSharedPreferences(any(), any()) } returns prefs }
        getFeedPage = mockk()
        likePost = mockk(); unlikePost = mockk(); deletePost = mockk()
        getComments = mockk(); addComment = mockk(); deleteComment = mockk()
        likeComment = mockk(); unlikeComment = mockk()
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm() = FeedViewModel(
        context, getFeedPage, likePost, unlikePost, deletePost,
        getComments, addComment, deleteComment, likeComment, unlikeComment)

    @Test fun `carga inicial llena la lista y endReached refleja el tamano de pagina`() = runTest {
        coEvery { getFeedPage(any(), any(), any()) } returns listOf(post(1), post(2))
        val vm = vm(); advanceUntilIdle()
        assertEquals(2, vm.state.value.posts.size)
        assertTrue("página corta → no hay más", vm.state.value.endReached)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
    }

    @Test fun `like optimista sube el contador al momento y adopta el del server`() = runTest {
        coEvery { getFeedPage(any(), any(), any()) } returns listOf(post(1, likes = 3))
        coEvery { likePost(1) } returns 10L   // el server dice 10
        val vm = vm(); advanceUntilIdle()

        vm.toggleLike(vm.state.value.posts.first())
        // Optimista inmediato (antes de que responda el server).
        assertEquals(4, vm.state.value.posts.first().likeCount)
        assertTrue(vm.state.value.posts.first().likedByMe)
        advanceUntilIdle()
        // Contador real del server.
        assertEquals(10, vm.state.value.posts.first().likeCount)
    }

    @Test fun `like optimista se REVIERTE si el server falla`() = runTest {
        coEvery { getFeedPage(any(), any(), any()) } returns listOf(post(1, likes = 3))
        coEvery { likePost(1) } throws RuntimeException("500")
        val vm = vm(); advanceUntilIdle()

        vm.toggleLike(vm.state.value.posts.first())
        advanceUntilIdle()
        // Sin reversión, el usuario vería un like fantasma que no existe.
        assertEquals(3, vm.state.value.posts.first().likeCount)
        assertFalse(vm.state.value.posts.first().likedByMe)
    }

    @Test fun `loadMore pagina con cursor y deduplica ids solapados`() = runTest {
        val primera = fullPage(1)                      // ids 1..20
        coEvery { getFeedPage(any(), null, any()) } returns primera
        // La segunda página SOLAPA el id 20 (pasa con el cursor) y trae 21.
        coEvery { getFeedPage(any(), 20L, any()) } returns listOf(post(20), post(21))
        val vm = vm(); advanceUntilIdle()
        assertFalse(vm.state.value.endReached)

        vm.loadMore(); advanceUntilIdle()
        val ids = vm.state.value.posts.map { it.id }
        assertEquals("no debe duplicar el 20", ids.distinct(), ids)
        assertEquals(21, ids.size)
        assertTrue("página corta → fin", vm.state.value.endReached)
    }

    @Test fun `fallo de red CON contenido previo no borra la lista ni pone error`() = runTest {
        coEvery { getFeedPage(any(), any(), any()) } returns listOf(post(1))
        val vm = vm(); advanceUntilIdle()
        assertEquals(1, vm.state.value.posts.size)

        coEvery { getFeedPage(any(), any(), any()) } throws java.net.UnknownHostException()
        vm.refresh(); advanceUntilIdle()
        // La lista sobrevive: peor un feed viejo que una pantalla vacía.
        assertEquals(1, vm.state.value.posts.size)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.refreshing)
    }

    @Test fun `fallo de conectividad SIN contenido produce estado offline, no error`() = runTest {
        coEvery { getFeedPage(any(), any(), any()) } throws java.net.UnknownHostException()
        val vm = vm(); advanceUntilIdle()
        assertTrue(vm.state.value.offline)
        assertNull("sin red se muestra el estado offline estándar", vm.state.value.error)
    }

    @Test fun `borrar un post lo quita de la lista`() = runTest {
        coEvery { getFeedPage(any(), any(), any()) } returns listOf(post(1), post(2))
        coEvery { deletePost(1) } returns Unit
        val vm = vm(); advanceUntilIdle()

        vm.deletePost(vm.state.value.posts.first()); advanceUntilIdle()
        assertEquals(listOf(2L), vm.state.value.posts.map { it.id })
    }

    @Test fun `añadir comentario sube el contador del post`() = runTest {
        coEvery { getFeedPage(any(), any(), any()) } returns listOf(post(1))
        coEvery { addComment(1, "hola", null) } returns FeedComment(
            id = "c1", postId = 1, uid = "uid1", author = ana,
            text = "hola", createdAt = "2026-07-19T11:00:00", mine = true)
        val vm = vm(); advanceUntilIdle()

        val r = vm.addComment(1, "hola", null)
        assertTrue(r.isSuccess)
        assertEquals(1, vm.state.value.posts.first().commentCount)
    }

    @Test fun `cambiar a RANKING no recarga el feed`() = runTest {
        coEvery { getFeedPage(any(), any(), any()) } returns listOf(post(1))
        val vm = vm(); advanceUntilIdle()

        vm.selectTab(FeedTab.RANKING); advanceUntilIdle()
        // La lista del feed queda como estaba (RANKING pinta su propio VM).
        assertEquals(1, vm.state.value.posts.size)
        assertEquals(FeedTab.RANKING, vm.state.value.tab)
    }
}
