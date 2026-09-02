package com.meteomontana.android.domain.model

/**
 * "Estoy aquí": alguien presente en una escuela ahora mismo. Vive un tiempo
 * limitado (máx. 10h desde el servidor) — pasado [expiresAt] deja de salir
 * en la lista de presencia activa.
 */
data class SchoolPresence(
    val uid: String,
    val username: String?,
    val displayName: String?,
    val photoUrl: String?,
    val expiresAt: Long   // epoch millis
)
