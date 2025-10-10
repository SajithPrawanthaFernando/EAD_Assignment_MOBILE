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
import com.ead.evcharge.data.model.EvOwnerResponse
import com.ead.evcharge.data.model.NearbyStationResponse
import com.ead.evcharge.data.model.UpdateEvOwnerRequest
import retrofit2.http.PATCH
import retrofit2.http.Query
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

    @GET("bookings/mine/{ownerNic}/detail")
    suspend fun getBookingsForOwner(@Path("ownerNic") ownerNic: String): Response<List<BookingResponse>>
    @PATCH("/api/ev-owners/{nic}/deactivate")
    suspend fun deactivateUser(@Path("nic") userId: String): Response<Unit>

    @GET("/api/ev-owners/{nic}")
    suspend fun getEvOwnerByNic(@Path("nic") nic: String): Response<EvOwnerResponse>

    @PUT("/api/ev-owners")
    suspend fun updateEvOwner(@Body request: UpdateEvOwnerRequest): Response<EvOwnerResponse>

    @GET("/api/stations/nearby")
    suspend fun getNearbyStations(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("rKm") radiusKm: Double = 10.0
    ): Response<List<NearbyStationResponse>>

    @POST("api/qr/verify")
    suspend fun verifyQr(@Body body: VerifyQrRequest): Response<VerifyQrResponse>

    @GET("api/bookings/{id}")
    suspend fun getBookingDetails(@Path("id") bookingId: String): Response<BookingDetailsResponse>

    @GET("api/bookings")
    suspend fun getAllBookings(): Response<List<BookingDetailsResponse>>

    @PATCH("api/bookings/{id}/start-charging")
    suspend fun startCharging(@Path("id") bookingId: String): Response<Unit>

    @PATCH("api/bookings/{id}/complete")
    suspend fun completeBooking(@Path("id") bookingId: String): Response<Unit>

}
