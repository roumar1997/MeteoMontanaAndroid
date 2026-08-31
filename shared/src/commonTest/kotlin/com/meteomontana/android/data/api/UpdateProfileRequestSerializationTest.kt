package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * El material del perfil desaparecia al reiniciar la app (iOS y Android):
 * se guardaba, se veia, y al volver a entrar no estaba. La BD de produccion
 * tenia `gear_json` en NULL, o sea que nunca llego.
 *
 * Servidor y capas intermedias estaban limpios, asi que el sospechoso era la
 * serializacion: kotlinx omite por defecto los campos que valen lo mismo que
 * su valor por defecto, y en [UpdateProfileRequest] TODOS son null.
 */
class UpdateProfileRequestSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Test
    fun elMaterialViajaEnLaPeticion() {
        val body = json.encodeToString(
            UpdateProfileRequest.serializer(),
            UpdateProfileRequest(username = "alvaro_jara", gearJson = "{\"crashpads\":1}")
        )
        assertTrue(body.contains("gearJson"), "gearJson no viaja en el JSON: $body")
        assertTrue(body.contains("crashpads"), "el contenido del material no viaja: $body")
    }
}
