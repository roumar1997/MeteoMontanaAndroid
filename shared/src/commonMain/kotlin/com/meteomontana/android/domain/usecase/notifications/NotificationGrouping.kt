package com.meteomontana.android.domain.usecase.notifications

import com.meteomontana.android.domain.model.Notification
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Tramo de la bandeja: HOY / ESTA SEMANA / ANTES. */
enum class NotificationSection { TODAY, THIS_WEEK, EARLIER }

/**
 * Una fila de la bandeja. Varios «me gusta» del mismo objeto se funden en una sola
 * («atala y 3 más…»): [lead] es el más reciente, [others] cuántas personas más hay y
 * [members] todos los avisos fundidos (para borrarlos de golpe).
 */
data class NotificationGroup(
    val section: NotificationSection,
    val lead: Notification,
    val others: Int,
    val members: List<Notification>
) {
    val id: String get() = lead.id
    val unread: Boolean get() = members.any { it.readAt == null }
    val memberIds: List<String> get() = members.map { it.id }
}

/** Agrupa y reparte en tramos los avisos (más recientes primero). Pura: igual en iOS y Android. */
object NotificationGrouping {

    private val groupable = setOf("FEED_LIKE", "FEED_COMMENT_LIKE")

    /** Para Swift (sin argumentos por defecto): hora y zona del móvil. */
    fun group(items: List<Notification>): List<NotificationGroup> =
        group(items, Clock.System.now(), TimeZone.currentSystemDefault())

    fun group(items: List<Notification>, now: Instant, zone: TimeZone): List<NotificationGroup> {
        val byKey = LinkedHashMap<String, MutableList<Notification>>()
        items.forEach { n ->
            val key = if (n.type in groupable && n.actorUid != null && n.targetId != null)
                "${n.type}|${n.targetType}|${n.targetId}" else "single|${n.id}"
            byKey.getOrPut(key) { mutableListOf() }.add(n)
        }
        return byKey.values.map { members ->
            val lead = members.maxByOrNull { it.createdAt } ?: members.first()
            val actors = members.mapNotNull { it.actorUid }.distinct().size
            NotificationGroup(
                section = sectionOf(lead.createdAt, now, zone),
                lead = lead,
                others = if (actors > 1) actors - 1 else 0,
                members = members
            )
        }.sortedByDescending { it.lead.createdAt }
    }

    /** «2026-08-10T09:15:00» (hora del servidor = UTC) → tramo según el día natural del móvil. */
    fun sectionOf(createdAt: String, now: Instant, zone: TimeZone): NotificationSection {
        val created = runCatching {
            LocalDateTime.parse(createdAt.take(19)).toInstant(TimeZone.UTC)
        }.getOrNull() ?: return NotificationSection.EARLIER
        val gap = created.toLocalDateTime(zone).date.daysUntil(now.toLocalDateTime(zone).date)
        return when {
            gap <= 0 -> NotificationSection.TODAY
            gap < 7 -> NotificationSection.THIS_WEEK
            else -> NotificationSection.EARLIER
        }
    }

}
