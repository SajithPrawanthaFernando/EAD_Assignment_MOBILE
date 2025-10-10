// RetrofitInstance.kt
package com.ead.evcharge.data.remote

import android.annotation.SuppressLint
import android.content.Context
import com.ead.evcharge.data.local.TokenManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@SuppressLint("StaticFieldLeak")
object RetrofitInstance {
    const val BASE_URL = "http://192.168.1.6:5244/"

    private var context: Context? = null

    fun initialize(appContext: Context) {
        context = appContext
    }

    private val okHttpClient: OkHttpClient by lazy {
        val tokenManager = TokenManager(context!!)
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
