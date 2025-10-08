package com.ead.evcharge.data.model


data class NearbyStationResponse(
    val id: String,
    val name: String,
    val type: String,
    val active: Boolean,
    val lat: Double,
    val lng: Double,
    val slots: List<StationSlot>
)

data class StationSlot(
    val slotId: String,
    val label: String,
    val available: Boolean
)