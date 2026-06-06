package com.meteomontana.android.detail

import androidx.lifecycle.SavedStateHandle
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.domain.model.FavoriteSchool
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.Current
import com.meteomontana.android.domain.model.Forecast
import android.content.Context
import com.meteomontana.android.domain.port.PhotoUploader
import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.blocks.CreateBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.DeleteBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.contributions.SubmitContributionUseCase
import com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.domain.usecase.notes.CreateNoteUseCase
import com.meteomontana.android.domain.usecase.notes.GetNotesUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolByIdUseCase
import com.meteomontana.android.ui.screens.detail.BoulderBloqueForm
import com.meteomontana.android.ui.screens.detail.SchoolDetailUiState
import com.meteomontana.android.ui.screens.detail.SchoolDetailViewModel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SchoolDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getSchoolById: GetSchoolByIdUseCase
    private lateinit var getForecast: GetForecastUseCase
    private lateinit var getNotes: GetNotesUseCase
    private lateinit var createNote: CreateNoteUseCase
    private lateinit var getMyFavorites: GetMyFavoritesUseCase
    private lateinit var addFavorite: AddFavoriteUseCase
    private lateinit var removeFavorite: RemoveFavoriteUseCase
    private lateinit var getBlocks: GetBlocksUseCase
    private lateinit var createBlock: CreateBlockUseCase
    private lateinit var deleteBlockUC: DeleteBlockUseCase
    private lateinit var submitContribution: SubmitContributionUseCase
    private lateinit var getMyProfile: GetMyProfileUseCase
    private lateinit var photoUploader: PhotoUploader
    private lateinit var context: Context

    private val schoolId = "s1"
    private val school = School(
        id = schoolId, name = "Pedriza", location = "Madrid", region = "Madrid",
        style = "Bloque", rockType = "Granito", lat = 40.768, lon = -3.852, source = null
    )

    private fun savedState() = SavedStateHandle(mapOf("schoolId" to schoolId))

    private val forecast = Forecast(
        schoolId = schoolId, schoolName = "Pedriza", lat = 40.768, lon = -3.852,
        current = Current(
            time = "2026-06-06T10:00", temperature = 20.0, humidity = 50.0,
            windSpeed = 5.0, precipitation = 0.0, precipitationProbability = 0,
            cloudCover = 10, dewPoint = 8.0, precip24h = 0.0, precip72h = 0.0,
            dryRock = true, score = 80, scoreLabel = "Bueno", factors = emptyList()
        ),
        hours = emptyList(), days = emptyList(), bestDay = null, bestWindow = null
    )

    private val profile = PrivateProfile(
        uid = "u1", email = null, username = null, displayName = null, photoUrl = null,
        bio = null, topGrade = null, isPublic = true, isAdmin = false, isPremium = false
    )

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getSchoolById = mockk()
        getForecast = mockk()
        getNotes = mockk()
        createNote = mockk()
        getMyFavorites = mockk()
        addFavorite = mockk()
        removeFavorite = mockk()
        getBlocks = mockk()
        createBlock = mockk()
        deleteBlockUC = mockk()
        submitContribution = mockk()
        getMyProfile = mockk()
        photoUploader = mockk()
        context = mockk()

        coEvery { getSchoolById(schoolId) } returns school
        coEvery { getForecast(schoolId) } returns forecast
        coEvery { getNotes(schoolId) } returns emptyList()
        coEvery { getMyFavorites() } returns emptyList()
        coEvery { getBlocks(schoolId) } returns emptyList()
        coEvery { getMyProfile() } returns profile
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newVm() = SchoolDetailViewModel(
        savedState(), getSchoolById, getForecast, getNotes, createNote,
        getMyFavorites, addFavorite, removeFavorite, getBlocks, createBlock,
        deleteBlockUC, submitContribution, getMyProfile, photoUploader, context
    )

    @Test fun `load con todo OK produce Success con forecast y sin error`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("debe ser Success, fue $state", state is SchoolDetailUiState.Success)
        val s = state as SchoolDetailUiState.Success
        assertEquals(schoolId, s.school.id)
        assertNotNull(s.forecast)
        assertNull(s.forecastError)
        assertFalse(s.isFavorite)
        assertFalse(s.isCurrentUserAdmin)
    }

    @Test fun `load con forecast error mantiene Success y rellena forecastError`() = runTest {
        coEvery { getForecast(schoolId) } throws RuntimeException("forecast caído")
        val vm = newVm()
        advanceUntilIdle()

        val s = vm.uiState.value as SchoolDetailUiState.Success
        assertNull(s.forecast)
        assertEquals("forecast caído", s.forecastError)
    }

    @Test fun `load con escuela inexistente produce Error`() = runTest {
        coEvery { getSchoolById(schoolId) } throws RuntimeException("not found")
        val vm = newVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is SchoolDetailUiState.Error)
        assertEquals("not found", (state as SchoolDetailUiState.Error).message)
    }

    @Test fun `load detecta admin desde getMyProfile`() = runTest {
        coEvery { getMyProfile() } returns profile.copy(isAdmin = true)
        val vm = newVm()
        advanceUntilIdle()

        val s = vm.uiState.value as SchoolDetailUiState.Success
        assertTrue(s.isCurrentUserAdmin)
    }

    @Test fun `isFavorite true cuando la escuela está en favoritos`() = runTest {
        coEvery { getMyFavorites() } returns listOf(
            FavoriteSchool(id = schoolId, name = "Pedriza", region = null, rockType = null, isFavorite = true)
        )
        val vm = newVm()
        advanceUntilIdle()

        assertTrue((vm.uiState.value as SchoolDetailUiState.Success).isFavorite)
    }

    @Test fun `toggleFavorite añade y refleja en el estado`() = runTest {
        coEvery { addFavorite(schoolId) } just Runs
        coEvery { removeFavorite(schoolId) } just Runs

        val vm = newVm()
        advanceUntilIdle()

        vm.toggleFavorite()
        advanceUntilIdle()

        coVerify { addFavorite(schoolId) }
        assertTrue((vm.uiState.value as SchoolDetailUiState.Success).isFavorite)

        vm.toggleFavorite()
        advanceUntilIdle()
        coVerify { removeFavorite(schoolId) }
        assertFalse((vm.uiState.value as SchoolDetailUiState.Success).isFavorite)
    }

    @Test fun `publishNote llama a createNote y refresca la lista de notas`() = runTest {
        val newNote = Note(
            id = "n1", schoolId = schoolId, text = "hola", author = "Rodrigo",
            uid = "u1", createdAt = "2026-06-06", upvotesCount = 0, downvotesCount = 0
        )
        coEvery { createNote(schoolId, "hola") } returns newNote
        coEvery { getNotes(schoolId) } returnsMany listOf(emptyList(), listOf(newNote))

        val vm = newVm()
        advanceUntilIdle()

        vm.publishNote("hola")
        advanceUntilIdle()

        coVerify { createNote(schoolId, "hola") }
        val notes = (vm.uiState.value as SchoolDetailUiState.Success).notes
        assertEquals(1, notes.size)
        assertEquals("hola", notes.first().text)
    }

    @Test fun `submitContribution PARKING llama a submitContribution con request correcto`() = runTest {
        val req = ContributionRequest(
            type = "PARKING", name = "Parking del río", lat = 40.0, lon = -3.0,
            notes = null, description = null, proposedLat = null, proposedLon = null,
            correctionReason = null, targetBlockId = null,
            photoUrl = null, bloquesJson = null, topoLinesJson = null
        )
        coEvery { submitContribution(schoolId, req) } returns dummyContribution()

        val vm = newVm()
        advanceUntilIdle()

        val result = vm.submitContribution(req)
        assertTrue(result.isSuccess)
        coVerify { submitContribution(schoolId, req) }
    }

    @Test fun `submitBoulderContribution sin foto no llama a storage`() = runTest {
        val captured = slot<ContributionRequest>()
        coEvery { submitContribution(eq(schoolId), capture(captured)) } returns dummyContribution()

        val vm = newVm()
        advanceUntilIdle()

        val bloques = listOf(BoulderBloqueForm(name = "Directa", grade = "6c", startType = "PIE"))
        val result = vm.submitBoulderContribution(
            lat = 40.0, lon = -3.0, name = "La Piedra", bloques = bloques, photoUri = null
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { photoUploader.uploadBoulderPhoto(any(), any(), any()) }
        assertEquals("BOULDER", captured.captured.type)
        assertEquals("La Piedra", captured.captured.name)
        assertNull(captured.captured.photoUrl)
        assertNotNull(captured.captured.bloquesJson)
    }

    @Test fun `submitAddLinesContribution incluye targetBlockId y omite foto`() = runTest {
        val captured = slot<ContributionRequest>()
        coEvery { submitContribution(eq(schoolId), capture(captured)) } returns dummyContribution()

        val vm = newVm()
        advanceUntilIdle()

        val bloques = listOf(BoulderBloqueForm(name = "Variante", grade = "7a", startType = "SIT"))
        val result = vm.submitAddLinesContribution(
            targetBlockId = "b1", targetLat = 40.0, targetLon = -3.0, bloques = bloques
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { photoUploader.uploadBoulderPhoto(any(), any(), any()) }
        assertEquals("b1", captured.captured.targetBlockId)
        assertNull(captured.captured.photoUrl)
        assertEquals("BOULDER", captured.captured.type)
    }

    @Test fun `addBlock crea bloque y refresca blocks en el estado`() = runTest {
        val req = CreateBlockRequest(
            type = "PARKING", name = "Parking", lat = 40.0, lon = -3.0,
            photoPath = null, description = null, lines = emptyList()
        )
        val createdBlock = Block(
            id = "b1", schoolId = schoolId, type = "PARKING", name = "Parking",
            lat = 40.0, lon = -3.0, photoPath = null, description = null,
            createdByUid = "u1", createdAt = "2026-06-06", lines = emptyList()
        )
        coEvery { createBlock(schoolId, req) } returns createdBlock
        coEvery { getBlocks(schoolId) } returnsMany listOf(emptyList(), listOf(createdBlock))

        val vm = newVm()
        advanceUntilIdle()

        vm.addBlock(req)
        advanceUntilIdle()

        coVerify { createBlock(schoolId, req) }
        val blocks = (vm.uiState.value as SchoolDetailUiState.Success).blocks
        assertEquals(1, blocks.size)
        assertEquals("b1", blocks.first().id)
    }

    @Test fun `deleteBlock borra y refresca lista llamando onDone(true)`() = runTest {
        coEvery { deleteBlockUC("b1") } just Runs
        coEvery { getBlocks(schoolId) } returnsMany listOf(
            listOf(Block("b1", schoolId, "PARKING", "P", 0.0, 0.0, null, null, "u", "t", emptyList())),
            emptyList()
        )

        val vm = newVm()
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as SchoolDetailUiState.Success).blocks.size)

        var doneOk: Boolean? = null
        vm.deleteBlock("b1") { doneOk = it }
        advanceUntilIdle()

        assertEquals(true, doneOk)
        assertTrue((vm.uiState.value as SchoolDetailUiState.Success).blocks.isEmpty())
    }

    private fun dummyContribution() = Contribution(
        id = "c1", type = "BOULDER", status = "PENDING", schoolId = schoolId,
        schoolName = "Pedriza", name = null, lat = 0.0, lon = 0.0, notes = null,
        description = null, submittedByName = null, reviewReason = null,
        createdAt = null, reviewedAt = null, photoUrl = null, bloquesJson = null,
        topoLinesJson = null, targetBlockId = null
    )
}
