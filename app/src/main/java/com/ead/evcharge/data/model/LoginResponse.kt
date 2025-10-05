package com.ead.evcharge.data.model

data class LoginResponse(
    val token: String,
    val user: UserData? = null
)