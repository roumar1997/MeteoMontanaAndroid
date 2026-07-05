package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/** Denuncia de contenido (para la cola del admin). */
@Serializable
data class ContentReportDto(
    val id: String,
    val targetType: String,          // COMMENT / NOTE / USER
    val targetId: String,
    val reason: String,              // SPAM / OFFENSIVE / FALSE_INFO / OTHER
    val snapshot: String? = null,    // copia del contenido denunciado
    val authorUid: String? = null,
    val reporterUid: String = "",
    val status: String = "PENDING",
    val resolution: String? = null,
    val createdAt: String? = null
)

@Serializable
data class ReportRequest(val targetType: String, val targetId: String, val reason: String)

/** Fila de denuncia en el historial de moderación de un usuario. */
@Serializable
data class ModReportRowDto(
    val type: String,
    val reason: String,
    val snapshot: String? = null,
    val createdAt: String? = null
)

/** Resumen de moderación de un usuario (consola de admin). */
@Serializable
data class UserModerationDto(
    val uid: String,
    val username: String? = null,
    val displayName: String? = null,
    val banned: Boolean = false,
    val suspendedUntil: String? = null,
    val warnings: Int = 0,
    val reportCount: Long = 0,
    val reports: List<ModReportRowDto> = emptyList()
)

/**
 * Moderación UGC (requisito App Store): denunciar comentarios/notas/usuarios,
 * bloquear usuarios, y la cola de revisión del admin.
 */
class KtorModerationApi(private val client: HttpClient) {

    // OJO Kotlin/Native: con expectSuccess=true, un 409 (ya denunciado / ya
    // bloqueado) lanza una excepción NO declarada @Throws → CRASHEA iOS al
    // cruzar a Swift (y en Android caía en un catch mudo). Todas estas
    // acciones son idempotentes, así que tragamos el error: denunciar/bloquear
    // dos veces = ya estaba hecho.
    suspend fun report(targetType: String, targetId: String, reason: String) {
        try {
            client.post("reports") {
                contentType(ContentType.Application.Json)
                setBody(ReportRequest(targetType, targetId, reason))
            }
        } catch (_: Throwable) { /* ya denunciado / sin red: idempotente */ }
    }

    suspend fun blockUser(uid: String) {
        try { client.post("users/$uid/block") } catch (_: Throwable) {}
    }

    suspend fun unblockUser(uid: String) {
        try { client.delete("users/$uid/block") } catch (_: Throwable) {}
    }

    /** Uids que tengo bloqueados (para pintar "Desbloquear" y filtrar en local). */
    suspend fun getBlocked(): Set<String> =
        try { client.get("me/blocked").body() } catch (_: Throwable) { emptySet() }

    // ── Admin ────────────────────────────────────────────────────────────

    suspend fun getContentReports(): List<ContentReportDto> =
        try { client.get("admin/content-reports").body() } catch (_: Throwable) { emptyList() }

    /**
     * action: REMOVE (borra el contenido) / IGNORE. Devuelve null si el backend
     * falla (p.ej. ya resuelta) en vez de crashear iOS; el caller la quita de
     * la lista igual.
     */
    suspend fun resolveContentReport(id: String, action: String): ContentReportDto? =
        try {
            client.post("admin/content-reports/$id/resolve") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("action" to action))
            }.body()
        } catch (_: Throwable) { null }

    /** Listas del panel de admin (STATS pulsables). */
    suspend fun getAdminUsers(): List<AdminUserRowDto> =
        try { client.get("admin/users").body() } catch (_: Throwable) { emptyList() }

    suspend fun getAdminNotes(): List<AdminNoteRowDto> =
        try { client.get("admin/notes").body() } catch (_: Throwable) { emptyList() }

    // ── Consola de moderación de usuarios (admin) ──────────────────────────
    // Todas tragan la excepción (idempotentes, sin crash en iOS) y devuelven el
    // resumen actualizado (o null si falla) para refrescar la ficha.

    suspend fun getUserModeration(uid: String): UserModerationDto? =
        try { client.get("admin/users/$uid/moderation").body() } catch (_: Throwable) { null }

    suspend fun warnUser(uid: String, reason: String?): UserModerationDto? =
        try {
            client.post("admin/users/$uid/warn") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("reason" to (reason ?: "")))
            }.body()
        } catch (_: Throwable) { null }

    suspend fun suspendUser(uid: String, days: Int): UserModerationDto? =
        try {
            client.post("admin/users/$uid/suspend") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("days" to days))
            }.body()
        } catch (_: Throwable) { null }

    suspend fun banUser2(uid: String): UserModerationDto? =
        try { client.post("admin/users/$uid/ban").body() } catch (_: Throwable) { null }

    suspend fun unbanUser2(uid: String): UserModerationDto? =
        try { client.post("admin/users/$uid/unban").body() } catch (_: Throwable) { null }
}

/** Fila de usuario para el panel de admin (STATS pulsables). */
@Serializable
data class AdminUserRowDto(
    val uid: String,
    val username: String? = null,
    val displayName: String? = null,
    val isAdmin: Boolean = false,
    val createdAt: String? = null
)

/** Fila de nota para el panel de admin. */
@Serializable
data class AdminNoteRowDto(
    val id: String,
    val schoolId: String? = null,
    val author: String? = null,
    val uid: String = "",
    val text: String = "",
    val createdAt: String? = null
)
