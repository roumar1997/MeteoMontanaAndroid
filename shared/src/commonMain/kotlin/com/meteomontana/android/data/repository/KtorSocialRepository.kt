package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorSocialApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.model.TopContributor
import com.meteomontana.android.domain.repository.SocialRepository

class KtorSocialRepository(private val api: KtorSocialApi) : SocialRepository {
    override suspend fun searchUsers(query: String, limit: Int): List<PublicProfile> =
        api.searchUsers(query, limit).map { it.toDomain() }
    override suspend fun getUserProfile(uid: String): PublicProfile =
        api.getUserProfile(uid).toDomain()
    override suspend fun getTopContributors(limit: Int): List<TopContributor> =
        api.getTopContributors(limit).map { it.toDomain() }
    override suspend fun follow(uid: String) = api.follow(uid)
    override suspend fun unfollow(uid: String) = api.unfollow(uid)
    override suspend fun removeFollower(uid: String) = api.removeFollower(uid)
    override suspend fun getFollowStatus(uid: String): FollowStatus =
        api.getFollowStatus(uid).toDomain()
    override suspend fun getFollowers(uid: String): List<PublicProfile> =
        api.getFollowers(uid).map { it.toDomain() }
    override suspend fun getFollowing(uid: String): List<PublicProfile> =
        api.getFollowing(uid).map { it.toDomain() }
    override suspend fun getMyFollowRequests(): List<PublicProfile> =
        api.getMyFollowRequests().map { it.toDomain() }
    override suspend fun acceptFollowRequest(requesterUid: String) =
        api.acceptFollowRequest(requesterUid)
    override suspend fun rejectFollowRequest(requesterUid: String) =
        api.rejectFollowRequest(requesterUid)
}
