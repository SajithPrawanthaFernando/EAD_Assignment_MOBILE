package com.ead.evcharge.data.model


data class UpdateEvOwnerRequest(
    val nic: String,
    val name: String,
    val phone: String,
    val email: String,
    val password: String = ""  // Optional - only send if user wants to change password
)