package com.example.fragments

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil

class DeleteReportsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_delete_reports, container, false)
        view.findViewById<View?>(R.id.btn_back)?.setOnClickListener {
            (activity as? MainActivity)?.switchFragment(HomeFragment())
        }

        val btnDelete = view.findViewById<Button?>(R.id.btn_delete_reports_action)
        btnDelete?.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_delete_loading, null)
            val dialog = AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setView(dialogView)
                .setCancelable(false)
                .create()
            dialog.show()

            val pb = dialogView.findViewById<ProgressBar?>(R.id.progress_bar)
            val tvMsg = dialogView.findViewById<TextView?>(R.id.tv_message)
            tvMsg?.text = "Cleaning Free Fire report files..."
            val handler = Handler(Looper.getMainLooper())

            Thread {
                val paths = listOf(
                    "/storage/emulated/0/Android/data/com.dts.freefiremax/files/report",
                    "/storage/emulated/0/Android/data/com.dts.freefiremax/files/crash",
                    "/storage/emulated/0/Android/data/com.dts.freefireth/files/report",
                    "/storage/emulated/0/Android/data/com.dts.freefireth/files/crash"
                )
                for (p in paths) {
                    if (RenameUtil.useShizukuOps && RenameUtil.shizukuAvailable()) {
                        RenameUtil.executeShizukuCommand("rm -rf '$p'")
                    }
                }

                handler.postDelayed({
                    dialog.dismiss()
                    (activity as? MainActivity)?.switchFragment(HomeFragment())
                }, 1500)
            }.start()
        }

        return view
    }
}
