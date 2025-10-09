package com.ead.evcharge.data.model

data class BookingDetailsResponse(
    val id: String,
    val ownerNic: String,
    val stationId: String,
    val slotId: String,
    val startTimeUtc: String,
    val status: String
)