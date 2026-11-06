package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.FeatureDisabledBody
import com.meteomontana.android.domain.model.FeatureDisabledException
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode

/** Código que manda el servidor en el cuerpo del 503 de una función apagada. */
internal const val FEATURE_DISABLED_CODE = "FEATURE_DISABLED"

/**
 * Convierte el 503 `{"code":"FEATURE_DISABLED","feature":...,"message":...}` en [FeatureDisabledException]
 * para que las pantallas enseñen el aviso de "función en pausa" en vez de un error genérico.
 * Cualquier otro 503 (o un cuerpo ilegible) sigue siendo el error de siempre.
 */
internal fun HttpClientConfig<*>.installFeatureDisabledMapping() {
    install(HttpCallValidator) {
        handleResponseExceptionWithRequest { cause, _ ->
            if (cause is ServerResponseException &&
                cause.response.status == HttpStatusCode.ServiceUnavailable
            ) {
                val body = runCatching { cause.response.body<FeatureDisabledBody>() }.getOrNull()
                val feature = body?.feature
                if (body?.code == FEATURE_DISABLED_CODE && feature != null) {
                    throw FeatureDisabledException(feature, body.message)
                }
            }
        }
    }
}
