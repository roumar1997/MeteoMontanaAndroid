package com.meteomontana.android.data.saved

import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.db.MeteoMontanaDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Caché local de perfiles públicos (nombre + foto + usuario) por uid, para que
 * el chat muestre nombres/avatares SIN conexión. Modelo "local-first": cada vez
 * que [com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase]
 * resuelve un perfil online lo escribe aquí; offline se lee como fallback.
 *
 * Solo se cachea lo que online SÍ se pudo ver: un privado que no te deja ver el
 * perfil tampoco se ve offline (no hay nada que guardar).
 */
class ProfileCacheRepository(private val db: MeteoMontanaDb) {

    private val q get() = db.schemaQueries

    /** Guarda/actualiza el perfil. No guarda perfiles "bloqueados" (sin datos). */
    @Throws(Exception::class)
    suspend fun save(profile: PublicProfile) = withContext(Dispatchers.Default) {
        if (profile.locked) return@withContext
        q.upsertProfile(
            uid = profile.uid,
            username = profile.username,
            displayName = profile.displayName,
            photoUrl = profile.photoUrl,
            bio = profile.bio,
            topGrade = profile.topGrade,
            isPublic = if (profile.isPublic) 1L else 0L,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
    }

    /** Último perfil conocido de [uid], o null si nunca cargó online. */
    @Throws(Exception::class)
    suspend fun load(uid: String): PublicProfile? = withContext(Dispatchers.Default) {
        q.findProfile(uid).executeAsOneOrNull()?.let {
            PublicProfile(
                uid = it.uid,
                username = it.username,
                displayName = it.displayName,
                photoUrl = it.photoUrl,
                bio = it.bio,
                topGrade = it.topGrade,
                locked = false,
                isPublic = it.isPublic == 1L
            )
        }
    }
}
