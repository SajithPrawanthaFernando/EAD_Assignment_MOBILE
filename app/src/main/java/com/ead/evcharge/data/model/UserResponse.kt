package com.ead.evcharge.data.model

data class UserResponse(
    val userId: String,
    val name: String,
    val email: String,
    val nic: String,
    val phone: String,
    val role: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
)