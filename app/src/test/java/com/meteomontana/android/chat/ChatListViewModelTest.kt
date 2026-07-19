package com.meteomontana.android.chat

import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.domain.usecase.social.GetFollowersUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowingUseCase
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.ui.screens.chat.ChatListViewModel
import com.meteomontana.android.ui.screens.chat.isHiddenForMe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ChatList: isHiddenForMe (borrada para mí sin mensajes posteriores) + carga de
 * contactos (seguidores ∪ seguidos, sin repetir, ordenados) + borrado optimista.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

    private val d = StandardTestDispatcher()

    private fun conv(id: String, cleared: Long?, lastAt: Long?) = ChatService.Conversation(
        id = id, participants = listOf("me", "otro"), lastMessage = "hola", lastFromUid = "otro",
        lastAtMillis = lastAt, unreadCount = 0, clearedAtMillis = cleared)

    // ── isHiddenForMe: función pura compartida con el badge de no-leídos ──

    @Test fun `no borrada nunca se oculta`() {
        assertFalse(isHiddenForMe(conv("c", cleared = null, lastAt = 100)))
    }

    @Test fun `borrada sin mensajes posteriores se oculta`() {
        assertTrue(isHiddenForMe(conv("c", cleared = 200, lastAt = 150)))
    }

    @Test fun `borrada pero con mensaje POSTERIOR reaparece`() {
        assertFalse(isHiddenForMe(conv("c", cleared = 200, lastAt = 300)))
    }

    @Test fun `borrada y sin ultimo mensaje se oculta`() {
        assertTrue(isHiddenForMe(conv("c", cleared = 200, lastAt = null)))
    }

    // ── loadContacts y borrado optimista ──

    @Before fun setUp() { Dispatchers.setMain(d) }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun profile(uid: String, name: String) =
        PublicProfile(uid = uid, username = name, displayName = name, photoUrl = null,
            bio = null, topGrade = null)

    @Test fun `loadContacts une seguidores y seguidos sin duplicar y ordenados`() = runTest {
        val chat = mockk<ChatService>(relaxed = true) {
            every { observeMyConversations() } returns flowOf(emptyList())
        }
        val auth = mockk<AuthService> { every { currentUid() } returns "me" }
        val getFollowers = mockk<GetFollowersUseCase>()
        val getFollowing = mockk<GetFollowingUseCase>()
        val getPublic = mockk<GetPublicProfileUseCase>(relaxed = true)
        coEvery { getFollowers("me") } returns listOf(profile("u1", "Carlos"), profile("u2", "Ana"))
        coEvery { getFollowing("me") } returns listOf(profile("u2", "Ana"), profile("u3", "Zoe"))

        val vm = ChatListViewModel(chat, auth, getPublic, getFollowers, getFollowing)
        vm.loadContacts(); advanceUntilIdle()

        val names = vm.contacts.value.map { it.displayName }
        assertEquals(listOf("Ana", "Carlos", "Zoe"), names)  // u2 no duplicado, orden alfabético
    }

    @Test fun `deleteConversation la quita de la lista al momento`() = runTest {
        val chat = mockk<ChatService>(relaxed = true) {
            every { observeMyConversations() } returns
                flowOf(listOf(conv("c1", null, 100), conv("c2", null, 100)))
        }
        val auth = mockk<AuthService>(relaxed = true) { every { currentUid() } returns "me" }
        val getPublic = mockk<GetPublicProfileUseCase>(relaxed = true)
        val vm = ChatListViewModel(chat, auth, getPublic, mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()
        assertEquals(2, vm.items.value.size)

        vm.deleteConversation("c1"); advanceUntilIdle()
        assertEquals(listOf("c2"), vm.items.value.map { it.conversation.id })
    }
}
