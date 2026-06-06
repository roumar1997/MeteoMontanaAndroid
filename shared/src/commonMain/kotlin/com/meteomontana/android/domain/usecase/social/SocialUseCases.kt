package com.meteomontana.android.domain.usecase.social

import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.repository.SocialRepository

class GetPublicProfileUseCase(private val repo: SocialRepository) {
    suspend operator fun invoke(uid: String): PublicProfile = repo.getUserProfile(uid)
}

class GetFollowStatusUseCase(private val repo: SocialRepository) {
    suspend operator fun invoke(uid: String): FollowStatus = repo.getFollowStatus(uid)
}

class SearchUsersUseCase(private val repo: SocialRepository) {
    suspend operator fun invoke(query: String, limit: Int = 20): List<PublicProfile> =
        repo.searchUsers(query, limit)
}

class GetFollowersUseCase(private val repo: SocialRepository) {
    suspend operator fun invoke(uid: String): List<PublicProfile> = repo.getFollowers(uid)
}

class GetFollowingUseCase(private val repo: SocialRepository) {
    suspend operator fun invoke(uid: String): List<PublicProfile> = repo.getFollowing(uid)
}

class FollowUserUseCase(private val repo: SocialRepository) {
    suspend operator fun invoke(uid: String) = repo.follow(uid)
}

class UnfollowUserUseCase(private val repo: SocialRepository) {
    suspend operator fun invoke(uid: String) = repo.unfollow(uid)
}
