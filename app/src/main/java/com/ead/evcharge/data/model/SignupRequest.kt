package com.ead.evcharge.data.model

data class SignupRequest(
    val nic: String,
    val name: String,
    val phone: String,
    val email: String,
    val password: String
)