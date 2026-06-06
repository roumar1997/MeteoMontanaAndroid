package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.Note

fun NoteDto.toDomain() = Note(
    id = id,
    schoolId = schoolId,
    text = text,
    author = author,
    uid = uid,
    createdAt = createdAt,
    upvotesCount = upvotesCount,
    downvotesCount = downvotesCount
)
