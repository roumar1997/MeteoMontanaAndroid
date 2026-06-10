package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.FollowStatusDto
import com.meteomontana.android.data.api.dto.PublicProfileDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post

class KtorSocialApi(private val client: HttpClient) {

    suspend fun searchUsers(query: String, limit: Int = 20): List<PublicProfileDto> =
        client.get("users/search") {
            parameter("q", query)
            parameter("limit", limit)
        }.body()

    suspend fun getUserProfile(uid: String): PublicProfileDto = client.get("users/$uid").body()

    suspend fun follow(uid: String) { client.post("users/$uid/follow") }

    suspend fun unfollow(uid: String) { client.delete("users/$uid/follow") }

    suspend fun getFollowStatus(uid: String): FollowStatusDto =
        client.get("users/$uid/follow-status").body()

    suspend fun getFollowers(uid: String): List<PublicProfileDto> =
        client.get("users/$uid/followers").body()

    suspend fun getFollowing(uid: String): List<PublicProfileDto> =
        client.get("users/$uid/following").body()

    suspend fun getMyFollowRequests(): List<PublicProfileDto> =
        client.get("me/follow-requests").body()

    suspend fun acceptFollowRequest(requesterUid: String) {
        client.post("me/follow-requests/$requesterUid/accept")
    }

    suspend fun rejectFollowRequest(requesterUid: String) {
        client.post("me/follow-requests/$requesterUid/reject")
    }
}
