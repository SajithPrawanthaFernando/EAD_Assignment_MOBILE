package com.ead.evcharge.data.model

data class BookingRequest(
    val ownerNic: String,
    val stationId: String,
    val slotId: String,
    val startTimeUtc: String
)

data class BookingResponse(
    val bookingId: String?,
    val message: String?
)