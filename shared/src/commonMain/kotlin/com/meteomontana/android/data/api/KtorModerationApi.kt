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

/**
 * Moderación UGC (requisito App Store): denunciar comentarios/notas/usuarios,
 * bloquear usuarios, y la cola de revisión del admin.
 */
class KtorModerationApi(private val client: HttpClient) {

    suspend fun report(targetType: String, targetId: String, reason: String) {
        client.post("reports") {
            contentType(ContentType.Application.Json)
            setBody(ReportRequest(targetType, targetId, reason))
        }
    }

    suspend fun blockUser(uid: String) { client.post("users/$uid/block") }

    suspend fun unblockUser(uid: String) { client.delete("users/$uid/block") }

    /** Uids que tengo bloqueados (para pintar "Desbloquear" y filtrar en local). */
    suspend fun getBlocked(): Set<String> = client.get("me/blocked").body()

    // ── Admin ────────────────────────────────────────────────────────────

    suspend fun getContentReports(): List<ContentReportDto> =
        client.get("admin/content-reports").body()

    /** action: REMOVE (borra el contenido) / IGNORE. */
    suspend fun resolveContentReport(id: String, action: String): ContentReportDto =
        client.post("admin/content-reports/$id/resolve") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("action" to action))
        }.body()

    /** Listas del panel de admin (STATS pulsables). */
    suspend fun getAdminUsers(): List<AdminUserRowDto> = client.get("admin/users").body()

    suspend fun getAdminNotes(): List<AdminNoteRowDto> = client.get("admin/notes").body()
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
