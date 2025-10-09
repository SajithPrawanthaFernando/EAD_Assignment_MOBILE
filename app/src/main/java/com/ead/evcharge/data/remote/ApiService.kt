package com.ead.evcharge.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Header
import com.ead.evcharge.data.model.LoginRequest
import com.ead.evcharge.data.model.LoginResponse
import com.ead.evcharge.data.model.SignupRequest
import com.ead.evcharge.data.model.SignupResponse
import com.ead.evcharge.data.model.UserResponse
import com.ead.evcharge.data.model.StationResponse
import com.ead.evcharge.data.model.BookingRequest
import com.ead.evcharge.data.model.BookingResponse
import com.ead.evcharge.data.model.VerifyQrRequest
import com.ead.evcharge.data.model.VerifyQrResponse
import com.ead.evcharge.data.model.BookingDetailsResponse

interface ApiService {
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @PUT("/api/ev-owners")
    suspend fun signup(@Body request: SignupRequest): Response<SignupResponse>

    @GET("/api/ev-owners/{nic}")
    suspend fun getOwnerDetails(@Path("nic") userId: String): Response<UserResponse>
    @GET("/api/users/{id}")
    suspend fun getUserDetails(@Path("id") userId: String): Response<UserResponse>
    @GET("/api/stations")
    suspend fun getAllStations(): Response<List<StationResponse>>

    @POST("/api/bookings")
    suspend fun createBooking(@Body request: BookingRequest): Response<BookingResponse>

    @POST("/api/qr/verify")
    suspend fun verifyQr(
        @Header("Authorization") token: String,
        @Body body: VerifyQrRequest
    ): Response<VerifyQrResponse>

    @POST("api/qr/bookings/{bookingId}/finalize")
    suspend fun finalizeBooking(
        @Header("Authorization") token: String,
        @Path("bookingId") bookingId: String
    ): Response<Unit>

    @GET("api/bookings/{id}")
    suspend fun getBookingDetails(
        @Header("Authorization") token: String,
        @Path("id") bookingId: String
    ): Response<BookingDetailsResponse>

    @GET("api/bookings")
    suspend fun getAllBookings(
        @Header("Authorization") token: String
    ): Response<List<BookingDetailsResponse>>
}
