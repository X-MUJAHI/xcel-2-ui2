package com.example

import android.app.Application
import com.example.utils.AppOpenAdManager
import com.example.utils.RewardAdManager

class MainApplication : Application() {

    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    override fun onCreate() {
        super.onCreate()
        RewardAdManager.initialize(this)
        appOpenAdManager = AppOpenAdManager(this)
        appOpenAdManager.loadAd(this)
    }
}
