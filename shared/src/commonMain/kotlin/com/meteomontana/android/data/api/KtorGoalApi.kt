package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class GoalDto(
    val year: Int,
    val discipline: String? = null,
    val grade: String? = null,
    val ascents: Int? = null,
    val days: Int? = null,
)

class KtorGoalApi(private val client: HttpClient) {

    /** GET /api/me/goals/{year} — 204 si ese año no hay objetivo (→ null). */
    suspend fun get(year: Int): GoalDto? {
        val res = client.get("me/goals/$year")
        return if (res.status == HttpStatusCode.NoContent) null else res.body()
    }

    suspend fun put(goal: GoalDto): GoalDto = client.put("me/goals/${goal.year}") { setBody(goal) }.body()

    suspend fun delete(year: Int) {
        client.delete("me/goals/$year")
    }
}
