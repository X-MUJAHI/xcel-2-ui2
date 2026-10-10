package com.example.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.utils.RealPathUtil
import com.example.utils.RenameUtil
import com.example.utils.PanelFileManager
import rikka.shizuku.Shizuku
import java.io.File

class SettingsFragment : Fragment() {

    private lateinit var tvCurrentScript: TextView

    private fun processNewScript() {
        val ctx = context ?: return
        com.example.ui.ScriptSelectionDialog(
            context = ctx,
            mode = com.example.ui.ScriptSelectionDialog.Mode.INSTALL_NEW,
            onScriptSelected = { script, config ->
                if (PanelFileManager.isScriptStaged(ctx, script)) {
                    val ok = PanelFileManager.activateScriptFromStaged(ctx, script)
                    if (ok) {
                        Toast.makeText(ctx, "Switched to ${script.name}! ⚡", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "Activated ${script.name}", Toast.LENGTH_SHORT).show()
                    }
                    updateScriptDisplays()
                } else {
                    val progressDialog = android.app.AlertDialog.Builder(ctx, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                        .setTitle("Installing ${script.name}")
                        .setMessage("Downloading script files from cloud...")
                        .setCancelable(false)
                        .create()
                    progressDialog.show()

                    PanelFileManager.downloadAndStageSpecificScript(
                        context = ctx,
                        script = script,
                        turnOffFile1Url = config.turnOffFile1Url,
                        turnOffFile2Url = config.turnOffFile2Url,
                        onProgress = { status, percent ->
                            requireActivity().runOnUiThread {
                                progressDialog.setMessage("$status ($percent%)")
                            }
                        },
                        onComplete = { success, errorMsg ->
                            requireActivity().runOnUiThread {
                                if (progressDialog.isShowing) progressDialog.dismiss()
                                if (success) {
                                    Toast.makeText(ctx, "${script.name} installed & activated! ⚡", Toast.LENGTH_SHORT).show()
                                    updateScriptDisplays()
                                } else {
                                    Toast.makeText(ctx, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        ).show()
    }

    private fun updateScriptDisplays() {
        val ctx = context ?: return
        Thread {
            var current = "Not Staged"
            if (PanelFileManager.areFilesStaged(ctx)) {
                val scriptName = PanelFileManager.getActiveScriptName(ctx)
                current = if (PanelFileManager.isModOn(ctx)) "$scriptName (ON 🟢)" else "$scriptName (OFF 🔴)"
            }
            requireActivity().runOnUiThread {
                if (isAdded) {
                    tvCurrentScript.text = "Current: $current"
                }
            }
        }.start()

        PanelFileManager.fetchScriptsConfigFromFirestore(
            context = ctx,
            onSuccess = { config ->
                activity?.runOnUiThread {
                    if (isAdded) {
                        val activeId = PanelFileManager.getActiveScriptId(ctx)
                        val sb = StringBuilder("Available Scripts (${config.scripts.size}):\n")
                        config.scripts.forEach { s ->
                            val isDownloaded = PanelFileManager.isScriptStaged(ctx, s)
                            val status = if (s.id == activeId) "Active 🟢" else if (isDownloaded) "Downloaded" else "Cloud"
                            sb.append("• ${s.name} ($status)\n")
                        }
                        view?.findViewById<TextView>(R.id.tv_available_scripts)?.text = sb.toString().trim()
                    }
                }
            },
            onError = {
                // Keep default or fallback
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
        
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            val parentActivity = requireActivity() as MainActivity
            parentActivity.switchFragment(HomeFragment())
        }
        view.findViewById<ImageView>(R.id.btn_back).visibility = View.VISIBLE

        tvCurrentScript = view.findViewById(R.id.tv_current_script)
        updateScriptDisplays()

        val switchHologram = view.findViewById<Switch>(R.id.switch_hologram)
        val switchAntiBan = view.findViewById<Switch>(R.id.switch_anti_ban)
        val switchShizukuOps = view.findViewById<Switch>(R.id.switch_shizuku_ops)

        switchHologram.isChecked = prefs.getBoolean("hologram_on", false)
        switchAntiBan.isChecked = prefs.getBoolean("anti_ban_on", false)
        switchShizukuOps.isChecked = prefs.getBoolean("shizuku_ops_on", true)

        switchHologram.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("hologram_on", isChecked).apply()
        }
        switchAntiBan.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("anti_ban_on", isChecked).apply()
        }
        switchShizukuOps.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("shizuku_ops_on", isChecked).apply()
            RenameUtil.useShizukuOps = isChecked
        }

        view.findViewById<Button>(R.id.btn_new_script).setOnClickListener {
            if (!RenameUtil.shizukuAvailable()) {
                Toast.makeText(context, "Please configure/authorize Shizuku first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            com.example.utils.RewardAdManager.showRewardedAdForAction(requireActivity()) {
                processNewScript()
            }
        }

        view.findViewById<Button>(R.id.btn_shizuku_perm).setOnClickListener {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(1001)
                }
            }
        }

        view.findViewById<Button>(R.id.btn_saf_perm).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            val uri = android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", 
                "primary:Android"
            )
            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)
            startActivityForResult(intent, 2001)
        }

        view.findViewById<Button>(R.id.btn_delete_reports).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DeleteReportsFragment())
                .addToBackStack(null)
                .commit()
        }
        
        return view
    }
}
