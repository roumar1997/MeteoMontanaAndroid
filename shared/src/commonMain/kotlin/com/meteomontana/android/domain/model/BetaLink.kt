package com.meteomontana.android.domain.model

/**
 * Enlace de la comunidad a un vídeo de beta (Instagram/YouTube) de una
 * piedra/muro (lineId=null) o de una vía concreta — con categoría opcional de
 * altura, porque la beta puede cambiar mucho entre gente alta y baja (Álvaro,
 * 2026-09-15: "beta personas +1.70 y personas -1.70"). Puede haber varios por
 * piedra/vía. Directo (sin revisión de admin), mismo trato que estrellas o
 * comentarios: se abre fuera de la app, no se embebe nada.
 */
data class BetaLink(
    val id: String,
    val blockId: String,
    val lineId: String? = null,
    val url: String,
    // "TALL" (+1,70) / "SHORT" (-1,70) / null = sin especificar.
    val heightCategory: String? = null,
    val uid: String,
    val createdAt: String? = null
)
