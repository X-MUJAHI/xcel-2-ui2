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
    // Official Google AdMob sample test unit for rewarded video ads (guaranteed 100% fill rate)
    const val FALLBACK_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

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
     * Preloads a rewarded ad with intelligent fallback and exponential backoff retry.
     */
    fun preload(context: Context) {
        if (isAdmin(context)) return
        if (cachedRewardedAd != null || isLoadingAd) return

        isLoadingAd = true
        val adRequest = AdRequest.Builder().build()
        val appContext = context.applicationContext

        // Attempt primary ad unit first
        RewardedAd.load(appContext, PRIMARY_AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d(TAG, "Primary rewarded ad preloaded successfully.")
                cachedRewardedAd = rewardedAd
                isLoadingAd = false
                retryAttempt = 0
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.w(TAG, "Primary rewarded ad failed to preload: ${loadAdError.message} (code ${loadAdError.code}). Attempting fallback test unit...")
                // Attempt fallback to ensure user is never blocked by No Fill / Inventory shortages
                RewardedAd.load(appContext, FALLBACK_TEST_AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        Log.d(TAG, "Fallback test rewarded ad preloaded successfully.")
                        cachedRewardedAd = rewardedAd
                        isLoadingAd = false
                        retryAttempt = 0
                    }

                    override fun onAdFailedToLoad(fallbackError: LoadAdError) {
                        Log.e(TAG, "Fallback rewarded ad also failed: ${fallbackError.message}")
                        cachedRewardedAd = null
                        isLoadingAd = false
                        schedulePreloadRetry(appContext)
                    }
                })
            }
        })
    }

    private fun schedulePreloadRetry(context: Context) {
        if (isRetryingPreload || cachedRewardedAd != null || isAdmin(context)) return
        isRetryingPreload = true

        val delays = longArrayOf(3000L, 6000L, 12000L, 20000L)
        val delay = delays.getOrElse(retryAttempt) { 20000L }
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
     * Otherwise displays a loading dialog with timeout and attempts live load (primary -> fallback).
     */
    fun showRewardedAdForAction(activity: Activity, onRewardEarned: () -> Unit) {
        // 1. Admin bypass check
        if (isAdmin(activity)) {
            Log.d(TAG, "User is ADMIN, bypassing rewarded ad.")
            onRewardEarned()
            return
        }

        // 2. If cached ad is ready, show immediately
        val ad = cachedRewardedAd
        if (ad != null) {
            cachedRewardedAd = null
            displayRewardedAd(activity, ad, onRewardEarned)
            return
        }

        // 3. Otherwise show themed loading dialog and fetch fresh ad
        val loadingDialog = createThemedLoadingDialog(activity)
        loadingDialog.show()

        val adRequest = AdRequest.Builder().build()
        isLoadingAd = true

        var hasHandledResponse = false

        // Timeout handler (12 seconds) to prevent infinite loading state
        val timeoutRunnable = Runnable {
            if (!hasHandledResponse && !activity.isFinishing && !activity.isDestroyed) {
                hasHandledResponse = true
                isLoadingAd = false
                if (loadingDialog.isShowing) {
                    try { loadingDialog.dismiss() } catch (ignored: Exception) {}
                }
                showRetryAlertDialog(
                    activity,
                    "Ad loading timed out. Please check your network connection and tap Retry.",
                    onRewardEarned
                )
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 12000L)

        // Try primary live ad unit
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
                Log.w(TAG, "Primary ad load failed (${loadAdError.message}). Attempting fallback unit...")
                if (hasHandledResponse) return

                // Immediate fallback to test ad unit
                RewardedAd.load(activity, FALLBACK_TEST_AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
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

                    override fun onAdFailedToLoad(fallbackError: LoadAdError) {
                        if (hasHandledResponse) return
                        hasHandledResponse = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        isLoadingAd = false
                        if (loadingDialog.isShowing) {
                            try { loadingDialog.dismiss() } catch (ignored: Exception) {}
                        }
                        showRetryAlertDialog(
                            activity,
                            "Ad could not be loaded (${fallbackError.message}). Please check your internet connection and try again.",
                            onRewardEarned
                        )
                    }
                })
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
                Log.e(TAG, "Failed to show rewarded ad: ${adError.message}")
                cachedRewardedAd = null
                preload(activity.applicationContext)
                showRetryAlertDialog(
                    activity,
                    "Could not display ad (${adError.message}). Please retry.",
                    onRewardEarned
                )
            }
        }

        rewardedAd.show(activity) { _ ->
            Log.d(TAG, "User earned reward!")
            userEarnedReward = true
        }
    }

    private fun createThemedLoadingDialog(activity: Activity): AlertDialog {
        return AlertDialog.Builder(activity, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("Loading Video Ad")
            .setMessage("Please wait while the video sponsor loads...")
            .setCancelable(false)
            .create()
    }

    private fun showRetryAlertDialog(
        activity: Activity,
        message: String,
        onRetryAction: (() -> Unit)? = null
    ) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            val builder = AlertDialog.Builder(activity, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setTitle("Ad Loading Failed")
                .setMessage(message)
                .setNegativeButton("Cancel", null)

            if (onRetryAction != null) {
                builder.setPositiveButton("RETRY NOW") { _, _ ->
                    showRewardedAdForAction(activity, onRetryAction)
                }
            } else {
                builder.setPositiveButton("OK", null)
            }
            builder.show()
        }
    }
}
