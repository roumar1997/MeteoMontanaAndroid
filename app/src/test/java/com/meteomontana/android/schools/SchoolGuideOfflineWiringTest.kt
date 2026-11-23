package com.meteomontana.android.schools

import com.meteomontana.android.di.CatalogUseCasesModule
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.model.LineExploreCriteria
import com.meteomontana.android.domain.repository.BlockRepository
import com.meteomontana.android.domain.usecase.schools.ExploreLinesUseCase
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guía de una escuela descargada SIN conexión: el use case que provee Hilt (el que usa `SchoolBlocksViewModel`)
 * tiene que llevar el `BlockRepository` para tirar de las piedras guardadas. Antes el VM lo creaba sin él y salía
 * «No se han podido cargar los bloques».
 */
class SchoolGuideOfflineWiringTest {

    private val explore: ExploreLinesUseCase = mockk()
    private val blocks: BlockRepository = mockk()

    private fun line(id: String, grade: String, order: Int) = BlockLine(
        id = id, name = "Vía $id", grade = grade, startType = "SIT", linePath = "[[0,0],[1,1]]", sortOrder = order,
        photoPath = "foto.jpg"
    )

    private fun block(id: String, type: String, name: String, lines: List<BlockLine> = emptyList(), sector: String? = null) =
        Block(
            id = id, schoolId = "pedriza", type = type, name = name, lat = 40.7, lon = -3.9, photoPath = null,
            description = null, createdByUid = "u", createdAt = "", lines = lines, sectorBlockId = sector
        )

    @Test fun `sin red y con piedras guardadas el use case de Hilt devuelve las lineas`() = runTest {
        coEvery { explore(any<LineExploreCriteria>()) } throws IOException("sin conexion")
        coEvery { blocks.getBlocks("pedriza") } returns listOf(
            block("z1", "ZONE", "Sector Norte"),
            block("b1", "BLOCK", "Patatonio", listOf(line("l1", "6a", 0), line("l2", "7a", 1)), sector = "z1")
        )

        val hits = CatalogUseCasesModule.provideLoadSchoolLinesUseCase(explore, blocks)("pedriza")

        assertEquals(listOf("6a", "7a"), hits.map { it.grade })
        assertTrue(hits.all { it.sectorName == "Sector Norte" && it.blockName == "Patatonio" })
    }

    @Test fun `sin red y sin piedras guardadas sigue fallando`() = runTest {
        coEvery { explore(any<LineExploreCriteria>()) } throws IOException("sin conexion")
        coEvery { blocks.getBlocks("pedriza") } returns emptyList()

        try {
            CatalogUseCasesModule.provideLoadSchoolLinesUseCase(explore, blocks)("pedriza")
            fail("debia lanzar la excepcion de red")
        } catch (e: IOException) {
            assertEquals("sin conexion", e.message)
        }
    }
}
