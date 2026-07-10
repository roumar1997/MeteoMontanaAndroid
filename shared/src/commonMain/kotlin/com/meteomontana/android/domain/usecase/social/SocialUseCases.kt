package com.meteomontana.android.domain.usecase.social

import com.meteomontana.android.data.saved.ProfileCacheRepository
import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.repository.SocialRepository

/**
 * Perfil público de un usuario, con caché offline "local-first": si hay red, el
 * perfil se resuelve online y se guarda en [cache]; si la red falla, se devuelve
 * el último perfil cacheado (nombre/foto) en vez de fallar — así el chat muestra
 * nombres/avatares sin conexión. Sin caché o sin dato previo, se propaga el error.
 */
class GetPublicProfileUseCase(
    private val repo: SocialRepository,
    private val cache: ProfileCacheRepository? = null
) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String): PublicProfile {
        return try {
            val fresh = repo.getUserProfile(uid)
            runCatching { cache?.save(fresh) }
            fresh
        } catch (e: Exception) {
            cache?.load(uid) ?: throw e
        }
    }
}

class GetFollowStatusUseCase(private val repo: SocialRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String): FollowStatus = repo.getFollowStatus(uid)
}

class SearchUsersUseCase(private val repo: SocialRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(query: String, limit: Int = 20): List<PublicProfile> =
        repo.searchUsers(query, limit)
}

/** Ranking de mayores contribuidores (pantalla Comunidad). */
class GetTopContributorsUseCase(private val repo: SocialRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(limit: Int = 20): List<com.meteomontana.android.domain.model.TopContributor> =
        repo.getTopContributors(limit)
}

class GetFollowersUseCase(private val repo: SocialRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String): List<PublicProfile> = repo.getFollowers(uid)
}

class GetFollowingUseCase(private val repo: SocialRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String): List<PublicProfile> = repo.getFollowing(uid)
}

class FollowUserUseCase(private val repo: SocialRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String) = repo.follow(uid)
}

class UnfollowUserUseCase(private val repo: SocialRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String) = repo.unfollow(uid)
}

/** Elimina a [uid] de mis seguidores (lo fuerza a dejar de seguirme). */
class RemoveFollowerUseCase(private val repo: SocialRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String) = repo.removeFollower(uid)
}

class GetMyFollowRequestsUseCase(private val repo: SocialRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<PublicProfile> = repo.getMyFollowRequests()
}

class AcceptFollowRequestUseCase(private val repo: SocialRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(requesterUid: String) = repo.acceptFollowRequest(requesterUid)
}

class RejectFollowRequestUseCase(private val repo: SocialRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(requesterUid: String) = repo.rejectFollowRequest(requesterUid)
}
