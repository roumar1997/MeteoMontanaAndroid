package com.meteomontana.android.chat

import androidx.lifecycle.SavedStateHandle
import com.meteomontana.android.data.api.KtorChatPushApi
import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowStatusUseCase
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import com.meteomontana.android.ui.screens.chat.ChatViewModel
import io.mockk.coEvery
import io.mockk.coVerify
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
 * ChatViewModel: reglas del modelo de privacidad y del eco optimista:
 *  - canWrite si el receptor es público O hay relación de seguimiento
 *  - send() no hace NADA si no puedo escribir (gating de privacidad)
 *  - send() con permiso muestra el mensaje al momento (eco optimista) antes de red
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val d = StandardTestDispatcher()
    private lateinit var chat: ChatService
    private lateinit var auth: AuthService
    private lateinit var getPublic: GetPublicProfileUseCase
    private lateinit var getFollow: GetFollowStatusUseCase
    private lateinit var getMyProfile: GetMyProfileUseCase
    private lateinit var pushApi: KtorChatPushApi

    private fun publicProfile(isPublic: Boolean) = PublicProfile(
        uid = "otro", username = "otro", displayName = "Otro", photoUrl = null,
        bio = null, topGrade = null, isPublic = isPublic)

    @Before fun setUp() {
        Dispatchers.setMain(d)
        chat = mockk(relaxed = true) {
            every { convIdFor(any(), any()) } returns "conv"
            every { observeMessages(any(), any()) } returns flowOf(emptyList())
            every { observeMyConversations() } returns flowOf(emptyList())
        }
        auth = mockk { every { currentUid() } returns "me" }
        getPublic = mockk(); getFollow = mockk(); getMyProfile = mockk(relaxed = true)
        pushApi = mockk(relaxed = true)
        coEvery { getMyProfile() } returns mockk(relaxed = true)
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ChatViewModel(
        SavedStateHandle(mapOf("uid" to "otro")),
        chat, auth, getPublic, getFollow, getMyProfile, pushApi)

    @Test fun `canWrite true si el receptor es publico`() = runTest {
        coEvery { getPublic("otro") } returns publicProfile(isPublic = true)
        coEvery { getFollow("otro") } returns FollowStatus(0, 0, false, false)
        val vm = vm(); advanceUntilIdle()
        assertTrue(vm.state.value.canWrite)
    }

    @Test fun `canWrite true si hay relacion de seguimiento aunque sea privado`() = runTest {
        coEvery { getPublic("otro") } returns publicProfile(isPublic = false)
        coEvery { getFollow("otro") } returns FollowStatus(0, 0, iFollowThem = true, theyFollowMe = false)
        val vm = vm(); advanceUntilIdle()
        assertTrue(vm.state.value.canWrite)
    }

    @Test fun `canWrite false si privado y sin relacion`() = runTest {
        coEvery { getPublic("otro") } returns publicProfile(isPublic = false)
        coEvery { getFollow("otro") } returns FollowStatus(0, 0, false, false)
        val vm = vm(); advanceUntilIdle()
        assertFalse(vm.state.value.canWrite)
    }

    @Test fun `send sin permiso no hace nada`() = runTest {
        coEvery { getPublic("otro") } returns publicProfile(isPublic = false)
        coEvery { getFollow("otro") } returns FollowStatus(0, 0, false, false)
        val vm = vm(); advanceUntilIdle()
        vm.send("hola"); advanceUntilIdle()
        assertTrue(vm.state.value.messages.isEmpty())
        coVerify(exactly = 0) { chat.sendMessage(any(), any(), any(), any(), any()) }
    }

    @Test fun `send con permiso muestra el eco optimista al momento`() = runTest {
        coEvery { getPublic("otro") } returns publicProfile(isPublic = true)
        coEvery { getFollow("otro") } returns FollowStatus(0, 0, false, false)
        coEvery { pushApi.startConversation("otro") } returns Unit
        val vm = vm(); advanceUntilIdle()
        vm.send("hola")
        // Antes incluso de avanzar la corrutina de red, el mensaje ya se ve.
        assertEquals(listOf("hola"), vm.state.value.messages.map { it.text })
        advanceUntilIdle()
    }

    @Test fun `startReply y cancelReply gestionan la cita`() = runTest {
        coEvery { getPublic("otro") } returns publicProfile(isPublic = true)
        coEvery { getFollow("otro") } returns FollowStatus(0, 0, false, false)
        val vm = vm(); advanceUntilIdle()
        val msg = ChatService.ChatMessage("m1", "otro", "cita", 1000)
        vm.startReply(msg)
        assertEquals("cita", vm.state.value.replyingTo?.text)
        vm.cancelReply()
        assertEquals(null, vm.state.value.replyingTo)
    }
}
