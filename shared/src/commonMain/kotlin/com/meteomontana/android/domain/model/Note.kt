package com.meteomontana.android.domain.model

data class Note(
    val id: String,
    val schoolId: String,
    val text: String,
    val author: String?,
    val uid: String,
    val createdAt: String,
    val upvotesCount: Int,
    val downvotesCount: Int,
    /** URL pública de la foto adjunta. Null si la nota no tiene foto. */
    val photoUrl: String? = null
)
