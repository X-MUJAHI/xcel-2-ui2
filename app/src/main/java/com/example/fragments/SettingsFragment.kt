package com.example.fragments

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.utils.FreezeManager
import com.example.utils.PanelFileManager
import com.example.utils.RenameUtil
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private var tvCurrentScript: TextView? = null
    private var tvAvailable: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val prefs = requireActivity().getSharedPreferences("xcel_prefs", Context.MODE_PRIVATE)

        view.findViewById<View?>(R.id.btn_back)?.setOnClickListener {
            (activity as? MainActivity)?.switchFragment(HomeFragment())
        }

        tvCurrentScript = view.findViewById(R.id.tv_current_script)
        tvAvailable = view.findViewById(R.id.tv_available_scripts)
        val btnNewScript = view.findViewById<Button?>(R.id.btn_new_script)
        val btnShizukuPerm = view.findViewById<Button?>(R.id.btn_shizuku_perm)
        val btnSafPerm = view.findViewById<Button?>(R.id.btn_saf_perm)
        val swHologram = view.findViewById<Switch?>(R.id.switch_hologram)
        val swAntiBan = view.findViewById<Switch?>(R.id.switch_anti_ban)
        val swShizuku = view.findViewById<Switch?>(R.id.switch_shizuku_ops)
        val btnDeleteReports = view.findViewById<Button?>(R.id.btn_delete_reports)

        updateScriptInfo()

        swShizuku?.isChecked = prefs.getBoolean("shizuku_ops_on", true)
        swShizuku?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("shizuku_ops_on", isChecked).apply()
            RenameUtil.useShizukuOps = isChecked
            Toast.makeText(context, if (isChecked) "Shizuku Fast Execution ON" else "Shizuku Fast Execution OFF", Toast.LENGTH_SHORT).show()
        }

        swHologram?.isChecked = prefs.getBoolean("switch_hologram_on", true)
        swHologram?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("switch_hologram_on", isChecked).apply()
        }

        swAntiBan?.isChecked = prefs.getBoolean("switch_anti_ban_on", true)
        swAntiBan?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("switch_anti_ban_on", isChecked).apply()
        }

        btnDeleteReports?.setOnClickListener {
            (activity as? MainActivity)?.switchFragment(DeleteReportsFragment())
        }

        btnShizukuPerm?.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (RenameUtil.shizukuAvailable()) {
                Toast.makeText(ctx, "Shizuku is authorized and active! ✓", Toast.LENGTH_SHORT).show()
            } else if (Shizuku.pingBinder()) {
                try {
                    Shizuku.requestPermission(1001)
                    Toast.makeText(ctx, "Requesting Shizuku authorization...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Error requesting Shizuku: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(ctx, "Shizuku service is not running. Please start Shizuku app first.", Toast.LENGTH_LONG).show()
            }
        }

        btnSafPerm?.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(ctx, "All Files Access already granted! ✓", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                        }
                        startActivity(intent)
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
            } else {
                Toast.makeText(ctx, "Storage Access configured. ✓", Toast.LENGTH_SHORT).show()
            }
        }

        // "+ INSTALL NEW SCRIPT" -> Redownload and replace scripts from ACTIVATE FIRST TIME
        btnNewScript?.setOnClickListener {
            handleInstallOrReplaceScript()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        updateScriptInfo()
    }

    private fun updateScriptInfo() {
        val ctx = context ?: return
        val isStaged = PanelFileManager.areFilesStaged(ctx)
        val f1 = PanelFileManager.getFile1Name(ctx)
        val f2 = PanelFileManager.getFile2Name(ctx)
        val prefs = ctx.getSharedPreferences("xcel_prefs", Context.MODE_PRIVATE)
        val lastUpdated = prefs.getString("scripts_last_updated", null)

        if (isStaged) {
            val scriptName = if (f1.isNotEmpty()) f1 else "V4_OPTIMIZED"
            val updateInfo = if (!lastUpdated.isNullOrEmpty()) " (Replaced: $lastUpdated)" else ""
            tvCurrentScript?.text = "Active Script: $scriptName$updateInfo"
            val fileList = if (f2.isNotEmpty()) "$scriptName, $f2" else scriptName
            tvAvailable?.text = "Available Scripts:\n• $fileList (Staged in Local Storage)"
        } else {
            tvCurrentScript?.text = "Active Script: None (Not Installed)"
            tvAvailable?.text = "Available Scripts:\nTap '+ INSTALL NEW SCRIPT' to download from server"
        }
    }

    private fun handleInstallOrReplaceScript() {
        val ctx = context ?: return
        if (FreezeManager.isCurrentlyFrozen(ctx)) {
            val reason = FreezeManager.getFreezeReason(ctx)
            Toast.makeText(ctx, "❄️ Action blocked: Server is frozen by admin.\n$reason", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(ctx, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("Install / Replace Scripts")
            .setMessage("This will redownload the latest game booster files from the server and replace all current staged scripts. Continue?")
            .setPositiveButton("REPLACE NOW") { _, _ ->
                startRedownloadAndReplace()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun startRedownloadAndReplace() {
        val ctx = context ?: return

        // Build cyber-styled progress dialog
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }
        val tvMsg = TextView(ctx).apply {
            text = "Connecting to server..."
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            progressTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            setPadding(0, 24, 0, 10)
        }
        layout.addView(tvMsg)
        layout.addView(pb)

        val dialog = AlertDialog.Builder(ctx, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("Updating Scripts...")
            .setView(layout)
            .setCancelable(false)
            .create()
        dialog.show()

        PanelFileManager.fetchManifestFromFirestore(
            ctx,
            onSuccess = { manifest ->
                activity?.runOnUiThread {
                    pb.isIndeterminate = false
                }
                PanelFileManager.downloadAndStageAllFiles(
                    ctx,
                    manifest,
                    onProgress = { status, percent ->
                        activity?.runOnUiThread {
                            tvMsg.text = "$status ($percent%)"
                            pb.progress = percent
                        }
                    },
                    onComplete = { success, errorMsg ->
                        activity?.runOnUiThread {
                            dialog.dismiss()
                            if (success) {
                                val timeStr = SimpleDateFormat("HH:mm:ss, MMM dd", Locale.US).format(Date())
                                ctx.getSharedPreferences("xcel_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("scripts_last_updated", timeStr)
                                    .apply()

                                updateScriptInfo()
                                Toast.makeText(ctx, "✓ Scripts redownloaded and replaced successfully!", Toast.LENGTH_LONG).show()
                            } else {
                                val err = errorMsg ?: "Unknown error"
                                Toast.makeText(ctx, "Failed to replace scripts: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            },
            onError = { err ->
                activity?.runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(ctx, "Could not fetch configuration: $err", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
