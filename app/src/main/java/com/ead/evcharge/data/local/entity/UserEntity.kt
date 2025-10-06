package com.ead.evcharge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val userId: String,
    val name: String,
    val email: String,
    val nic: String,
    val phone: String,
    val role: String,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)