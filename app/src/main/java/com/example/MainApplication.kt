package com.example

import android.app.Application
import com.google.android.gms.ads.MobileAds

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            MobileAds.initialize(this) {}
        } catch (_: Exception) {}
    }
}
