package com.meteomontana.android

import com.meteomontana.android.data.api.dto.FeedCommentDto
import com.meteomontana.android.data.api.dto.FeedPostDto
import com.meteomontana.android.data.api.dto.toDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CONTRATO del feed con el backend — la mitad "app" de la alarma.
 *
 * La otra mitad vive en el repo del backend:
 * api/src/test/.../feed/FeedContractTest.java (clava la forma del JSON que
 * emite FeedService). Este test parsea una MUESTRA de ese JSON con los DTOs
 * REALES de shared (FeedDto.kt) y la MISMA config de Json que ApiHttpClient.
 *
 * Por qué existe: el 2026-07-14 el backend cambió author de objeto a String y
 * el parseo kotlinx explotó EN SILENCIO (lista de comentarios vacía, POST que
 * "no hacía nada") — nadie pudo comentar en prod. Con este test, ese cambio se
 * detecta compilando, no en producción.
 *
 * Si cambias FeedDto.kt o el shape del backend: actualiza LA MISMA muestra en
 * los dos tests. Un campo NUEVO nullable es aditivo (los dos verdes sin tocar
 * la muestra vieja); renombrar/borrar/cambiar tipo rompe este test = rompe las
 * apps instaladas → no lo hagas, usa expand-contract.
 */
class FeedContractTest {

    /** Misma config que ApiHttpClient.kt (el parser real de las apps). */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Muestra representativa de lo que emite el backend HOY (verificado por
     *  el FeedContractTest.java del repo API). */
    private val goldenPost = """
        {
          "id": 42, "kind": "TICK", "createdAt": "2026-07-19T10:30:00",
          "author": {"uid": "uid123", "username": "ana_escaladora",
                     "displayName": "Ana", "photoUrl": "https://x/foto.jpg"},
          "schoolId": "school-1", "schoolName": "Zarzalejo",
          "blockId": "block-9", "blockName": "Piedra 15",
          "lineId": "line-7", "lineName": "La ola", "grade": "7a",
          "discipline": "BOULDER", "rockType": "Granito",
          "photoPath": "topos/p15.jpg", "linePath": "[{\"x\":0.1,\"y\":0.9}]",
          "likeCount": 3, "likedByMe": true, "commentCount": 5, "mine": false,
          "startType": "SIT", "caption": "Por fin!",
          "photoUrl": "https://cdn/celebracion.jpg",
          "blockLines": [
            {"name": "La ola", "grade": "7a", "startType": "SIT",
             "linePath": "[{\"x\":0.1,\"y\":0.9}]"}
          ]
        }
    """.trimIndent()

    private val goldenComment = """
        {
          "id": "77", "postId": 42, "uid": "uid456",
          "author": {"uid": "uid456", "username": "karly",
                     "displayName": "Karly", "photoUrl": null},
          "text": "Qué máquina", "createdAt": "2026-07-19T11:00:00",
          "mine": false, "likeCount": 2, "likedByMe": true, "parentId": "55"
        }
    """.trimIndent()

    @Test
    fun `el post del backend se parsea con el DTO real`() {
        val post = json.decodeFromString<FeedPostDto>(goldenPost)

        assertEquals(42L, post.id)
        assertEquals("TICK", post.kind)
        // EL BUG de 2026-07-14: author debe llegar como objeto parseado.
        assertEquals("ana_escaladora", post.author.username)
        assertEquals("Ana", post.author.displayName)
        assertEquals("Zarzalejo", post.schoolName)
        assertEquals("7a", post.grade)
        assertEquals("BOULDER", post.discipline)
        assertEquals(3L, post.likeCount)
        assertTrue(post.likedByMe)
        assertEquals("SIT", post.startType)
        assertEquals("https://cdn/celebracion.jpg", post.photoUrl)
        assertEquals(1, post.blockLines?.size)
        assertEquals("La ola", post.blockLines?.first()?.name)

        // Y el mapping a dominio no pierde nada crítico.
        val domain = post.toDomain()
        assertEquals("La ola", domain.lineName)
        assertEquals("uid123", domain.author.uid)
    }

    @Test
    fun `el comentario del backend se parsea con el DTO real`() {
        val c = json.decodeFromString<FeedCommentDto>(goldenComment)

        assertEquals("77", c.id)
        assertEquals(42L, c.postId)
        // author objeto, no String (bug 2026-07-14).
        assertNotNull(c.author)
        assertEquals("karly", c.author?.username)
        assertEquals("Qué máquina", c.text)
        assertEquals(2L, c.likeCount)
        assertTrue(c.likedByMe)
        assertEquals("55", c.parentId)
    }

    @Test
    fun `un campo desconocido nuevo del backend no rompe el parseo`() {
        // Garantiza que un cambio ADITIVO del backend (campo nuevo) deja a las
        // apps viejas funcionando: así validamos la regla expand-contract.
        val conCampoNuevo = goldenPost.replaceFirst(
            "\"id\": 42,", "\"id\": 42, \"campoFuturo\": {\"x\": 1},")
        val post = json.decodeFromString<FeedPostDto>(conCampoNuevo)
        assertEquals(42L, post.id)
    }

    @Test
    fun `author como String rompe el parseo - el test documenta el modo de fallo`() {
        // Regresión literal del bug de 2026-07-14: si el backend vuelve a
        // mandar author como "@usuario" (String), el parseo DEBE fallar (y
        // este contrato existe para que eso se cace aquí, no en producción).
        val roto = goldenPost.replace(
            Regex("\"author\": \\{[^}]*\\}"), "\"author\": \"@ana\"")
        var exploto = false
        try {
            json.decodeFromString<FeedPostDto>(roto)
        } catch (e: Exception) {
            exploto = true
        }
        assertTrue("author String debe fallar el parseo (contrato roto)", exploto)
    }
}
