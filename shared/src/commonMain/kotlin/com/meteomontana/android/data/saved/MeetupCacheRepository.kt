package com.meteomontana.android.data.saved

import com.meteomontana.android.data.api.dto.MeetupDto
import com.meteomontana.android.data.api.dto.MeetupMemberDto
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.db.MeteoMontanaDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Caché local de quedadas para offline (SQLDelight).
 * Serialización manual de days y members como JSON simple (sin dependencia de kotlinx-serialization
 * que no siempre está disponible en commonMain de forma trivial).
 */
class MeetupCacheRepository(private val db: MeteoMontanaDb) {

    private val q get() = db.schemaQueries

    suspend fun saveAll(meetups: List<MeetupDto>) = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        meetups.forEach { m ->
            q.upsertMeetup(
                id = m.id,
                schoolId = m.schoolId,
                schoolName = m.schoolName,
                name = m.name,
                discipline = m.discipline,
                privacy = m.privacy,
                memberLimit = m.memberLimit?.toLong(),
                memberCount = m.memberCount.toLong(),
                photoUrl = m.photoUrl,
                creatorUid = m.creatorUid,
                creatorUsername = m.creatorUsername,
                creatorPhotoUrl = m.creatorPhotoUrl,
                conversationId = m.conversationId,
                daysJson = daysToJson(m.days),
                lastDay = m.lastDay,
                expiresAt = isoToEpochMillis(m.expiresAt),
                createdAt = isoToEpochMillis(m.createdAt),
                membersJson = membersToJson(m.members),
                joined = if (m.joined) 1L else 0L,
                fetchedAt = now
            )
        }
        // Limpiar caducadas
        q.deleteExpiredMeetups(now)
    }

    suspend fun getAll(): List<Meetup> = withContext(Dispatchers.Default) {
        q.allMeetups().executeAsList().map { row ->
            MeetupDto(
                id = row.id,
                schoolId = row.schoolId,
                schoolName = row.schoolName,
                name = row.name,
                discipline = row.discipline,
                privacy = row.privacy,
                memberLimit = row.memberLimit?.toInt(),
                memberCount = row.memberCount.toInt(),
                photoUrl = row.photoUrl,
                creatorUid = row.creatorUid,
                creatorUsername = row.creatorUsername,
                creatorPhotoUrl = row.creatorPhotoUrl,
                conversationId = row.conversationId,
                days = jsonToDays(row.daysJson),
                lastDay = row.lastDay,
                expiresAt = epochMillisToIso(row.expiresAt),
                createdAt = epochMillisToIso(row.createdAt),
                members = jsonToMembers(row.membersJson),
                joined = row.joined != 0L
            ).toDomain()
        }
    }

    suspend fun getById(id: String): Meetup? = withContext(Dispatchers.Default) {
        q.findMeetup(id).executeAsOneOrNull()?.let { row ->
            MeetupDto(
                id = row.id, schoolId = row.schoolId, schoolName = row.schoolName,
                name = row.name, discipline = row.discipline, privacy = row.privacy,
                memberLimit = row.memberLimit?.toInt(), memberCount = row.memberCount.toInt(),
                photoUrl = row.photoUrl, creatorUid = row.creatorUid,
                creatorUsername = row.creatorUsername, creatorPhotoUrl = row.creatorPhotoUrl,
                conversationId = row.conversationId, days = jsonToDays(row.daysJson),
                lastDay = row.lastDay, expiresAt = epochMillisToIso(row.expiresAt),
                createdAt = epochMillisToIso(row.createdAt),
                members = jsonToMembers(row.membersJson), joined = row.joined != 0L
            ).toDomain()
        }
    }

    suspend fun updateJoined(id: String, joined: Boolean, memberCount: Int) =
        withContext(Dispatchers.Default) {
            val current = q.findMeetup(id).executeAsOneOrNull() ?: return@withContext
            q.upsertMeetup(
                id = current.id, schoolId = current.schoolId, schoolName = current.schoolName,
                name = current.name, discipline = current.discipline, privacy = current.privacy,
                memberLimit = current.memberLimit, memberCount = memberCount.toLong(),
                photoUrl = current.photoUrl, creatorUid = current.creatorUid,
                creatorUsername = current.creatorUsername, creatorPhotoUrl = current.creatorPhotoUrl,
                conversationId = current.conversationId, daysJson = current.daysJson,
                lastDay = current.lastDay, expiresAt = current.expiresAt, createdAt = current.createdAt,
                membersJson = current.membersJson,
                joined = if (joined) 1L else 0L,
                fetchedAt = Clock.System.now().toEpochMilliseconds()
            )
        }

    // ─── JSON serialization helpers ──────────────────────────────────────────

    private fun daysToJson(days: List<String>): String =
        "[${days.joinToString(",") { "\"$it\"" }}]"

    private fun jsonToDays(json: String): List<String> =
        json.trim('[', ']').split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }

    private fun membersToJson(members: List<MeetupMemberDto>): String =
        "[${members.joinToString(",") { m ->
            "{\"uid\":\"${m.uid}\"" +
            (m.username?.let { ",\"username\":\"$it\"" } ?: "") +
            (m.displayName?.let { ",\"displayName\":\"$it\"" } ?: "") +
            (m.photoUrl?.let { ",\"photoUrl\":\"$it\"" } ?: "") +
            "}"
        }}]"

    private fun jsonToMembers(json: String): List<MeetupMemberDto> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            json.trim('[', ']').split("},{").map { obj ->
                val clean = obj.trim('{', '}')
                fun field(key: String): String? = Regex("\"$key\":\"([^\"]*)\"")
                    .find(clean)?.groupValues?.getOrNull(1)
                MeetupMemberDto(
                    uid = field("uid") ?: return@map null,
                    username = field("username"),
                    displayName = field("displayName"),
                    photoUrl = field("photoUrl")
                )
            }.filterNotNull()
        } catch (e: Exception) { emptyList() }
    }

    private fun isoToEpochMillis(iso: String): Long {
        return try {
            val parts = iso.split("T")[0].split("-")
            val y = parts[0].toInt(); val m = parts[1].toInt(); val d = parts[2].toInt()
            var yr = y.toLong(); var mo = m.toLong()
            if (mo <= 2) { yr--; mo += 12 }
            val a = yr / 100; val b = 2 - a + a / 4
            ((365.25 * (yr + 4716)).toLong() + (30.6001 * (mo + 1)).toLong() + d + b - 2451163L - 1L) * 86_400_000L
        } catch (e: Exception) { 0L }
    }

    private fun epochMillisToIso(millis: Long): String {
        val totalDays = millis / 86_400_000L
        var z = totalDays + 2440588L
        val a = (z - 1867216.25).toLong() / 36524
        z += 1 + a - a / 4
        val b = z + 1524
        val c = ((b - 122.1) / 365.25).toLong()
        val d = (365.25 * c).toLong()
        val e = ((b - d) / 30.6001).toLong()
        val day = (b - d - (30.6001 * e).toLong()).toInt()
        val month = if (e < 14) (e - 1).toInt() else (e - 13).toInt()
        val year = if (month > 2) (c - 4716).toInt() else (c - 4715).toInt()
        val yy = year.toString().padStart(4, '0')
        val mm = month.toString().padStart(2, '0')
        val dd = day.toString().padStart(2, '0')
        return "${yy}-${mm}-${dd}T00:00:00"
    }
}
