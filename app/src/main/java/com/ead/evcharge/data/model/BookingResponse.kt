package com.ead.evcharge.data.model

data class BookingResponse(
    val id: String,
    val ownerNic: String,
    val slotId: String,
    val startTimeUtc: String,
    val status: String,
    val station: StationResponse
)