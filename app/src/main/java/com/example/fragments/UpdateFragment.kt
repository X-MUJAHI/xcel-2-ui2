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
import com.example.MainActivity
import com.example.R

class UpdateFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_update, container, false)
        val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

        val tvVersionInfo = view.findViewById<TextView>(R.id.tv_update_version_info)
        val tvMessage = view.findViewById<TextView>(R.id.tv_update_message)
        val btnUpdate = view.findViewById<Button>(R.id.btn_update)

        val localVersion = try {
            resources.openRawResource(R.raw.version).bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) { "1.0" }

        val remoteVersion = prefs.getString("app_version", null)
        val updateUrl = prefs.getString("update_url", "")?.trim() ?: ""

        if (!remoteVersion.isNullOrBlank() && remoteVersion != localVersion) {
            tvVersionInfo.text = "Current: v$localVersion  ➜  Latest: v$remoteVersion"
        } else {
            tvVersionInfo.text = "Version: v$localVersion"
        }

        btnUpdate.setOnClickListener {
            val targetUrl = if (updateUrl.isNotEmpty()) updateUrl else "https://t.me/xcel1_panel"
            try {
                val formattedUrl = if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                    "https://$targetUrl"
                } else {
                    targetUrl
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
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
