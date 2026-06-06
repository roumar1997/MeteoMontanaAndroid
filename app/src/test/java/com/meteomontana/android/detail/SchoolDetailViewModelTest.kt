package com.meteomontana.android.detail

import androidx.lifecycle.SavedStateHandle
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.ContributionDto
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.api.dto.CreateNoteRequest
import com.meteomontana.android.data.api.dto.CurrentDto
import com.meteomontana.android.data.api.dto.FavoriteSchoolDto
import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.NoteDto
import com.meteomontana.android.data.api.dto.PrivateProfileDto
import com.meteomontana.android.data.storage.StorageUploadHelper
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.repository.SchoolRepository
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

    private lateinit var repo: SchoolRepository
    private lateinit var api: SchoolApi
    private lateinit var storage: StorageUploadHelper

    private val schoolId = "s1"
    private val school = School(
        id = schoolId, name = "Pedriza", location = "Madrid", region = "Madrid",
        style = "Bloque", rockType = "Granito", lat = 40.768, lon = -3.852, source = null
    )

    private fun savedState() = SavedStateHandle(mapOf("schoolId" to schoolId))

    private val forecast = ForecastDto(
        schoolId = schoolId, schoolName = "Pedriza", lat = 40.768, lon = -3.852,
        current = CurrentDto(
            time = "2026-06-06T10:00", temperature = 20.0, humidity = 50.0,
            windSpeed = 5.0, precipitation = 0.0, precipitationProbability = 0,
            cloudCover = 10, dewPoint = 8.0, precip24h = 0.0, precip72h = 0.0,
            dryRock = true, score = 80, scoreLabel = "Bueno", factors = emptyList()
        ),
        hours = emptyList(), days = emptyList(), bestDay = null, bestWindow = null
    )

    private val profile = PrivateProfileDto(
        uid = "u1", email = null, username = null, displayName = null, photoUrl = null,
        bio = null, topGrade = null, isPublic = true, isAdmin = false, isPremium = false
    )

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk()
        api = mockk()
        storage = mockk()

        coEvery { repo.getSchoolById(schoolId) } returns school
        coEvery { api.getForecast(schoolId) } returns forecast
        coEvery { api.getNotesBySchool(schoolId) } returns emptyList()
        coEvery { api.getMyFavorites() } returns emptyList()
        coEvery { api.getBlocks(schoolId) } returns emptyList()
        coEvery { api.getMyProfile() } returns profile
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `load con todo OK produce Success con forecast y sin error`() = runTest {
        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
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
        coEvery { api.getForecast(schoolId) } throws RuntimeException("forecast caído")
        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
        advanceUntilIdle()

        val s = vm.uiState.value as SchoolDetailUiState.Success
        assertNull(s.forecast)
        assertEquals("forecast caído", s.forecastError)
    }

    @Test fun `load con escuela inexistente produce Error`() = runTest {
        coEvery { repo.getSchoolById(schoolId) } throws RuntimeException("not found")
        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is SchoolDetailUiState.Error)
        assertEquals("not found", (state as SchoolDetailUiState.Error).message)
    }

    @Test fun `load detecta admin desde getMyProfile`() = runTest {
        coEvery { api.getMyProfile() } returns profile.copy(isAdmin = true)
        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
        advanceUntilIdle()

        val s = vm.uiState.value as SchoolDetailUiState.Success
        assertTrue(s.isCurrentUserAdmin)
    }

    @Test fun `isFavorite true cuando la escuela está en favoritos`() = runTest {
        coEvery { api.getMyFavorites() } returns listOf(
            FavoriteSchoolDto(id = schoolId, name = "Pedriza", region = null, rockType = null, isFavorite = true)
        )
        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
        advanceUntilIdle()

        assertTrue((vm.uiState.value as SchoolDetailUiState.Success).isFavorite)
    }

    @Test fun `toggleFavorite añade y refleja en el estado`() = runTest {
        coEvery { api.addFavorite(schoolId) } just Runs
        coEvery { api.removeFavorite(schoolId) } just Runs

        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
        advanceUntilIdle()

        vm.toggleFavorite()
        advanceUntilIdle()

        coVerify { api.addFavorite(schoolId) }
        assertTrue((vm.uiState.value as SchoolDetailUiState.Success).isFavorite)

        vm.toggleFavorite()
        advanceUntilIdle()
        coVerify { api.removeFavorite(schoolId) }
        assertFalse((vm.uiState.value as SchoolDetailUiState.Success).isFavorite)
    }

    @Test fun `publishNote llama a la API y refresca la lista de notas`() = runTest {
        val newNote = NoteDto(
            id = "n1", schoolId = schoolId, text = "hola", author = "Rodrigo",
            uid = "u1", createdAt = "2026-06-06", upvotesCount = 0, downvotesCount = 0
        )
        coEvery { api.createNote(schoolId, CreateNoteRequest("hola")) } returns newNote
        coEvery { api.getNotesBySchool(schoolId) } returnsMany listOf(emptyList(), listOf(newNote))

        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
        advanceUntilIdle()

        vm.publishNote("hola")
        advanceUntilIdle()

        coVerify { api.createNote(schoolId, CreateNoteRequest("hola")) }
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
        coEvery { api.submitContribution(schoolId, req) } returns dummyContribution()

        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
        advanceUntilIdle()

        val result = vm.submitContribution(req)
        assertTrue(result.isSuccess)
        coVerify { api.submitContribution(schoolId, req) }
    }

    @Test fun `submitBoulderContribution sin foto no llama a storage`() = runTest {
        val captured = slot<ContributionRequest>()
        coEvery { api.submitContribution(eq(schoolId), capture(captured)) } returns dummyContribution()

        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
        advanceUntilIdle()

        val bloques = listOf(BoulderBloqueForm(name = "Directa", grade = "6c", startType = "PIE"))
        val result = vm.submitBoulderContribution(
            lat = 40.0, lon = -3.0, name = "La Piedra", bloques = bloques, photoUri = null
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { storage.uploadBoulderPhoto(any(), any()) }
        assertEquals("BOULDER", captured.captured.type)
        assertEquals("La Piedra", captured.captured.name)
        assertNull(captured.captured.photoUrl)
        assertNotNull(captured.captured.bloquesJson)
    }

    @Test fun `submitAddLinesContribution incluye targetBlockId y omite foto`() = runTest {
        val captured = slot<ContributionRequest>()
        coEvery { api.submitContribution(eq(schoolId), capture(captured)) } returns dummyContribution()

        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
        advanceUntilIdle()

        val bloques = listOf(BoulderBloqueForm(name = "Variante", grade = "7a", startType = "SIT"))
        val result = vm.submitAddLinesContribution(
            targetBlockId = "b1", targetLat = 40.0, targetLon = -3.0, bloques = bloques
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { storage.uploadBoulderPhoto(any(), any()) }
        assertEquals("b1", captured.captured.targetBlockId)
        assertNull(captured.captured.photoUrl)
        assertEquals("BOULDER", captured.captured.type)
    }

    @Test fun `addBlock crea bloque y refresca blocks en el estado`() = runTest {
        val req = CreateBlockRequest(
            type = "PARKING", name = "Parking", lat = 40.0, lon = -3.0,
            photoPath = null, description = null, lines = emptyList()
        )
        val createdBlock = BlockDto(
            id = "b1", schoolId = schoolId, type = "PARKING", name = "Parking",
            lat = 40.0, lon = -3.0, photoPath = null, description = null,
            createdByUid = "u1", createdAt = "2026-06-06", lines = emptyList()
        )
        coEvery { api.createBlock(schoolId, req) } returns createdBlock
        coEvery { api.getBlocks(schoolId) } returnsMany listOf(emptyList(), listOf(createdBlock))

        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
        advanceUntilIdle()

        vm.addBlock(req)
        advanceUntilIdle()

        coVerify { api.createBlock(schoolId, req) }
        val blocks = (vm.uiState.value as SchoolDetailUiState.Success).blocks
        assertEquals(1, blocks.size)
        assertEquals("b1", blocks.first().id)
    }

    @Test fun `deleteBlock borra y refresca lista llamando onDone(true)`() = runTest {
        coEvery { api.deleteBlock("b1") } just Runs
        coEvery { api.getBlocks(schoolId) } returnsMany listOf(
            listOf(BlockDto("b1", schoolId, "PARKING", "P", 0.0, 0.0, null, null, "u", "t", emptyList())),
            emptyList()
        )

        val vm = SchoolDetailViewModel(savedState(), repo, api, storage)
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as SchoolDetailUiState.Success).blocks.size)

        var doneOk: Boolean? = null
        vm.deleteBlock("b1") { doneOk = it }
        advanceUntilIdle()

        assertEquals(true, doneOk)
        assertTrue((vm.uiState.value as SchoolDetailUiState.Success).blocks.isEmpty())
    }

    private fun dummyContribution() = ContributionDto(
        id = "c1", type = "BOULDER", status = "PENDING", schoolId = schoolId,
        schoolName = "Pedriza", name = null, lat = 0.0, lon = 0.0, notes = null,
        description = null, submittedByName = null, reviewReason = null,
        createdAt = null, reviewedAt = null, photoUrl = null, bloquesJson = null,
        topoLinesJson = null, targetBlockId = null
    )
}
