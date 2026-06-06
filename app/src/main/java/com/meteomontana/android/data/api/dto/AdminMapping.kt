package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminPushResult
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.Submission

fun AdminStatsDto.toDomain() = AdminStats(
    totalUsers, totalAdmins, totalSchools, totalNotes,
    submissionsPending, submissionsApproved, submissionsRejected
)

fun AdminLogDto.toDomain() = AdminLog(
    id, actorUid, action, targetType, targetId, details, createdAt
)

fun AdminPushResponse.toDomain() = AdminPushResult(sent, recipients)

fun SubmissionDto.toDomain() = Submission(
    id = id,
    proposedName = proposedName,
    proposedRegion = proposedRegion,
    proposedStyle = proposedStyle,
    proposedRockType = proposedRockType,
    proposedLat = proposedLat,
    proposedLon = proposedLon,
    proposedLocation = proposedLocation,
    proposedSource = proposedSource,
    notes = notes,
    status = status,
    submittedByUid = submittedByUid,
    reviewedByUid = reviewedByUid,
    reviewReason = reviewReason,
    createdSchoolId = createdSchoolId,
    createdAt = createdAt,
    reviewedAt = reviewedAt
)
