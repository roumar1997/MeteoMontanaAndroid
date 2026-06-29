package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.model.MeetupMember

fun MeetupDto.toDomain(): Meetup = Meetup(
    id = id,
    schoolId = schoolId,
    schoolName = schoolName,
    schoolLat = schoolLat,
    schoolLon = schoolLon,
    name = name,
    description = description,
    discipline = discipline,
    privacy = privacy,
    memberLimit = memberLimit,
    memberCount = memberCount,
    photoUrl = photoUrl,
    creatorUid = creatorUid,
    creatorUsername = creatorUsername,
    creatorPhotoUrl = creatorPhotoUrl,
    conversationId = conversationId,
    days = days,
    lastDay = lastDay,
    expiresAt = isoToEpochMillis(expiresAt),
    createdAt = isoToEpochMillis(createdAt),
    members = members.map { it.toDomain() },
    joined = joined
)

fun MeetupMemberDto.toDomain() = MeetupMember(
    uid = uid,
    username = username,
    displayName = displayName,
    photoUrl = photoUrl,
    gearJson = gearJson
)

private fun isoToEpochMillis(iso: String): Long {
    // ISO-8601 básico: "2026-07-06T00:00:00" — parseamos manualmente para commonMain
    return try {
        val parts = iso.split("T")
        val dateParts = parts[0].split("-")
        val year = dateParts[0].toInt()
        val month = dateParts[1].toInt()
        val day = dateParts[2].toInt()
        // Aproximación en millis (suficiente para comparar caducidad offline)
        val daysSinceEpoch = daysFromEpoch(year, month, day)
        daysSinceEpoch * 86_400_000L
    } catch (e: Exception) {
        0L
    }
}

private fun daysFromEpoch(year: Int, month: Int, day: Int): Long {
    // Algoritmo de días desde 1970-01-01 (sin librería de tiempo)
    var y = year.toLong()
    var m = month.toLong()
    if (m <= 2) { y--; m += 12 }
    val a = y / 100
    val b = 2 - a + a / 4
    return (365.25 * (y + 4716)).toLong() + (30.6001 * (m + 1)).toLong() + day + b - 2451163L - 1L
}
