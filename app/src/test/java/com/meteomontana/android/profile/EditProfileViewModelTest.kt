package com.meteomontana.android.profile

import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.domain.port.PhotoUploader
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateMyProfileUseCase
import com.meteomontana.android.ui.screens.profile.EditProfileViewModel
import com.meteomontana.android.ui.screens.profile.EditState
import io.mockk.coEvery
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
 * EditProfileViewModel: load → Editing; save exitoso → Saved; error → Error.
 * (uploadPhoto usa android.net.Uri + FileReader/Storage → prueba de
 * instrumentación, no unitaria.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

    private val d = StandardTestDispatcher()
    private lateinit var getMyProfile: GetMyProfileUseCase
    private lateinit var updateMyProfile: UpdateMyProfileUseCase
    private lateinit var photoUploader: PhotoUploader
    private lateinit var fileReader: FileReader

    private val profile = PrivateProfile(
        uid = "me", email = "e", username = "yo", displayName = "Yo", photoUrl = null,
        bio = null, topGrade = null, isPublic = true, isAdmin = false, isPremium = false)

    @Before fun setUp() {
        Dispatchers.setMain(d)
        getMyProfile = mockk(); updateMyProfile = mockk()
        photoUploader = mockk(relaxed = true); fileReader = mockk(relaxed = true)
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = EditProfileViewModel(getMyProfile, updateMyProfile, photoUploader, fileReader)

    @Test fun `load produce Editing con el perfil`() = runTest {
        coEvery { getMyProfile() } returns profile
        val vm = vm(); advanceUntilIdle()
        val s = vm.state.value as EditState.Editing
        assertEquals("yo", s.profile.username)
    }

    @Test fun `load con error produce Error`() = runTest {
        coEvery { getMyProfile() } throws RuntimeException("500")
        val vm = vm(); advanceUntilIdle()
        assertTrue(vm.state.value is EditState.Error)
    }

    @Test fun `save exitoso pasa a Saved`() = runTest {
        coEvery { getMyProfile() } returns profile
        coEvery { updateMyProfile(any()) } returns profile
        val vm = vm(); advanceUntilIdle()
        vm.save(UpdateProfileRequest(bio = "hola")); advanceUntilIdle()
        assertEquals(EditState.Saved, vm.state.value)
    }

    @Test fun `save con error pasa a Error`() = runTest {
        coEvery { getMyProfile() } returns profile
        coEvery { updateMyProfile(any()) } throws RuntimeException("no")
        val vm = vm(); advanceUntilIdle()
        vm.save(UpdateProfileRequest(bio = "hola")); advanceUntilIdle()
        assertTrue(vm.state.value is EditState.Error)
    }
}
