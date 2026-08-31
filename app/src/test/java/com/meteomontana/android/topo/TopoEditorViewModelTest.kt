package com.meteomontana.android.topo

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.usecase.blocks.GetBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.UpdateBlockUseCase
import com.meteomontana.android.ui.screens.topo.TopoEditorViewModel
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TopoEditorViewModel: editor de vías sobre la foto. Reglas que protege:
 *  - añadir/deshacer puntos de la vía seleccionada
 *  - borrar una vía la quita y deselecciona
 *  - AL GUARDAR se descartan las vías COMPLETAMENTE vacías (sin nombre, grado
 *    ni trazo) — si se colaran no se podrían borrar (chip minúsculo). Regresión
 *    real corregida en su día.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TopoEditorViewModelTest {

    private val d = StandardTestDispatcher()
    private lateinit var getBlock: GetBlockUseCase
    private lateinit var updateBlock: UpdateBlockUseCase

    private val block = Block(
        id = "b1", schoolId = "esc", type = "BLOCK", name = "Piedra 1", lat = 40.0, lon = -3.0,
        photoPath = "foto.jpg", description = null, createdByUid = "me", createdAt = "2026-07-19",
        lines = emptyList())

    @Before fun setUp() {
        Dispatchers.setMain(d)
        getBlock = mockk(); updateBlock = mockk()
        coEvery { getBlock("b1") } returns block
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = TopoEditorViewModel(
        SavedStateHandle(mapOf("blockId" to "b1")), getBlock, updateBlock)

    @Test fun `añadir y deshacer puntos de la via seleccionada`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.startNewLine("Vía A", "6a", "SIT")
        vm.addPointToSelected(Offset(0.1f, 0.9f))
        vm.addPointToSelected(Offset(0.2f, 0.8f))
        assertEquals(2, vm.state.value.lines.first().stroke.points.size)
        vm.undoLastPoint()
        assertEquals(1, vm.state.value.lines.first().stroke.points.size)
    }

    @Test fun `borrar via la quita y deselecciona`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.startNewLine("Vía A", "6a", "SIT")
        val id = vm.state.value.selectedLineId!!
        vm.deleteLine(id)
        assertTrue(vm.state.value.lines.isEmpty())
        assertEquals(null, vm.state.value.selectedLineId)
    }

    @Test fun `al guardar se descartan las vias completamente vacias`() = runTest {
        coEvery { updateBlock(any(), any()) } returns block
        val vm = vm(); advanceUntilIdle()
        // Vía con nombre (se guarda) …
        vm.startNewLine("Vía buena", "6a", null)
        vm.addPointToSelected(Offset(0.1f, 0.9f))
        // … y una vía COMPLETAMENTE vacía (sin nombre, grado ni trazo) → se descarta.
        vm.startNewLine("", null, null)

        val reqSlot = slot<com.meteomontana.android.data.api.dto.CreateBlockRequest>()
        vm.save(); advanceUntilIdle()
        io.mockk.coVerify { updateBlock(eq("b1"), capture(reqSlot)) }
        assertEquals("solo la vía buena debe persistir", 1, reqSlot.captured.lines.size)
        assertEquals("Vía buena", reqSlot.captured.lines.first().name)
    }
}
