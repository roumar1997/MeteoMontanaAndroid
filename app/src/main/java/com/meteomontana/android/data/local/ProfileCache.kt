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
                stats.bySchool.map { SchoolStatSnap(it.schoolName, it.blockCount, it.maxGrade) }
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
                snap.stats.blockCount, snap.stats.schoolCount, snap.stats.maxGrade,
                snap.stats.bySchool.map { SchoolStats(it.schoolName, it.blockCount, it.maxGrade) }
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
        val bySchool: List<SchoolStatSnap>
    )

    @Serializable
    private data class SchoolStatSnap(val schoolName: String, val blockCount: Int, val maxGrade: String?)

    private companion object { const val KEY = "profile_snapshot" }
}
