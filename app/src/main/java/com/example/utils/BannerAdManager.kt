package com.example.utils

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.example.MainActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Manages banner ad loading and presentation.
 * Displays real banner ads on all screens/tabs for users.
 * Does not show test ads when no fill is available.
 */
object BannerAdManager {

    private const val TAG = "BannerAdManager"
    const val PRIMARY_BANNER_AD_UNIT_ID = "ca-app-pub-4490063343359332/3518253739"

    private fun isAdmin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val userType = prefs.getString(MainActivity.KEY_USER_TYPE, "NORMAL")
        val savedKey = prefs.getString(MainActivity.KEY_SAVED_KEY, null)
        return userType == "ADMIN" || savedKey == "mujahi@admin"
    }

    /**
     * Initializes and attaches the banner ad to the container.
     */
    fun setupBanner(context: Context, container: FrameLayout) {
        if (isAdmin(context)) {
            container.visibility = View.GONE
            container.removeAllViews()
            return
        }

        container.removeAllViews()
        container.visibility = View.GONE

        val adView = AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = PRIMARY_BANNER_AD_UNIT_ID
        }

        val adRequest = AdRequest.Builder().build()

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                Log.d(TAG, "Primary banner ad loaded successfully.")
                container.visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                Log.w(TAG, "Primary banner ad failed: ${loadAdError.message}. Hiding container, no test ads.")
                // No test ads - hide container cleanly
                container.visibility = View.GONE
            }
        }

        container.addView(adView)
        adView.loadAd(adRequest)
    }

    fun updateVisibilityBasedOnRole(context: Context, container: FrameLayout) {
        if (isAdmin(context)) {
            container.visibility = View.GONE
            container.removeAllViews()
        } else {
            if (container.childCount == 0) {
                setupBanner(context, container)
            }
        }
    }
}
