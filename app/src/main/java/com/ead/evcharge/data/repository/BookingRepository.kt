package com.ead.evcharge.data.repository

import com.ead.evcharge.data.remote.ApiService

class BookingRepository(private val api: ApiService) {

    // No need to pass token now — ApiService handles authorization internally
    suspend fun getAllBookings() = api.getAllBookings()

    suspend fun getBookingDetails(bookingId: String) =
        api.getBookingDetails(bookingId)
}
