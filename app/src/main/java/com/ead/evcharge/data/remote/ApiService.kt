package com.ead.evcharge.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import com.ead.evcharge.data.model.LoginRequest
import com.ead.evcharge.data.model.LoginResponse

interface ApiService {
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}