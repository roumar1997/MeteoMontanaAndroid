package com.meteomontana.android.domain.model

/** Denuncia de contenido en la cola de revisión del admin. */
data class ContentReport(
    val id: String,
    val targetType: String,          // COMMENT / NOTE / USER / FEED_POST / FEED_COMMENT
    val targetId: String,
    val reason: String,              // SPAM / OFFENSIVE / FALSE_INFO / OTHER
    val snapshot: String?,
    val authorUid: String?,
    val reporterUid: String,
    val status: String,
    val resolution: String?,
    val createdAt: String?
)

/** Fila de usuario para el panel de admin. */
data class AdminUserRow(
    val uid: String,
    val username: String?,
    val displayName: String?,
    val isAdmin: Boolean,
    val createdAt: String?
)

/** Fila de nota para el panel de admin. */
data class AdminNoteRow(
    val id: String,
    val schoolId: String?,
    val author: String?,
    val uid: String,
    val text: String,
    val createdAt: String?
)

/** Sugerencia/fallo mandado desde el botón "?" de ayuda (panel admin). */
data class AdminSuggestionRow(
    val id: String,
    val uid: String,
    val email: String?,
    val displayName: String?,
    val message: String,
    val platform: String,
    val appVersion: String?,
    val createdAt: String?,
    val resolved: Boolean,
    val adminReply: String?,
    val repliedAt: String?
)

/** Resumen de moderación de un usuario (consola de admin). */
data class UserModeration(
    val uid: String,
    val username: String?,
    val displayName: String?,
    val banned: Boolean,
    val suspendedUntil: String?,
    val warnings: Int,
    val reportCount: Long,
    val reports: List<ModReportRow>,
    val actions: List<ModActionRow>
)

data class ModReportRow(
    val type: String,
    val reason: String,
    val snapshot: String?,
    val createdAt: String?
)

data class ModActionRow(
    val action: String,           // WARN/SUSPEND/BAN/UNBAN/DELETE_NOTE/DELETE_COMMENT
    val reason: String?,
    val snapshot: String?,
    val createdAt: String?
)
