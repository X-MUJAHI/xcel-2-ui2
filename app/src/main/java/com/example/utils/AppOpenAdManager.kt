package com.example.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.example.MainActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

/**
 * Manages App Open Ad loading and presentation for normal users.
 * Automatically loads real App Open ads and shows them on app startup or foregrounding.
 * Never falls back to test ads; skips silently if real ad is not available.
 */
class AppOpenAdManager(private val application: Application) : Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "AppOpenAdManager"
        const val AD_UNIT_ID = "ca-app-pub-4490063343359332/2365874033"
        private const val FOUR_HOURS_MILLIS = 4 * 3600 * 1000L
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime: Long = 0
    private var currentActivity: Activity? = null

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    private fun isAdmin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val userType = prefs.getString(MainActivity.KEY_USER_TYPE, "NORMAL")
        val savedKey = prefs.getString(MainActivity.KEY_SAVED_KEY, null)
        return userType == "ADMIN" || savedKey == "mujahi@admin"
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && (Date().time - loadTime < FOUR_HOURS_MILLIS)
    }

    fun loadAd(context: Context) {
        if (isAdmin(context)) return
        if (isLoadingAd || isAdAvailable()) return

        isLoadingAd = true
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            context,
            AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "Real App Open Ad loaded successfully.")
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Real App Open Ad failed to load: ${loadAdError.message}. Skipping test ads.")
                    isLoadingAd = false
                    appOpenAd = null
                }
            }
        )
    }

    /**
     * Shows the App Open ad if available and user is NORMAL.
     * Skips immediately without showing test ads if real ad is not available.
     */
    fun showAdIfAvailable(activity: Activity, onShowComplete: () -> Unit = {}) {
        if (isAdmin(activity)) {
            Log.d(TAG, "User is admin, skipping App Open Ad.")
            onShowComplete()
            return
        }

        if (isShowingAd) {
            Log.d(TAG, "App Open Ad is already showing.")
            return
        }

        if (!isAdAvailable()) {
            Log.d(TAG, "App Open Ad is not ready. Skipping directly without test ads.")
            onShowComplete()
            loadAd(activity)
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App Open Ad dismissed.")
                appOpenAd = null
                isShowingAd = false
                onShowComplete()
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "App Open Ad failed to show: ${adError.message}")
                appOpenAd = null
                isShowingAd = false
                onShowComplete()
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open Ad showing fullscreen.")
                isShowingAd = true
            }
        }

        isShowingAd = true
        appOpenAd?.show(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
}
