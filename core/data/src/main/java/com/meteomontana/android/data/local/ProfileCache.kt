package com.meteomontana.android.data.local

import android.content.Context
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.model.SchoolStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Caché LOCAL del perfil privado + stats del diario, para poder ver el perfil
 * SIN conexión. Se guarda cada vez que el perfil carga online; offline se lee
 * de aquí. Persiste en SharedPreferences como JSON.
 *
 * Los modelos de dominio no son `@Serializable`, así que usamos snapshots
 * locales (`@Serializable`) y convertimos en ambos sentidos.
 */
class ProfileCache(context: Context) {

    private val prefs = context.getSharedPreferences("profile_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(profile: PrivateProfile, stats: JournalStats, followers: Long, following: Long) {
        val snap = Snapshot(
            profile = ProfileSnap(
                profile.uid, profile.email, profile.username, profile.displayName,
                profile.photoUrl, profile.bio, profile.topGrade,
                profile.isPublic, profile.isAdmin, profile.isPremium
            ),
            stats = StatsSnap(
                stats.blockCount, stats.schoolCount, stats.maxGrade,
                stats.bySchool.map { SchoolStatSnap(it.schoolName, it.blockCount, it.maxGrade) },
                stats.boulderCount, stats.routeCount, stats.maxBoulderGrade, stats.maxRouteGrade,
                stats.projectCount, stats.projectBoulderCount, stats.projectRouteCount
            ),
            followers = followers,
            following = following
        )
        prefs.edit().putString(KEY, json.encodeToString(Snapshot.serializer(), snap)).apply()
    }

    /** Último perfil/stats cacheado, o null si nunca cargó online. */
    fun load(): Cached? {
        val raw = prefs.getString(KEY, null) ?: return null
        val snap = runCatching { json.decodeFromString(Snapshot.serializer(), raw) }.getOrNull() ?: return null
        val p = snap.profile
        return Cached(
            profile = PrivateProfile(
                p.uid, p.email, p.username, p.displayName, p.photoUrl, p.bio, p.topGrade,
                p.isPublic, p.isAdmin, p.isPremium
            ),
            stats = JournalStats(
                blockCount = snap.stats.blockCount,
                boulderCount = snap.stats.boulderCount,
                routeCount = snap.stats.routeCount,
                schoolCount = snap.stats.schoolCount,
                maxGrade = snap.stats.maxGrade,
                maxBoulderGrade = snap.stats.maxBoulderGrade,
                maxRouteGrade = snap.stats.maxRouteGrade,
                bySchool = snap.stats.bySchool.map { SchoolStats(it.schoolName, it.blockCount, it.maxGrade) },
                projectCount = snap.stats.projectCount,
                projectBoulderCount = snap.stats.projectBoulderCount,
                projectRouteCount = snap.stats.projectRouteCount
            ),
            followers = snap.followers,
            following = snap.following
        )
    }

    data class Cached(
        val profile: PrivateProfile,
        val stats: JournalStats,
        val followers: Long,
        val following: Long
    )

    @Serializable
    private data class Snapshot(
        val profile: ProfileSnap,
        val stats: StatsSnap,
        val followers: Long,
        val following: Long
    )

    @Serializable
    private data class ProfileSnap(
        val uid: String, val email: String?, val username: String?, val displayName: String?,
        val photoUrl: String?, val bio: String?, val topGrade: String?,
        val isPublic: Boolean, val isAdmin: Boolean, val isPremium: Boolean
    )

    @Serializable
    private data class StatsSnap(
        val blockCount: Int, val schoolCount: Int, val maxGrade: String?,
        val bySchool: List<SchoolStatSnap>,
        val boulderCount: Int = 0, val routeCount: Int = 0,
        val maxBoulderGrade: String? = null, val maxRouteGrade: String? = null,
        val projectCount: Int = 0, val projectBoulderCount: Int = 0, val projectRouteCount: Int = 0
    )

    @Serializable
    private data class SchoolStatSnap(val schoolName: String, val blockCount: Int, val maxGrade: String?)

    private companion object { const val KEY = "profile_snapshot" }
}
