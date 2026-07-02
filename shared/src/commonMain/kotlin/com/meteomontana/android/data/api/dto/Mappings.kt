package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminPushResult
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.BestDay
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockFace
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.Current
import com.meteomontana.android.domain.model.DayCell
import com.meteomontana.android.domain.model.DayForecast
import com.meteomontana.android.domain.model.FavoriteRow
import com.meteomontana.android.domain.model.FavoriteSchool
import com.meteomontana.android.domain.model.FavoritesGrid
import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.HourForecast
import com.meteomontana.android.domain.model.Inbox
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.model.Notification
import com.meteomontana.android.domain.model.OptimalWindow
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.model.RockDrying
import com.meteomontana.android.domain.model.ScoreFactor
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.model.SchoolScore
import com.meteomontana.android.domain.model.SchoolStats
import com.meteomontana.android.domain.model.Submission

// School
fun SchoolDto.toDomain() = School(
    id = id, name = name, location = location, region = region,
    style = style, rockType = rockType, lat = lat, lon = lon, source = source
)

// Forecast
fun ForecastDto.toDomain() = Forecast(
    schoolId = schoolId, schoolName = schoolName, lat = lat, lon = lon,
    current = current.toDomain(), hours = hours.map { it.toDomain() },
    days = days.map { it.toDomain() }, bestDay = bestDay?.toDomain(),
    bestWindow = bestWindow?.toDomain()
)

fun CurrentDto.toDomain() = Current(
    time, temperature, humidity, windSpeed, precipitation,
    precipitationProbability, cloudCover, dewPoint, precip24h, precip72h,
    dryRock, score, scoreLabel, factors.map { it.toDomain() },
    drying?.let { RockDrying(it.wet, it.dryingHours, it.message) }
)

fun HourForecastDto.toDomain() = HourForecast(
    time, temperature, humidity, windSpeed, precipitation,
    precipitationProbability, cloudCover, dewPoint, score, scoreLabel, weatherCode
)

fun DayForecastDto.toDomain() = DayForecast(date, tempMax, tempMin, precipitationTotal, avgScore, scoreLabel)
fun BestDayDto.toDomain() = BestDay(date, score, label, daysFromToday)
fun OptimalWindowDto.toDomain() = OptimalWindow(start, end, avgScore)
fun ScoreFactorDto.toDomain() = ScoreFactor(name, display, passes)

// Reverse mappings: domain → DTO (para serializar snapshots offline a JSON)
fun Forecast.toDto() = ForecastDto(
    schoolId = schoolId, schoolName = schoolName, lat = lat, lon = lon,
    current = current.toDto(), hours = hours.map { it.toDto() },
    days = days.map { it.toDto() }, bestDay = bestDay?.toDto(),
    bestWindow = bestWindow?.toDto()
)
fun Current.toDto() = CurrentDto(
    time, temperature, humidity, windSpeed, precipitation,
    precipitationProbability, cloudCover, dewPoint, precip24h, precip72h,
    dryRock, score, scoreLabel, factors.map { it.toDto() },
    drying?.let { RockDryingDto(it.wet, it.dryingHours, it.message) }
)
fun HourForecast.toDto() = HourForecastDto(
    time, temperature, humidity, windSpeed, precipitation,
    precipitationProbability, cloudCover, dewPoint, score, scoreLabel, weatherCode
)
fun DayForecast.toDto() = DayForecastDto(date, tempMax, tempMin, precipitationTotal, avgScore, scoreLabel)
fun BestDay.toDto() = BestDayDto(date, score, label, daysFromToday)
fun OptimalWindow.toDto() = OptimalWindowDto(start, end, avgScore)
fun ScoreFactor.toDto() = ScoreFactorDto(name, display, passes)

fun SchoolScoreDto.toDomain() = SchoolScore(id, todayScore, hourlyScores, dryRock, rainMm, rainProb)

fun RangeScoreDto.toDomain() = com.meteomontana.android.domain.model.RangeScore(
    id = id, combinedScore = combinedScore, avgScore = avgScore,
    days = days.map { it.toDomain() }, rainDays = rainDays, maxRainMm = maxRainMm
)

