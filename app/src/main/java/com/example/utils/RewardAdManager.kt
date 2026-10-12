package com.example.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.MainActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object RewardAdManager {

    private const val TAG = "RewardAdManager"
    const val PRIMARY_AD_UNIT_ID = "ca-app-pub-4490063343359332/1223494398"

    private var cachedRewardedAd: RewardedAd? = null
    private var isLoadingAd = false
    private var isInitialized = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var retryAttempt = 0
    private var isRetryingPreload = false

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            MobileAds.initialize(context) {
                Log.d(TAG, "Google Mobile Ads SDK initialized successfully.")
                isInitialized = true
                preload(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MobileAds: ${e.message}")
        }
    }

    fun isAdmin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val userType = prefs.getString(MainActivity.KEY_USER_TYPE, "NORMAL")
        val savedKey = prefs.getString(MainActivity.KEY_SAVED_KEY, null)
        return userType == "ADMIN" || savedKey == "mujahi@admin"
    }

    /**
     * Preloads a real rewarded ad with exponential backoff retry.
     * Never loads test ads.
     */
    fun preload(context: Context) {
        if (isAdmin(context)) return
        if (cachedRewardedAd != null || isLoadingAd) return

        isLoadingAd = true
        val adRequest = AdRequest.Builder().build()
        val appContext = context.applicationContext

        // Attempt real production ad unit
        RewardedAd.load(appContext, PRIMARY_AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d(TAG, "Primary rewarded ad preloaded successfully.")
                cachedRewardedAd = rewardedAd
                isLoadingAd = false
                retryAttempt = 0
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.w(TAG, "Primary rewarded ad failed to preload: ${loadAdError.message} (code ${loadAdError.code}).")
                cachedRewardedAd = null
                isLoadingAd = false
                schedulePreloadRetry(appContext)
            }
        })
    }

    private fun schedulePreloadRetry(context: Context) {
        if (isRetryingPreload || cachedRewardedAd != null || isAdmin(context)) return
        isRetryingPreload = true

        val delays = longArrayOf(4000L, 8000L, 16000L, 30000L)
        val delay = delays.getOrElse(retryAttempt) { 30000L }
        retryAttempt++

        mainHandler.postDelayed({
            isRetryingPreload = false
            if (cachedRewardedAd == null) {
                Log.d(TAG, "Executing scheduled preload retry #$retryAttempt...")
                preload(context)
            }
        }, delay)
    }

    /**
     * Executes the target action with rewarded ad gating for normal users.
     * Admin users bypass the ad completely.
     * If preloaded ad exists, shows immediately.
     * If no real ad is available or fails to load, DOES NOT show test ads; skips ad and continues directly!
     */
    fun showRewardedAdForAction(activity: Activity, onRewardEarned: () -> Unit) {
        // 1. Admin bypass check
        if (isAdmin(activity)) {
            Log.d(TAG, "User is ADMIN, bypassing rewarded ad.")
            onRewardEarned()
            return
        }

        // 2. If real cached ad is ready, show immediately
        val ad = cachedRewardedAd
        if (ad != null) {
            cachedRewardedAd = null
            displayRewardedAd(activity, ad, onRewardEarned)
            return
        }

        // 3. Otherwise show brief loading dialog (up to 4 seconds) to give real ad a chance to load.
        // If not filled / fails, immediately dismiss and skip ad so user continues smoothly!
        val loadingDialog = createThemedLoadingDialog(activity)
        loadingDialog.show()

        val adRequest = AdRequest.Builder().build()
        isLoadingAd = true

        var hasHandledResponse = false

        // Quick timeout (4 seconds) - if real ad isn't loaded quickly, skip and continue
        val timeoutRunnable = Runnable {
            if (!hasHandledResponse && !activity.isFinishing && !activity.isDestroyed) {
                hasHandledResponse = true
                isLoadingAd = false
                if (loadingDialog.isShowing) {
                    try { loadingDialog.dismiss() } catch (ignored: Exception) {}
                }
                Log.d(TAG, "Ad load timed out - skipping ad and continuing for user.")
                preload(activity.applicationContext)
                onRewardEarned()
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 4000L)

        // Try real ad unit
        RewardedAd.load(activity, PRIMARY_AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                if (hasHandledResponse) return
                hasHandledResponse = true
                mainHandler.removeCallbacks(timeoutRunnable)
                isLoadingAd = false
                if (loadingDialog.isShowing) {
                    try { loadingDialog.dismiss() } catch (ignored: Exception) {}
                }
                displayRewardedAd(activity, rewardedAd, onRewardEarned)
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.w(TAG, "Real ad load failed (${loadAdError.message}). Skipping ad and continuing action.")
                if (hasHandledResponse) return
                hasHandledResponse = true
                mainHandler.removeCallbacks(timeoutRunnable)
                isLoadingAd = false
                if (loadingDialog.isShowing) {
                    try { loadingDialog.dismiss() } catch (ignored: Exception) {}
                }
                // Schedule background preload for later
                preload(activity.applicationContext)
                // Skip the ad and continue directly!
                onRewardEarned()
            }
        })
    }

    private fun displayRewardedAd(
        activity: Activity,
        rewardedAd: RewardedAd,
        onRewardEarned: () -> Unit
    ) {
        var userEarnedReward = false

        rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed. Reward earned: $userEarnedReward")
                cachedRewardedAd = null
                preload(activity.applicationContext)

                if (userEarnedReward) {
                    mainHandler.post {
                        onRewardEarned()
                    }
                } else {
                    mainHandler.post {
                        Toast.makeText(
                            activity,
                            "⚠️ You must watch the complete ad to unlock this action.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Failed to show rewarded ad: ${adError.message}. Skipping and continuing.")
                cachedRewardedAd = null
                preload(activity.applicationContext)
                // If showing fails, do not block the user - skip and proceed
                mainHandler.post {
                    onRewardEarned()
                }
            }
        }

        rewardedAd.show(activity) { _ ->
            Log.d(TAG, "User earned reward!")
            userEarnedReward = true
        }
    }

    private fun createThemedLoadingDialog(activity: Activity): AlertDialog {
        return AlertDialog.Builder(activity, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("Loading Sponsor")
            .setMessage("Please wait a moment...")
            .setCancelable(false)
            .create()
    }
}
