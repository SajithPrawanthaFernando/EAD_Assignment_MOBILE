package com.ead.evcharge.data.model

data class EvOwnerResponse(
    val nic: String,
    val name: String,
    val phone: String,
    val status: String,
    val user: UserData2
)

data class UserData2(
    val email: String,
    val active: Boolean,
    val roles: List<String>
)