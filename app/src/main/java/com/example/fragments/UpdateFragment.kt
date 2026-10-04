package com.example.fragments

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
import androidx.fragment.app.Fragment
import com.example.R
import java.io.BufferedReader
import java.io.InputStreamReader

class UpdateFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_update, container, false)
        val prefs = requireActivity().getSharedPreferences("xcel_prefs", Context.MODE_PRIVATE)
        val tvVersionInfo = view.findViewById<TextView>(R.id.tv_update_version_info)
        val btnUpdate = view.findViewById<Button>(R.id.btn_update)

        var currentVer = "1.0"
        try {
            val stream = resources.openRawResource(R.raw.version)
            val reader = BufferedReader(InputStreamReader(stream))
            currentVer = reader.readLine().trim()
            reader.close()
        } catch (_: Exception) {}

        val latestVer = prefs.getString("app_version", null)
        val updateUrl = prefs.getString("update_url", "https://t.me/xcel1_panel")?.trim() ?: "https://t.me/xcel1_panel"

        if (!latestVer.isNullOrBlank() && latestVer != currentVer) {
            tvVersionInfo.text = "Current: v$currentVer  ➔  Latest: v$latestVer"
        } else {
            tvVersionInfo.text = "Version: v$currentVer"
        }

        btnUpdate.setOnClickListener {
            var url = updateUrl
            if (url.isEmpty()) url = "https://t.me/xcel1_panel"
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open update link: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }
}
