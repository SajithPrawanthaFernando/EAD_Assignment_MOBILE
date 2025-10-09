package com.ead.evcharge.data.repository

import com.ead.evcharge.data.model.VerifyQrRequest
import com.ead.evcharge.data.remote.ApiService

class QrRepository(private val api: ApiService) {
    suspend fun verifyQr(authToken: String, qrToken: String) =
        api.verifyQr("Bearer $authToken", VerifyQrRequest(qrToken))
}