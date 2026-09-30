package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockFace
import com.meteomontana.android.domain.model.BlockLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Qué fotos se guardan para ver una escuela sin cobertura.
 *
 * Lo que blinda de verdad: que entren las fotos de las CARAS, no solo la portada
 * de la piedra. Las dos apps se dejaban las caras, así que una piedra de tres
 * fotos se quedaba en una — y eso solo se descubre ya en la roca, sin remedio.
 */
class FotosDeEscuelaTest {

    private fun via(id: String, foto: String?) = BlockLine(
        id = id, name = "v$id", grade = null, startType = null,
        linePath = null, sortOrder = 0, photoPath = foto, faceOrder = 0
    )

    private fun piedra(
        id: String, portada: String?, vias: List<BlockLine> = emptyList(),
        faces: List<BlockFace> = emptyList()
    ) = Block(
        id = id, schoolId = "e", type = "BLOCK", name = id, lat = 0.0, lon = 0.0,
        photoPath = portada, description = null, createdByUid = "u", createdAt = "",
        lines = vias, faces = faces
    )

    @Test
    fun `incluye la portada de la piedra Y las fotos de sus caras`() {
        val bloques = listOf(
            piedra("1", "cara-a.jpg", listOf(via("l1", "cara-a.jpg"), via("l2", "cara-b.jpg")))
        )

        val urls = FotosDeEscuela.urlsParaGuardar(bloques)

        assertTrue(urls.contains("cara-b.jpg"), "falta la cara B — el bug que se arregla")
        assertTrue(urls.contains("cara-a.jpg"))
    }

    @Test
    fun `incluye las fotos de faces REALES mandadas por el backend, no solo las derivadas`() {
        // Caso real online: el backend ya manda `faces` pobladas con su PROPIO
        // photoPath (que la pantalla pinta vía facesOrDerived()) — distinto del
        // photoPath/lines.photoPath de "portada" cuando eso pasa. Álvaro,
        // 2026-09-30: "las imágenes salen en negro" en modo avión pese a haber
        // aceptado la descarga — se guardaba la portada, que nadie pedía en
        // pantalla, y no la cara real, que sí.
        val bloques = listOf(
            piedra(
                "1", portada = "portada-que-nadie-pide.jpg",
                faces = listOf(
                    BlockFace(photoPath = "cara-real-1.jpg", sortOrder = 0, lines = emptyList()),
                    BlockFace(photoPath = "cara-real-2.jpg", sortOrder = 1, lines = emptyList())
                )
            )
        )

        val urls = FotosDeEscuela.urlsParaGuardar(bloques)

        assertTrue(urls.contains("cara-real-1.jpg"), "falta la cara real 1 — el bug que se arregla")
        assertTrue(urls.contains("cara-real-2.jpg"), "falta la cara real 2 — el bug que se arregla")
    }

    @Test
    fun `no repite la foto cuando la via hereda la de la piedra`() {
        // Con una sola cara, la vía lleva la MISMA foto que la piedra: si no se
        // quitan los duplicados se descargaría dos veces.
        val bloques = listOf(piedra("1", "unica.jpg", listOf(via("l1", "unica.jpg"))))

        assertEquals(listOf("unica.jpg"), FotosDeEscuela.urlsParaGuardar(bloques))
    }

    @Test
    fun `ignora piedras sin foto y cadenas vacias`() {
        val bloques = listOf(
            piedra("1", null),                       // parking o sector: sin foto
            piedra("2", "", listOf(via("l", ""))),   // vacías, no son URLs
            piedra("3", "buena.jpg")
        )

        assertEquals(listOf("buena.jpg"), FotosDeEscuela.urlsParaGuardar(bloques))
    }

    @Test
    fun `una escuela sin piedras no pide nada`() {
        assertEquals(emptyList(), FotosDeEscuela.urlsParaGuardar(emptyList()))
        assertEquals(0L, FotosDeEscuela.pesoEstimadoBytes(emptyList()))
    }

    @Test
    fun `el peso estimado sale del tamano medio real medido en produccion`() {
        // 25 fotos ~ 7 MB: es Zarzalejo, la escuela más pesada (medida 2026-08-16).
        val mb = FotosDeEscuela.pesoEstimadoBytes(List(25) { "f$it.jpg" }) / (1024.0 * 1024.0)

        assertTrue(mb > 6.0 && mb < 8.0, "esperado ~7 MB, salió $mb")
    }
}
