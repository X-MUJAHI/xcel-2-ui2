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
        
        android.app.AlertDialog.Builder(ctx, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("Re-Download / Update Files")
            .setMessage("This will fetch the latest 4 files from Firestore and store them safely. Continue?")
            .setPositiveButton("Yes") { _, _ ->
                val progressDialog = android.app.AlertDialog.Builder(ctx)
                    .setTitle("Syncing Files")
                    .setMessage("Fetching manifest from Firestore...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()
        
                PanelFileManager.fetchManifestFromFirestore(
                    context = ctx,
                    onSuccess = { manifest ->
                        requireActivity().runOnUiThread { progressDialog.setMessage("Downloading 4 files...") }
                        PanelFileManager.downloadAndStageAllFiles(
                            context = ctx,
                            manifest = manifest,
                            onProgress = { status, percent ->
                                requireActivity().runOnUiThread { progressDialog.setMessage("$status ($percent%)") }
                            },
                            onComplete = { success, errorMsg ->
                                requireActivity().runOnUiThread {
                                    if (progressDialog.isShowing) progressDialog.dismiss()
                                    if (success) {
                                        Toast.makeText(ctx, "Files updated & staged successfully!", Toast.LENGTH_SHORT).show()
                                        parentFragmentManager.beginTransaction()
                                            .detach(this@SettingsFragment)
                                            .attach(this@SettingsFragment)
                                            .commit()
                                    } else {
                                        Toast.makeText(ctx, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    },
                    onError = { err ->
                        requireActivity().runOnUiThread {
                            if (progressDialog.isShowing) progressDialog.dismiss()
                            Toast.makeText(ctx, "Config Error: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            .setNegativeButton("No", null)
            .show()
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
        val tvAvailableScripts = view.findViewById<TextView>(R.id.tv_available_scripts)

        Thread {
            val ctx = context
            var current = "Not Staged"
            if (ctx != null && PanelFileManager.areFilesStaged(ctx)) {
                current = if (PanelFileManager.isModOn(ctx)) "Mod (ON 🟢)" else "Mod (OFF 🔴)"
            }
            
            requireActivity().runOnUiThread {
                tvCurrentScript.text = "Current: $current"
                tvAvailableScripts.text = "Available Scripts:\n- 4-File Managed Profile"
            }
        }.start()

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
            processNewScript()
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
