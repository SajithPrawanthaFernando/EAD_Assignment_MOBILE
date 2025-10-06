package com.ead.evcharge.data.model

import com.google.gson.annotations.SerializedName

data class StationResponse(
    val id: String,
    val name: String,
    val type: String,
    val active: Boolean,
    val lat: Double,
    val lng: Double,
    val slots: List<SlotResponse>
)

data class SlotResponse(
    val slotId: String,
    val label: String,
    val available: Boolean
)