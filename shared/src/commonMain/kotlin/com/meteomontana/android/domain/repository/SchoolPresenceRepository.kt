package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.SchoolPresence

/**
 * Puerto de presencia en una escuela (hexagonal). Los use cases dependen de
 * esta abstracción, nunca del `KtorSchoolPresenceApi` concreto.
 */
interface SchoolPresenceRepository {
    /** Quién está presente ahora mismo en esa escuela. Público, no requiere sesión. */
    suspend fun getActivePresence(schoolId: String): List<SchoolPresence>

    /** "Estoy aquí". Repetirlo en la misma escuela renueva la caducidad. */
    suspend fun markPresence(schoolId: String): SchoolPresence

    /** "Ya no estoy". */
    suspend fun clearPresence(schoolId: String)
}
