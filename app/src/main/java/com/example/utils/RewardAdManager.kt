package com.example.utils

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.MainActivity
import com.example.R
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object RewardAdManager {

    private const val TAG = "RewardAdManager"
    const val AD_UNIT_ID = "ca-app-pub-4490063343359332/1223494398"

    private var cachedRewardedAd: RewardedAd? = null
    private var isLoadingAd = false
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            MobileAds.initialize(context) {
                Log.d(TAG, "Google Mobile Ads SDK initialized.")
                isInitialized = true
                preload(context.applicationContext)
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

    fun preload(context: Context) {
        if (isAdmin(context)) return
        if (cachedRewardedAd != null || isLoadingAd) return

        isLoadingAd = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d(TAG, "Rewarded ad preloaded successfully.")
                cachedRewardedAd = rewardedAd
                isLoadingAd = false
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.w(TAG, "Rewarded ad failed to preload: ${loadAdError.message}")
                cachedRewardedAd = null
                isLoadingAd = false
            }
        })
    }

    /**
     * Executes the target action with rewarded ad gating for normal users.
     * Admin users bypass the ad completely.
     * Normal users must complete the video to earn the reward and execute the action.
     * If the ad fails to load, an alert dialog informs the user to retry.
     */
    fun showRewardedAdForAction(activity: Activity, onRewardEarned: () -> Unit) {
        // 1. Admin bypass check
        if (isAdmin(activity)) {
            Log.d(TAG, "User is ADMIN, bypassing rewarded ad.")
            onRewardEarned()
            return
        }

        // 2. If already cached, show immediately
        val ad = cachedRewardedAd
        if (ad != null) {
            displayRewardedAd(activity, ad, onRewardEarned)
            return
        }

        // 3. Otherwise, show cyber loading dialog and fetch ad
        val loadingDialog = createThemedLoadingDialog(activity)
        loadingDialog.show()

        val adRequest = AdRequest.Builder().build()
        isLoadingAd = true
        RewardedAd.load(activity, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                isLoadingAd = false
                if (loadingDialog.isShowing) {
                    try { loadingDialog.dismiss() } catch (ignored: Exception) {}
                }
                displayRewardedAd(activity, rewardedAd, onRewardEarned)
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                isLoadingAd = false
                if (loadingDialog.isShowing) {
                    try { loadingDialog.dismiss() } catch (ignored: Exception) {}
                }
                showRetryAlertDialog(
                    activity,
                    "Ad could not be loaded (${loadAdError.message}). Please check your internet connection and try again to proceed."
                )
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
                    Handler(Looper.getMainLooper()).post {
                        onRewardEarned()
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
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
                    "Could not display ad (${adError.message}). Please retry."
                )
            }
        }

        rewardedAd.show(activity) { _ ->
            Log.d(TAG, "User earned reward!")
            userEarnedReward = true
        }
    }

    private fun createThemedLoadingDialog(activity: Activity): AlertDialog {
        val dialog = AlertDialog.Builder(activity, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("Loading Video Ad")
            .setMessage("Please wait while the sponsor video loads...")
            .setCancelable(false)
            .create()
        return dialog
    }

    private fun showRetryAlertDialog(activity: Activity, message: String) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            AlertDialog.Builder(activity, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setTitle("Ad Loading Failed")
                .setMessage(message)
                .setPositiveButton("RETRY", null)
                .show()
        }
    }
}
