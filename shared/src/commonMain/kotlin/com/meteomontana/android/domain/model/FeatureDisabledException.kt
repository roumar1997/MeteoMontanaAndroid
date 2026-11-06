package com.meteomontana.android.domain.model

/**
 * El servidor ha rechazado una acción (503 FEATURE_DISABLED) porque esa función está en pausa.
 * [message] es el aviso del servidor, ya traducido; puede ser null.
 */
class FeatureDisabledException(val key: String, override val message: String?) : Exception(message)
