package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.PrivateProfileDto
import com.meteomontana.android.data.api.dto.toDomain
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El material del perfil llegaba VACIO a la pantalla de editar perfil aunque
 * el servidor lo tenia guardado — y la bio de la MISMA respuesta si llegaba.
 *
 * Esto mete la respuesta REAL de produccion por el mismo camino que usa la
 * app (deserializar + mapear a dominio) para ver si el material sobrevive.
 */
class PrivateProfileParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** Copiada tal cual de GET /api/me en produccion (2026-08-11). */
    private val respuestaReal = """
        {"uid":"BNBpmGUg0HdWnyNQLV2TGKkgUCP2","email":"roumar1997@gmail.com",
         "username":"alvaro_jara","displayName":"Jara","photoUrl":null,
         "bio":"Solo sé escalar 2","topGrade":"7b+","isPublic":true,
         "isAdmin":true,"isPremium":false,"gender":"MAN",
         "gearJson":"{\"crashpads\":3,\"grigri\":1,\"cuerda\":1,\"cintas\":2}"}
    """.trimIndent()

    @Test
    fun elMaterialSobreviveAlLeerElPerfil() {
        val dto = json.decodeFromString(PrivateProfileDto.serializer(), respuestaReal)
        assertEquals("{\"crashpads\":3,\"grigri\":1,\"cuerda\":1,\"cintas\":2}", dto.gearJson,
            "el DTO pierde el material")

        val perfil = dto.toDomain()
        assertEquals("Solo sé escalar 2", perfil.bio, "el dominio pierde la bio")
        assertEquals("{\"crashpads\":3,\"grigri\":1,\"cuerda\":1,\"cintas\":2}", perfil.gearJson,
            "el dominio pierde el material")
    }
}
