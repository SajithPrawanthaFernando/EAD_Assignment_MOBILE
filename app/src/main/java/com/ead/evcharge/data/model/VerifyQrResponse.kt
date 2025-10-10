package com.ead.evcharge.data.model

data class VerifyQrResponse(
    val valid: Boolean,
    val bookingId: String
)