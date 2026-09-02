package com.meteomontana.android.domain.usecase.presence

import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.repository.SchoolPresenceRepository

// Los use cases dependen del PUERTO SchoolPresenceRepository, no del
// KtorSchoolPresenceApi concreto. @Throws en los tres porque cruzan a Swift
// (ver SwiftBoundaryThrowsTest) — igual que MeetupUseCases.

class GetSchoolPresenceUseCase(private val repo: SchoolPresenceRepository) {
    @Throws(Exception::class)
    suspend fun execute(schoolId: String): List<SchoolPresence> = repo.getActivePresence(schoolId)
}

class MarkSchoolPresenceUseCase(private val repo: SchoolPresenceRepository) {
    @Throws(Exception::class)
    suspend fun execute(schoolId: String): SchoolPresence = repo.markPresence(schoolId)
}

class ClearSchoolPresenceUseCase(private val repo: SchoolPresenceRepository) {
    @Throws(Exception::class)
    suspend fun execute(schoolId: String) = repo.clearPresence(schoolId)
}
