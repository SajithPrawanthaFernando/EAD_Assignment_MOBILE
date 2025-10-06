// data/remote/AuthInterceptor.kt
package com.ead.evcharge.data.remote

import android.util.Log
import com.ead.evcharge.data.local.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    companion object {
        private const val TAG = "AuthInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            tokenManager.getToken().first()
        }

        val originalRequest = chain.request()

        Log.d(TAG, "==============================================")
        Log.d(TAG, "Request: $originalRequest")
        Log.d(TAG, "Has Token: ${token != null}")

        val request = if (token != null) {
            Log.d(TAG, "✅ Token found: ${token.take(30)}...")
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            Log.e(TAG, "❌ No token found!")
            originalRequest
        }

        val response = chain.proceed(request)
        Log.d(TAG, "Response: $response")
        Log.d(TAG, "==============================================")

        return response
    }
}
