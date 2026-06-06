package com.meteomontana.android.admin

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.AdminPushResponse
import com.meteomontana.android.data.api.dto.AdminStatsDto
import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.ContributionDto
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.api.dto.SchoolDto
import com.meteomontana.android.domain.usecase.admin.ApproveContributionUseCase
import com.meteomontana.android.domain.usecase.admin.ApproveSubmissionUseCase
import com.meteomontana.android.domain.usecase.admin.GetAdminLogsUseCase
import com.meteomontana.android.domain.usecase.admin.GetAdminStatsUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingContributionsUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingSubmissionsUseCase
import com.meteomontana.android.domain.usecase.admin.RejectContributionUseCase
import com.meteomontana.android.domain.usecase.admin.RejectSubmissionUseCase
import com.meteomontana.android.domain.usecase.admin.SendPushUseCase
import com.meteomontana.android.domain.usecase.blocks.DeleteBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.blocks.UpdateBlockUseCase
import com.meteomontana.android.ui.screens.admin.AdminViewModel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getStats: GetAdminStatsUseCase
    private lateinit var getPendingSubmissions: GetPendingSubmissionsUseCase
    private lateinit var getPendingContributions: GetPendingContributionsUseCase
    private lateinit var getLogs: GetAdminLogsUseCase
    private lateinit var approveSubmission: ApproveSubmissionUseCase
    private lateinit var rejectSubmission: RejectSubmissionUseCase
    private lateinit var approveContribution: ApproveContributionUseCase
    private lateinit var rejectContribution: RejectContributionUseCase
    private lateinit var sendPush: SendPushUseCase
    private lateinit var getBlocks: GetBlocksUseCase
    private lateinit var updateBlockUC: UpdateBlockUseCase
    private lateinit var deleteBlockUC: DeleteBlockUseCase
    private lateinit var schoolApi: SchoolApi

    private val stats = AdminStatsDto(
        totalUsers = 10, totalAdmins = 1, totalSchools = 191, totalNotes = 0,
        submissionsPending = 0, submissionsApproved = 0, submissionsRejected = 0
    )

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getStats = mockk()
        getPendingSubmissions = mockk()
        getPendingContributions = mockk()
        getLogs = mockk()
        approveSubmission = mockk()
        rejectSubmission = mockk()
        approveContribution = mockk()
        rejectContribution = mockk()
        sendPush = mockk()
        getBlocks = mockk()
        updateBlockUC = mockk()
        deleteBlockUC = mockk()
        schoolApi = mockk()

        coEvery { getStats() } returns stats
        coEvery { getPendingSubmissions() } returns emptyList()
        coEvery { getPendingContributions() } returns emptyList()
        coEvery { getLogs() } returns emptyList()
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newVm() = AdminViewModel(
        getStats, getPendingSubmissions, getPendingContributions, getLogs,
        approveSubmission, rejectSubmission, approveContribution, rejectContribution,
        sendPush, getBlocks, updateBlockUC, deleteBlockUC, schoolApi
    )

    @Test fun `load llena stats y baja loading a false`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(false, s.loading)
        assertNull(s.error)
        assertEquals(stats, s.stats)
    }

    @Test fun `load con stats fallando marca error`() = runTest {
        coEvery { getStats() } throws RuntimeException("no auth")
        val vm = newVm()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(false, s.loading)
        assertEquals("no auth", s.error)
    }

    @Test fun `fetchSchoolBlocks cachea por schoolId`() = runTest {
        val blocks = listOf(
            BlockDto("b1", "s1", "PARKING", "P", 0.0, 0.0, null, null, "u", "t", emptyList())
        )
        coEvery { getBlocks("s1") } returns blocks

        val vm = newVm()
        advanceUntilIdle()

        vm.fetchSchoolBlocks("s1")
        advanceUntilIdle()
        vm.fetchSchoolBlocks("s1")
        advanceUntilIdle()

        coVerify(exactly = 1) { getBlocks("s1") }
        assertEquals(blocks, vm.state.value.schoolBlocks["s1"])
    }

    @Test fun `deleteBlock refresca el cache de bloques de esa escuela`() = runTest {
        val initial = listOf(
            BlockDto("b1", "s1", "PARKING", "P", 0.0, 0.0, null, null, "u", "t", emptyList())
        )
        coEvery { deleteBlockUC("b1") } just Runs
        coEvery { getBlocks("s1") } returnsMany listOf(initial, emptyList())

        val vm = newVm()
        advanceUntilIdle()

        vm.fetchSchoolBlocks("s1")
        advanceUntilIdle()
        assertEquals(1, vm.state.value.schoolBlocks["s1"]?.size)

        vm.deleteBlock("b1", "s1")
        advanceUntilIdle()

        coVerify { deleteBlockUC("b1") }
        assertEquals(0, vm.state.value.schoolBlocks["s1"]?.size)
    }

    @Test fun `updateBlock llama PUT y onDone con true`() = runTest {
        val req = CreateBlockRequest(
            type = "PARKING", name = "P2", lat = 1.0, lon = 2.0,
            photoPath = null, description = null, lines = emptyList()
        )
        val updated = BlockDto("b1", "s1", "PARKING", "P2", 1.0, 2.0, null, null, "u", "t", emptyList())
        coEvery { updateBlockUC("b1", req) } returns updated
        coEvery { getBlocks("s1") } returns listOf(updated)

        val vm = newVm()
        advanceUntilIdle()

        var ok: Boolean? = null
        vm.updateBlock("b1", "s1", req) { ok = it }
        advanceUntilIdle()

        assertEquals(true, ok)
        coVerify { updateBlockUC("b1", req) }
        assertEquals("P2", vm.state.value.schoolBlocks["s1"]?.firstOrNull()?.name)
    }

    @Test fun `loadAllSchools carga solo una vez`() = runTest {
        val list = listOf(
            SchoolDto(
                id = "s1", name = "Pedriza", location = null, region = null,
                style = null, rockType = null, lat = 0.0, lon = 0.0, source = null
            )
        )
        coEvery { schoolApi.getSchools() } returns list

        val vm = newVm()
        advanceUntilIdle()

        vm.loadAllSchools()
        advanceUntilIdle()
        vm.loadAllSchools()
        advanceUntilIdle()

        coVerify(exactly = 1) { schoolApi.getSchools() }
        assertEquals(1, vm.state.value.allSchools.size)
        assertEquals(false, vm.state.value.schoolsLoading)
    }

    @Test fun `approve llama al use case y recarga`() = runTest {
        coEvery { approveSubmission("sub1") } returns mockk(relaxed = true)
        val vm = newVm()
        advanceUntilIdle()

        vm.approve("sub1")
        advanceUntilIdle()

        coVerify { approveSubmission("sub1") }
        coVerify(atLeast = 2) { getStats() }
    }

    @Test fun `approveContribution llama y recarga`() = runTest {
        coEvery { approveContribution("c1") } returns mockk<ContributionDto>(relaxed = true)
        val vm = newVm()
        advanceUntilIdle()

        vm.approveContribution("c1")
        advanceUntilIdle()

        coVerify { approveContribution("c1") }
        coVerify(atLeast = 2) { getStats() }
    }

    @Test fun `sendPush actualiza pushResult con sent y recipients`() = runTest {
        coEvery { sendPush(any(), any(), any()) } returns AdminPushResponse(sent = 3, recipients = 5)
        val vm = newVm()
        advanceUntilIdle()

        vm.sendPush(targetUid = null, title = "t", body = "b")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(false, s.pushBusy)
        assertEquals("Enviado a 3/5", s.pushResult)
    }
}
