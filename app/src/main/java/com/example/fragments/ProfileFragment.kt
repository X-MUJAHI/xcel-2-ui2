package com.example.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        val prefs = requireActivity().getSharedPreferences("xcel_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("saved_key", "None") ?: "None"

        val tvKey = view.findViewById<TextView>(R.id.tv_key)
        val btnCopyKey = view.findViewById<Button>(R.id.btn_copy_current_key)
        val btnChangeKey = view.findViewById<Button>(R.id.btn_change_key)
        val btnLogoutKey = view.findViewById<Button>(R.id.btn_logout_key)
        val btnInstagram = view.findViewById<Button>(R.id.btn_profile_instagram)

        tvKey.text = savedKey

        btnCopyKey.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("License Key", savedKey))
            Toast.makeText(context, "Key copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
        }

        btnChangeKey.setOnClickListener {
            prefs.edit().remove("saved_key").remove("user_type").apply()
            Toast.makeText(context, "Key cleared. Please restart app to enter new key.", Toast.LENGTH_LONG).show()
            (activity as? MainActivity)?.switchFragment(HomeFragment())
        }

        btnLogoutKey.setOnClickListener {
            AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setTitle("Logout Key")
                .setMessage("Are you sure you want to remove your saved key from this device?")
                .setPositiveButton("Logout") { _, _ ->
                    prefs.edit().remove("saved_key").remove("user_type").apply()
                    Toast.makeText(context, "Logged out successfully.", Toast.LENGTH_SHORT).show()
                    (activity as? MainActivity)?.switchFragment(HomeFragment())
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnInstagram.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/x_celestials")))
            } catch (_: Exception) {}
        }

        return view
    }
}
