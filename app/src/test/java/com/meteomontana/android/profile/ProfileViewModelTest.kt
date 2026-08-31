package com.meteomontana.android.profile

import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import com.meteomontana.android.data.auth.AuthManager
import com.meteomontana.android.data.local.ProfileCache
import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.ui.screens.profile.ProfileUiState
import com.meteomontana.android.ui.screens.profile.ProfileViewModel
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ProfileViewModel: reglas que protege:
 *  - carga en paralelo perfil+stats → Success
 *  - sin red cae a la caché local (offline=true); si no hay caché, arma un perfil
 *    mínimo desde Firebase Auth en vez de dar error (la pantalla nunca queda rota)
 *  - pendingReview solo se cuenta para admin
 *  - addBlock sube el grado tope del perfil solo si el nuevo es MAYOR (gradeRank)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val d = StandardTestDispatcher()

    private lateinit var getMyProfile: com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
    private lateinit var getStats: com.meteomontana.android.domain.usecase.journal.GetMyJournalStatsUseCase
    private lateinit var createJournal: com.meteomontana.android.domain.usecase.journal.CreateJournalEntryUseCase
    private lateinit var getPendingSubs: com.meteomontana.android.domain.usecase.admin.GetPendingSubmissionsUseCase
    private lateinit var getPendingContribs: com.meteomontana.android.domain.usecase.admin.GetPendingContributionsUseCase
    private lateinit var getFollowStatus: com.meteomontana.android.domain.usecase.social.GetFollowStatusUseCase
    private lateinit var updateMyProfile: com.meteomontana.android.domain.usecase.profile.UpdateMyProfileUseCase
    private lateinit var cache: ProfileCache
    private lateinit var deleteAccount: com.meteomontana.android.domain.usecase.profile.DeleteMyAccountUseCase
    private lateinit var auth: AuthManager

    private fun profile(admin: Boolean = false, top: String? = "6b") = PrivateProfile(
        uid = "me", email = "me@x.com", username = "yo", displayName = "Yo", photoUrl = "p",
        bio = null, topGrade = top, isPublic = true, isAdmin = admin, isPremium = false)

    private val stats = JournalStats(
        blockCount = 5, schoolCount = 2, maxGrade = "6b", bySchool = emptyList(), projectCount = 3)

    @Before fun setUp() {
        Dispatchers.setMain(d)
        getMyProfile = mockk(); getStats = mockk(); createJournal = mockk(relaxed = true)
        getPendingSubs = mockk(); getPendingContribs = mockk(); getFollowStatus = mockk()
        updateMyProfile = mockk(relaxed = true); cache = mockk(relaxed = true)
        deleteAccount = mockk(relaxed = true)
        auth = mockk(relaxed = true) { every { currentPhotoUrl() } returns null }
        coEvery { getFollowStatus(any()) } returns FollowStatus(10, 5, false, false)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ProfileViewModel(
        getMyProfile, getStats, createJournal, getPendingSubs, getPendingContribs,
        getFollowStatus, updateMyProfile, cache, deleteAccount, auth)

    @Test fun `carga produce Success con followers y projectCount`() = runTest {
        coEvery { getMyProfile() } returns profile()
        coEvery { getStats() } returns stats
        val vm = vm(); advanceUntilIdle()
        val s = vm.uiState.value as ProfileUiState.Success
        assertEquals(10, s.followers)
        assertEquals(5, s.following)
        assertEquals(3, s.projectCount)
        assertEquals(0, s.pendingReview) // no admin
    }

    @Test fun `pendingReview cuenta subs mas contribs solo si admin`() = runTest {
        coEvery { getMyProfile() } returns profile(admin = true)
        coEvery { getStats() } returns stats
        coEvery { getPendingSubs() } returns List(4) { mockk() }
        coEvery { getPendingContribs() } returns List(3) { mockk() }
        val vm = vm(); advanceUntilIdle()
        val s = vm.uiState.value as ProfileUiState.Success
        assertEquals(7, s.pendingReview)
    }

    @Test fun `sin red cae a la cache local con offline true`() = runTest {
        coEvery { getMyProfile() } throws java.net.UnknownHostException()
        every { cache.load() } returns ProfileCache.Cached(profile(), stats, 8, 4)
        val vm = vm(); advanceUntilIdle()
        val s = vm.uiState.value as ProfileUiState.Success
        assertTrue(s.offline)
        assertEquals(8, s.followers)
    }

    @Test fun `sin red y sin cache arma perfil minimo desde Auth`() = runTest {
        coEvery { getMyProfile() } throws java.net.UnknownHostException()
        every { cache.load() } returns null
        every { auth.currentUid() } returns "me"
        every { auth.currentEmail() } returns "me@x.com"
        every { auth.currentDisplayName() } returns "Yo"
        val vm = vm(); advanceUntilIdle()
        val s = vm.uiState.value as ProfileUiState.Success
        assertTrue(s.offline)
        assertEquals("me", s.profile.uid)  // nunca queda en Error
    }

    @Test fun `addBlock sube el grado tope si el nuevo es mayor`() = runTest {
        coEvery { getMyProfile() } returns profile(top = "6b")
        coEvery { getStats() } returns stats
        val vm = vm(); advanceUntilIdle()
        vm.addBlock(com.meteomontana.android.data.api.dto.CreateJournalRequest(
            schoolId = "esc", blockName = "Vía", grade = "7a", date = "2026-07-19", status = "DONE"))
        advanceUntilIdle()
        // 7a > 6b → actualiza el tope
        coVerify { updateMyProfile(UpdateProfileRequest(topGrade = "7a")) }
    }

    @Test fun `addBlock NO baja el grado tope si el nuevo es menor`() = runTest {
        coEvery { getMyProfile() } returns profile(top = "7a")
        coEvery { getStats() } returns stats
        val vm = vm(); advanceUntilIdle()
        vm.addBlock(com.meteomontana.android.data.api.dto.CreateJournalRequest(
            schoolId = "esc", blockName = "Vía", grade = "6a", date = "2026-07-19", status = "DONE"))
        advanceUntilIdle()
        coVerify(exactly = 0) { updateMyProfile(any()) }
    }
}
