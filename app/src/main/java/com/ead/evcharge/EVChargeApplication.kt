package com.ead.evcharge

import android.app.Application
import com.ead.evcharge.data.remote.RetrofitInstance

class EVChargeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitInstance.initialize(this)
    }
}