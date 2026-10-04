package com.example.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil

class SettingsFragment : Fragment() {

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

        val tvCurrentScript = view.findViewById<TextView?>(R.id.tv_current_script)
        val tvAvailable = view.findViewById<TextView?>(R.id.tv_available_scripts)
        val btnNewScript = view.findViewById<Button?>(R.id.btn_new_script)
        val btnShizukuPerm = view.findViewById<Button?>(R.id.btn_shizuku_perm)
        val btnSafPerm = view.findViewById<Button?>(R.id.btn_saf_perm)
        val swHologram = view.findViewById<Switch?>(R.id.switch_hologram)
        val swAntiBan = view.findViewById<Switch?>(R.id.switch_anti_ban)
        val swShizuku = view.findViewById<Switch?>(R.id.switch_shizuku_ops)
        val btnDeleteReports = view.findViewById<Button?>(R.id.btn_delete_reports)

        tvCurrentScript?.text = "Active Script: V4_OPTIMIZED"
        tvAvailable?.text = "Available: 1 Preset"

        swShizuku?.isChecked = prefs.getBoolean("shizuku_ops_on", true)
        swShizuku?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("shizuku_ops_on", isChecked).apply()
            RenameUtil.useShizukuOps = isChecked
            Toast.makeText(context, if (isChecked) "Shizuku Ops ON" else "Shizuku Ops OFF", Toast.LENGTH_SHORT).show()
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
            if (RenameUtil.shizukuAvailable()) {
                Toast.makeText(context, "Shizuku is authorized and active! ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Please start Shizuku app and grant permission.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSafPerm?.setOnClickListener {
            Toast.makeText(context, "Storage Access Framework configured. ✓", Toast.LENGTH_SHORT).show()
        }

        btnNewScript?.setOnClickListener {
            Toast.makeText(context, "Select ZIP script to install", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
