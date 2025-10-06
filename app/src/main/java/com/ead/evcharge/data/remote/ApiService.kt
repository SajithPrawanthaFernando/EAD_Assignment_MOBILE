package com.ead.evcharge.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.GET
import retrofit2.http.Path
import com.ead.evcharge.data.model.LoginRequest
import com.ead.evcharge.data.model.LoginResponse
import com.ead.evcharge.data.model.SignupRequest
import com.ead.evcharge.data.model.SignupResponse
import com.ead.evcharge.data.model.UserResponse

interface ApiService {
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @PUT("/api/ev-owners")
    suspend fun signup(@Body request: SignupRequest): Response<SignupResponse>

    @GET("/api/ev-owners/{nic}")
    suspend fun getUserDetails(@Path("nic") userId: String): Response<UserResponse>
}