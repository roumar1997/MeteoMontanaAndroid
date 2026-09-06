package com.meteomontana.android.domain.model

/** Una fila del historial de un usuario en el panel de admin. */
data class UserActivityItem(
    val id: String,
    val kind: String,
    val label: String?,
    val status: String,
    val createdAt: String
)
