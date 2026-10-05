package com.example

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(getString(R.string.google_app_id))
                    .setApiKey(getString(R.string.google_api_key))
                    .setGcmSenderId(getString(R.string.gcm_defaultSenderId))
                    .setStorageBucket(getString(R.string.google_storage_bucket))
                    .setProjectId(getString(R.string.project_id))
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            MobileAds.initialize(this) {}
        } catch (_: Exception) {}
    }
}

