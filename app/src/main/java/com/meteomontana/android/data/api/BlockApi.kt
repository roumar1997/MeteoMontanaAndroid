package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface BlockApi {

    @GET("schools/{id}/blocks")
    suspend fun getBlocks(@Path("id") id: String): List<BlockDto>

    @GET("blocks/{id}")
    suspend fun getBlock(@Path("id") id: String): BlockDto

    @POST("schools/{id}/blocks")
    suspend fun createBlock(@Path("id") id: String, @Body req: CreateBlockRequest): BlockDto

    @PUT("blocks/{id}")
    suspend fun updateBlock(@Path("id") id: String, @Body req: CreateBlockRequest): BlockDto

    @DELETE("blocks/{id}")
    suspend fun deleteBlock(@Path("id") id: String)
}
