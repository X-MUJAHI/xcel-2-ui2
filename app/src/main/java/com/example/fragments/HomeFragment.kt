package com.example.fragments

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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.ui.GlassmorphicDialogHelper
import com.example.ui.PermissionPromptType
import com.example.utils.FreezeManager
import com.example.utils.PanelFileManager
import com.example.utils.RenameUtil
import java.io.File
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
    private var tvGameStatus: TextView? = null
    private var btnCopyLog: Button? = null

    private var isOn = false
    private var countDownTimer: CountDownTimer? = null

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

        // Status diagnostics click
        tvStatus.setOnClickListener {
            showDiagnosticsDialog()
        }

        // Copy log button
        btnCopyLog?.setOnClickListener {
            copyDiagnosticsLogToClipboard()
        }

        // Version display
        try {
            val vers = resources.openRawResource(R.raw.version).bufferedReader().use { it.readText() }.trim()
            tvVersion.text = "v$vers"
        } catch (e: Exception) {
            tvVersion.text = "v1.0"
        }

        view.findViewById<Button?>(R.id.btn_telegram)?.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/xcel1_panel")))
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open Telegram link", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button?>(R.id.btn_instagram)?.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/x_celestials")))
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open Instagram link", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button?>(R.id.btn_not_working)?.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setTitle("App Not Working?")
                .setMessage("If you are facing issues, you can follow the tutorial, check system diagnostics, or contact admin for help.")
                .setPositiveButton("Tutorial") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://x-link.vercel.app/hologram-tutorial")))
                }
                .setNegativeButton("Contact Admin") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/x_celestials")))
                }
                .setNeutralButton("Cancel", null)
                .show()
        }

        btnActivate.setOnClickListener { 
            val ctx = context ?: return@setOnClickListener
            if (FreezeManager.isCurrentlyFrozen(ctx)) {
                val reason = FreezeManager.getFreezeReason(ctx)
                Toast.makeText(ctx, "❄️ Server is currently frozen by admin:\n$reason", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            com.example.utils.RewardAdManager.showRewardedAdForAction(requireActivity()) {
                showActivateScriptSelection()
            }
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
                com.example.utils.RewardAdManager.showRewardedAdForAction(requireActivity()) {
                    turnOnLastUsedScript()
                }
            }
        }

        btnTogglePower.setOnLongClickListener {
            val ctx = context ?: return@setOnLongClickListener true
            if (FreezeManager.isCurrentlyFrozen(ctx)) {
                val reason = FreezeManager.getFreezeReason(ctx)
                Toast.makeText(ctx, "❄️ Server is currently frozen by admin:\n$reason", Toast.LENGTH_LONG).show()
                return@setOnLongClickListener true
            }
            if (!isOn) {
                Toast.makeText(ctx, "Select script to switch...", Toast.LENGTH_SHORT).show()
                showTurnOnScriptSelection()
            }
            true
        }

        btnBoost.setOnClickListener {
            // Bounce animation
            btnBoost.animate()
                .scaleX(0.9f).scaleY(0.9f)
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
                    activity?.runOnUiThread {
                        if (success) {
                            Toast.makeText(context, "Device Boosted! Caches cleared.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to clear caches.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Shizuku is required for Boost feature.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(ctx, "Free Fire MAX not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (userType == "NORMAL") {
                // Check usage stats permission
                val appOps = requireContext().getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
                    android.os.Process.myUid(), 
                    requireContext().packageName
                )
                if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                    GlassmorphicDialogHelper.showPermissionPrompt(
                        requireActivity(),
                        PermissionPromptType.USAGE_ACCESS,
                        onPrimary = {
                            startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        onSecondary = {
                            com.example.utils.RewardAdManager.showRewardedAdForAction(requireActivity()) {
                                showUidSelectionDialog(launchIntent)
                            }
                        }
                    )
                    return@setOnClickListener
                }
                
                com.example.utils.RewardAdManager.showRewardedAdForAction(requireActivity()) {
                    showUidSelectionDialog(launchIntent)
                }
            } else {
                startActivity(launchIntent)
            }
        }

        view.findViewById<ImageButton?>(R.id.btn_refresh)?.setOnClickListener {
            updateUIState()
            fetchSystemStats(view)
            Toast.makeText(context, "Refreshed ✓", Toast.LENGTH_SHORT).show()
        }

        fetchSystemStats(view)
        return view
    }

    private fun showDiagnosticsDialog() {
        Thread {
            val ctx = context ?: return@Thread
            val act = PanelFileManager.areFilesStaged(ctx)
            val (t1, t2) = PanelFileManager.getTargetPaths(ctx)
            val d1Exists = RenameUtil.checkDirExists(t1.trimEnd('/'))
            val d2Exists = RenameUtil.checkDirExists(t2.trimEnd('/'))
            val shizukuOk = RenameUtil.shizukuAvailable()
            val isModOn = PanelFileManager.isModOn(ctx)
            
            val msg = "System Diagnostics:\n\n" +
                    "• Shizuku Authorized: $shizukuOk\n" +
                    "• Files Activated (Staged): $act\n" +
                    "• Mod State: ${if (isModOn) "ON 🟢" else "OFF 🔴"}\n\n" +
                    "• Target Path 1:\n  $t1\n  (Dir Exists: $d1Exists)\n\n" +
                    "• Target Path 2:\n  $t2\n  (Dir Exists: $d2Exists)\n\n" +
                    "• Last Error/Log:\n  ${PanelFileManager.lastLog.ifEmpty { RenameUtil.lastError.ifEmpty { "None" } }}"
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                    .setTitle("System Diagnostics")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Re-Download Files") { _, _ ->
                        startDownloadActivation()
                    }
                    .show()
            }
        }.start()
    }

    private fun copyDiagnosticsLogToClipboard() {
        Thread {
            val ctx = context ?: return@Thread
            val act = PanelFileManager.areFilesStaged(ctx)
            val (t1, t2) = PanelFileManager.getTargetPaths(ctx)
            val d1Exists = RenameUtil.checkDirExists(t1.trimEnd('/'))
            val d2Exists = RenameUtil.checkDirExists(t2.trimEnd('/'))
            val shizukuOk = RenameUtil.shizukuAvailable()
            val isModOn = PanelFileManager.isModOn(ctx)
            
            val sb = StringBuilder()
            sb.append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append("\n")
            sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            sb.append("Shizuku: ").append(shizukuOk).append("\n")
            sb.append("Files Staged: ").append(act).append("\n")
            sb.append("Mod State: ").append(if (isModOn) "ON" else "OFF").append("\n")
            sb.append("Target 1: ").append(t1).append(" (Exists: ").append(d1Exists).append(")\n")
            sb.append("Target 2: ").append(t2).append(" (Exists: ").append(d2Exists).append(")\n")
            sb.append("Last Log: ").append(PanelFileManager.lastLog.ifEmpty { RenameUtil.lastError.ifEmpty { "None" } }).append("\n")

            activity?.runOnUiThread {
                try {
                    val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Diagnostics Log", sb.toString()))
                    Toast.makeText(context, "Diagnostics log copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to copy log", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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
                        activity?.runOnUiThread { 
                            if (isAdded) tvPing.text = "${time}ms" 
                        }
                    } else {
                        activity?.runOnUiThread { 
                            if (isAdded) tvPing.text = "Error" 
                        }
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread { 
                        if (isAdded) tvPing.text = "Error" 
                    }
                }
            }.start()
            pingHandler.postDelayed(this, 3000)
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
        
        val dialog = android.app.AlertDialog.Builder(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val listView = dialogView.findViewById<android.widget.ListView>(R.id.list_uids)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_loading_uids)
        val tvNoUids = dialogView.findViewById<TextView>(R.id.tv_no_uids)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_dialog)
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        progressBar.visibility = View.VISIBLE
        listView.visibility = View.GONE
        tvNoUids.visibility = View.GONE
        
        dialog.show()
        
        Thread {
            val uids = RenameUtil.getGameUIDs()
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                progressBar.visibility = View.GONE
                if (uids.isEmpty()) {
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                    startActivity(launchIntent)
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
        
        val progressDialog = android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("Anti-Ban Active")
            .setMessage("Applying protections and launching game...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        val activity = requireActivity() as MainActivity
        activity.executeTurnOnGlobal { success ->
            updateUIState()
            
            // Calculate remaining time to wait (min 3 seconds total)
            val elapsed = System.currentTimeMillis() - startTime
            val remainingWait = Math.max(0L, 3000L - elapsed)
            
            Handler(Looper.getMainLooper()).postDelayed({
                if (progressDialog.isShowing) {
                    progressDialog.dismiss()
                }
                if (success) {
                    Toast.makeText(context, "Anti-Ban applied! Launching...", Toast.LENGTH_SHORT).show()
                    
                    // Start GameMonitorService
                    val serviceIntent = Intent(requireContext(), com.example.services.GameMonitorService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(serviceIntent)
                    } else {
                        requireContext().startService(serviceIntent)
                    }
                    
                    val rootActivity = activity as? MainActivity
                    val inAppTimerView = rootActivity?.findViewById<View>(R.id.floating_inapp_timer)
                    com.example.utils.FloatingTimerManager.start(requireContext(), inAppTimerView)

                    startActivity(launchIntent)
                } else {
                    val err = RenameUtil.lastError.ifEmpty { "Please activate panel first." }
                    Toast.makeText(context, "Could not turn on: $err", Toast.LENGTH_LONG).show()
                }
            }, remainingWait)
        }
    }

    private var statusAnimator: android.animation.ObjectAnimator? = null
    private var glowAnimator: android.animation.ObjectAnimator? = null

    private fun updateUIState() {
        val ctx = context ?: return
        Thread {
            val isStaged = PanelFileManager.areFilesStaged(ctx)
            val isModActive = if (isStaged) PanelFileManager.isModOn(ctx) else false
            val isFrozen = FreezeManager.isCurrentlyFrozen(ctx)

            activity?.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread

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
                    // Files are staged & activated
                    btnActivate.visibility = View.GONE
                    btnTogglePower.visibility = View.VISIBLE
                    btnTogglePower.isEnabled = true

                    val activeScriptName = PanelFileManager.getActiveScriptName(ctx)
                    if (isModActive) {
                        isOn = true
                        tvStatus.text = "Status: ON 🟢 ($activeScriptName)"
                        tvStatus.setTextColor(Color.parseColor("#00E676"))
                        
                        btnTogglePower.text = "TURN OFF"
                        btnTogglePower.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))
                        btnTogglePower.setTextColor(Color.WHITE)
                        
                        statusAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                            tvStatus,
                            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.05f),
                            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.05f)
                        ).apply {
                            duration = 1000
                            repeatCount = android.animation.ObjectAnimator.INFINITE
                            repeatMode = android.animation.ObjectAnimator.REVERSE
                            start()
                        }
                        
                        glowAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                            btnTogglePower,
                            android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 1.0f),
                            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.98f, 1.02f),
                            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.98f, 1.02f)
                        ).apply {
                            duration = 1000
                            repeatCount = android.animation.ObjectAnimator.INFINITE
                            repeatMode = android.animation.ObjectAnimator.REVERSE
                            interpolator = android.view.animation.PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
                            start()
                        }
                    } else {
                        isOn = false
                        tvStatus.text = "Status: OFF 🔴 ($activeScriptName)"
                        tvStatus.setTextColor(Color.parseColor("#FF5252"))

                        btnTogglePower.text = "TURN ON"
                        btnTogglePower.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                        btnTogglePower.setTextColor(Color.parseColor("#151A22"))
                    }
                }
            }
        }.start()
    }

    private fun startTimerUpdate() {
        countDownTimer?.cancel()
        tvTimer.text = "Timer: Unlimited"
    }

    private fun showActivateScriptSelection() {
        val ctx = context ?: return
        com.example.ui.ScriptSelectionDialog(
            context = ctx,
            mode = com.example.ui.ScriptSelectionDialog.Mode.ACTIVATE_FIRST_TIME,
            onScriptSelected = { script, config ->
                startDownloadActivation(script, config)
            }
        ).show()
    }

    private fun startDownloadActivation(
        selectedScript: PanelFileManager.ScriptItem? = null,
        config: PanelFileManager.ScriptsConfig? = null
    ) {
        val ctx = context ?: return
        if (FreezeManager.isCurrentlyFrozen(ctx)) {
            val reason = FreezeManager.getFreezeReason(ctx)
            Toast.makeText(ctx, "❄️ Server is currently frozen by admin:\n$reason", Toast.LENGTH_LONG).show()
            return
        }

        if (selectedScript == null || config == null) {
            showActivateScriptSelection()
            return
        }

        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = false
        progressBar.progress = 0
        tvProgress.visibility = View.VISIBLE
        tvProgress.text = "Downloading ${selectedScript.name}..."
        btnActivate.isEnabled = false

        PanelFileManager.downloadAndStageSpecificScript(
            context = ctx,
            script = selectedScript,
            turnOffFile1Url = config.turnOffFile1Url,
            turnOffFile2Url = config.turnOffFile2Url,
            onProgress = { status, percent ->
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    tvProgress.text = status
                    progressBar.isIndeterminate = (percent <= 0)
                    progressBar.progress = percent
                }
            },
            onComplete = { success, errorMsg ->
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                    btnActivate.isEnabled = true
                    if (success) {
                        Toast.makeText(ctx, "Activation successful! ${selectedScript.name} ready.", Toast.LENGTH_LONG).show()
                        updateUIState()
                    } else {
                        val err = errorMsg ?: "Unknown error downloading files"
                        Toast.makeText(ctx, "Activation failed: $err", Toast.LENGTH_LONG).show()
                        android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                            .setTitle("Activation Error")
                            .setMessage(err)
                            .setPositiveButton("OK", null)
                            .show()
                        updateUIState()
                    }
                }
            }
        )
    }

    private fun turnOnLastUsedScript() {
        val ctx = context ?: return
        val activeName = PanelFileManager.getActiveScriptName(ctx)

        // Show progress dialog while deleting old and downloading fresh script files
        val progressDialog = android.app.AlertDialog.Builder(ctx, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("Updating $activeName")
            .setMessage("Deleting old files & downloading latest...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        // 1. Delete old target files
        PanelFileManager.deleteOldTargetFiles(ctx)

        // 2. Download fresh script files
        PanelFileManager.refreshActiveScriptFiles(
            context = ctx,
            onProgress = { status, percent ->
                activity?.runOnUiThread {
                    if (progressDialog.isShowing) {
                        progressDialog.setMessage("$status ($percent%)")
                    }
                }
            },
            onComplete = { success, errorMsg ->
                activity?.runOnUiThread {
                    if (progressDialog.isShowing) {
                        try { progressDialog.dismiss() } catch (ignored: Exception) {}
                    }
                    if (success) {
                        handleTurnOn()
                    } else {
                        // If network failed but files are staged, fallback to staged files
                        if (PanelFileManager.areFilesStaged(ctx)) {
                            Toast.makeText(ctx, "Download failed, using cached script: $errorMsg", Toast.LENGTH_SHORT).show()
                            handleTurnOn()
                        } else {
                            val err = errorMsg ?: "Failed to download script files"
                            Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
                            android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                                .setTitle("Download Error")
                                .setMessage(err)
                                .setPositiveButton("OK", null)
                                .show()
                            updateUIState()
                        }
                    }
                }
            }
        )
    }

    private fun showTurnOnScriptSelection() {
        val ctx = context ?: return
        com.example.ui.ScriptSelectionDialog(
            context = ctx,
            mode = com.example.ui.ScriptSelectionDialog.Mode.TURN_ON,
            onScriptSelected = { script, config ->
                if (PanelFileManager.isScriptStaged(ctx, script)) {
                    PanelFileManager.activateScriptFromStaged(ctx, script)
                    handleTurnOn()
                } else {
                    val progressDialog = android.app.AlertDialog.Builder(ctx, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                        .setTitle("Downloading ${script.name}")
                        .setMessage("Preparing script files...")
                        .setCancelable(false)
                        .create()
                    progressDialog.show()

                    PanelFileManager.downloadAndStageSpecificScript(
                        context = ctx,
                        script = script,
                        turnOffFile1Url = config.turnOffFile1Url,
                        turnOffFile2Url = config.turnOffFile2Url,
                        onProgress = { status, percent ->
                            activity?.runOnUiThread {
                                progressDialog.setMessage("$status ($percent%)")
                            }
                        },
                        onComplete = { success, errorMsg ->
                            activity?.runOnUiThread {
                                if (progressDialog.isShowing) progressDialog.dismiss()
                                if (success) {
                                    handleTurnOn()
                                } else {
                                    Toast.makeText(ctx, "Failed to download script: $errorMsg", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        ).show()
    }

    private fun handleTurnOn() {
        val activity = requireActivity() as MainActivity
        val ctx = context ?: return
        btnTogglePower.isEnabled = false
        
        // Reset and track session
        val prefs = ctx.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("session_start_time", System.currentTimeMillis())
            .putLong("game_active_time_ms", 0L)
            .apply()

        activity.executeTurnOnGlobal { success ->
            btnTogglePower.isEnabled = true
            if (success) {
                Toast.makeText(context, "Mod turned ON", Toast.LENGTH_SHORT).show()
                startTimerUpdate()
                val rootActivity = activity as? MainActivity
                val inAppTimerView = rootActivity?.findViewById<View>(R.id.floating_inapp_timer)
                com.example.utils.FloatingTimerManager.start(ctx, inAppTimerView)
            } else {
                val err = RenameUtil.lastError.ifEmpty { "Check destination folders." }
                Toast.makeText(context, "Failed to turn ON:\n$err", Toast.LENGTH_LONG).show()
                android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                    .setTitle("Turn ON Error")
                    .setMessage(err)
                    .setPositiveButton("OK", null)
                    .show()
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
                Toast.makeText(context, "Mod turned OFF", Toast.LENGTH_SHORT).show()
            } else {
                val err = RenameUtil.lastError.ifEmpty { "Check destination folders." }
                Toast.makeText(context, "Failed to turn OFF:\n$err", Toast.LENGTH_LONG).show()
                android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                    .setTitle("Turn OFF Error")
                    .setMessage(err)
                    .setPositiveButton("OK", null)
                    .show()
            }
            updateUIState()
            countDownTimer?.cancel()
            tvTimer.text = "Timer: --"
            com.example.utils.FloatingTimerManager.stop(context)
        }
    }

    private fun fetchSystemStats(view: View) {
        // Battery
        try {
            val bm = requireContext().getSystemService(android.content.Context.BATTERY_SERVICE) as BatteryManager
            val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            tvBattery.text = "$batLevel%"
        } catch (e: Exception) {
            tvBattery.text = "--%"
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
            tvFps.text = "$refreshRate FPS"
        } catch (e: Exception) {
            tvFps.text = "60 FPS"
        }

        // Playtime & Game status
        try {
            val appOps = requireContext().getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
                android.os.Process.myUid(), 
                requireContext().packageName
            )
            if (mode == android.app.AppOpsManager.MODE_ALLOWED || mode == android.app.AppOpsManager.MODE_DEFAULT) {
                val usageStatsManager = requireContext().getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val cal = java.util.Calendar.getInstance()
                // today
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

                tvGameStatus?.let { statusView ->
                    if (isActuallyForeground) {
                        statusView.text = "Active"
                        statusView.setTextColor(Color.parseColor("#4CAF50"))
                    } else {
                        statusView.text = "Inactive"
                        statusView.setTextColor(Color.parseColor("#EF4444"))
                    }
                }

                // yesterday
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
                tvPlaytimeToday.text = "Tap to enable required permission"
                tvPlaytimeToday.setOnClickListener {
                    startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                tvPlaytimeYesterday.text = "Stats hidden"
            }
        } catch (e: Exception) {
            tvPlaytimeToday.text = "Permission missing or feature not available"
        }
    }
}
