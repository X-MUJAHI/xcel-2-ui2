package com.example.fragments

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private lateinit var tvKey: TextView
    private lateinit var btnChange: Button
    private lateinit var btnLogout: Button
    private var btnCopyKey: Button? = null
    private var btnInstagram: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        tvKey = view.findViewById(R.id.tv_key)
        btnChange = view.findViewById(R.id.btn_change_key)
        btnLogout = view.findViewById(R.id.btn_logout_key)
        btnCopyKey = view.findViewById(R.id.btn_copy_current_key)
        btnInstagram = view.findViewById(R.id.btn_profile_instagram)

        loadKeyDisplay()

        btnCopyKey?.setOnClickListener {
            val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
            val key = prefs.getString(MainActivity.KEY_SAVED_KEY, null)
            if (!key.isNullOrEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Xcel Key", key)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Key copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No key available to copy", Toast.LENGTH_SHORT).show()
            }
        }

        btnInstagram?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/x_celestials")))
        }

        btnChange.setOnClickListener { showChangeKeyDialog() }
        btnLogout.setOnClickListener {
            val mainActivity = requireActivity() as? MainActivity
            mainActivity?.forceTurnOffIfNeeded {
                val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
                prefs.edit().remove(MainActivity.KEY_SAVED_KEY).remove(MainActivity.KEY_USER_TYPE).apply()
                loadKeyDisplay()
                Toast.makeText(context, "Key removed. Restart the app or enter a new key.", Toast.LENGTH_LONG).show()
                showChangeKeyDialog()
            }
        }

        return view
    }

    private fun loadKeyDisplay() {
        val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
        val key = prefs.getString(MainActivity.KEY_SAVED_KEY, null)
        if (key != null) {
            val masked = if (key.length >= 2) "***${key.substring(key.length - 2)}" else key
            tvKey.text = "Current Key: $masked"
        } else {
            tvKey.text = "No key stored"
        }
    }

    private fun showChangeKeyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_key_input, null)
        val editText = dialogView.findViewById<EditText>(R.id.et_key)

        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
                val pasteText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!pasteText.isNullOrEmpty()) {
                    editText.setText(pasteText)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("Enter New Key")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("ACTIVATE") { dialog, _ ->
                val newKey = editText.text.toString().trim()
                if (newKey.isNotEmpty()) {
                    validateAndApplyKey(newKey)
                }
                dialog.dismiss()
            }
            .setNeutralButton("INSTAGRAM") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/x_celestials"))
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validateAndApplyKey(newKey: String) {
        if (newKey == "mujahi@admin") {
            applyNewKey("ADMIN", newKey)
            return
        }
        val androidId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        FirebaseFirestore.getInstance().collection("users").document(newKey).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val isActive = doc.getBoolean("isActive") ?: false
                if (!isActive) {
                    Toast.makeText(context, "Key is deactivated by admin.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val expireTime = doc.getTimestamp("expireDate")?.toDate()?.time ?: Long.MAX_VALUE
                if (System.currentTimeMillis() > expireTime) {
                    Toast.makeText(context, "Key is expired.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val devices = doc.get("devices") as? MutableList<String> ?: mutableListOf()
                val maxDevices = doc.getLong("maxDevices")?.toInt() ?: 1
                
                if (!devices.contains(androidId)) {
                    if (devices.size >= maxDevices) {
                        Toast.makeText(context, "Device limit reached for this key.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    devices.add(androidId)
                    doc.reference.update("devices", devices)
                }

                val userType = doc.getString("role") ?: "NORMAL"
                applyNewKey(userType, newKey)
            } else {
                Toast.makeText(context, "Invalid key", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Error verifying key", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyNewKey(userType: String, key: String) {
        val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
        val oldType = prefs.getString(MainActivity.KEY_USER_TYPE, null)
        
        if (oldType == "ADMIN" && userType == "NORMAL") {
            (requireActivity() as MainActivity).forceTurnOffIfNeeded {
                prefs.edit().putString(MainActivity.KEY_USER_TYPE, userType)
                    .putString(MainActivity.KEY_SAVED_KEY, key).apply()
                loadKeyDisplay()
                Toast.makeText(context, "Key changed", Toast.LENGTH_SHORT).show()
            }
        } else {
            prefs.edit().putString(MainActivity.KEY_USER_TYPE, userType)
                .putString(MainActivity.KEY_SAVED_KEY, key).apply()
            loadKeyDisplay()
            Toast.makeText(context, "Key changed", Toast.LENGTH_SHORT).show()
        }
    }
}
