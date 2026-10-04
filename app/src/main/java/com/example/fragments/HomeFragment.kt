package com.example.fragments

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.ui.GlassmorphicDialogHelper
import com.example.ui.PermissionPromptType
import com.example.utils.FreezeManager
import com.example.utils.PanelFileManager
import com.example.utils.RenameUtil
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnActivate: Button
    private lateinit var btnTogglePower: Button
    private lateinit var btnOpenGame: Button
    private lateinit var btnBoost: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvPing: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvFps: TextView
    private lateinit var tvPlaytimeToday: TextView
    private lateinit var tvPlaytimeYesterday: TextView
    private lateinit var tvGameStatus: TextView
    private var btnCopyLog: Button? = null

    private var isOn: Boolean = false
    private var countDownTimer: CountDownTimer? = null
    private var statusAnimator: ObjectAnimator? = null
    private var glowAnimator: ObjectAnimator? = null

    // AdMob Rewarded Ad
    private var rewardedAd: RewardedAd? = null
    private var isAdLoading: Boolean = false
    private val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    private val freezeListener = object : FreezeManager.FreezeStateListener {
        override fun onFreezeStateChanged(isFrozen: Boolean, reason: String) {
            activity?.runOnUiThread {
                if (isFrozen) {
                    Toast.makeText(context, "❄️ Server is frozen: $reason", Toast.LENGTH_SHORT).show()
                }
                updateUIState()
            }
        }
    }

    private val pingHandler = Handler(Looper.getMainLooper())
    private val pingRunnable = object : Runnable {
        override fun run() {
            Thread {
                var pingMs = -1L
                try {
                    val start = System.currentTimeMillis()
                    val socket = Socket()
                    socket.connect(InetSocketAddress("8.8.8.8", 53), 1500)
                    socket.close()
                    pingMs = System.currentTimeMillis() - start
                } catch (_: Exception) {}

                activity?.runOnUiThread {
                    if (isAdded && view != null) {
                        if (pingMs in 0..999) {
                            tvPing.text = "$pingMs ms"
                            tvPing.setTextColor(
                                when {
                                    pingMs < 60 -> Color.parseColor("#00E676")
                                    pingMs < 120 -> Color.parseColor("#00E5FF")
                                    else -> Color.parseColor("#FF5252")
                                }
                            )
                        } else {
                            tvPing.text = "-- ms"
                            tvPing.setTextColor(Color.parseColor("#64748B"))
                        }
                    }
                }
            }.start()
            pingHandler.postDelayed(this, 10000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        tvStatus = view.findViewById(R.id.tv_status)
        tvTimer = view.findViewById(R.id.tv_timer)
        btnActivate = view.findViewById(R.id.btn_activate)
        btnTogglePower = view.findViewById(R.id.btn_toggle_power)
        btnOpenGame = view.findViewById(R.id.btn_open_game)
        btnBoost = view.findViewById(R.id.btn_boost)
        progressBar = view.findViewById(R.id.progress_bar)
        tvProgress = view.findViewById(R.id.tv_progress)
        tvVersion = view.findViewById(R.id.tv_version)
        tvPing = view.findViewById(R.id.tv_ping)
        tvBattery = view.findViewById(R.id.tv_battery)
        tvFps = view.findViewById(R.id.tv_fps)
        tvPlaytimeToday = view.findViewById(R.id.tv_playtime_today)
        tvPlaytimeYesterday = view.findViewById(R.id.tv_playtime_yesterday)
        tvGameStatus = view.findViewById(R.id.tv_game_status)
        btnCopyLog = view.findViewById(R.id.btn_copy_log)

        // Version display
        try {
            val vIn = resources.openRawResource(R.raw.version)
            val reader = BufferedReader(InputStreamReader(vIn))
            tvVersion.text = "v" + reader.readLine().trim()
            reader.close()
        } catch (_: Exception) {
            tvVersion.text = "v1.0"
        }

        // Activate button
        btnActivate.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (FreezeManager.isCurrentlyFrozen(ctx)) {
                val reason = FreezeManager.getFreezeReason(ctx)
                Toast.makeText(ctx, "❄️ Server is currently frozen by admin:\n$reason", Toast.LENGTH_LONG).show()
            } else {
                startDownloadActivation()
            }
        }

        // Toggle power button
        btnTogglePower.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (FreezeManager.isCurrentlyFrozen(ctx)) {
                val reason = FreezeManager.getFreezeReason(ctx)
                Toast.makeText(ctx, "❄️ Action blocked: Server is frozen by admin.\n$reason", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (isOn) {
                handleTurnOff()
            } else {
                handleTurnOn()
            }
        }

        // Boost button
        btnBoost.setOnClickListener {
            Thread {
                if (RenameUtil.useShizukuOps && RenameUtil.shizukuAvailable()) {
                    val success = RenameUtil.executeShizukuCommand("pm trim-caches 999999G")
                    activity?.runOnUiThread {
                        if (success) {
                            Toast.makeText(context, "⚡ Device Boosted! Caches cleared.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to clear caches: ${RenameUtil.lastError}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Shizuku is required for Lag Fix / Boost feature.", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        // OPEN GAME BUTTON with Rewarded Test Ad & Rate Limiting
        btnOpenGame.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (FreezeManager.isCurrentlyFrozen(ctx)) {
                val reason = FreezeManager.getFreezeReason(ctx)
                Toast.makeText(ctx, "❄️ Cannot open game: Server is currently frozen by admin.\n$reason", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val launchIntent = requireContext().packageManager.getLaunchIntentForPackage("com.dts.freefiremax")
            if (launchIntent == null) {
                Toast.makeText(ctx, "Free Fire MAX not found on this device", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val proceedToGameLaunch = Runnable {
                executeGameLaunchSequence(launchIntent)
            }

            // Issue i): Never show ads for ADMIN users
            if (isAdminUser()) {
                proceedToGameLaunch.run()
                return@setOnClickListener
            }

            // Issue iii): Rate limit ads to 2 minutes. Game opens directly within 2 minutes.
            val prefs = requireActivity().getSharedPreferences("xcel_prefs", Context.MODE_PRIVATE)
            val lastAdTime = prefs.getLong("last_ad_shown_time", 0L)
            val currentTime = System.currentTimeMillis()
            val twoMinutesMillis = 2 * 60 * 1000L // 2 minutes

            if (currentTime - lastAdTime < twoMinutesMillis) {
                // Within 2 minutes: open game directly without showing ad
                proceedToGameLaunch.run()
                return@setOnClickListener
            }

            // Check if Rewarded Ad is loaded
            val ad = rewardedAd
            if (ad != null) {
                Toast.makeText(ctx, "🎬 Showing rewarded ad before opening game...", Toast.LENGTH_SHORT).show()
                rewardedAd = null // Consume current ad
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        prefs.edit().putLong("last_ad_shown_time", System.currentTimeMillis()).apply()
                        loadRewardedAd() // Preload for next time
                        proceedToGameLaunch.run()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        loadRewardedAd()
                        proceedToGameLaunch.run()
                    }
                }
                ad.show(requireActivity()) { rewardItem ->
                    prefs.edit().putLong("last_ad_shown_time", System.currentTimeMillis()).apply()
                    Toast.makeText(ctx, "🎁 Reward granted! Opening Free Fire MAX...", Toast.LENGTH_SHORT).show()
                }
            } else {
                // If ad is not ready yet, preload it and proceed to open game without blocking user
                loadRewardedAd()
                proceedToGameLaunch.run()
            }
        }

        // Refresh button
        view.findViewById<ImageButton?>(R.id.btn_refresh)?.setOnClickListener {
            updateUIState()
            fetchSystemStats(view)
            Toast.makeText(context, "Refreshed ✓", Toast.LENGTH_SHORT).show()
        }

        // Copy log button
        btnCopyLog?.setOnClickListener {
            copyDiagnosticsLogToClipboard()
        }

        // Help / tutorial button
        view.findViewById<Button?>(R.id.btn_not_working)?.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            AlertDialog.Builder(ctx, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setTitle("App Not Working?")
                .setMessage("If you are facing issues, you can follow the tutorial, check system diagnostics, or contact admin for help.")
                .setPositiveButton("Tutorial") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/"))
                    startActivity(intent)
                }
                .setNegativeButton("Diagnostics") { _, _ ->
                    showDiagnosticsDialog()
                }
                .setNeutralButton("Close", null)
                .show()
        }

        // Social links
        view.findViewById<Button?>(R.id.btn_telegram)?.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/")))
            } catch (_: Exception) {}
        }
        view.findViewById<Button?>(R.id.btn_instagram)?.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/")))
            } catch (_: Exception) {}
        }

        fetchSystemStats(view)
        loadRewardedAd()
        return view
    }

    private fun isAdminUser(): Boolean {
        val ctx = context ?: activity ?: return false
        val prefs = ctx.getSharedPreferences("xcel_prefs", Context.MODE_PRIVATE)
        val userType = prefs.getString("user_type", "") ?: ""
        val savedKey = prefs.getString("saved_key", "") ?: ""
        return userType.equals("ADMIN", ignoreCase = true) ||
                savedKey.equals("mujahi@admin", ignoreCase = true) ||
                savedKey.contains("admin", ignoreCase = true)
    }

    fun loadRewardedAd() {
        if (isAdminUser()) {
            rewardedAd = null
            return
        }
        if (rewardedAd != null || isAdLoading) return
        val ctx = context ?: return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            ctx,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (isAdminUser()) {
                        rewardedAd = null
                    } else {
                        rewardedAd = ad
                    }
                    isAdLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isAdLoading = false
                }
            }
        )
    }

    private fun executeGameLaunchSequence(launchIntent: Intent) {
        val prefs = requireActivity().getSharedPreferences("xcel_prefs", Context.MODE_PRIVATE)
        val userType = prefs.getString("user_type", null)

        if (userType == "NORMAL") {
            val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = appOps?.unsafeCheckOpNoThrow(
                "android:get_usage_stats",
                Process.myUid(),
                requireContext().packageName
            ) ?: 0

            if (mode != 0) {
                GlassmorphicDialogHelper.showPermissionPrompt(
                    requireActivity(),
                    PermissionPromptType.USAGE_ACCESS,
                    onPrimary = {
                        startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onSecondary = {
                        showUidSelectionDialog(launchIntent)
                    }
                )
                return
            }
            showUidSelectionDialog(launchIntent)
            return
        }
        startActivity(launchIntent)
    }

    private fun showUidSelectionDialog(launchIntent: Intent) {
        startActivity(launchIntent)
    }

    fun updateUIState() {
        val ctx = context ?: return
        Thread {
            val isStaged = PanelFileManager.areFilesStaged(ctx)
            val isModActive = if (isStaged) PanelFileManager.isModOn(ctx) else false
            val isFrozen = FreezeManager.isCurrentlyFrozen(ctx)

            activity?.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                isOn = isModActive

                statusAnimator?.cancel()
                glowAnimator?.cancel()
                tvStatus.scaleX = 1.0f
                tvStatus.scaleY = 1.0f

                when {
                    isFrozen -> {
                        btnActivate.visibility = View.GONE
                        btnTogglePower.visibility = View.VISIBLE
                        btnTogglePower.isEnabled = false
                        btnTogglePower.text = "FROZEN ❄️"
                        btnTogglePower.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E2738"))
                        tvStatus.text = "Server Frozen: ${FreezeManager.getFreezeReason(ctx)}"
                        tvStatus.setTextColor(Color.parseColor("#FF5252"))
                    }
                    !isStaged -> {
                        btnActivate.visibility = View.VISIBLE
                        btnActivate.isEnabled = true
                        btnTogglePower.visibility = View.GONE
                        tvStatus.text = "Status: NOT ACTIVATED ⚠️"
                        tvStatus.setTextColor(Color.parseColor("#FFD740"))
                    }
                    isModActive -> {
                        btnActivate.visibility = View.GONE
                        btnTogglePower.visibility = View.VISIBLE
                        btnTogglePower.isEnabled = true
                        btnTogglePower.text = "TURN OFF"
                        btnTogglePower.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF5252"))
                        tvStatus.text = "Status: ACTIVATED 🟢"
                        tvStatus.setTextColor(Color.parseColor("#00E676"))
                        startTimerUpdate()
                    }
                    else -> {
                        btnActivate.visibility = View.GONE
                        btnTogglePower.visibility = View.VISIBLE
                        btnTogglePower.isEnabled = true
                        btnTogglePower.text = "TURN ON"
                        btnTogglePower.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                        tvStatus.text = "Status: READY (OFF) ⚪"
                        tvStatus.setTextColor(Color.parseColor("#94A3B8"))
                        tvTimer.text = "00:00:00"
                        countDownTimer?.cancel()
                    }
                }
            }
        }.start()
    }

    private fun handleTurnOn() {
        val activity = activity as? MainActivity ?: return
        btnTogglePower.isEnabled = false
        activity.executeTurnOnGlobal { success ->
            btnTogglePower.isEnabled = true
            if (success) {
                Toast.makeText(context, "Mod turned ON 🟢", Toast.LENGTH_SHORT).show()
                startTimerUpdate()
            } else {
                val err = RenameUtil.lastError.ifEmpty { PanelFileManager.lastLog.ifEmpty { "Check destination folders." } }
                Toast.makeText(context, "Failed to turn ON:\n$err", Toast.LENGTH_LONG).show()
                showErrorWithCopyButton("Turn ON Issue", "Could not turn ON mod:\n$err")
            }
            updateUIState()
        }
    }

    private fun handleTurnOff() {
        val activity = activity as? MainActivity ?: return
        btnTogglePower.isEnabled = false
        activity.executeTurnOffGlobal { err ->
            btnTogglePower.isEnabled = true
            val isSuccess = err == null || err.equals("SUCCESS", ignoreCase = true) || err.trim().isEmpty()
            if (isSuccess) {
                Toast.makeText(context, "Mod turned OFF 🔴", Toast.LENGTH_SHORT).show()
                countDownTimer?.cancel()
                tvTimer.text = "00:00:00"
            } else {
                Toast.makeText(context, "Failed to turn OFF:\n$err", Toast.LENGTH_LONG).show()
                showErrorWithCopyButton("Turn OFF Issue", "Could not turn OFF mod:\n$err")
            }
            updateUIState()
        }
    }

    fun startDownloadActivation() {
        val ctx = context ?: return
        if (FreezeManager.isCurrentlyFrozen(ctx)) {
            val reason = FreezeManager.getFreezeReason(ctx)
            Toast.makeText(ctx, "❄️ Server is currently frozen by admin:\n$reason", Toast.LENGTH_LONG).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        tvProgress.visibility = View.VISIBLE
        tvProgress.text = "Connecting to Firebase..."
        btnActivate.isEnabled = false

        PanelFileManager.fetchManifestFromFirestore(
            ctx,
            onSuccess = { manifest ->
                progressBar.isIndeterminate = false
                PanelFileManager.downloadAndStageAllFiles(
                    ctx,
                    manifest,
                    onProgress = { status, percent ->
                        activity?.runOnUiThread {
                            tvProgress.text = "$status ($percent%)"
                            progressBar.progress = percent
                        }
                    },
                    onComplete = { success, errorMsg ->
                        activity?.runOnUiThread {
                            progressBar.visibility = View.GONE
                            tvProgress.visibility = View.GONE
                            btnActivate.isEnabled = true
                            if (success) {
                                Toast.makeText(ctx, "All files staged and verified! ✓", Toast.LENGTH_SHORT).show()
                                updateUIState()
                            } else {
                                val err = errorMsg ?: "Unknown error"
                                Toast.makeText(ctx, "Activation failed: $err", Toast.LENGTH_LONG).show()
                                showErrorWithCopyButton("Activation Failed", err)
                            }
                        }
                    }
                )
            },
            onError = { err ->
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                    btnActivate.isEnabled = true
                    Toast.makeText(ctx, "Could not fetch configuration: $err", Toast.LENGTH_LONG).show()
                    showErrorWithCopyButton("Download Error", "Could not load settings:\n$err")
                }
            }
        )
    }

    private fun startTimerUpdate() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(3600000, 1000) {
            var elapsedSeconds = 0L
            override fun onTick(millisUntilFinished: Long) {
                elapsedSeconds++
                val hours = elapsedSeconds / 3600
                val minutes = (elapsedSeconds % 3600) / 60
                val seconds = elapsedSeconds % 60
                tvTimer.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            }

            override fun onFinish() {
                tvTimer.text = "00:00:00"
            }
        }.start()
    }

    private fun fetchSystemStats(view: View) {
        val ctx = context ?: return

        // Battery
        try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (level >= 0) {
                tvBattery.text = "$level%"
            } else {
                tvBattery.text = "-- %"
            }
        } catch (_: Exception) {
            tvBattery.text = "-- %"
        }

        // FPS / Refresh rate
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity?.display
            } else {
                @Suppress("DEPRECATION")
                activity?.windowManager?.defaultDisplay
            }
            val refreshRate = display?.refreshRate?.toInt() ?: 60
            tvFps.text = "$refreshRate Hz"
        } catch (_: Exception) {
            tvFps.text = "60 Hz"
        }

        // Game status
        tvGameStatus.text = if (isOn) "Active" else "Inactive"
        tvGameStatus.setTextColor(
            if (isOn) Color.parseColor("#00E676") else Color.parseColor("#FF5252")
        )
    }

    private fun showDiagnosticsDialog() {
        val log = getDiagnosticLog()
        AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("System Diagnostics & Log")
            .setMessage(log)
            .setPositiveButton("OK", null)
            .setNeutralButton("COPY LOG") { _, _ ->
                copyToClipboard(log, "System Diagnostics")
            }
            .show()
    }

    private fun showErrorWithCopyButton(title: String, message: String) {
        val fullLog = "$title\n$message\n\n--- System Diagnostics ---\n${getDiagnosticLog()}"
        AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("COPY DIAGNOSTICS") { _, _ ->
                copyToClipboard(fullLog, "$title Log")
            }
            .show()
    }

    private fun getDiagnosticLog(): String {
        val sb = StringBuilder()
        sb.append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append("\n")
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        context?.let { ctx ->
            sb.append("Staged: ").append(PanelFileManager.areFilesStaged(ctx)).append("\n")
            sb.append("ModOn: ").append(PanelFileManager.isModOn(ctx)).append("\n")
            sb.append("Frozen: ").append(FreezeManager.isCurrentlyFrozen(ctx)).append("\n")
        }
        sb.append("Shizuku: ").append(RenameUtil.shizukuAvailable()).append("\n")
        sb.append("Last Log: ").append(PanelFileManager.lastLog).append("\n")
        return sb.toString()
    }

    private fun copyDiagnosticsLogToClipboard() {
        val log = getDiagnosticLog()
        copyToClipboard(log, "Diagnostics Log")
    }

    private fun copyToClipboard(text: String, label: String = "Log") {
        try {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
            Toast.makeText(context, "Log copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy log: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        FreezeManager.registerListener(freezeListener)
        updateUIState()
        view?.let { fetchSystemStats(it) }
        pingHandler.post(pingRunnable)
        loadRewardedAd()
    }

    override fun onPause() {
        super.onPause()
        FreezeManager.unregisterListener(freezeListener)
        pingHandler.removeCallbacks(pingRunnable)
        countDownTimer?.cancel()
    }
}
