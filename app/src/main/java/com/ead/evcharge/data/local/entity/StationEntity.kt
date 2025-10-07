package com.ead.evcharge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.ead.evcharge.data.local.converter.SlotListConverter

@Entity(tableName = "stations")
@TypeConverters(SlotListConverter::class)
data class StationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,
    val active: Boolean,
    val lat: Double,
    val lng: Double,
    val slots: List<SlotEntity>,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)

data class SlotEntity(
    val slotId: String,
    val label: String,
    val available: Boolean
)