package com.meteomontana.android.util

import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Convierte cualquier excepción de red/HTTP en un mensaje legible.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is SocketTimeoutException -> "El servidor no responde. ¿Está el back arrancado?"
    is ConnectException       -> "No se puede conectar al servidor."
    is UnknownHostException   -> "Sin conexión a internet."
    is HttpException           -> when (code()) {
        401 -> "Sesión expirada. Vuelve a iniciar sesión."
        403 -> "Sin permiso para esta acción."
        404 -> "Recurso no encontrado."
        500 -> "Error interno del servidor."
        else -> "Error HTTP ${code()}"
    }
    else -> message ?: "Error desconocido"
}
