package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.model.TopContributor

interface SocialRepository {
    suspend fun searchUsers(query: String, limit: Int = 20): List<PublicProfile>
    suspend fun getUserProfile(uid: String): PublicProfile
    suspend fun getTopContributors(limit: Int = 20): List<TopContributor>
    suspend fun follow(uid: String)
    suspend fun unfollow(uid: String)
    suspend fun removeFollower(uid: String)
    suspend fun getFollowStatus(uid: String): FollowStatus
    suspend fun getFollowers(uid: String): List<PublicProfile>
    suspend fun getFollowing(uid: String): List<PublicProfile>
    suspend fun getMyFollowRequests(): List<PublicProfile>
    suspend fun acceptFollowRequest(requesterUid: String)
    suspend fun rejectFollowRequest(requesterUid: String)
}
