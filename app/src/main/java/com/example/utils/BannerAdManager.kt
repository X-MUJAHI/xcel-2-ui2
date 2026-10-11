package com.example.utils

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.MainActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Manages banner ad loading and presentation.
 * Displays banner ads on all screens/tabs for users.
 */
object BannerAdManager {

    private const val TAG = "BannerAdManager"
    const val PRIMARY_BANNER_AD_UNIT_ID = "ca-app-pub-4490063343359332/3518253739"
    // Fallback official test banner ad unit in case of no-fill
    const val FALLBACK_TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

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
        container.visibility = View.VISIBLE

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
                Log.w(TAG, "Primary banner ad failed: ${loadAdError.message}. Loading fallback test banner...")
                loadFallbackBanner(context, container)
            }
        }

        container.addView(adView)
        adView.loadAd(adRequest)
    }

    private fun loadFallbackBanner(context: Context, container: FrameLayout) {
        container.removeAllViews()
        val fallbackAdView = AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = FALLBACK_TEST_BANNER_AD_UNIT_ID
        }

        val adRequest = AdRequest.Builder().build()
        fallbackAdView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                Log.d(TAG, "Fallback test banner ad loaded successfully.")
                container.visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                Log.e(TAG, "Fallback banner ad also failed: ${error.message}")
            }
        }

        container.addView(fallbackAdView)
        fallbackAdView.loadAd(adRequest)
    }

    fun updateVisibilityBasedOnRole(context: Context, container: FrameLayout) {
        if (isAdmin(context)) {
            container.visibility = View.GONE
            container.removeAllViews()
        } else {
            if (container.childCount == 0) {
                setupBanner(context, container)
            } else {
                container.visibility = View.VISIBLE
            }
        }
    }
}
