package com.example.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
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

    private var isOn = false
    private var countDownTimer: CountDownTimer? = null
    private var statusAnimator: android.animation.ObjectAnimator? = null
    private var glowAnimator: android.animation.ObjectAnimator? = null

    private val freezeListener = object : FreezeManager.FreezeStateListener {
        override fun onFreezeStateChanged(isFrozen: Boolean, reason: String) {
            activity?.runOnUiThread {
                if (isAdded) {
                    if (isFrozen) {
                        Toast.makeText(context, "❄️ Server frozen by admin:\n$reason", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "✨ Server unfrozen by admin!", Toast.LENGTH_SHORT).show()
                    }
                    updateUIState()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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

        try {
            val vers = resources.openRawResource(R.raw.version).bufferedReader().use { it.readText() }.trim()
            tvVersion.text = "v$vers"
        } catch (e: Exception) {
            tvVersion.text = "v1.0"
        }

        tvStatus.setOnClickListener {
            showDiagnosticsDialog()
        }

        btnCopyLog?.setOnClickListener {
            copyDiagnosticsLogToClipboard()
        }

        view.findViewById<Button>(R.id.btn_telegram)?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/xcel1_panel")))
        }

        view.findViewById<Button>(R.id.btn_instagram)?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/x_celestials")))
        }

        view.findViewById<Button>(R.id.btn_not_working)?.setOnClickListener {
            AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setTitle("App Not Working?")
                .setMessage("If you are facing issues, you can follow the tutorial, check system diagnostics, or contact admin for help.")
                .setPositiveButton("Tutorial") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://x-link.vercel.app/hologram-tutorial")))
                }
                .setNeutralButton("Copy Log") { _, _ ->
                    copyDiagnosticsLogToClipboard()
                }
                .setNegativeButton("Contact Admin") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/x_celestials")))
                }
                .show()
        }

        btnActivate.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (FreezeManager.isCurrentlyFrozen(ctx)) {
                val reason = FreezeManager.getFreezeReason(ctx)
                Toast.makeText(ctx, "❄️ Server is currently frozen by admin:\n$reason", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startDownloadActivation()
        }

        btnTogglePower.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (FreezeManager.isCurrentlyFrozen(ctx)) {
                val reason = FreezeManager.getFreezeReason(ctx)
                Toast.makeText(ctx, "❄️ Server is currently frozen by admin:\n$reason", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (isOn) {
                handleTurnOff()
            } else {
                handleTurnOn()
            }
        }

        btnBoost.setOnClickListener {
            btnBoost.animate()
                .scaleX(0.92f).scaleY(0.92f)
                .setDuration(100)
                .withEndAction {
                    btnBoost.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()

            Thread {
                if (RenameUtil.useShizukuOps && RenameUtil.shizukuAvailable()) {
                    val success = RenameUtil.executeShizukuCommand("pm trim-caches 999999G")
                    requireActivity().runOnUiThread {
                        if (success) {
                            Toast.makeText(context, "⚡ Device Boosted! Caches cleared.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to clear caches: ${RenameUtil.lastError}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    requireActivity().runOnUiThread {
                        Toast.makeText(context, "Shizuku is required for Lag Fix / Boost feature.", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        btnOpenGame.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (FreezeManager.isCurrentlyFrozen(ctx)) {
                val reason = FreezeManager.getFreezeReason(ctx)
                Toast.makeText(ctx, "❄️ Cannot open game: Server is currently frozen by admin.\n$reason", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
            val userType = prefs.getString(MainActivity.KEY_USER_TYPE, null)
            val launchIntent = requireContext().packageManager.getLaunchIntentForPackage("com.dts.freefiremax")

            if (launchIntent == null) {
                Toast.makeText(ctx, "Free Fire MAX not found on this device", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (userType == "NORMAL") {
                val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), requireContext().packageName
                )
                if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
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
                    return@setOnClickListener
                }
                showUidSelectionDialog(launchIntent)
            } else {
                startActivity(launchIntent)
            }
        }

        view.findViewById<ImageButton>(R.id.btn_refresh)?.setOnClickListener {
            updateUIState()
            fetchSystemStats(view)
            Toast.makeText(context, "Refreshed ✓", Toast.LENGTH_SHORT).show()
        }

        fetchSystemStats(view)
        return view
    }

    private fun copyToClipboard(text: String, label: String = "Log") {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Log copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy log: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getDiagnosticLog(): String {
        val ctx = context ?: return "Context unavailable"
        val isStaged = PanelFileManager.areFilesStaged(ctx)
        val (t1, t2) = PanelFileManager.getTargetPaths(ctx)
        val d1Exists = RenameUtil.checkDirExists(t1.trimEnd('/'))
        val d2Exists = RenameUtil.checkDirExists(t2.trimEnd('/'))
        val shizukuOk = RenameUtil.shizukuAvailable()
        val isModActive = PanelFileManager.isModOn(ctx)
        val f1Name = PanelFileManager.getFile1Name(ctx)
        val f2Name = PanelFileManager.getFile2Name(ctx)

        val sb = StringBuilder()
        sb.append("=== X-CELESTIALS DIAGNOSTICS LOG ===\n")
        sb.append("Timestamp: ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())).append("\n")
        sb.append("Mod State: ").append(if (isModActive) "ON 🟢" else "OFF 🔴").append("\n")
        sb.append("Files Activated (Staged): ").append(isStaged).append("\n")
        sb.append("File 1 Name: ").append(f1Name).append("\n")
        sb.append("File 2 Name: ").append(f2Name).append("\n")
        sb.append("Shizuku Available & Authorized: ").append(shizukuOk).append("\n")
        sb.append("Target Path 1: ").append(t1).append(" (Exists: ").append(d1Exists).append(")\n")
        sb.append("Target Path 2: ").append(t2).append(" (Exists: ").append(d2Exists).append(")\n")
        sb.append("Last Panel Log: ").append(PanelFileManager.lastLog.ifEmpty { "None" }).append("\n")
        sb.append("Last Rename/Shizuku Error: ").append(RenameUtil.lastError.ifEmpty { "None" }).append("\n")
        sb.append("=====================================")
        return sb.toString()
    }

    private fun copyDiagnosticsLogToClipboard() {
        val log = getDiagnosticLog()
        copyToClipboard(log, "X-Celestials Diagnostics")
    }

    private fun showDiagnosticsDialog() {
        Thread {
            val log = getDiagnosticLog()
            requireActivity().runOnUiThread {
                AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                    .setTitle("System Diagnostics & Log")
                    .setMessage(log)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("COPY LOG") { _, _ ->
                        copyToClipboard(log, "System Diagnostics")
                    }
                    .setNegativeButton("Re-Download Files") { _, _ ->
                        startDownloadActivation()
                    }
                    .show()
            }
        }.start()
    }

    private fun showErrorWithCopyButton(title: String, message: String) {
        val diag = getDiagnosticLog()
        val fullLog = "$message\n\n$diag"
        AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("COPY LOG") { _, _ ->
                copyToClipboard(fullLog, "$title Log")
            }
            .show()
    }

    private val pingHandler = Handler(Looper.getMainLooper())
    private val pingRunnable = object : Runnable {
        override fun run() {
            Thread {
                try {
                    val process = Runtime.getRuntime().exec("ping -c 1 -w 2 8.8.8.8")
                    val returnVal = process.waitFor()
                    if (returnVal == 0) {
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                        var time = "--"
                        for (line in reader.lines()) {
                            if (line.contains("time=")) {
                                time = line.substringAfter("time=").substringBefore(" ")
                                break
                            }
                        }
                        requireActivity().runOnUiThread { tvPing.text = "${time}ms" }
                    } else {
                        requireActivity().runOnUiThread { tvPing.text = "Error" }
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread { tvPing.text = "--" }
                }
            }.start()
            pingHandler.postDelayed(this, 4000)
        }
    }

    override fun onResume() {
        super.onResume()
        FreezeManager.registerListener(freezeListener)
        updateUIState()
        view?.let { fetchSystemStats(it) }
        startTimerUpdate()
        pingHandler.post(pingRunnable)
    }

    override fun onPause() {
        super.onPause()
        FreezeManager.unregisterListener(freezeListener)
        countDownTimer?.cancel()
        statusAnimator?.cancel()
        glowAnimator?.cancel()
        pingHandler.removeCallbacks(pingRunnable)
    }

    private fun showUidSelectionDialog(launchIntent: Intent) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_uid_selection, null)
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        val listView = dialogView.findViewById<android.widget.ListView>(R.id.list_uids)
        val pBar = dialogView.findViewById<ProgressBar>(R.id.progress_loading_uids)
        val tvNoUids = dialogView.findViewById<TextView>(R.id.tv_no_uids)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_dialog)

        btnCancel.setOnClickListener { dialog.dismiss() }

        pBar.visibility = View.VISIBLE
        listView.visibility = View.GONE
        tvNoUids.visibility = View.GONE
        dialog.show()

        Thread {
            val uids = RenameUtil.getGameUIDs()
            requireActivity().runOnUiThread {
                pBar.visibility = View.GONE
                if (uids.isEmpty()) {
                    tvNoUids.visibility = View.VISIBLE
                } else {
                    listView.visibility = View.VISIBLE
                    val adapter = android.widget.ArrayAdapter(requireContext(), R.layout.item_uid, android.R.id.text1, uids)
                    listView.adapter = adapter
                    listView.setOnItemClickListener { _, _, _, _ ->
                        dialog.dismiss()
                        executeGameLaunchSequence(launchIntent)
                    }
                }
            }
        }.start()
    }

    private fun executeGameLaunchSequence(launchIntent: Intent) {
        val startTime = System.currentTimeMillis()
        val progressDialog = AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("Anti-Ban Active")
            .setMessage("Applying protections and launching game...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        val activity = requireActivity() as MainActivity
        activity.executeTurnOnGlobal { success ->
            updateUIState()
            val elapsed = System.currentTimeMillis() - startTime
            val remainingWait = Math.max(0L, 2500L - elapsed)

            Handler(Looper.getMainLooper()).postDelayed({
                progressDialog.dismiss()
                if (success) {
                    Toast.makeText(context, "Anti-Ban applied! Launching...", Toast.LENGTH_SHORT).show()
                    val serviceIntent = Intent(requireContext(), com.example.services.GameMonitorService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(serviceIntent)
                    } else {
                        requireContext().startService(serviceIntent)
                    }
                    startActivity(launchIntent)
                } else {
                    val err = RenameUtil.lastError.ifEmpty { PanelFileManager.lastLog.ifEmpty { "Please activate panel first." } }
                    showErrorWithCopyButton("Launch Error", "Could not turn on protections:\n$err")
                }
            }, remainingWait)
        }
    }

    private fun updateUIState() {
        val ctx = context ?: return
        Thread {
            val isStaged = PanelFileManager.areFilesStaged(ctx)
            val isModActive = if (isStaged) PanelFileManager.isModOn(ctx) else false
            val isFrozen = FreezeManager.isCurrentlyFrozen(ctx)

            requireActivity().runOnUiThread {
                statusAnimator?.cancel()
                glowAnimator?.cancel()
                tvStatus.scaleX = 1f
                tvStatus.scaleY = 1f
                tvStatus.alpha = 1f
                btnTogglePower.scaleX = 1f
                btnTogglePower.scaleY = 1f
                btnTogglePower.alpha = 1f

                if (isFrozen) {
                    isOn = false
                    tvStatus.text = "Status: FROZEN ❄️"
                    tvStatus.setTextColor(Color.parseColor("#00E5FF"))

                    btnTogglePower.text = "FROZEN ❄️"
                    btnTogglePower.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#37474F"))
                    btnTogglePower.setTextColor(Color.parseColor("#90A4AE"))
                    btnTogglePower.isEnabled = false
                    btnTogglePower.visibility = View.VISIBLE
                    btnActivate.visibility = View.GONE
                } else if (!isStaged) {
                    isOn = false
                    btnActivate.visibility = View.VISIBLE
                    btnActivate.isEnabled = true
                    btnTogglePower.visibility = View.GONE

                    tvStatus.text = "Status: NOT ACTIVATED ⚠️"
                    tvStatus.setTextColor(Color.parseColor("#FFD740"))
                } else {
                    btnActivate.visibility = View.GONE
                    btnTogglePower.visibility = View.VISIBLE
                    btnTogglePower.isEnabled = true

                    if (isModActive) {
                        isOn = true
                        tvStatus.text = "Status: ON 🟢"
                        tvStatus.setTextColor(Color.parseColor("#00E676"))

                        btnTogglePower.text = "TURN OFF"
                        btnTogglePower.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF5252"))
                        btnTogglePower.setTextColor(Color.WHITE)

                        statusAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                            tvStatus,
                            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.04f),
                            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.04f)
                        ).apply {
                            duration = 900
                            repeatCount = android.animation.ObjectAnimator.INFINITE
                            repeatMode = android.animation.ObjectAnimator.REVERSE
                            start()
                        }

                        glowAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                            btnTogglePower,
                            android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.85f, 1.0f),
                            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.98f, 1.01f),
                            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.98f, 1.01f)
                        ).apply {
                            duration = 1000
                            repeatCount = android.animation.ObjectAnimator.INFINITE
                            repeatMode = android.animation.ObjectAnimator.REVERSE
                            start()
                        }
                    } else {
                        isOn = false
                        tvStatus.text = "Status: OFF 🔴"
                        tvStatus.setTextColor(Color.parseColor("#FF5252"))

                        btnTogglePower.text = "TURN ON"
                        btnTogglePower.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                        btnTogglePower.setTextColor(Color.parseColor("#0B0E14"))
                    }
                }
            }
        }.start()
    }

    private fun startTimerUpdate() {
        countDownTimer?.cancel()
        tvTimer.text = "Timer: Unlimited"
    }

    private fun startDownloadActivation() {
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
            context = ctx,
            onSuccess = { manifest ->
                requireActivity().runOnUiThread {
                    tvProgress.text = "Downloading 4 files..."
                    progressBar.isIndeterminate = false
                    progressBar.progress = 0
                }

                PanelFileManager.downloadAndStageAllFiles(
                    context = ctx,
                    manifest = manifest,
                    onProgress = { status, percent ->
                        requireActivity().runOnUiThread {
                            tvProgress.text = status
                            progressBar.isIndeterminate = (percent <= 0)
                            progressBar.progress = percent
                        }
                    },
                    onComplete = { success, errorMsg ->
                        requireActivity().runOnUiThread {
                            progressBar.visibility = View.GONE
                            tvProgress.visibility = View.GONE
                            btnActivate.isEnabled = true

                            if (success) {
                                Toast.makeText(ctx, "Activation successful! All 4 files ready.", Toast.LENGTH_LONG).show()
                                updateUIState()
                            } else {
                                val err = errorMsg ?: "Unknown error downloading files"
                                Toast.makeText(ctx, "Activation failed: $err", Toast.LENGTH_LONG).show()
                                showErrorWithCopyButton("Activation Error", err)
                                updateUIState()
                            }
                        }
                    }
                )
            },
            onError = { err ->
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                    btnActivate.isEnabled = true
                    Toast.makeText(ctx, "Failed to load config: $err", Toast.LENGTH_LONG).show()
                    showErrorWithCopyButton("Configuration Needed", err)
                    updateUIState()
                }
            }
        )
    }

    private fun handleTurnOn() {
        val activity = requireActivity() as MainActivity
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
        val activity = requireActivity() as MainActivity
        btnTogglePower.isEnabled = false
        activity.executeTurnOffGlobal { result ->
            btnTogglePower.isEnabled = true
            if (result == "SUCCESS") {
                Toast.makeText(context, "Mod turned OFF 🔴", Toast.LENGTH_SHORT).show()
            } else {
                val err = RenameUtil.lastError.ifEmpty { PanelFileManager.lastLog.ifEmpty { "Check destination folders." } }
                Toast.makeText(context, "Failed to turn OFF:\n$err", Toast.LENGTH_LONG).show()
                showErrorWithCopyButton("Turn OFF Issue", "Could not turn OFF mod:\n$err")
            }
            updateUIState()
            countDownTimer?.cancel()
            tvTimer.text = "Timer: --"
        }
    }

    private fun fetchSystemStats(view: View) {
        try {
            val bm = requireContext().getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val batLevel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            tvBattery.text = "$batLevel%"
        } catch (e: Exception) {
            tvBattery.text = "--%"
        }

        try {
            val windowManager = requireContext().getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val display = windowManager.defaultDisplay
            val refreshRate = display.refreshRate
            tvFps.text = "${refreshRate.toInt()} FPS"
        } catch (e: Exception) {
            tvFps.text = "-- FPS"
        }

        try {
            val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), requireContext().packageName)
            if (mode == android.app.AppOpsManager.MODE_ALLOWED || mode == android.app.AppOpsManager.MODE_DEFAULT) {
                val usageStatsManager = requireContext().getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                val todayStart = cal.timeInMillis
                val todayEnd = System.currentTimeMillis()

                var timeToday = 0L
                val eventsToday = usageStatsManager.queryEvents(todayStart, todayEnd)
                var lastTime = todayStart
                val event = android.app.usage.UsageEvents.Event()
                var isForeground = false
                var currentForegroundPkg: String? = null

                while (eventsToday.hasNextEvent()) {
                    eventsToday.getNextEvent(event)
                    if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == 1) {
                        currentForegroundPkg = event.packageName
                    } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == 2 || event.eventType == 23) {
                        if (currentForegroundPkg == event.packageName) {
                            currentForegroundPkg = null
                        }
                    }

                    if (event.packageName == "com.dts.freefiremax") {
                        if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == 1) {
                            if (!isForeground) {
                                isForeground = true
                                lastTime = event.timeStamp
                            }
                        } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == 2 || event.eventType == 23) {
                            if (isForeground) {
                                isForeground = false
                                timeToday += (event.timeStamp - lastTime)
                            }
                        }
                    }
                }

                val isActuallyForeground = (currentForegroundPkg == "com.dts.freefiremax")
                if (isForeground) {
                    timeToday += (todayEnd - lastTime)
                }

                val hoursToday = timeToday / 3600000
                val minsToday = (timeToday % 3600000) / 60000
                tvPlaytimeToday.text = "Today: ${hoursToday}h ${minsToday}m"

                if (isActuallyForeground) {
                    tvGameStatus.text = "Active"
                    tvGameStatus.setTextColor(Color.parseColor("#00E676"))
                } else {
                    tvGameStatus.text = "Inactive"
                    tvGameStatus.setTextColor(Color.parseColor("#FF5252"))
                }

                // Yesterday stats
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                val yesterdayStart = cal.timeInMillis
                val yesterdayEnd = todayStart

                var timeYesterday = 0L
                val eventsYesterday = usageStatsManager.queryEvents(yesterdayStart, yesterdayEnd)
                var lastTimeY = yesterdayStart
                isForeground = false
                while (eventsYesterday.hasNextEvent()) {
                    eventsYesterday.getNextEvent(event)
                    if (event.packageName == "com.dts.freefiremax") {
                        if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == 1) {
                            if (!isForeground) {
                                isForeground = true
                                lastTimeY = event.timeStamp
                            }
                        } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == 2) {
                            if (isForeground) {
                                isForeground = false
                                timeYesterday += (event.timeStamp - lastTimeY)
                            }
                        }
                    }
                }
                if (isForeground) {
                    timeYesterday += (yesterdayEnd - lastTimeY)
                }

                val hoursYest = timeYesterday / 3600000
                val minsYest = (timeYesterday % 3600000) / 60000
                tvPlaytimeYesterday.text = "Yesterday: ${hoursYest}h ${minsYest}m"

            } else {
                tvPlaytimeToday.text = "Tap to enable permission"
                tvPlaytimeToday.setOnClickListener {
                    startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                tvPlaytimeYesterday.text = "Stats hidden"
            }
        } catch (e: Exception) {
            tvPlaytimeToday.text = "Feature unavailable"
        }
    }
}
