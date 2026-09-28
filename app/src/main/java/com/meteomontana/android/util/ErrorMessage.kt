package com.meteomontana.android.util

import com.meteomontana.android.util.AppText
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/**
 * Convierte cualquier excepción de red/HTTP en un mensaje legible.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is SocketTimeoutException -> AppText.get(R.string.error_message_v4_el_servidor_no_responde_esta)
    is ConnectException       -> AppText.get(R.string.error_message_v4_no_se_puede_conectar_al)
    is UnknownHostException   -> AppText.get(R.string.error_message_v4_sin_conexion_a_internet)
    is ClientRequestException -> when (response.status.value) {
        401 -> AppText.get(R.string.error_message_v4_sesion_expirada_vuelve_a_iniciar)
        403 -> AppText.get(R.string.error_message_v4_sin_permiso_para_esta_accion)
        404 -> AppText.get(R.string.error_message_v4_recurso_no_encontrado)
        else -> AppText.get(R.string.error_message_v4_error_http, response.status.value)
    }
    is ServerResponseException -> AppText.get(R.string.error_message_v4_error_interno_del_servidor)
    else -> message ?: AppText.get(R.string.error_message_v4_error_desconocido)
}
