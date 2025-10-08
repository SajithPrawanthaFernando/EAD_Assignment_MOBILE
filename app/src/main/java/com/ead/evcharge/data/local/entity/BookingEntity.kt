package com.ead.evcharge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey val id: String,
    val ownerNic: String,
    val stationId: String,
    val stationName: String,
    val slotId: String,
    val slotLabel: String,
    val startTimeUtc: String,
    val status: String,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)