fun RangeDayScoreDto.toDomain() = com.meteomontana.android.domain.model.RangeDayScore(
    date = date, score = score, rainMm = rainMm, rainProb = rainProb, rainy = rainy
)

// Block
fun BlockDto.toDomain() = Block(
    id = id, schoolId = schoolId, type = type, name = name, lat = lat, lon = lon,
    photoPath = photoPath, description = description, createdByUid = createdByUid,
    createdAt = createdAt, lines = lines.map { it.toDomain(photoPath) },
    sectorBlockId = sectorBlockId,
    discipline = discipline,
    geometry = geometry, path = path, direction = direction,
    faces = faces.map { it.toDomain(photoPath) }
)

fun BlockLineDto.toDomain(coverPhoto: String? = null) =
    BlockLine(id, name, grade, startType, linePath, sortOrder, photoPath ?: coverPhoto, faceOrder, avgStars, myStars)

fun BlockFaceDto.toDomain(coverPhoto: String? = null): BlockFace {
    val facePhoto = photoPath ?: coverPhoto
    return BlockFace(facePhoto, sortOrder, lines.map { it.toDomain(facePhoto) })
}

// Note
fun NoteDto.toDomain() = Note(id, schoolId, text, author, uid, createdAt, upvotesCount, downvotesCount, photoUrl)

// Favorites
fun FavoriteSchoolDto.toDomain() = FavoriteSchool(id, name, region, rockType, isFavorite)
fun FavoritesGridDto.toDomain() = FavoritesGrid(rows.map { it.toDomain() }, days)
fun FavoriteRowDto.toDomain() = FavoriteRow(schoolId, schoolName, cells.map { it.toDomain() })
fun DayCellDto.toDomain() = DayCell(date, avgScore, label)

// Profile & Social
fun PrivateProfileDto.toDomain() = PrivateProfile(uid, email, username, displayName, photoUrl, bio, topGrade, isPublic, isAdmin, isPremium, gender, gearJson)
fun PublicProfileDto.toDomain() = PublicProfile(uid, username, displayName, photoUrl, bio, topGrade, locked, isPublic)
fun FollowStatusDto.toDomain() = FollowStatus(followers, following, iFollowThem, theyFollowMe, requestPending)
fun NotificationDto.toDomain() = Notification(id, type, title, body, targetType, targetId, readAt, createdAt)
fun InboxDto.toDomain() = Inbox(unreadCount, items.map { it.toDomain() })

// Admin
fun AdminStatsDto.toDomain() = AdminStats(totalUsers, totalAdmins, totalSchools, totalNotes, submissionsPending, submissionsApproved, submissionsRejected)
fun AdminLogDto.toDomain() = AdminLog(id, actorUid, action, targetType, targetId, details, createdAt)
fun AdminPushResponse.toDomain() = AdminPushResult(sent, recipients)
fun SubmissionDto.toDomain() = Submission(id, proposedName, proposedRegion, proposedStyle, proposedRockType, proposedLat, proposedLon, proposedLocation, proposedSource, notes, status, submittedByUid, reviewedByUid, reviewReason, createdSchoolId, createdAt, reviewedAt)
fun ContributionDto.toDomain() = Contribution(id, type, status, schoolId, schoolName, name, lat, lon, notes, description, submittedByName, reviewReason, createdAt, reviewedAt, photoUrl, bloquesJson, topoLinesJson, targetBlockId, targetLineId, sectorBlockId, proposedLat, proposedLon, correctionReason, geometry, path, direction)

// Journal
fun JournalSessionDto.toDomain() = JournalSession(id, schoolId, schoolName, sector, blockName, grade, notes, date, createdAt, discipline, lineId, status)
fun JournalStatsDto.toDomain() = JournalStats(blockCount, boulderCount, routeCount, schoolCount, maxGrade, maxBoulderGrade, maxRouteGrade, bySchool.map { it.toDomain() })
fun SchoolStatsDto.toDomain() = SchoolStats(schoolName, blockCount, maxGrade)
